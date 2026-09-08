/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.functional.core.model;

import java.util.Objects;

/**
 * A complete lambda or method reference in an extracted pipeline.
 *
 * <p>Types describe the resolved functional interface and its single argument and
 * result, including boxing. A renderer must retain the function boundary when
 * {@code requiresInvocation} is true (for example for local returns or parameter
 * writes). Pattern variables need a separate scope when lowering a predicate to
 * a loop guard. The JDT adapter retains the corresponding AST nodes and bindings;
 * this value and the ULR core have no dependency on JDT.</p>
 */
public record FunctionalExpression(String expression, String parameterName,
        String inputType, String outputType, String functionType,
        boolean requiresInvocation, boolean containsPatternVariable) {

    public FunctionalExpression {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(inputType, "inputType");
        Objects.requireNonNull(outputType, "outputType");
        Objects.requireNonNull(functionType, "functionType");
        if (!requiresInvocation && parameterName == null) {
            throw new IllegalArgumentException("Inlining requires a lambda parameter");
        }
    }
}
