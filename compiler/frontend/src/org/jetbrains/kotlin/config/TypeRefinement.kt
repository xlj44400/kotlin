/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.config

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeConstructor
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker
import org.jetbrains.kotlin.types.checker.RefineKotlinTypeChecker
import org.jetbrains.kotlin.utils.DFS
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class RefineKotlinTypeCheckerImpl(
    private val moduleDescriptor: ModuleDescriptor,
    storageManager: StorageManager,
    languageVersionSettings: LanguageVersionSettings
) : RefineKotlinTypeChecker {
    private val isRefinementDisabled = !languageVersionSettings.getFlag(AnalysisFlags.useTypeRefinement)
    private val isRefinementNeededForTypeConstructor = storageManager.createMemoizedFunction(TypeConstructor::areThereExpectSupertypes)

    override fun isSubtypeOf(subtype: KotlinType, supertype: KotlinType): Boolean {
        if (isRefinementDisabled) return NewKotlinTypeChecker.isSubtypeOf(subtype, supertype)

        return NewKotlinTypeChecker.isSubtypeOf(subtype.refine(moduleDescriptor), supertype.refine(moduleDescriptor))
    }

    override fun equalTypes(subtype: KotlinType, supertype: KotlinType): Boolean {
        if (isRefinementDisabled) return NewKotlinTypeChecker.equalTypes(subtype, supertype)

        return NewKotlinTypeChecker.equalTypes(subtype.refine(moduleDescriptor), supertype.refine(moduleDescriptor))
    }

    override fun refineType(type: KotlinType): KotlinType {
        if (isRefinementDisabled) return type
        return type.refine(moduleDescriptor)
    }

    override fun refineSupertypes(classDescriptor: ClassDescriptor): Collection<KotlinType> {
        if (isRefinementDisabled) return classDescriptor.typeConstructor.supertypes
        return classDescriptor.typeConstructor.supertypes.map { it.refine(moduleDescriptor) }
    }

    override fun isRefinementNeeded(typeConstructor: TypeConstructor): Boolean {
        if (isRefinementDisabled) return false
        return isRefinementNeededForTypeConstructor.invoke(typeConstructor)
    }

    override val overridingUtil = OverridingUtil.createWithRefinedTypeChecker(this)
}

private fun TypeConstructor.areThereExpectSupertypes(): Boolean {
    var result = false
    DFS.dfs(
        listOf(this),
        DFS.Neighbors { current ->
            current.supertypes.map { it.constructor }
        },
        DFS.VisitedWithSet(),
        object : DFS.AbstractNodeHandler<TypeConstructor, Unit>() {
            override fun beforeChildren(current: TypeConstructor): Boolean {
                if (current.isExpectClass()) {
                    result = true
                    return false
                }
                return true
            }

            override fun result() = Unit
        }
    )

    return result
}

private fun TypeConstructor.isExpectClass() =
    declarationDescriptor?.safeAs<ClassDescriptor>()?.isExpect == true
