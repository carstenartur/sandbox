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
package org.sandbox.jdt.triggerpattern.nullability;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the nullability assessment and confidence score for a single
 * mining match.
 *
 * @param trivialChange score from 0 (trivial/ignore) to 10 (critical)
 * @param nullStatus the determined nullability status
 * @param severity the computed severity level
 * @param reason human-readable explanation for the assessment
 * @param evidence lines of evidence supporting the assessment (e.g., null-check locations)
 * @since 1.2.6
 */
public record MatchScore(
		int trivialChange,
		NullStatus nullStatus,
		MatchSeverity severity,
		String reason,
		List<String> evidence) {

	/**
	 * Canonical constructor with validation.
	 */
	public MatchScore {
		if (trivialChange < 0 || trivialChange > 10) {
			throw new IllegalArgumentException("trivialChange must be between 0 and 10"); //$NON-NLS-1$
		}
		Objects.requireNonNull(nullStatus, "nullStatus"); //$NON-NLS-1$
		Objects.requireNonNull(severity, "severity"); //$NON-NLS-1$
		Objects.requireNonNull(reason, "reason"); //$NON-NLS-1$
		evidence = evidence != null ? Collections.unmodifiableList(evidence) : List.of();
	}

	/**
	 * Convenience constructor without evidence.
	 */
	public MatchScore(int trivialChange, NullStatus nullStatus, MatchSeverity severity, String reason) {
		this(trivialChange, nullStatus, severity, reason, List.of());
	}
}
