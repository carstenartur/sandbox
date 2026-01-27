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
package org.sandbox.jdt.internal.corext.fix.helper;

/**
 * Enum representing the target format for loop conversions.
 * 
 * <p>This enum defines the different formats that loops can be converted to:
 * <ul>
 *   <li>STREAM - Java 8+ Stream API (forEach, map, filter, reduce, etc.)</li>
 *   <li>FOR_LOOP - Enhanced for-loop (for-each style: for (T item : collection))</li>
 *   <li>WHILE_LOOP - While loop with iterator</li>
 * </ul>
 * </p>
 * 
 * <p><b>Note:</b> This enum is retained for potential future use with combo box preferences,
 * but the current implementation uses radio buttons with separate boolean constants in
 * {@link org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants}.</p>
 */
public enum LoopTargetFormat {
    /**
     * Convert to Java 8+ Stream API operations.
     * Example: list.stream().forEach(item -> System.out.println(item))
     */
    STREAM("stream"),
    
    /**
     * Convert to enhanced for-loop (for-each style).
     * Example: for (String item : list) { System.out.println(item); }
     * 
     * <p><b>Note:</b> This targets the enhanced for-loop introduced in Java 5,
     * not the classic indexed for-loop with initialization/condition/update.</p>
     */
    FOR_LOOP("for"),
    
    /**
     * Convert to while loop with iterator.
     * Example: Iterator<String> it = list.iterator(); while (it.hasNext()) { String item = it.next(); ... }
     */
    WHILE_LOOP("while");
    
    private final String id;
    
    LoopTargetFormat(String id) {
        this.id = id;
    }
    
    /**
     * Returns the string identifier for this format.
     */
    public String getId() {
        return id;
    }
    
    /**
     * Parses a string identifier into a LoopTargetFormat.
     * 
     * @param id the string identifier
     * @return the corresponding LoopTargetFormat, or STREAM if not found
     */
    public static LoopTargetFormat fromId(String id) {
        if (id == null) {
            return STREAM; // default
        }
        for (LoopTargetFormat format : values()) {
            if (format.id.equals(id)) {
                return format;
            }
        }
        return STREAM; // default fallback
    }
}
