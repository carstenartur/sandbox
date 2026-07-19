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
package org.sandbox.jdt.triggerpattern.llm;

import java.time.Instant;

/**
 * Holds the evaluation result for a single commit from the selected LLM provider.
 *
 * @param commitHash            the commit hash
 * @param commitMessage         the commit message
 * @param repoUrl               the repository URL
 * @param evaluatedAt           when the evaluation was performed
 * @param commitDate            the original Git commit's author date
 * @param relevant              whether the commit is relevant for DSL mining
 * @param irrelevantReason      reason if not relevant
 * @param isDuplicate           whether a similar rule already exists
 * @param duplicateOf           existing rule this duplicates
 * @param reusability           legacy score; discovery-first prompts map confidence to this 0-10 value
 * @param codeImprovement       legacy code improvement score
 * @param implementationEffort  legacy implementation effort score
 * @param trafficLight          overall assessment
 * @param category              category of the transformation
 * @param isNewCategory         whether this is a new category
 * @param categoryReason        reason for the category assignment
 * @param canImplementInCurrentDsl whether it can be implemented in the current DSL
 * @param dslRule               suggested DSL rule
 * @param targetHintFile        target hint file for the rule
 * @param languageChangeNeeded  legacy description of language change needed
 * @param dslRuleAfterChange    legacy DSL rule after a language extension
 * @param summary               human-readable summary
 * @param dslValidationResult   result of deterministic DSL validation
 * @param beforeExample         Java code example that should be matched by the rule
 * @param afterExample          expected Java code after the rule is applied
 * @param negativeExample       Java code example that should not match the rule
 * @param sourceVersion         Java source level for parsing and guard evaluation
 */
public record CommitEvaluation(
		String commitHash,
		String commitMessage,
		String repoUrl,
		Instant evaluatedAt,
		Instant commitDate,
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
		String dslValidationResult,
		String beforeExample,
		String afterExample,
		String negativeExample,
		String sourceVersion) {

	/**
	 * Compatibility constructor for existing callers and persisted test fixtures.
	 * New discovery clients should supply the source version explicitly.
	 */
	public CommitEvaluation(
			String commitHash,
			String commitMessage,
			String repoUrl,
			Instant evaluatedAt,
			Instant commitDate,
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
			String dslValidationResult,
			String beforeExample,
			String afterExample,
			String negativeExample) {
		this(commitHash, commitMessage, repoUrl, evaluatedAt, commitDate,
				relevant, irrelevantReason, isDuplicate, duplicateOf,
				reusability, codeImprovement, implementationEffort, trafficLight,
				category, isNewCategory, categoryReason, canImplementInCurrentDsl,
				dslRule, targetHintFile, languageChangeNeeded, dslRuleAfterChange,
				summary, dslValidationResult, beforeExample, afterExample,
				negativeExample, "21"); //$NON-NLS-1$
	}

	/** Returns a usable source version even for legacy JSON with no field. */
	public String sourceVersion() {
		return sourceVersion == null || sourceVersion.isBlank() ? "21" : sourceVersion; //$NON-NLS-1$
	}

	/** Traffic light assessment retained for reporter compatibility. */
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
