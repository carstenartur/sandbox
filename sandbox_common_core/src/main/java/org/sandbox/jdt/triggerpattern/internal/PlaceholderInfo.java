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
package org.sandbox.jdt.triggerpattern.internal;

/**
 * Holds parsed information about a placeholder in a pattern.
 * 
 * <p>Supports both single placeholders (e.g., {@code $x}) and multi-placeholders 
 * (e.g., {@code $args$}) with optional type constraints (e.g., {@code $msg:StringLiteral}).</p>
 * 
 * @param name the placeholder name including $ markers (e.g., "$x", "$args$")
 * @param typeConstraint optional type constraint (e.g., "StringLiteral"), null if none
 * @param isMulti true if this is a multi-placeholder ($x$ style) that matches zero or more nodes
 * 
 * @since 1.3.1
 */
public record PlaceholderInfo(
    String name,
    String typeConstraint,
    boolean isMulti
) {
    /**
     * Creates a placeholder info with parsed components.
     * 
     * @param name the placeholder name including $ markers
     * @param typeConstraint optional type constraint, null if none
     * @param isMulti true for multi-placeholders ($x$ style)
     */
    public PlaceholderInfo {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Placeholder name cannot be null or empty"); //$NON-NLS-1$
        }
        if (!name.startsWith("$")) { //$NON-NLS-1$
            throw new IllegalArgumentException("Placeholder name must start with $: " + name); //$NON-NLS-1$
        }
    }
    
    /**
     * Returns the placeholder name without the $ markers.
     * For single placeholders: "$x" returns "x"
     * For multi-placeholders: "$args$" returns "args"
     * 
     * @return the bare placeholder name
     */
    public String getBareName() {
        String result = name;
        if (result.startsWith("$")) { //$NON-NLS-1$
            result = result.substring(1);
        }
        if (result.endsWith("$")) { //$NON-NLS-1$
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
