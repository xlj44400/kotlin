/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

// TODO: At present, there is no reliable way to distinguish Java imports from Kotlin cross-module imports.
val ORIGINS_FROM_JAVA = setOf(IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB, IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB)

fun IrDeclaration.comesFromJava() = parent.safeAs<IrClass>()?.origin?.let { it in ORIGINS_FROM_JAVA } ?: false

