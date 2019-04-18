/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.backend.common.descriptors.WrappedClassConstructorDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.WrappedSimpleFunctionDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.WrappedValueParameterDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.synthesizedName
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrConstructorImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrValueParameterImpl
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrEnumConstructorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrConstructorSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedCallableMemberDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedMemberDescriptor

class DefaultArgumentsStubGenerator(
    private val irBuiltIns: IrBuiltIns,
    private val defaultConstructorMarker: IrType,
    private val skipInlineMethods: Boolean = true,
    private val skipExternalMethods: Boolean = false,
    private val shouldGenerateHandlerParameterForDefaultBodyFun: Boolean = false,
    val stubsAreStatic: Boolean = false
) {
    // TODO this implementation is exponential
    fun functionNeedsDefaultArgumentsStub(irFunction: IrFunction): Boolean {
        if (skipInlineMethods && irFunction.isInline) return false
        if (skipExternalMethods && irFunction.isEffectivelyExternal()) return false
        if (irFunction.valueParameters.any { it.defaultValue != null}) return true

        if (irFunction !is IrSimpleFunction) return false

        return irFunction.overriddenSymbols.any { functionNeedsDefaultArgumentsStub(it.owner) }
    }

    fun generateStub(irFunction: IrFunction, origin: IrDeclarationOrigin): IrFunction {
        val newFunction = buildFunctionDeclaration(irFunction, origin)

        with (irFunction) {
            newFunction.copyTypeParametersFrom(irFunction)

            var shift = 0
            val newValueParameters = mutableListOf<IrValueParameter>()
            if (stubsAreStatic && irFunction !is IrConstructor) {
                dispatchReceiverParameter?.let {
                    newValueParameters.add(it.copyTo(newFunction, index = shift++))
                }
                extensionReceiverParameter?.let {
                    newValueParameters.add(it.copyTo(newFunction, index = shift++))
                }
            } else {
                newFunction.dispatchReceiverParameter = dispatchReceiverParameter?.copyTo(newFunction)
                newFunction.extensionReceiverParameter = extensionReceiverParameter?.copyTo(newFunction)
            }

            valueParameters.mapTo(newValueParameters) { it.copyTo(newFunction, index = it.index + shift) }

            for (i in 0 until (valueParameters.size + 31) / 32) {
                newValueParameters +=
                    newFunction.valueParameter(newValueParameters.size, parameterMaskName(i), irBuiltIns.intType)
            }

            if (this is IrConstructor) {
                newValueParameters += newFunction.valueParameter(
                    newValueParameters.size,
                    kConstructorMarkerName,
                    defaultConstructorMarker
                )
            } else if (shouldGenerateHandlerParameterForDefaultBodyFun) {
                newValueParameters += newFunction.valueParameter(
                    newValueParameters.size,
                    "handler".synthesizedName,
                    irBuiltIns.anyType
                )
            }

            newValueParameters.forEach {
                it.defaultValue = null
            }

            newFunction.returnType = returnType
            newFunction.valueParameters += newValueParameters

            annotations.mapTo(newFunction.annotations) { it.deepCopyWithSymbols() }

        }

        return newFunction
    }

    fun transformCall(
        context: CommonBackendContext, expression: IrFunctionAccessExpression,
        realFunction: IrFunction,
        shiftForInnerClass: Boolean = false
    ): IrFunctionAccessExpression {
        val declaration = expression.symbol.owner

        val valueArguments = mutableListOf<IrExpression?>()
        var dispatchReceiverValue: IrExpression? = null
        var extensionReceiverValue: IrExpression? = null

        if (stubsAreStatic && declaration !is IrConstructor) {
            expression.dispatchReceiver?.let {
                valueArguments += it
            }
            expression.extensionReceiver?.let {
                valueArguments += it
            }
        } else {
            dispatchReceiverValue = expression.dispatchReceiver
            extensionReceiverValue = expression.extensionReceiver
        }

        var innerClassShift = 0
        if (shiftForInnerClass && declaration is IrConstructor && declaration.parentAsClass.isInner) {
            valueArguments.add(expression.getValueArgument(0))
            innerClassShift = 1
        }

        val maskValues = Array((declaration.valueParameters.size - innerClassShift + 31) / 32) { 0 }
        valueArguments += declaration.valueParameters.drop(innerClassShift).mapIndexed { i, valueParameter ->
            val valueArgument = expression.getValueArgument(i + innerClassShift)
            if (valueArgument == null) {
                val maskIndex = i / 32
                maskValues[maskIndex] = maskValues[maskIndex] or (1 shl (i % 32))
            }
            val defaultValueArgument = if (valueParameter.varargElementType != null) {
                null
            } else {
                nullConst(context, expression, valueParameter.type)
            }

            valueArgument ?: defaultValueArgument
        }

        val startOffset = expression.startOffset
        val endOffset = expression.endOffset
        maskValues.forEachIndexed { i, maskValue ->
            valueArguments += IrConstImpl.int(
                startOffset = startOffset,
                endOffset = endOffset,
                type = context.irBuiltIns.intType,
                value = maskValue
            )
        }
        if (expression.symbol is IrConstructorSymbol) {
            val defaultArgumentMarker = context.ir.symbols.defaultConstructorMarker
            valueArguments += IrConstImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                defaultArgumentMarker.owner.defaultType,
                IrConstKind.Null,
                null
            )
//                    TODO: On JVM, the marker type has no public constructors, so we pass null.
//                    Any reason to pass an actual object in other backends?
//                    IrGetObjectValueImpl(
//                        startOffset = startOffset,
//                        endOffset = endOffset,
//                        type = defaultArgumentMarker.owner.defaultType,
//                        symbol = defaultArgumentMarker
//                    )
        } else if (shouldGenerateHandlerParameterForDefaultBodyFun) {
            valueArguments += IrConstImpl.constNull(startOffset, endOffset, context.irBuiltIns.nothingNType)
        }

        val newCall = generateStubCall(expression, realFunction).apply {
            copyTypeArgumentsFrom(expression)
            dispatchReceiver = dispatchReceiverValue
            extensionReceiver = extensionReceiverValue
            valueArguments.forEachIndexed { i, arg -> putValueArgument(i, arg)}
        }

        return newCall
    }

    fun maskParameter(function: IrFunction, number: Int) =
        function.valueParameters.single { it.name == parameterMaskName(number) }

    companion object {
        private fun buildFunctionDeclaration(irFunction: IrFunction, origin: IrDeclarationOrigin): IrFunction {
            when (irFunction) {
                is IrConstructor -> {
                    val descriptor = WrappedClassConstructorDescriptor(irFunction.descriptor.annotations, irFunction.descriptor.source)
                    return IrConstructorImpl(
                        irFunction.startOffset,
                        irFunction.endOffset,
                        origin,
                        IrConstructorSymbolImpl(descriptor),
                        irFunction.name,
                        irFunction.visibility,
                        irFunction.returnType,
                        irFunction.isInline,
                        false,
                        false
                    ).also {
                        descriptor.bind(it)
                        it.parent = irFunction.parent
                    }
                }
                is IrSimpleFunction -> {
                    val descriptor = WrappedSimpleFunctionDescriptor(irFunction.descriptor.annotations, irFunction.descriptor.source)
                    val name = Name.identifier("${irFunction.name}\$default")

                    return IrFunctionImpl(
                        irFunction.startOffset,
                        irFunction.endOffset,
                        origin,
                        IrSimpleFunctionSymbolImpl(descriptor),
                        name,
                        irFunction.visibility,
                        Modality.FINAL,
                        irFunction.returnType,
                        irFunction.isInline,
                        false,
                        false,
                        irFunction.isSuspend
                    ).also {
                        descriptor.bind(it)
                        // Kludge to work around KotlinTypeMapper
                        if (irFunction.parent is IrPackageFragment && irFunction.descriptor is DeserializedCallableMemberDescriptor) {
                            val deserializedDescriptor = irFunction.descriptor as DeserializedMemberDescriptor
                            val facadeClassName = deserializedDescriptor.containerSource!!.getFacadeClassName()
                            val facadeClass = buildClass {
                                this.name = facadeClassName
                            }.apply {
                                parent = irFunction.parent
                            }
                            it.parent = facadeClass
                        } else {
                            it.parent = irFunction.parent
                        }
                    }
                }
                else -> throw IllegalStateException("Unknown function type")
            }
        }

        private fun IrFunction.valueParameter(index: Int, name: Name, type: IrType): IrValueParameter {
            val parameterDescriptor = WrappedValueParameterDescriptor()

            return IrValueParameterImpl(
                startOffset,
                endOffset,
                IrDeclarationOrigin.DEFINED,
                IrValueParameterSymbolImpl(parameterDescriptor),
                name,
                index,
                type,
                null,
                false,
                false
            ).also {
                parameterDescriptor.bind(it)
                it.parent = this
            }
        }


        internal val kConstructorMarkerName = "marker".synthesizedName

        private fun parameterMaskName(number: Int) = "mask$number".synthesizedName

        private fun nullConst(context: CommonBackendContext, expression: IrElement?, type: IrType): IrExpression {
            val startOffset = expression?.startOffset ?: UNDEFINED_OFFSET
            val endOffset = expression?.endOffset ?: UNDEFINED_OFFSET
            return when {
                type.isFloat() -> IrConstImpl.float(startOffset, endOffset, type, 0.0F)
                type.isDouble() -> IrConstImpl.double(startOffset, endOffset, type, 0.0)
                type.isBoolean() -> IrConstImpl.boolean(startOffset, endOffset, type, false)
                type.isByte() -> IrConstImpl.byte(startOffset, endOffset, type, 0)
                type.isChar() -> IrConstImpl.char(startOffset, endOffset, type, 0.toChar())
                type.isShort() -> IrConstImpl.short(startOffset, endOffset, type, 0)
                type.isInt() -> IrConstImpl.int(startOffset, endOffset, type, 0)
                type.isLong() -> IrConstImpl.long(startOffset, endOffset, type, 0)
                else -> IrConstImpl.constNull(startOffset, endOffset, context.irBuiltIns.nothingNType)
            }
        }

        private fun generateStubCall(expression: IrFunctionAccessExpression, stub: IrFunction): IrFunctionAccessExpression =
            when (expression) {
                is IrDelegatingConstructorCall -> IrDelegatingConstructorCallImpl(
                    expression.startOffset, expression.endOffset, expression.type,
                    stub.symbol as IrConstructorSymbol,
                    stub.descriptor as ClassConstructorDescriptor,
                    expression.typeArgumentsCount
                )
                is IrEnumConstructorCall -> IrEnumConstructorCallImpl(
                    expression.startOffset, expression.endOffset, expression.type,
                    stub.symbol as IrConstructorSymbol,
                    expression.typeArgumentsCount
                )
                is IrCall -> IrCallImpl(
                    expression.startOffset, expression.endOffset, expression.type,
                    stub.symbol, stub.descriptor,
                    expression.typeArgumentsCount,
                    origin = IrStatementOrigin.DEFAULT_DISPATCH_CALL

                )
                else -> error("Unknown subclass of IrFunctionAccessExpression: ${expression}")
            }

        // Ugly!!!!
        private fun DeserializedContainerSource.getFacadeClassName(): Name =
            Name.identifier(
                presentableString
                    .substringAfterLast('.')
                    .substringAfterLast('/')
                    .substringBefore("__")
                    .substringBeforeLast('\'')
            )
    }
}
