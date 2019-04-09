/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.common.deepCopyWithWrappedDescriptors
import org.jetbrains.kotlin.backend.common.descriptors.WrappedClassConstructorDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.WrappedSimpleFunctionDescriptor
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.ir.passTypeArgumentsFrom
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrConstructorImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.impl.IrConstructorSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.hasDefaultValue
import org.jetbrains.kotlin.resolve.jvm.annotations.JVM_OVERLOADS_FQ_NAME

internal val jvmOverloadsAnnotationPhase = makeIrFilePhase(
    ::JvmOverloadsAnnotationLowering,
    name = "JvmOverloadsAnnotation",
    description = "Handle JvmOverloads annotations"
)

// TODO: `IrValueParameter.defaultValue` property does not track default values in super-parameters. See KT-28637.

private class JvmOverloadsAnnotationLowering(val context: JvmBackendContext) : ClassLoweringPass {

    override fun lower(irClass: IrClass) {
        val functions = irClass.declarations.filterIsInstance<IrFunction>().filter {
            it.hasAnnotation(JVM_OVERLOADS_FQ_NAME)
        }

        functions.forEach {
            generateWrappers(it, irClass)
        }
    }

    private fun generateWrappers(target: IrFunction, irClass: IrClass) {
        val numDefaultParameters = target.valueParameters.count { it.defaultValue != null }
        for (i in 0 until numDefaultParameters) {
            val wrapper = generateWrapper(target, i)
            irClass.addMember(wrapper)
        }
    }

    private fun generateWrapper(target: IrFunction, numDefaultParametersToExpect: Int): IrFunction {
        val wrapperIrFunction = generateWrapperHeader(target, numDefaultParametersToExpect)

        // Construct a fake call that we will translate using defaultArgumentStubGenerator
        val fakeCall = if (target is IrConstructor)
            IrDelegatingConstructorCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, target.returnType, target.symbol, target.descriptor)
        else
            IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, target.returnType, target.symbol, target.descriptor)

        with(fakeCall) {
            passTypeArgumentsFrom(wrapperIrFunction)
            dispatchReceiver = wrapperIrFunction.dispatchReceiverParameter?.let {
                IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, it.symbol)
            }
            extensionReceiver = wrapperIrFunction.extensionReceiverParameter?.let {
                IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, it.symbol)
            }
            var paramNum = 0
            var numDefaultParameters = 0
            target.valueParameters.mapIndexed { i, param ->
                if (!param.hasDefaultValue() || numDefaultParameters++ < numDefaultParametersToExpect) {
                    putValueArgument(i, IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, wrapperIrFunction.valueParameters[paramNum++].symbol))
                }
            }
        }

        val (calleeSymbol, params) = context.defaultArgumentsStubGenerator.argumentsForCall(context, fakeCall, shiftForInnerClass = true)
        val callee = calleeSymbol.owner

        val call = if (callee is IrConstructor)
            IrDelegatingConstructorCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, callee.returnType, callee.symbol, callee.descriptor)
        else
            IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, callee.returnType, callee.symbol, callee.descriptor)

        with(call) {
            passTypeArgumentsFrom(wrapperIrFunction)
            if (callee !is IrConstructor) {
                var shift = 0
                wrapperIrFunction.dispatchReceiverParameter?.let {
                    putValueArgument(shift++, IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, it.symbol))
                }
                wrapperIrFunction.extensionReceiverParameter?.let {
                    putValueArgument(shift++, IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, it.symbol))
                }
            }
            for ((parameter, expression) in params) {
                putValueArgument(parameter.index, expression)
            }
        }

        wrapperIrFunction.body = IrExpressionBodyImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET, call
        )

        return wrapperIrFunction
    }

    private fun generateWrapperHeader(oldFunction: IrFunction, numDefaultParametersToExpect: Int): IrFunction {
        val res = when (oldFunction) {
            is IrConstructor -> {
                val descriptor = WrappedClassConstructorDescriptor(oldFunction.descriptor.annotations)
                IrConstructorImpl(
                    oldFunction.startOffset, oldFunction.endOffset,
                    JvmLoweredDeclarationOrigin.JVM_OVERLOADS_WRAPPER,
                    IrConstructorSymbolImpl(descriptor),
                    oldFunction.name,
                    oldFunction.visibility,
                    returnType = oldFunction.returnType,
                    isInline = oldFunction.isInline,
                    isExternal = false,
                    isPrimary = false
                ).apply {
                    descriptor.bind(this)
                }
            }
            is IrSimpleFunction -> {
                val descriptor = WrappedSimpleFunctionDescriptor(oldFunction.descriptor.annotations)
                IrFunctionImpl(
                    oldFunction.startOffset, oldFunction.endOffset,
                    JvmLoweredDeclarationOrigin.JVM_OVERLOADS_WRAPPER,
                    IrSimpleFunctionSymbolImpl(descriptor),
                    oldFunction.name,
                    oldFunction.visibility,
                    oldFunction.modality,
                    returnType = oldFunction.returnType,
                    isInline = oldFunction.isInline,
                    isExternal = false,
                    isTailrec = false,
                    isSuspend = oldFunction.isSuspend
                ).apply {
                    descriptor.bind(this)
                }
            }
            else -> error("Unknown kind of IrFunction: $oldFunction")
        }

        res.parent = oldFunction.parent
        res.annotations.addAll(oldFunction.annotations.map { it.deepCopyWithWrappedDescriptors(res) })
        res.copyTypeParametersFrom(oldFunction)
        res.dispatchReceiverParameter = oldFunction.dispatchReceiverParameter?.copyTo(res)
        res.extensionReceiverParameter = oldFunction.extensionReceiverParameter?.copyTo(res)
        res.valueParameters.addAll(res.generateNewValueParameters(oldFunction, numDefaultParametersToExpect))
        return res
    }

    private fun IrFunction.generateNewValueParameters(
        oldFunction: IrFunction,
        numDefaultParametersToExpect: Int
    ): List<IrValueParameter> {
        var parametersCopied = 0
        var defaultParametersCopied = 0
        val result = mutableListOf<IrValueParameter>()
        for (oldValueParameter in oldFunction.valueParameters) {
            if (oldValueParameter.defaultValue != null &&
                defaultParametersCopied < numDefaultParametersToExpect
            ) {
                defaultParametersCopied++
                result.add(
                    oldValueParameter.copyTo(
                        this,
                        index = parametersCopied++,
                        defaultValue = null,
                        isCrossinline = oldValueParameter.isCrossinline,
                        isNoinline = oldValueParameter.isNoinline
                    )
                )
            } else if (oldValueParameter.defaultValue == null) {
                result.add(oldValueParameter.copyTo(this, index = parametersCopied++))
            }
        }
        return result
    }
}