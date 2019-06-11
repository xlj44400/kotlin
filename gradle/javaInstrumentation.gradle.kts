/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

allprojects {
    afterEvaluate {
        configureJavaInstrumentation()
    }
}

// Hide window of instrumentation tasks
val headlessOldValue: String? = System.setProperty("java.awt.headless", "true")
logger.info("Setting java.awt.headless=true, old value was $headlessOldValue")

data class BasicFingerprint(val lastModified: FileTime, val size: Long) {
    constructor(attrs: BasicFileAttributes) : this(attrs.lastModifiedTime(), attrs.size())
}

fun File.visitClasses(visitClassFile: (path: Path, attrs: BasicFileAttributes) -> Unit) {
    val classFileVisitor = object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (file.toString().endsWith(".class")) {
                visitClassFile(file, attrs)
            }
            return FileVisitResult.CONTINUE
        }
    }

    Files.walkFileTree(toPath(), classFileVisitor)
}

/**
 *  Configures instrumentation for all JavaCompile tasks in project
 */
fun Project.configureJavaInstrumentation() {
    if (plugins.hasPlugin("org.gradle.java")) {
        val javaInstrumentator by configurations.creating
        dependencies {
            javaInstrumentator(intellijDep()) {
                includeJars("javac2", "jdom", "asm-all", rootProject = rootProject)
            }
        }

        tasks.withType<JavaCompile> {
            val oldFingerprints = HashMap<Path, BasicFingerprint>()
            doFirst {
                if (options.isIncremental) {
                    destinationDir.visitClasses { path, attrs ->
                        oldFingerprints[path] = BasicFingerprint(attrs)
                    }
                }
            }
            doLast {
                if (oldFingerprints.isEmpty()) {
                    // instrument non incrementally in-place
                    instrumentClasses(javaInstrumentator.asPath, destinationDir, classpath)
                    println("Instrumented all classes")
                } else {
                    //instrument incrementally
                    val classesDir = destinationDir
                    val instrumentationDir = project.buildDir.resolve("nullInstr_$name")
                    instrumentationDir.deleteRecursively()
                    instrumentationDir.mkdirs()

                    val instrumentedToOriginal = HashMap<Path, Path>()
                    val sourceDir = classesDir.toPath()
                    val targetDir = instrumentationDir.toPath()

                    classesDir.visitClasses { path, attrs ->
                        val oldPrint = oldFingerprints[path]
                        val newPrint = BasicFingerprint(attrs)
                        if (oldPrint == null || oldPrint != newPrint) {
                            val targetPath = targetDir.resolve(sourceDir.relativize(path))
                            Files.createDirectories(targetPath.parent)
                            Files.move(path, targetPath)
                            instrumentedToOriginal[targetPath] = path
                        }
                    }

                    instrumentClasses(javaInstrumentator.asPath, instrumentationDir, project.files(destinationDir) + classpath)

                    for ((instrumented, original) in instrumentedToOriginal) {
                        Files.move(instrumented, original)
                    }

                    println("Instrumented ${instrumentedToOriginal.size} classes")
                }
            }
        }
    }
}

fun JavaCompile.instrumentClasses(instrumentatorClasspath: String, instrumenterDir: File, myClasspath: FileCollection) {
    ant.withGroovyBuilder {
        "taskdef"(
            "name" to "instrumentIdeaExtensions",
            "classpath" to instrumentatorClasspath,
            "loaderref" to "java2.loader",
            "classname" to "com.intellij.ant.InstrumentIdeaExtensions"
        )
    }

    val sourceSet = project.sourceSets.single { it.compileJavaTaskName == name }

    val javaSourceDirectories = sourceSet.allJava.sourceDirectories.filter { it.exists() }

    ant.withGroovyBuilder {
        javaSourceDirectories.forEach { directory ->
            "instrumentIdeaExtensions"(
                "srcdir" to directory,
                "destdir" to instrumenterDir,
                "classpath" to myClasspath.asPath,
                "includeantruntime" to false,
                "instrumentNotNull" to true
            )
        }
    }
}