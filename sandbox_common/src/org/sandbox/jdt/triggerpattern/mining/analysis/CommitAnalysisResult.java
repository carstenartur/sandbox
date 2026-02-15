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
package org.sandbox.jdt.triggerpattern.mining.analysis;

import java.time.Duration;
import java.util.List;

/**
 * Result of analyzing a single commit for inferable transformation rules.
 *
 * @param commitId      the commit hash that was analyzed
 * @param status        the current analysis status
 * @param inferredRules the rules inferred from this commit (empty if none found)
 * @param analysisTime  the wall-clock time taken for analysis (may be {@code null} if not yet complete)
 * @since 1.2.6
 */
public record CommitAnalysisResult(
		String commitId,
		AnalysisStatus status,
		List<InferredRule> inferredRules,
		Duration analysisTime) {

	/**
	 * Status of a commit analysis.
	 */
	public enum AnalysisStatus {
		/** Waiting to be analyzed. */
		PENDING,
		/** Currently being analyzed. */
		ANALYZING,
		/** Analysis completed, rules may have been inferred. */
		DONE,
		/** Analysis failed due to an error. */
		FAILED,
		/** Analysis completed but no rules could be inferred. */
		NO_RULES
	}
}
