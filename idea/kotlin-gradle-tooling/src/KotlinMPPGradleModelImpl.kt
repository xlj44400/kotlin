/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import java.io.File
import kotlin.collections.HashSet

class KotlinSourceSetImpl(
    override val name: String,
    override val languageSettings: KotlinLanguageSettings,
    override val sourceDirs: Set<File>,
    override val resourceDirs: Set<File>,
    override val dependencies: Array<KotlinDependencyId>,
    override val dependsOnSourceSets: Set<String>,
    val defaultPlatform: KotlinPlatformContainerImpl = KotlinPlatformContainerImpl(),
    val defaultIsTestModule: Boolean = false
) : KotlinSourceSet {

    constructor(kotlinSourceSet: KotlinSourceSet, cloningCache: MutableMap<Any, Any>) : this(
        kotlinSourceSet.name,
        KotlinLanguageSettingsImpl(kotlinSourceSet.languageSettings),
        HashSet(kotlinSourceSet.sourceDirs),
        HashSet(kotlinSourceSet.resourceDirs),
        kotlinSourceSet.dependencies,
        HashSet(kotlinSourceSet.dependsOnSourceSets),
        KotlinPlatformContainerImpl(kotlinSourceSet.actualPlatforms),
        kotlinSourceSet.isTestModule
    )

    override var actualPlatforms: KotlinPlatformContainer = defaultPlatform
        internal set

    override var isTestModule: Boolean = defaultIsTestModule
        internal set

    override fun toString() = name
}

data class KotlinLanguageSettingsImpl(
    override val languageVersion: String?,
    override val apiVersion: String?,
    override val isProgressiveMode: Boolean,
    override val enabledLanguageFeatures: Set<String>,
    override val experimentalAnnotationsInUse: Set<String>,
    override val compilerPluginArguments: Array<String>,
    override val compilerPluginClasspath: Set<File>
) : KotlinLanguageSettings {
    constructor(settings: KotlinLanguageSettings) : this(
        settings.languageVersion,
        settings.apiVersion,
        settings.isProgressiveMode,
        settings.enabledLanguageFeatures,
        settings.experimentalAnnotationsInUse,
        settings.compilerPluginArguments,
        settings.compilerPluginClasspath
    )
}

data class KotlinCompilationOutputImpl(
    override val classesDirs: Set<File>,
    override val effectiveClassesDir: File?,
    override val resourcesDir: File?
) : KotlinCompilationOutput {
    constructor(output: KotlinCompilationOutput) : this(
        HashSet(output.classesDirs),
        output.effectiveClassesDir,
        output.resourcesDir
    )
}

data class KotlinCompilationArgumentsImpl(
    override val defaultArguments: Array<String>,
    override val currentArguments: Array<String>
) : KotlinCompilationArguments {
    constructor(arguments: KotlinCompilationArguments) : this(
        arguments.defaultArguments,
        arguments.currentArguments
    )
}

data class KotlinCompilationImpl(
    override val name: String,
    override val sourceSets: Collection<KotlinSourceSet>,
    override val dependencies: Array<KotlinDependencyId>,
    override val output: KotlinCompilationOutput,
    override val arguments: KotlinCompilationArguments,
    override val dependencyClasspath: Array<String>,
    override val kotlinTaskProperties: KotlinTaskProperties
) : KotlinCompilation {

    // create deep copy
    constructor(kotlinCompilation: KotlinCompilation, cloningCache: MutableMap<Any, Any>) : this(
        kotlinCompilation.name,
        kotlinCompilation.sourceSets.map { initialSourceSet ->
            (cloningCache[initialSourceSet] as? KotlinSourceSet) ?: KotlinSourceSetImpl(initialSourceSet, cloningCache).also {
                cloningCache[initialSourceSet] = it
            }
        }.toList(),
        kotlinCompilation.dependencies,
        KotlinCompilationOutputImpl(kotlinCompilation.output),
        KotlinCompilationArgumentsImpl(kotlinCompilation.arguments),
        kotlinCompilation.dependencyClasspath,
        KotlinTaskPropertiesImpl(kotlinCompilation.kotlinTaskProperties)
    ) {
        disambiguationClassifier = kotlinCompilation.disambiguationClassifier
        platform = kotlinCompilation.platform
    }

    override var disambiguationClassifier: String? = null
        internal set
    override lateinit var platform: KotlinPlatform
        internal set


    override val isTestModule: Boolean
        get() = name == KotlinCompilation.TEST_COMPILATION_NAME
                || platform == KotlinPlatform.ANDROID && name.contains("Test")

    override fun toString() = name
}

data class KotlinTargetJarImpl(
    override val archiveFile: File?
) : KotlinTargetJar

data class KotlinTargetImpl(
    override val name: String,
    override val presetName: String?,
    override val disambiguationClassifier: String?,
    override val platform: KotlinPlatform,
    override val compilations: Collection<KotlinCompilation>,
    override val jar: KotlinTargetJar?
) : KotlinTarget {
    override fun toString() = name

    constructor(target: KotlinTarget, cloningCache: MutableMap<Any, Any>) : this(
        target.name,
        target.presetName,
        target.disambiguationClassifier,
        KotlinPlatform.byId(target.platform.id) ?: KotlinPlatform.COMMON,
        target.compilations.map { initialCompilation ->
            (cloningCache[initialCompilation] as? KotlinCompilation) ?: KotlinCompilationImpl(initialCompilation, cloningCache).also {
                cloningCache[initialCompilation] = it
            }
        }.toList(),
        KotlinTargetJarImpl(target.jar?.archiveFile)
    )
}

data class ExtraFeaturesImpl(
    override val coroutinesState: String?,
    override val isHMPPEnabled: Boolean
) : ExtraFeatures

data class KotlinMPPGradleModelImpl(
    override val sourceSets: Map<String, KotlinSourceSet>,
    override val targets: Collection<KotlinTarget>,
    override val extraFeatures: ExtraFeatures,
    override val kotlinNativeHome: String,
    override val dependencyMap: Map<KotlinDependencyId, KotlinDependency>
) : KotlinMPPGradleModel {

    constructor(mppModel: KotlinMPPGradleModel, cloningCache: MutableMap<Any, Any>) : this(
        mppModel.sourceSets.mapValues { initialSourceSet ->
            (cloningCache[initialSourceSet] as? KotlinSourceSet) ?: KotlinSourceSetImpl(
                initialSourceSet.value,
                cloningCache
            ).also { cloningCache[initialSourceSet] = it }
        },
        mppModel.targets.map { initialTarget ->
            (cloningCache[initialTarget] as? KotlinTarget) ?: KotlinTargetImpl(initialTarget, cloningCache).also {
                cloningCache[initialTarget] = it
            }
        }.toList(),
        ExtraFeaturesImpl(mppModel.extraFeatures.coroutinesState, mppModel.extraFeatures.isHMPPEnabled),
        mppModel.kotlinNativeHome,
        mppModel.dependencyMap.map { it.key to it.value.deepCopy(cloningCache) }.toMap()
    )
}

class KotlinPlatformContainerImpl() : KotlinPlatformContainer {
    private val defaultCommonPlatform = setOf(KotlinPlatform.COMMON)
    private var myPlatforms: MutableSet<KotlinPlatform>? = null


    constructor(platform: KotlinPlatformContainer) : this() {
        myPlatforms = HashSet<KotlinPlatform>(platform.platforms)
    }

    override val platforms: Collection<KotlinPlatform>
        get() = myPlatforms ?: defaultCommonPlatform

    override fun supports(simplePlatform: KotlinPlatform): Boolean = platforms.contains(simplePlatform)

    override fun addSimplePlatforms(platforms: Collection<KotlinPlatform>) {
        (myPlatforms ?: HashSet<KotlinPlatform>().apply { myPlatforms = this }).addAll(platforms)
    }
}
