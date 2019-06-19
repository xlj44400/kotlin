/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.copy
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirWrappedArgumentExpression
import org.jetbrains.kotlin.fir.resolve.constructFunctionalTypeRef
import org.jetbrains.kotlin.fir.types.FirImplicitTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.impl.FirErrorTypeRefImpl
import org.jetbrains.kotlin.fir.visitors.CompositeTransformResult

class FirMissedImplicitTypeTransformer : FirAbstractTreeTransformer() {

    private lateinit var session: FirSession

    override fun transformFile(file: FirFile, data: Nothing?): CompositeTransformResult<FirFile> {
        session = file.fileSession
        return super.transformFile(file, data)
    }

    private val anonymousFunctionStack = mutableListOf<FirAnonymousFunction>()

    override fun transformAnonymousFunction(
        anonymousFunction: FirAnonymousFunction,
        data: Nothing?
    ): CompositeTransformResult<FirDeclaration> {
        anonymousFunctionStack += anonymousFunction
        val result = if (anonymousFunction.returnTypeRef is FirResolvedTypeRef) {
            super.transformAnonymousFunction(anonymousFunction, data)
        } else {
            val receiverTypeRef = when (val initialTypeRef = anonymousFunction.receiverTypeRef) {
                is FirImplicitTypeRef -> FirErrorTypeRefImpl(
                    session, anonymousFunction.psi, "Cannot infer anonymous function receiver type"
                )
                else -> initialTypeRef
            }
            val af = anonymousFunction.copy(
                receiverTypeRef = receiverTypeRef,
                returnTypeRef = anonymousFunction.returnTypeRef.takeIf { it !is FirImplicitTypeRef } ?: FirErrorTypeRefImpl(
                    session, anonymousFunction.psi, "Cannot infer anonymous function return type"
                )
            )
            af.replaceTypeRef(af.constructFunctionalTypeRef(session))
            super.transformAnonymousFunction(af, data)
        }
        anonymousFunctionStack.removeAt(anonymousFunctionStack.size - 1)
        return result
    }

    override fun transformExpression(expression: FirExpression, data: Nothing?): CompositeTransformResult<FirStatement> {
        if (expression is FirWrappedArgumentExpression ||
            anonymousFunctionStack.isEmpty() ||
            anonymousFunctionStack.last().returnTypeRef !is FirImplicitTypeRef ||
            expression.typeRef !is FirImplicitTypeRef
        ) {
            return super.transformExpression(expression, data)
        }
        expression.replaceTypeRef(
            FirErrorTypeRefImpl(session, expression.psi, "Cannot infer expression type because of unresolved lambda")
        )
        return super.transformExpression(expression, data)
    }
}