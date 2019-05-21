/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.config

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker
import org.jetbrains.kotlin.types.checker.RefineKotlinTypeChecker
import org.jetbrains.kotlin.types.typeUtil.refinedSupertypesIfNeeded

fun ClassDescriptor.refinedSupertypesIfNeeded(
    moduleDescriptor: ModuleDescriptor,
    languageVersionSettings: LanguageVersionSettings
): Collection<KotlinType> = refinedSupertypesIfNeeded(moduleDescriptor, languageVersionSettings.getFlag(AnalysisFlags.useTypeRefinement))

class RefineKotlinTypeCheckerImpl(
    private val moduleDescriptor: ModuleDescriptor,
    languageVersionSettings: LanguageVersionSettings
) : RefineKotlinTypeChecker {
    private val isRefinementDisabled = !languageVersionSettings.getFlag(AnalysisFlags.useTypeRefinement)

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

    override val overridingUtil = OverridingUtil.createWithRefinedTypeChecker(this)
}
