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

/**
 * Computes a {@link MatchScore} from a {@link NullabilityResult}.
 *
 * <p>The scorer assigns a {@code trivialChange} value from 0 (trivial) to
 * 10 (critical) and a {@link MatchSeverity} based on the nullability status
 * and the reason/evidence collected by the {@link NullabilityGuard}.</p>
 *
 * @since 1.2.6
 */
public class ConfidenceScorer {

	/**
	 * Scores a nullability result.
	 *
	 * @param result the nullability analysis result
	 * @return the computed match score
	 */
	public MatchScore score(NullabilityResult result) {
		return switch (result.status()) {
			case NON_NULL -> scoreNonNull(result);
			case NULLABLE -> scoreNullable(result);
			case POTENTIALLY_NULLABLE -> scorePotentiallyNullable(result);
			case UNKNOWN -> scoreUnknown(result);
		};
	}

	private MatchScore scoreNonNull(NullabilityResult result) {
		String reason = result.reason();

		// AST getter on structural child → INFO (trivialChange 1)
		if (reason.contains("structural child")) { //$NON-NLS-1$
			return new MatchScore(1, NullStatus.NON_NULL, MatchSeverity.INFO, reason, result.evidence());
		}

		// Everything else that is NON_NULL → IGNORE (trivialChange 0)
		return new MatchScore(0, NullStatus.NON_NULL, MatchSeverity.IGNORE, reason, result.evidence());
	}

	private MatchScore scoreNullable(NullabilityResult result) {
		String reason = result.reason();

		// SpotBugs-style: null check AFTER usage → highest risk
		if (reason.contains("after usage")) { //$NON-NLS-1$
			return new MatchScore(10, NullStatus.NULLABLE, MatchSeverity.WARNING, reason, result.evidence());
		}

		// @Nullable annotation → CLEANUP
		if (reason.contains("@Nullable") || reason.contains("@CheckForNull")) { //$NON-NLS-1$ //$NON-NLS-2$
			return new MatchScore(10, NullStatus.NULLABLE, MatchSeverity.CLEANUP, reason, result.evidence());
		}

		// Map.get() → CLEANUP
		if (reason.contains("Map.get")) { //$NON-NLS-1$
			return new MatchScore(8, NullStatus.NULLABLE, MatchSeverity.CLEANUP, reason, result.evidence());
		}

		// Generic nullable → WARNING
		return new MatchScore(9, NullStatus.NULLABLE, MatchSeverity.WARNING, reason, result.evidence());
	}

	private MatchScore scorePotentiallyNullable(NullabilityResult result) {
		// Null check exists somewhere → QUICKASSIST with medium score
		return new MatchScore(5, NullStatus.POTENTIALLY_NULLABLE, MatchSeverity.QUICKASSIST,
				result.reason(), result.evidence());
	}

	private MatchScore scoreUnknown(NullabilityResult result) {
		String reason = result.reason();

		// Parameter without null check → QUICKASSIST
		if (reason.contains("parameter")) { //$NON-NLS-1$
			return new MatchScore(3, NullStatus.UNKNOWN, MatchSeverity.QUICKASSIST, reason, result.evidence());
		}

		// Field without null check → QUICKASSIST
		if (reason.contains("field")) { //$NON-NLS-1$
			return new MatchScore(5, NullStatus.UNKNOWN, MatchSeverity.QUICKASSIST, reason, result.evidence());
		}

		// Getter on unknown type → QUICKASSIST
		if (reason.contains("getter")) { //$NON-NLS-1$
			return new MatchScore(4, NullStatus.UNKNOWN, MatchSeverity.QUICKASSIST, reason, result.evidence());
		}

		// Default unknown → QUICKASSIST
		return new MatchScore(3, NullStatus.UNKNOWN, MatchSeverity.QUICKASSIST, reason, result.evidence());
	}
}
