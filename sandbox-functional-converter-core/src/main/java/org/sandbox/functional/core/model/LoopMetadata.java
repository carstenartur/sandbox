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
 * <p>Enforces strict rules for loop refactoring safety (thread-safety and semantics):</p>
 * <ul>
 *   <li>Collection modifications (add/remove/put/clear) on the iterated structure block conversion</li>
 *   <li>Iterator.remove() usage blocks conversion to enhanced for or stream</li>
 *   <li>Index variable usage beyond simple element access blocks conversion</li>
 * </ul>
 * 
 * @param hasBreak whether the loop contains break statements
 * @param hasContinue whether the loop contains continue statements
 * @param hasReturn whether the loop contains return statements
 * @param modifiesCollection whether the loop modifies the collection being iterated
 * @param requiresOrdering whether the loop requires ordering to be preserved
 * @param hasIteratorRemove whether the loop calls iterator.remove()
 * @param usesIndexBeyondGet whether the index variable is used for more than simple element access
 * @since 1.0.0
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
 */
public record LoopMetadata(
    boolean hasBreak,
    boolean hasContinue,
    boolean hasReturn,
    boolean modifiesCollection,
    boolean requiresOrdering,
    boolean hasIteratorRemove,
    boolean usesIndexBeyondGet
) {
    /**
     * Creates a LoopMetadata with all flags set to false.
     */
    public static LoopMetadata safe() {
        return new LoopMetadata(false, false, false, false, false, false, false);
    }
    
    /**
     * Checks if the loop can be converted to a stream or enhanced for.
     * 
     * <p>A loop is not convertible if any of the following conditions hold:</p>
     * <ul>
     *   <li>Contains break statements (cannot be expressed in lambdas)</li>
     *   <li>Contains return statements (changes control flow)</li>
     *   <li>Modifies the iterated collection (add/remove/put/clear cause ConcurrentModificationException)</li>
     *   <li>Uses iterator.remove() (not expressible in enhanced for or stream)</li>
     *   <li>Uses the index variable beyond simple element access (semantics change in enhanced for)</li>
     * </ul>
     * 
     * @return true if convertible
     * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
     */
    public boolean isConvertible() {
        return !hasBreak && !hasReturn && !modifiesCollection 
            && !hasIteratorRemove && !usesIndexBeyondGet;
    }
}
