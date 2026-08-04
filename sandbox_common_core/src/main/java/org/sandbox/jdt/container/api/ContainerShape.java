/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.api;

/**
 * Structural representation used by a container before or after a semantic migration.
 *
 * <p>The shape describes representation only. Ordering, uniqueness, mutability and
 * concurrency are modelled separately by {@link ContainerUsageProfile}.</p>
 */
public enum ContainerShape {
	ARRAY,
	LIST,
	SET,
	MAP,
	DEQUE,
	SYNCHRONIZED_WRAPPER,
	CONCURRENT_CONTAINER,
	CUSTOM_BUFFER
}
