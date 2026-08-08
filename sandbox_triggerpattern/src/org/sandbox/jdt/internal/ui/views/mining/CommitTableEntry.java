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
 * analysis status, AI evaluations, inferred rules, and an actionable failure
 * message when analysis cannot complete.
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
	private String failureMessage;

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

	public CommitInfo getCommitInfo() {
		return commitInfo;
	}

	public AnalysisStatus getStatus() {
		return status;
	}

	public void setStatus(AnalysisStatus status) {
		this.status = status;
		if (status != AnalysisStatus.FAILED) {
			failureMessage = null;
		}
	}

	public List<InferredRule> getInferredRules() {
		return inferredRules;
	}

	public void setInferredRules(List<InferredRule> inferredRules) {
		this.inferredRules = inferredRules != null ? inferredRules : List.of();
	}

	public List<CommitEvaluation> getEvaluations() {
		return evaluations;
	}

	public void setEvaluations(List<CommitEvaluation> evaluations) {
		this.evaluations = evaluations != null ? evaluations : List.of();
	}

	/** Returns the user-facing reason for a failed analysis, if available. */
	public String getFailureMessage() {
		return failureMessage;
	}

	/** Records an analysis failure without retaining prompts, credentials, or provider responses. */
	public void setFailureMessage(String failureMessage) {
		this.failureMessage = failureMessage;
		this.status = AnalysisStatus.FAILED;
	}

	public int getRuleCount() {
		if (!evaluations.isEmpty()) {
			return (int) evaluations.stream()
					.filter(e -> e.dslRule() != null && !e.dslRule().isBlank())
					.count();
		}
		return inferredRules.size();
	}

	public boolean hasRules() {
		return status == AnalysisStatus.DONE && getRuleCount() > 0;
	}

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
