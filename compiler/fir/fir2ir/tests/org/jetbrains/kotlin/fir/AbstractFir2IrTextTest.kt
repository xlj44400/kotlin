/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.search.GlobalSearchScope
import junit.framework.TestCase
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.fir.backend.Fir2IrConverter
import org.jetbrains.kotlin.fir.builder.RawFirBuilder
import org.jetbrains.kotlin.fir.resolve.FirProvider
import org.jetbrains.kotlin.fir.resolve.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.resolve.transformers.FirTotalResolveTransformer
import org.jetbrains.kotlin.ir.AbstractIrTextTestCase
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import java.io.File

abstract class AbstractFir2IrTextTest : AbstractIrTextTestCase() {

    private fun prepareProjectExtensions(project: Project) {
        Extensions.getArea(project)
            .getExtensionPoint(PsiElementFinder.EP_NAME)
            .unregisterExtension(JavaElementFinder::class.java)
    }

    override fun doTest(filePath: String?) {
        if (filePath == null) {
            super.doTest(filePath)
        }
        val text = File(filePath).readText()
        if (text.startsWith("// FIR_FAIL")) {
            try {
                super.doTest(filePath)
                TestCase.fail("This FIR2IR test threw exception before! Remove // FIR_FAIL line")
            } catch (t: Throwable) {

            }
        } else {
            super.doTest(filePath)
        }
    }

    override fun getExpectedTextFileName(testFile: TestFile, name: String): String {
        if ("// FIR_IDENTICAL" in testFile.content.split("\n")) {
            return super.getExpectedTextFileName(testFile, name)
        }
        return name.replace(".txt", ".fir.txt").replace(".kt", ".fir.txt")
    }

    override fun generateIrModule(ignoreErrors: Boolean): IrModuleFragment {
        val psiFiles = myFiles.psiFiles

        val project = psiFiles.first().project
        prepareProjectExtensions(project)

        val scope = GlobalSearchScope.filesScope(project, psiFiles.map { it.virtualFile })
            .uniteWith(TopDownAnalyzerFacadeForJVM.AllJavaSourcesInProjectScope(project))
        val session = createSession(myEnvironment, scope)

        val builder = RawFirBuilder(session, stubMode = false)

        val resolveTransformer = FirTotalResolveTransformer()
        val firFiles = psiFiles.map {
            val firFile = builder.buildFirFile(it)
            (session.service<FirProvider>() as FirProviderImpl).recordFile(firFile)
            firFile
        }.also {
            try {
                resolveTransformer.processFiles(it)
            } catch (e: Exception) {
                throw e
            }
        }

        return Fir2IrConverter.createModuleFragment(session, firFiles, myEnvironment.configuration.languageVersionSettings)
    }
}