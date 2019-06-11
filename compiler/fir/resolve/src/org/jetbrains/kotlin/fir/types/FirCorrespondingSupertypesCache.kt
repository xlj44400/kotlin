/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.toFirClassLike
import org.jetbrains.kotlin.fir.resolve.constructClassType
import org.jetbrains.kotlin.fir.resolve.constructType
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.types.AbstractTypeCheckerContext
import org.jetbrains.kotlin.types.model.CaptureStatus
import org.jetbrains.kotlin.types.model.SimpleTypeMarker
import org.jetbrains.kotlin.types.model.TypeConstructorMarker

class FirCorrespondingSupertypesCache(session: FirSession) {
    private val context = ConeTypeCheckerContext(false, session)
    private val cache = mutableMapOf<ClassId, Map<ClassId, List<ConeClassLikeType>>?>()

    fun getCorrespondingSupertypes(
        type: ConeLookupTagBasedType,
        supertypeConstructor: TypeConstructorMarker
    ): List<ConeClassLikeType>? {
        if (type !is ConeClassLikeType) return null

        val classId = type.lookupTag.classId
        val supertypeClassId = (supertypeConstructor as? ConeClassLikeSymbol)?.classId ?: return null

        if (classId == supertypeClassId) return listOf(type)

        if (classId !in cache) {
            cache[classId] = computeSupertypesMap(type, classId)
        }

        val resultTypes = cache[classId]?.getOrDefault(supertypeClassId, emptyList()) ?: return null
        if (type.typeArguments.isEmpty()) return resultTypes

        val substitutionSupertypePolicy = context.substitutionSupertypePolicy(type)
        return resultTypes.map {
            substitutionSupertypePolicy.transformType(context, it) as ConeClassLikeType
        }
    }

    private fun computeSupertypesMap(
        subtype: ConeLookupTagBasedType,
        subtypeClassId: ClassId
    ): Map<ClassId, List<ConeClassLikeType>>? {
        val resultingMap = mutableMapOf<ClassId, List<ConeClassLikeType>>()

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
                { it !is ConeClassLikeType }
            ) { supertype -> computeSupertypePolicyAndPutInMap(supertype, subtypeClassId, resultingMap) }
        ) {
            return null
        }
        return resultingMap
    }

    private fun computeSupertypePolicyAndPutInMap(
        supertype: SimpleTypeMarker,
        subtypeClassId: ClassId,
        resultingMap: MutableMap<ClassId, List<ConeClassLikeType>>
    ): AbstractTypeCheckerContext.SupertypesPolicy {
        val anotherClassId = (supertype as ConeClassLikeType).lookupTag.classId
        val captured = context.captureFromArguments(supertype, CaptureStatus.FOR_SUBTYPING) as ConeClassLikeType? ?: supertype

        if (anotherClassId != subtypeClassId) {
            resultingMap[anotherClassId] = listOf(captured)
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
