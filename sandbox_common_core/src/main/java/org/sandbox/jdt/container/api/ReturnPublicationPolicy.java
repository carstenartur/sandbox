/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.api;

/** Explicit policy for publishing a closed append-only builder as a read-only list. */
public enum ReturnPublicationPolicy {
	/** Preserve null elements in the read-only view of the fresh, unaliased builder. */
	PRESERVE_NULLS,
	/** Create an immutable snapshot and throw at publication when an element is null. */
	REJECT_NULLS
}
