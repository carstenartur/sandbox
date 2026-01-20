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

/**
 * Represents a map operation in a stream pipeline.
 */
public record MapOp(String expression, String targetType) implements Operation {
    
    /**
     * Creates a MapOp with just an expression and no target type.
     * @param expression the mapping expression
     */
    public MapOp(String expression) { 
        this(expression, null); 
    }
    
    @Override
    public String operationType() { 
        return "map"; 
    }
}
