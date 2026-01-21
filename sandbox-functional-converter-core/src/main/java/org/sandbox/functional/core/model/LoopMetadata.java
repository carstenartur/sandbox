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
package org.sandbox.functional.core.model;

/**
 * Metadata about loop characteristics and constraints.
 * 
 * @param hasBreak whether the loop contains break statements
 * @param hasContinue whether the loop contains continue statements
 * @param hasReturn whether the loop contains return statements
 * @param modifiesCollection whether the loop modifies the collection being iterated
 * @param requiresOrdering whether the loop requires ordering to be preserved
 * @since 1.0.0
 */
public record LoopMetadata(
    boolean hasBreak,
    boolean hasContinue,
    boolean hasReturn,
    boolean modifiesCollection,
    boolean requiresOrdering
) {
    /**
     * Creates a LoopMetadata with all flags set to false.
     */
    public static LoopMetadata safe() {
        return new LoopMetadata(false, false, false, false, false);
    }
    
    /**
     * Checks if the loop can be converted to a stream.
     * @return true if convertible (no break, no return)
     */
    public boolean isConvertible() {
        return !hasBreak && !hasReturn;
    }
}
