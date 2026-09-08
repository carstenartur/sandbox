/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.functional.core.operation;

import java.util.Objects;
import org.sandbox.functional.core.model.FunctionalExpression;

/**
 * Represents a peek operation in a stream pipeline.
 */
public record PeekOp(String expression, FunctionalExpression function) implements Operation {
    public PeekOp(String expression) {
        this(expression, null);
    }

    public PeekOp {
        Objects.requireNonNull(expression, "expression must not be null");
        if (function != null && !"void".equals(function.outputType())) {
            throw new IllegalArgumentException("A peek action must return void");
        }
    }

    @Override
    public String operationType() { 
        return "peek"; 
    }
}
