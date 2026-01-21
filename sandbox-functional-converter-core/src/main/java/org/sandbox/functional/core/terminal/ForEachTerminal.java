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
package org.sandbox.functional.core.terminal;

import java.util.List;

/**
 * Represents a forEach or forEachOrdered terminal operation.
 */
public record ForEachTerminal(
    List<String> bodyStatements,
    boolean ordered
) implements TerminalOperation {
    
    /**
     * Creates a ForEachTerminal with unordered forEach.
     * @param bodyStatements the statements to execute for each element
     */
    public ForEachTerminal(List<String> bodyStatements) {
        this(bodyStatements, false);
    }
    
    @Override
    public String operationType() { 
        return ordered ? "forEachOrdered" : "forEach"; 
    }
}
