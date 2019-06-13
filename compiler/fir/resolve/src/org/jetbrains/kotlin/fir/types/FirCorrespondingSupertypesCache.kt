/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types

import com.intellij.util.SmartFMap
import gnu.trove.THashMap
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.toFirClassLike
import org.jetbrains.kotlin.fir.resolve.constructClassType
import org.jetbrains.kotlin.fir.resolve.constructType
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeSymbol
import org.jetbrains.kotlin.types.AbstractTypeCheckerContext
import org.jetbrains.kotlin.types.model.CaptureStatus
import org.jetbrains.kotlin.types.model.SimpleTypeMarker
import org.jetbrains.kotlin.types.model.TypeConstructorMarker

class FirCorrespondingSupertypesCache(private val session: FirSession) {
    companion object {
        private const val ARRAY_THRESHOLD = 6
    }

    private val context = ConeTypeCheckerContext(false, session)
    private val cache = THashMap<ConeClassLikeSymbol, Map<ConeClassLikeSymbol, List<ConeClassLikeType>>?>(1000, 0.5f)

    fun getCorrespondingSupertypes(
        type: ConeKotlinType,
        supertypeConstructor: TypeConstructorMarker
    ): List<ConeClassLikeType>? {
        if (type !is ConeClassLikeType || supertypeConstructor !is ConeClassLikeSymbol) return null

        val symbol = type.lookupTag.toSymbol(session) as? ConeClassLikeSymbol ?: return null
        if (symbol == supertypeConstructor) return listOf(captureType(type))

        if (symbol !in cache) {
            cache[symbol] = computeSupertypesMap(type, symbol)
        }

        val resultTypes = cache[symbol]?.getOrDefault(supertypeConstructor, emptyList()) ?: return null
        if (type.typeArguments.isEmpty()) return resultTypes

        val capturedType = captureType(type)
        val substitutionSupertypePolicy = context.substitutionSupertypePolicy(capturedType)
        return resultTypes.map {
            substitutionSupertypePolicy.transformType(context, it) as ConeClassLikeType
        }
    }

    private fun captureType(type: ConeClassLikeType): ConeClassLikeType =
        (context.captureFromArguments(type, CaptureStatus.FOR_SUBTYPING) ?: type) as ConeClassLikeType

    private fun computeSupertypesMap(
        subtype: ConeLookupTagBasedType,
        subtypeSymbol: ConeClassLikeSymbol
    ): Map<ConeClassLikeSymbol, List<ConeClassLikeType>>? {
        val resultingMap = THashMap<ConeClassLikeSymbol, List<ConeClassLikeType>>()

        val subtypeClassSymbol = with(context) {
            subtype.typeConstructor() as? ConeClassLikeSymbol ?: return null
        }
        val subtypeFirClass = subtypeClassSymbol.toFirClassLike() ?: return null

        val defaultType = subtypeClassSymbol.toLookupTag().constructClassType(
            subtypeFirClass.typeParameters.map {
                it.symbol.toLookupTag().constructType(emptyArray(), isNullable = false)
            }.toTypedArray(),
            isNullable = false
        )

        if (context.anySupertype(
                defaultType,
                { it !is ConeClassLikeType || it.lookupTag.toSymbol(session) !is ConeClassLikeSymbol }
            ) { supertype -> computeSupertypePolicyAndPutInMap(supertype, subtypeSymbol, resultingMap) }
        ) {
            return null
        }

        if (resultingMap.size < ARRAY_THRESHOLD) {
            return SmartFMap.emptyMap<ConeClassLikeSymbol, List<ConeClassLikeType>>().plusAll(resultingMap)
        }

        return resultingMap
    }

    private fun computeSupertypePolicyAndPutInMap(
        supertype: SimpleTypeMarker,
        subtypeSymbol: ConeClassLikeSymbol,
        resultingMap: MutableMap<ConeClassLikeSymbol, List<ConeClassLikeType>>
    ): AbstractTypeCheckerContext.SupertypesPolicy {
        val supertypeSymbol = (supertype as ConeClassLikeType).lookupTag.toSymbol(session) as ConeClassLikeSymbol
        val captured = context.captureFromArguments(supertype, CaptureStatus.FOR_SUBTYPING) as ConeClassLikeType? ?: supertype

        if (supertypeSymbol != subtypeSymbol) {
            resultingMap[supertypeSymbol] = listOf(captured)
        }

        return when {
            with(context) { captured.argumentsCount() } == 0 -> {
                AbstractTypeCheckerContext.SupertypesPolicy.LowerIfFlexible
            }
            else -> {
                context.substitutionSupertypePolicy(captured)
            }
        }
    }
}
