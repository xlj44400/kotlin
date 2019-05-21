/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types.checker

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.types.KotlinType

interface RefineKotlinTypeChecker {
    fun isSubtypeOf(subtype: KotlinType, supertype: KotlinType): Boolean
    fun equalTypes(subtype: KotlinType, supertype: KotlinType): Boolean
    fun refineType(type: KotlinType): KotlinType
    fun refineSupertypes(classDescriptor: ClassDescriptor): Collection<KotlinType>

    val overridingUtil: OverridingUtil

    object Default : RefineKotlinTypeChecker {
        override fun isSubtypeOf(subtype: KotlinType, supertype: KotlinType) = KotlinTypeChecker.DEFAULT.isSubtypeOf(subtype, supertype)
        override fun equalTypes(subtype: KotlinType, supertype: KotlinType) = KotlinTypeChecker.DEFAULT.equalTypes(subtype, supertype)
        override fun refineType(type: KotlinType): KotlinType = type
        override fun refineSupertypes(classDescriptor: ClassDescriptor) = classDescriptor.typeConstructor.supertypes

        override val overridingUtil: OverridingUtil
            get() = OverridingUtil.DEFAULT
    }
}
