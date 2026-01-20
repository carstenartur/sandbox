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
 * Sealed interface for stream pipeline operations.
 * 
 * Each operation represents an intermediate stream operation
 * like filter, map, flatMap, etc.
 */
public sealed interface Operation 
    permits FilterOp, MapOp, FlatMapOp, PeekOp, DistinctOp, SortOp, LimitOp, SkipOp {
    
    /**
     * Returns the expression used in this operation.
     * @return the expression string, or null if not applicable
     */
    String expression();
    
    /**
     * Returns the operation type name for code generation.
     * @return the stream method name (e.g., "filter", "map")
     */
    String operationType();
}
