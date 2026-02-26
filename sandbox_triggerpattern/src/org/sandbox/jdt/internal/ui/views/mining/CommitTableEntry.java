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
package org.sandbox.jdt.internal.ui.views.mining;

import java.util.ArrayList;
import java.util.List;

import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;

/**
 * Mutable model entry for the commit table, wrapping a {@link CommitInfo} with
 * analysis status, AI evaluations, and inferred rules.
 *
 * <p>Supports both AI-based evaluation ({@link CommitEvaluation}) and
 * deterministic rule inference ({@link InferredRule}).</p>
 *
 * @since 1.2.6
 */
public class CommitTableEntry {

	private final CommitInfo commitInfo;
	private AnalysisStatus status;
	private List<InferredRule> inferredRules;
	private List<CommitEvaluation> evaluations;

	/**
	 * Creates a new table entry with initial PENDING status.
	 *
	 * @param commitInfo the commit metadata
	 */
	public CommitTableEntry(CommitInfo commitInfo) {
		this.commitInfo = commitInfo;
		this.status = AnalysisStatus.PENDING;
		this.inferredRules = List.of();
		this.evaluations = List.of();
	}

	/**
	 * @return the commit metadata
	 */
	public CommitInfo getCommitInfo() {
		return commitInfo;
	}

	/**
	 * @return the current analysis status
	 */
	public AnalysisStatus getStatus() {
		return status;
	}

	/**
	 * @param status the new analysis status
	 */
	public void setStatus(AnalysisStatus status) {
		this.status = status;
	}

	/**
	 * @return the inferred rules (empty list if none)
	 */
	public List<InferredRule> getInferredRules() {
		return inferredRules;
	}

	/**
	 * @param inferredRules the inferred rules
	 */
	public void setInferredRules(List<InferredRule> inferredRules) {
		this.inferredRules = inferredRules != null ? inferredRules : List.of();
	}

	/**
	 * @return the AI evaluations (empty list if none)
	 */
	public List<CommitEvaluation> getEvaluations() {
		return evaluations;
	}

	/**
	 * @param evaluations the AI evaluations
	 */
	public void setEvaluations(List<CommitEvaluation> evaluations) {
		this.evaluations = evaluations != null ? evaluations : List.of();
	}

	/**
	 * @return the number of rules (from AI evaluations or deterministic inference)
	 */
	public int getRuleCount() {
		if (!evaluations.isEmpty()) {
			return (int) evaluations.stream()
					.filter(e -> e.dslRule() != null && !e.dslRule().isBlank())
					.count();
		}
		return inferredRules.size();
	}

	/**
	 * @return {@code true} if the commit has analyzable rules
	 */
	public boolean hasRules() {
		return status == AnalysisStatus.DONE && getRuleCount() > 0;
	}

	/**
	 * Collects DSL rule strings from AI evaluations.
	 *
	 * @return list of non-blank DSL rules
	 */
	public List<String> getDslRules() {
		List<String> rules = new ArrayList<>();
		for (CommitEvaluation eval : evaluations) {
			if (eval.dslRule() != null && !eval.dslRule().isBlank()) {
				rules.add(eval.dslRule());
			}
		}
		return rules;
	}
}
