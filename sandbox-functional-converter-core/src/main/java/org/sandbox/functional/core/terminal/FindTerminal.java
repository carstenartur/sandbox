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
 * Represents a find terminal operation (findFirst or findAny).
 */
public record FindTerminal(boolean findFirst) implements TerminalOperation {
    @Override
    public String operationType() {
        return findFirst ? "findFirst" : "findAny";
    }
}
