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
 * Stream pipeline operation classes.
 * 
 * <p>This package contains the sealed interface {@link org.sandbox.functional.core.operation.Operation}
 * and its implementations representing intermediate stream operations like filter, map, flatMap, etc.</p>
 * 
 * <p>Each operation record captures the necessary information to generate the corresponding
 * stream method call during code transformation.</p>
 * 
 * @since 1.0.0
 */
package org.sandbox.functional.core.operation;
