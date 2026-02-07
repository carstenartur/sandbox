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
 * Enumeration of loop types in the Unified Loop Representation.
 * 
 * <p>This enum represents the different kinds of loops that can be
 * analyzed and potentially converted to functional stream operations.</p>
 * 
 * @since 1.0.0
 */
public enum LoopKind {
    /**
     * Enhanced for-loop (for-each loop).
     * Example: {@code for (String item : items) { ... }}
     */
    ENHANCED_FOR,
    
    /**
     * Traditional for-loop with init, condition, and update.
     * Example: {@code for (int i = 0; i < n; i++) { ... }}
     */
    TRADITIONAL_FOR,
    
    /**
     * While loop.
     * Example: {@code while (condition) { ... }}
     */
    WHILE,
    
    /**
     * Do-while loop.
     * Example: {@code do { ... } while (condition);}
     */
    DO_WHILE,
    
    /**
     * While loop using an iterator.
     * Example: {@code while (it.hasNext()) { String item = it.next(); ... }}
     */
    ITERATOR_WHILE
}
