/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.testing.internal

import org.gradle.api.GradleException
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.*
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider
import java.io.File
import java.net.URI

/**
 * Aggregates tests reports for kotlin test tasks added by [registerTestTask].
 *
 * Individual test tasks will not fail build if this task will be executed,
 * also individual html and xml reports will replaced by one consolidated html report.
 * This behavior can be disabled by setting `kotlin.tests.individualTaskReports` property
 * to true.
 *
 * Aggregate test reports may form hierarchy, for example:
 *  - allTest // aggregates all tests
 *      - jvmTest
 *      - nativeTest // aggregates all native tests
 *          - macos64Test
 *          - linux64Test
 *          - mingw64
 *      - jsTests // aggregates all js tests
 *          - browserTest
 *          - nodejsTest
 *
 * In this case, only topmost aggregate test task will override reporting,
 * event if child tasks will be executed.
 */
open class KotlinTestReport : TestReport() {
    @Internal
    val testTasks = mutableListOf<AbstractTestTask>()

    private var parent: KotlinTestReport? = null

    @Internal
    val children = mutableListOf<KotlinTestReport>()

    private val projectProperties = PropertiesProvider(project)

    val overrideReporting: Boolean
        @Input get() = projectProperties.individualTaskReports == null

    @Input
    var checkFailedTests: Boolean = false

    @Input
    var ignoreFailures: Boolean = false

    private var hasOwnFailedTests = false
    private val hasFailedTests: Boolean
        get() = hasOwnFailedTests || children.any { it.hasFailedTests }

    private val failedTestsListener = object : TestListener {
        override fun beforeTest(testDescriptor: TestDescriptor) {
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        }

        override fun beforeSuite(suite: TestDescriptor) {
        }

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            if (result.failedTestCount > 0) {
                hasOwnFailedTests = true
            }
        }
    }

    fun addChild(child: KotlinTestReport) {
        check(child.parent == null) { "$child already registers as child of ${child.parent}" }
        child.parent = this

        children.add(child)
        reportOnChildTasks(child)
    }

    private fun reportOnChildTasks(child: KotlinTestReport) {
        child.testTasks.forEach {
            reportOn(it)
        }
        child.children.forEach {
            reportOnChildTasks(it)
        }
    }

    fun registerTestTask(task: AbstractTestTask) {
        testTasks.add(task)
        task.addTestListener(failedTestsListener)
        reportOn(task)

        addToParents(task)
    }

    private fun addToParents(task: AbstractTestTask) {
        val parent = parent
        if (parent != null) {
            parent.reportOn(task)
            parent.addToParents(task)
        }
    }

    private fun reportOn(task: AbstractTestTask) {
        @Suppress("UnstableApiUsage")
        reportOn(task.binResultsDir)
    }

    open val htmlReportUrl: String?
        @Internal get() = destinationDir?.let { asClickableFileUrl(it.resolve("index.html")) }

    private fun asClickableFileUrl(path: File): String {
        return URI("file", "", path.toURI().path, null, null).toString()
    }

    @TaskAction
    fun checkFailedTests() {
        if (checkFailedTests && hasFailedTests) {
            val message = StringBuilder("There were failing tests.")

            val reportUrl = htmlReportUrl
            if (reportUrl != null) {
                message.append(" See the report at: $reportUrl")
            }

            if (ignoreFailures) {
                logger.warn(message.toString())
            } else {
                throw GradleException(message.toString())
            }
        }
    }

    fun maybeOverrideReporting(graph: TaskExecutionGraph) {
        if (!overrideReporting) return
        if (!graph.hasTask(this)) return

        // only topmost aggregate should override reporting
        var parent = parent
        while (parent != null) {
            if (parent.overrideReporting && graph.hasTask(parent)) return
            parent = parent.parent
        }

        overrideReporting()
    }

    private fun overrideReporting() {
        ignoreFailures = false
        checkFailedTests = true

        disableIndividualTestTaskReportingAndFailing()
    }

    private fun disableIndividualTestTaskReportingAndFailing() {
        testTasks.forEach {
            disableTestReporting(it)
        }

        children.forEach {
            it.checkFailedTests = false
            it.disableIndividualTestTaskReportingAndFailing()
        }
    }

    private fun disableTestReporting(task: AbstractTestTask) {
        task.ignoreFailures = true

        @Suppress("UnstableApiUsage")
        task.reports.html.isEnabled = false

        @Suppress("UnstableApiUsage")
        task.reports.junitXml.isEnabled = false
    }
}
