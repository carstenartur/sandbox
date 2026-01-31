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

/**
 * Sealed interface for stream terminal operations.
 */
public sealed interface TerminalOperation 
    permits ForEachTerminal, CollectTerminal, ReduceTerminal, 
            MatchTerminal, FindTerminal, CountTerminal {
    
    /**
     * Returns the terminal operation type name.
     * @return the stream method name (e.g., "forEach", "collect")
     */
    String operationType();
}
