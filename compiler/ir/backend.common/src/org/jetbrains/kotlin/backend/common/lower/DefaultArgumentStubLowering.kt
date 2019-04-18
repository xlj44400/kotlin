/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower

import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.backend.common.ir.*
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

val defaultArgumentStubPhase = makeIrFilePhase(
    ::DefaultArgumentStubLowering,
    name = "DefaultArgumentsStubs",
    description = "Generate synthetic stubs for functions with default parameter values"
)

val defaultParameterInjectorPhase = makeIrFilePhase(
    ::DefaultParameterInjector,
    name = "DefaultParameterInjector",
    description = "Replace call site with default parameters with corresponding stub function"
)

// TODO: fix expect/actual default parameters

open class DefaultArgumentStubLowering(open val context: CommonBackendContext) : DeclarationContainerLoweringPass {

    override fun lower(irDeclarationContainer: IrDeclarationContainer) {
        irDeclarationContainer.transformDeclarationsFlat { memberDeclaration ->
            if (memberDeclaration is IrFunction)
                lower(memberDeclaration)
            else
                null
        }
    }

    private val symbols get() = context.ir.symbols

    private fun lower(irFunction: IrFunction): List<IrFunction> {
        if (!context.defaultArgumentsStubGenerator.functionNeedsDefaultArgumentsStub(irFunction))
            return listOf(irFunction)

        val backendContext = context

        val bodies = irFunction.valueParameters.mapNotNull { it.defaultValue }


        log { "detected ${irFunction.name.asString()} has got #${bodies.size} default expressions" }

        if (bodies.isEmpty()) {
            // Fake override
            val newIrFunction = irFunction.getDefaultsFunction(context, IrDeclarationOrigin.FAKE_OVERRIDE)

            return listOf(irFunction, newIrFunction)
        }

        val newIrFunction = irFunction.getDefaultsFunction(context, IrDeclarationOrigin.FUNCTION_FOR_DEFAULT_PARAMETER)

        log { "$irFunction -> $newIrFunction" }
        val builder = context.createIrBuilder(newIrFunction.symbol)

        val defaultArgumentsStubGenerator = backendContext.defaultArgumentsStubGenerator
        newIrFunction.body = builder.irBlockBody(newIrFunction) {
            val params = mutableListOf<IrValueDeclaration>()
            val variables = mutableMapOf<IrValueDeclaration, IrValueDeclaration>()

            var shift = 0

            if (irFunction !is IrConstructor) {
                irFunction.dispatchReceiverParameter?.let {
                    if (defaultArgumentsStubGenerator.stubsAreStatic) {
                        variables[it] = newIrFunction.valueParameters[shift++]
                        params.add(variables[it]!!)
                    } else {
                        variables[it] = newIrFunction.dispatchReceiverParameter!!
                    }
                }

                irFunction.extensionReceiverParameter?.let {
                    if (defaultArgumentsStubGenerator.stubsAreStatic) {
                        variables[it] = newIrFunction.valueParameters[shift++]
                        params.add(variables[it]!!)
                    } else {
                        variables[it] = newIrFunction.extensionReceiverParameter!!
                    }
                }
            }

            for (valueParameter in irFunction.valueParameters) {
                val parameter = newIrFunction.valueParameters[valueParameter.index + shift]

                val argument = if (valueParameter.defaultValue != null) {
                    val kIntAnd = symbols.intAnd.owner
                    val condition = irNotEquals(irCall(kIntAnd).apply {
                        dispatchReceiver = irGet(
                            defaultArgumentsStubGenerator.maskParameter(
                                newIrFunction,
                                valueParameter.index / 32
                            )
                        )
                        putValueArgument(0, irInt(1 shl (valueParameter.index % 32)))
                    }, irInt(0))

                    val expressionBody = valueParameter.defaultValue!!
                    expressionBody.patchDeclarationParents(newIrFunction)

                    expressionBody.transformChildrenVoid(object : IrElementTransformerVoid() {
                        override fun visitGetValue(expression: IrGetValue): IrExpression {
                            log { "GetValue: ${expression.symbol.owner}" }
                            val valueSymbol = variables[expression.symbol.owner] ?: return expression
                            return irGet(valueSymbol)
                        }
                    })

                    irIfThenElse(
                        type = parameter.type,
                        condition = condition,
                        thenPart = expressionBody.expression,
                        elsePart = irGet(parameter)
                    )
                } else {
                    irGet(parameter)
                }

                val temporaryVariable = createTmpVariable(argument, nameHint = parameter.name.asString())
                temporaryVariable.parent = newIrFunction

                params.add(temporaryVariable)
                variables[valueParameter] = temporaryVariable
            }

            when (irFunction) {
                is IrConstructor -> +IrDelegatingConstructorCallImpl(
                    startOffset = irFunction.startOffset,
                    endOffset = irFunction.endOffset,
                    type = context.irBuiltIns.unitType,
                    symbol = irFunction.symbol, descriptor = irFunction.symbol.descriptor,
                    typeArgumentsCount = newIrFunction.parentAsClass.typeParameters.size + newIrFunction.typeParameters.size
                ).apply {
                    passTypeArgumentsFrom(newIrFunction.parentAsClass)
                    dispatchReceiver = newIrFunction.dispatchReceiverParameter?.let { irGet(it) }

                    params.forEachIndexed { i, variable -> putValueArgument(i, irGet(variable)) }
                }
                is IrSimpleFunction -> +irReturn(
                    dispatchToImplementation(
                        irFunction,
                        newIrFunction,
                        params,
                        defaultArgumentsStubGenerator.stubsAreStatic
                    )
                )
                else -> error("Unknown function declaration")
            }
        }
        // Remove default argument initializers.
        irFunction.valueParameters.forEach {
            if (it.defaultValue != null) {
                it.defaultValue = IrExpressionBodyImpl(IrErrorExpressionImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, it.type, "Default Stub"))
            }
        }
        return listOf(irFunction, newIrFunction)
    }

    private fun IrBlockBodyBuilder.dispatchToImplementation(
        irFunction: IrSimpleFunction,
        newIrFunction: IrFunction,
        params: MutableList<IrValueDeclaration>,
        stubsAreStatic: Boolean
    ): IrExpression {
        val dispatchCall = irCall(irFunction).apply {
            passTypeArgumentsFrom(newIrFunction)
            var shift = 0

            if (stubsAreStatic) {
                irFunction.dispatchReceiverParameter?.let {
                    dispatchReceiver = irGet(params[shift++])
                }
                irFunction.extensionReceiverParameter?.let {
                    extensionReceiver = irGet(params[shift++])
                }
            } else {
                dispatchReceiver = newIrFunction.dispatchReceiverParameter?.let { irGet(it) }
                extensionReceiver = newIrFunction.extensionReceiverParameter?.let { irGet(it) }
            }

            params.drop(shift).forEachIndexed { i, variable -> putValueArgument(i, irGet(variable)) }
        }
        return if (needSpecialDispatch(irFunction)) {
            val handlerDeclaration = newIrFunction.valueParameters.last()
            // if $handler != null $handler(a, b, c) else foo(a, b, c)
            irIfThenElse(
                irFunction.returnType,
                irEqualsNull(irGet(handlerDeclaration)),
                dispatchCall,
                generateHandleCall(handlerDeclaration, irFunction, newIrFunction, params)
            )
        } else dispatchCall
    }

    protected open fun needSpecialDispatch(irFunction: IrSimpleFunction) = false
    protected open fun IrBlockBodyBuilder.generateHandleCall(
        handlerDeclaration: IrValueParameter,
        oldIrFunction: IrFunction,
        newIrFunction: IrFunction,
        params: MutableList<IrValueDeclaration>
    ): IrExpression {
        assert(needSpecialDispatch(oldIrFunction as IrSimpleFunction))
        error("This method should be overridden")
    }

    private fun log(msg: () -> String) = context.log { "DEFAULT-REPLACER: ${msg()}" }
}

open class DefaultParameterInjector(val context: CommonBackendContext) : FileLoweringPass {

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
                val declaration = expression.symbol.owner

                if (!context.defaultArgumentsStubGenerator.functionNeedsDefaultArgumentsStub(declaration))
                    return super.visitFunctionAccess(expression)

                val argumentsCount = argumentCount(expression)
                if (argumentsCount == declaration.valueParameters.size)
                    return super.visitFunctionAccess(expression)

                return super.visitFunctionAccess(transformCall(expression))
            }

            private fun transformCall(expression: IrFunctionAccessExpression): IrFunctionAccessExpression {
                val declaration = expression.symbol.owner
                val realFunction = declaration.getRealDefaultsFunction(context)!!
                return context.defaultArgumentsStubGenerator.transformCall(context, expression, realFunction)
            }

            private fun argumentCount(expression: IrMemberAccessExpression): Int {
                var result = 0
                for (i in 0 until expression.valueArgumentsCount) {
                    expression.getValueArgument(i)?.run { ++result }
                }
                return result
            }
        })
    }

    private fun log(msg: () -> String) = context.log { "DEFAULT-INJECTOR: ${msg()}" }
}

class DefaultParameterCleaner constructor(val context: CommonBackendContext) : FunctionLoweringPass {
    override fun lower(irFunction: IrFunction) {
        irFunction.valueParameters.forEach { it.defaultValue = null }
    }
}

private fun IrFunction.generateDefaultsFunctionImpl(context: CommonBackendContext, origin: IrDeclarationOrigin): IrFunction {

    val newFunction = context.defaultArgumentsStubGenerator.generateStub(this, origin)

    if (origin == IrDeclarationOrigin.FAKE_OVERRIDE) {
        for (baseFunSymbol in (this as IrSimpleFunction).overriddenSymbols) {
            val baseFun = baseFunSymbol.owner
            if (context.defaultArgumentsStubGenerator.functionNeedsDefaultArgumentsStub(baseFun)) {
                val baseOrigin = if (baseFun.valueParameters.any { it.defaultValue != null }) {
                    IrDeclarationOrigin.FUNCTION_FOR_DEFAULT_PARAMETER
                } else {
                    IrDeclarationOrigin.FAKE_OVERRIDE
                }
                val defaultsBaseFun =
                    baseFun.getDefaultsFunction(context, baseOrigin)
                (newFunction as IrSimpleFunction).overriddenSymbols.add((defaultsBaseFun as IrSimpleFunction).symbol)
            }
        }
    }

    return newFunction
}

internal fun IrFunction.getDefaultsFunction(context: CommonBackendContext, origin: IrDeclarationOrigin): IrFunction =
    context.ir.defaultParameterDeclarationsCache.getOrPut(this) {
        generateDefaultsFunctionImpl(context, origin)
    }

// Get the defaults stub that should actually be called, rather than a fake override.
fun IrFunction.getRealDefaultsFunction(context: CommonBackendContext): IrFunction? {
    val keyFunction = findSuperMethodWithDefaultArguments(context) ?: return null
    return keyFunction.getDefaultsFunction(context, IrDeclarationOrigin.FUNCTION_FOR_DEFAULT_PARAMETER)
}

private fun IrFunction.findSuperMethodWithDefaultArguments(context: CommonBackendContext): IrFunction? {
    if (!context.defaultArgumentsStubGenerator.functionNeedsDefaultArgumentsStub(this)) return null

    if (this !is IrSimpleFunction) return this

    for (s in overriddenSymbols) {
        s.owner.findSuperMethodWithDefaultArguments(context)?.let { return it }
    }

    return this
}
