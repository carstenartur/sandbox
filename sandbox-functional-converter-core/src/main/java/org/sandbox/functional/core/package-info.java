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
 * Core model classes for the Unified Loop Representation (ULR).
 * 
 * <p>This package contains AST-independent representations of loop structures
 * that can be transformed into functional/stream-based equivalents. The ULR
 * model is designed to be independent of Eclipse JDT AST structures, making
 * it easier to test, maintain, and potentially reuse in other contexts.</p>
 * 
 * @see org.sandbox.functional.core.model.LoopModel
 * @see <a href="https://github.com/carstenartur/sandbox/issues/450">Issue #450</a>
 */
package org.sandbox.functional.core;
