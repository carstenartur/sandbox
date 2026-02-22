/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining.core.llm;

import java.time.Instant;

/**
 * Holds the evaluation result for a single commit from the selected LLM provider.
 *
 * @param commitHash            the commit hash
 * @param commitMessage         the commit message
 * @param repoUrl               the repository URL
 * @param evaluatedAt           when the evaluation was performed
 * @param relevant              whether the commit is relevant for DSL mining
 * @param irrelevantReason      reason if not relevant
 * @param isDuplicate           whether a similar rule already exists
 * @param duplicateOf           existing rule this duplicates
 * @param reusability           reusability score (1-10)
 * @param codeImprovement       code improvement score (1-10)
 * @param implementationEffort  implementation effort score (1-10)
 * @param trafficLight          overall assessment (GREEN, YELLOW, RED, NOT_APPLICABLE)
 * @param category              category of the transformation
 * @param isNewCategory         whether this is a new category
 * @param categoryReason        reason for the category assignment
 * @param canImplementInCurrentDsl whether it can be implemented in the current DSL
 * @param dslRule               suggested DSL rule
 * @param targetHintFile        target hint file for the rule
 * @param languageChangeNeeded  description of language change needed
 * @param dslRuleAfterChange    DSL rule that would work after language change
 * @param summary               human-readable summary
 * @param dslValidationResult   result of DSL validation ("VALID" or error message), null if not validated
 */
public record CommitEvaluation(
		String commitHash,
		String commitMessage,
		String repoUrl,
		Instant evaluatedAt,
		boolean relevant,
		String irrelevantReason,
		boolean isDuplicate,
		String duplicateOf,
		int reusability,
		int codeImprovement,
		int implementationEffort,
		TrafficLight trafficLight,
		String category,
		boolean isNewCategory,
		String categoryReason,
		boolean canImplementInCurrentDsl,
		String dslRule,
		String targetHintFile,
		String languageChangeNeeded,
		String dslRuleAfterChange,
		String summary,
		String dslValidationResult) {

	/**
	 * Traffic light assessment for a commit evaluation.
	 */
	public enum TrafficLight {
		/** Directly implementable as DSL rule */
		GREEN,
		/** Implementable with DSL extensions */
		YELLOW,
		/** Not implementable in current DSL */
		RED,
		/** Not applicable / irrelevant commit */
		NOT_APPLICABLE
	}
}
