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
 * Represents a sorted operation in a stream pipeline.
 */
public record SortOp(String comparatorExpression) implements Operation {
    
    /**
     * Creates a SortOp with natural ordering (no comparator).
     */
    public SortOp() { 
        this(null); 
    }
    
    @Override
    public String expression() { 
        return comparatorExpression; 
    }
    
    @Override
    public String operationType() { 
        return "sorted"; 
    }
}
