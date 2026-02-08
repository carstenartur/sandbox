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
package org.sandbox.functional.core.tree;

/**
 * Represents the conversion decision for a loop node in the loop tree.
 * 
 * <p>This enum tracks whether a loop can be converted to functional style
 * and if it should be skipped due to nested loop conversions.</p>
 * 
 * @since 1.0.0
 */
public enum ConversionDecision {
    /**
     * The loop can be converted to functional stream operations.
     */
    CONVERTIBLE,
    
    /**
     * The loop cannot be converted due to precondition failures.
     */
    NOT_CONVERTIBLE,
    
    /**
     * The loop is not converted because an inner nested loop was converted.
     * Converting both would lead to incorrect semantics.
     */
    SKIPPED_INNER_CONVERTED,
    
    /**
     * The conversion decision has not yet been determined.
     * This is the initial state before analysis.
     */
    PENDING
}
