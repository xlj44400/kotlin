
description = "Kotlin SamWithReceiver Compiler Plugin"

plugins {
    kotlin("jvm")
    id("jps-compatible")
}

dependencies {
    compileOnly(project(":compiler:frontend"))
    compileOnly(project(":compiler:frontend.java"))
    compileOnly(project(":compiler:plugin-api"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }

    testCompile(project(":compiler:backend"))
    testCompile(project(":compiler:cli"))
    testCompile(projectTests(":compiler:tests-common"))
    testCompile(commonDep("junit:junit"))

    testRuntimeOnly(intellijCoreDep()) { includeJars("intellij-core") }
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

publish()

runtimeJar()
sourcesJar()
javadocJar()
testsJar()

projectTest(parallel = true) {
    dependsOn(":dist")
    workingDir = rootDir
}
