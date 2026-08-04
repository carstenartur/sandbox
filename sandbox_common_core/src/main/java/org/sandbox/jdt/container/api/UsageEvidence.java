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

import java.util.Objects;

/**
 * One source-backed fact contributing to a container usage profile.
 *
 * <p>The record deliberately stores only stable scalar data. AST nodes belong to the
 * parser pass that produced the evidence and must not be retained by a multi-file plan.</p>
 *
 * @param kind semantic category of the observation
 * @param summary concise explanation suitable for reports
 * @param sourceStart zero-based source offset
 * @param sourceLength source length in characters
 */
public record UsageEvidence(Kind kind, String summary, int sourceStart, int sourceLength) {

	public UsageEvidence {
		Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
		summary= Objects.requireNonNull(summary, "summary").strip(); //$NON-NLS-1$
		if (summary.isEmpty()) {
			throw new IllegalArgumentException("summary must not be empty"); //$NON-NLS-1$
		}
		if (sourceStart < 0) {
			throw new IllegalArgumentException("sourceStart must not be negative"); //$NON-NLS-1$
		}
		if (sourceLength < 0) {
			throw new IllegalArgumentException("sourceLength must not be negative"); //$NON-NLS-1$
		}
	}

	/** Semantic categories understood by container analysis and reporting. */
	public enum Kind {
		ARRAY_GROWTH,
		APPEND_WRITE,
		REFERENCE_COMPONENT,
		ARRAY_LENGTH_READ,
		INDEXED_READ,
		INDEXED_WRITE,
		ENCOUNTER_ITERATION,
		LOCAL_USAGE_COMPLETE,
		FLOW_CONTINUATION_ROOT,
		UNSUPPORTED_CONTINUATION,
		CAPTURED_USAGE,
		UNSAFE_ESCAPE,
		ARRAY_IDENTITY,
		UNCLASSIFIED_USAGE,
		UNRESOLVED_BINDING,
		REJECTION_BOUNDARY
	}
}
