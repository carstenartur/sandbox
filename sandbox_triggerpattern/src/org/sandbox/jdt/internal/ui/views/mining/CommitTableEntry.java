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

import java.util.List;

import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;

/**
 * Mutable model entry for the commit table, wrapping a {@link CommitInfo} with
 * analysis status and inferred rules.
 *
 * @since 1.2.6
 */
public class CommitTableEntry {

	private final CommitInfo commitInfo;
	private AnalysisStatus status;
	private List<InferredRule> inferredRules;

	/**
	 * Creates a new table entry with initial PENDING status.
	 *
	 * @param commitInfo the commit metadata
	 */
	public CommitTableEntry(CommitInfo commitInfo) {
		this.commitInfo = commitInfo;
		this.status = AnalysisStatus.PENDING;
		this.inferredRules = List.of();
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
	 * @return the number of inferred rules
	 */
	public int getRuleCount() {
		return inferredRules.size();
	}

	/**
	 * @return {@code true} if the commit has analyzable rules
	 */
	public boolean hasRules() {
		return status == AnalysisStatus.DONE && !inferredRules.isEmpty();
	}
}
