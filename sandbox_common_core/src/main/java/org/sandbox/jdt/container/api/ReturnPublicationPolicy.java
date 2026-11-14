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
	REJECT_NULLS;

	public ContainerCompatibilityDecision compatibility() {
		return switch (this) {
			case PRESERVE_NULLS -> new ContainerCompatibilityDecision(ContainerCompatibilityDecision.Mode.POLICY_ADAPTER,
					ContainerCompatibilityDecision.BridgeFeasibility.IMPOSSIBLE_RETURN_TYPE_ONLY,
					"Collections.unmodifiableList publishes a read-only view of the fresh builder. Order, duplicates and null elements are preserved. The builder has no surviving alias. Java cannot retain the old same-name array return."); //$NON-NLS-1$
			case REJECT_NULLS -> new ContainerCompatibilityDecision(ContainerCompatibilityDecision.Mode.BREAKING_SOURCE_MIGRATION,
					ContainerCompatibilityDecision.BridgeFeasibility.IMPOSSIBLE_RETURN_TYPE_ONLY,
					"List.copyOf publishes a read-only snapshot that preserves order and duplicates. A null element now throws NullPointerException at publication. Known callers migrate and the old array return descriptor is removed."); //$NON-NLS-1$
		};
	}
}
