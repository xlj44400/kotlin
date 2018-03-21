/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.translate.intrinsic.operation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.js.backend.ast.JsExpression;
import org.jetbrains.kotlin.js.translate.context.TranslationContext;
import org.jetbrains.kotlin.psi.KtBinaryExpression;

public interface BinaryOperationIntrinsic {
    @NotNull
    JsExpression invoke(KtBinaryExpression expression, JsExpression left, JsExpression right, TranslationContext context);
}