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
 * Represents a match terminal operation (anyMatch, allMatch, noneMatch).
 */
public record MatchTerminal(
    MatchType matchType,
    String predicate
) implements TerminalOperation {
    
    /**
     * Types of match operations.
     */
    public enum MatchType {
        /** anyMatch predicate */
        ANY_MATCH, 
        /** allMatch predicate */
        ALL_MATCH, 
        /** noneMatch predicate */
        NONE_MATCH
    }
    
    @Override
    public String operationType() {
        return switch (matchType) {
            case ANY_MATCH -> "anyMatch";
            case ALL_MATCH -> "allMatch";
            case NONE_MATCH -> "noneMatch";
        };
    }
}
