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
/**
 * Stream terminal operation classes.
 * 
 * <p>This package contains the sealed interface {@link org.sandbox.functional.core.terminal.TerminalOperation}
 * and its implementations representing terminal stream operations like forEach, collect, reduce, etc.</p>
 * 
 * <p>Each terminal operation record captures the necessary information to generate the corresponding
 * stream terminal method call during code transformation.</p>
 * 
 * @since 1.0.0
 */
package org.sandbox.functional.core.terminal;
