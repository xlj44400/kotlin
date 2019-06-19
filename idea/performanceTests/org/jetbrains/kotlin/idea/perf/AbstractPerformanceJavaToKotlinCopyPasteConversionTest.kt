/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.conversion.copy.AbstractJavaToKotlinCopyPasteConversionTest
import org.jetbrains.kotlin.idea.conversion.copy.ConvertJavaCopyPasteProcessor
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File

abstract class AbstractPerformanceJavaToKotlinCopyPasteConversionTest : AbstractJavaToKotlinCopyPasteConversionTest() {

    private val stats: Stats = Stats("-j2k")

    override fun setUp() {
        super.setUp()
        doWarmUpPerfTest()
    }

    private fun doWarmUpPerfTest() {
        with(myFixture) {
            configureByText(JavaFileType.INSTANCE, "<selection>public class Foo {\nprivate String value;\n}</selection>")
            performEditorAction(IdeActions.ACTION_CUT)
            configureByText(KotlinFileType.INSTANCE, "<caret>")
            ConvertJavaCopyPasteProcessor.conversionPerformed = false
            tcSimplePerfTest("", "warm-up java2kotlin conversion", stats) {
                performEditorAction(IdeActions.ACTION_PASTE)
            }
        }

        kotlin.test.assertFalse(!ConvertJavaCopyPasteProcessor.conversionPerformed, "No conversion to Kotlin suggested")
        assertEquals("class Foo {\n    private val value: String? = null\n}", myFixture.file.text)
    }

    fun doPerfTest(path: String) {
        myFixture.testDataPath = testDataPath
        val testName = getTestName(false)
        myFixture.configureByFiles("$testName.java")

        val fileText = myFixture.editor.document.text
        val noConversionExpected = InTextDirectivesUtils.findListWithPrefixes(fileText, "// NO_CONVERSION_EXPECTED").isNotEmpty()

        myFixture.performEditorAction(IdeActions.ACTION_COPY)

        configureByDependencyIfExists("$testName.dependency.kt")
        configureByDependencyIfExists("$testName.dependency.java")

        configureTargetFile("$testName.to.kt")

        ConvertJavaCopyPasteProcessor.conversionPerformed = false

        tcSimplePerfTest(testName, "java2kotlin conversion: $testName", stats) {
            myFixture.performEditorAction(IdeActions.ACTION_PASTE)
        }

        kotlin.test.assertEquals(
            noConversionExpected, !ConvertJavaCopyPasteProcessor.conversionPerformed,
            if (noConversionExpected) "Conversion to Kotlin should not be suggested" else "No conversion to Kotlin suggested"
        )

        KotlinTestUtils.assertEqualsToFile(File(path.replace(".java", ".expected.kt")), myFixture.file.text)
    }
}