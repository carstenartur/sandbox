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
package org.sandbox.jdt.triggerpattern.llm;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;

/**
 * Lightweight first-stage LLM result used to decide whether a commit is worth
 * a full candidate-generation request.
 * <p>
 * This deliberately omits expensive fields such as DSL rules, before/after
 * examples, and negative examples. Those should only be requested for commits
 * that pass this cheap screening stage.
 * </p>
 *
 * @param commitHash   the commit hash
 * @param relevant     whether the commit is relevant for mining
 * @param trafficLight coarse suitability assessment
 * @param category     coarse category, if known
 * @param confidence   confidence in the assessment, between {@code 0.0} and {@code 1.0}
 * @param reason       short explanation for the decision
 */
public record CommitScreening(
		String commitHash,
		boolean relevant,
		TrafficLight trafficLight,
		String category,
		double confidence,
		String reason) {

	/** Default confidence threshold for a full candidate request. */
	public static final double DEFAULT_CANDIDATE_THRESHOLD = 0.75d;

	/**
	 * @return whether this result should continue to full candidate generation
	 */
	public boolean shouldRequestCandidateDetails() {
		return relevant && trafficLight == TrafficLight.GREEN
				&& confidence >= DEFAULT_CANDIDATE_THRESHOLD;
	}
}
