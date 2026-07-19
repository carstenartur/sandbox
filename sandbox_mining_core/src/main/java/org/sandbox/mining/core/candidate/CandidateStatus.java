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
package org.sandbox.mining.core.candidate;

/**
 * Enforced lifecycle status of a mined cleanup candidate.
 *
 * <pre>
 * DISCOVERED -> DSL_VALID -> BEHAVIOR_VALID -> READY_FOR_REVIEW
 *                                                |       |
 *                                                v       v
 *                                            APPROVED  REJECTED
 *                                                |
 *                                                v
 *                                            PROMOTED
 * </pre>
 *
 * <p>{@link #DUPLICATE} records deterministic cross-origin duplicate
 * detection. {@link #SUPERSEDED} is a terminal state for an older proposal
 * revision or origin. Generated source files and test execution are
 * implementation details and are deliberately not domain states.</p>
 */
public enum CandidateStatus {

	/** Candidate was produced by discovery and has not been validated. */
	DISCOVERED,

	/** The DSL parser and deterministic DSL validator accepted the proposal. */
	DSL_VALID,

	/** Before/after/negative examples passed deterministic behavior verification. */
	BEHAVIOR_VALID,

	/** Candidate passed automated gates and is ready for a human decision. */
	READY_FOR_REVIEW,

	/** A human reviewer approved the candidate for promotion. */
	APPROVED,

	/** The rule and its behavior test were merged into the curated rule set. */
	PROMOTED,

	/** A reviewer or deterministic policy rejected the candidate. */
	REJECTED,

	/** The normalized rule duplicates another staged candidate. */
	DUPLICATE,

	/** This proposal was replaced by another candidate or revision. */
	SUPERSEDED;

	/**
	 * Returns whether a transition from this state to {@code target} is valid.
	 */
	public boolean canTransitionTo(CandidateStatus target) {
		if (target == null || target == this) {
			return false;
		}
		if (target == REJECTED || target == DUPLICATE || target == SUPERSEDED) {
			return this != PROMOTED && this != REJECTED
					&& this != DUPLICATE && this != SUPERSEDED;
		}
		return switch (this) {
		case DISCOVERED -> target == DSL_VALID;
		case DSL_VALID -> target == BEHAVIOR_VALID;
		case BEHAVIOR_VALID -> target == READY_FOR_REVIEW;
		case READY_FOR_REVIEW -> target == APPROVED;
		case APPROVED -> target == PROMOTED;
		case PROMOTED, REJECTED, DUPLICATE, SUPERSEDED -> false;
		};
	}
}
