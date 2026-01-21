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
 * Describes the loop element (the variable being iterated).
 * 
 * @param variableName the variable name
 * @param typeName the type name  
 * @param isFinal whether the variable is final
 * @since 1.0.0
 */
public record ElementDescriptor(
    String variableName,
    String typeName,
    boolean isFinal
) {
    /**
     * Creates an ElementDescriptor with isFinal=false.
     */
    public ElementDescriptor(String variableName, String typeName) {
        this(variableName, typeName, false);
    }
}
