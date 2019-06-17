/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.intentions.*
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.lineCount
import org.jetbrains.kotlin.idea.util.textRangeIn
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getReferenceTargets
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.VariableAsFunctionResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ReplaceSingleLineLetInspection : AbstractApplicabilityBasedInspection<KtCallExpression>(
    KtCallExpression::class.java
) {
    override fun inspectionText(element: KtCallExpression) = "Replace single line .let"

    override fun inspectionHighlightRangeInElement(element: KtCallExpression) = element.calleeExpression?.textRangeIn(element)

    override fun inspectionHighlightType(element: KtCallExpression): ProblemHighlightType = if (isSingleLine(element))
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    else
        ProblemHighlightType.INFORMATION

    override val defaultFixText = "Remove redundant '.let' call"

    override fun isApplicable(element: KtCallExpression): Boolean {
        if (!element.isLetMethodCall()) return false
        val lambdaExpression = element.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return false
        val parameterName = lambdaExpression.getParameterName() ?: return false

        return when (val bodyExpression = lambdaExpression.bodyExpression?.children?.singleOrNull() ?: return false) {
            is KtBinaryExpression ->
                element.parent !is KtSafeQualifiedExpression && bodyExpression.isApplicable(parameterName)
            is KtDotQualifiedExpression ->
                bodyExpression.isApplicable(parameterName)
            is KtCallExpression ->
                if (element.parent is KtSafeQualifiedExpression) {
                    false
                } else {
                    val count = lambdaExpression.functionLiteral.valueParameterReferences(bodyExpression).count()
                    val destructuringDeclaration = lambdaExpression.functionLiteral.valueParameters.firstOrNull()?.destructuringDeclaration
                    count == 0 || (count == 1 && destructuringDeclaration == null)
                }
            else ->
                false
        }
    }

    override fun applyTo(element: KtCallExpression, project: Project, editor: Editor?) {
        val lambdaExpression = element.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return
        when (val bodyExpression = lambdaExpression.bodyExpression?.children?.singleOrNull() ?: return) {
            is KtDotQualifiedExpression -> bodyExpression.applyTo(element)
            is KtBinaryExpression -> bodyExpression.applyTo(element)
            is KtCallExpression -> bodyExpression.applyTo(element, lambdaExpression.functionLiteral, editor)
        }
    }
}

private fun KtBinaryExpression.applyTo(element: KtCallExpression) {
    val left = left ?: return
    val factory = KtPsiFactory(element.project)
    when (val parent = element.parent) {
        is KtQualifiedExpression -> {
            val receiver = parent.receiverExpression
            val newLeft = when (left) {
                is KtDotQualifiedExpression -> left.replaceFirstReceiver(factory, receiver, parent is KtSafeQualifiedExpression)
                else -> receiver
            }
            val newExpression = factory.createExpressionByPattern("$0 $1 $2", newLeft, operationReference, right!!)
            parent.replace(newExpression)
        }
        else -> {
            val newLeft = when (left) {
                is KtDotQualifiedExpression -> left.deleteFirstReceiver()
                else -> factory.createThisExpression()
            }
            val newExpression = factory.createExpressionByPattern("$0 $1 $2", newLeft, operationReference, right!!)
            element.replace(newExpression)
        }
    }
}

private fun KtDotQualifiedExpression.applyTo(element: KtCallExpression) {
    when (val parent = element.parent) {
        is KtQualifiedExpression -> {
            val factory = KtPsiFactory(element.project)
            val receiver = parent.receiverExpression
            parent.replace(replaceFirstReceiver(factory, receiver, parent is KtSafeQualifiedExpression))
        }
        else -> {
            element.replace(deleteFirstReceiver())
        }
    }
}

private fun KtCallExpression.applyTo(element: KtCallExpression, functionLiteral: KtFunctionLiteral, editor: Editor?) {
    val parent = element.parent as? KtQualifiedExpression
    val reference = functionLiteral.valueParameterReferences(this).firstOrNull()
    val replaced = if (parent != null) {
        reference?.replace(parent.receiverExpression)
        parent.replaced(this)
    } else {
        reference?.replace(KtPsiFactory(this).createThisExpression())
        element.replaced(this)
    }
    editor?.caretModel?.moveToOffset(replaced.startOffset)
}

private fun KtBinaryExpression.isApplicable(parameterName: String, isTopLevel: Boolean = true): Boolean {
    val left = left ?: return false
    if (isTopLevel) {
        when (left) {
            is KtNameReferenceExpression -> if (left.text != parameterName) return false
            is KtDotQualifiedExpression -> if (!left.isApplicable(parameterName)) return false
            else -> return false
        }
    } else {
        if (!left.isApplicable(parameterName)) return false
    }

    val right = right ?: return false
    return right.isApplicable(parameterName)
}

private fun KtExpression.isApplicable(parameterName: String): Boolean = when (this) {
    is KtNameReferenceExpression -> text != parameterName
    is KtDotQualifiedExpression -> !hasLambdaExpression() && !nameUsed(parameterName)
    is KtBinaryExpression -> isApplicable(parameterName, isTopLevel = false)
    is KtCallExpression -> isApplicable(parameterName)
    is KtConstantExpression -> true
    else -> false
}

private fun KtCallExpression.isApplicable(parameterName: String): Boolean = valueArguments.all {
    val argumentExpression = it.getArgumentExpression() ?: return@all false
    argumentExpression.isApplicable(parameterName)
}

private fun KtDotQualifiedExpression.isApplicable(parameterName: String) =
    !hasLambdaExpression() && getLeftMostReceiverExpression().let { receiver ->
        receiver is KtNameReferenceExpression &&
                receiver.getReferencedName() == parameterName &&
                !nameUsed(parameterName, except = receiver)
    } && callExpression?.resolveToCall() !is VariableAsFunctionResolvedCall

private fun KtDotQualifiedExpression.hasLambdaExpression() = selectorExpression?.anyDescendantOfType<KtLambdaExpression>() ?: false

private fun KtCallExpression.isLetMethodCall() = calleeExpression?.text == "let" && isMethodCall("kotlin.let")

private fun KtLambdaExpression.getParameterName(): String? {
    val parameters = valueParameters
    if (parameters.size > 1) return null
    return if (parameters.size == 1) parameters[0].text else "it"
}

private fun KtExpression.nameUsed(name: String, except: KtNameReferenceExpression? = null): Boolean =
    anyDescendantOfType<KtNameReferenceExpression> { it != except && it.getReferencedName() == name }

private fun KtFunctionLiteral.valueParameterReferences(callExpression: KtCallExpression): List<KtNameReferenceExpression> {
    val context = analyze(BodyResolveMode.PARTIAL)
    val parameterDescriptor = context[BindingContext.FUNCTION, this]?.valueParameters?.singleOrNull() ?: return emptyList()
    val variableDescriptorByName = if (parameterDescriptor is ValueParameterDescriptorImpl.WithDestructuringDeclaration)
        parameterDescriptor.destructuringVariables.associateBy { it.name }
    else
        mapOf(parameterDescriptor.name to parameterDescriptor)

    val callee = (callExpression.calleeExpression as? KtNameReferenceExpression)?.let {
        val descriptor = variableDescriptorByName[it.getReferencedNameAsName()]
        if (descriptor != null && it.getReferenceTargets(context).singleOrNull() == descriptor) listOf(it) else null
    } ?: emptyList()
    return callee + callExpression.valueArguments.flatMap { arg ->
        arg.collectDescendantsOfType<KtNameReferenceExpression>().filter {
            val descriptor = variableDescriptorByName[it.getReferencedNameAsName()]
            descriptor != null && it.getResolvedCall(context)?.resultingDescriptor == descriptor
        }
    }
}

private fun isSingleLine(element: KtCallExpression): Boolean {
    val qualifiedExpression = element.getQualifiedExpressionForSelector() ?: return true
    var receiver = qualifiedExpression.receiverExpression as? KtQualifiedExpression ?: return true
    if (receiver.lineCount() > 1) return false
    var count = 1
    while (true) {
        if (count > 2) return false
        receiver = receiver.receiverExpression as? KtQualifiedExpression ?: break
        count++
    }
    return true
}