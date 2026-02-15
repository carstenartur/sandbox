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

import java.util.List;

/**
 * Listener interface for asynchronous commit analysis status updates.
 *
 * <p>Implementations receive callbacks when the analysis of a commit starts,
 * completes (with inferred rules), or fails.</p>
 *
 * @since 1.2.6
 */
public interface CommitAnalysisListener {

	/**
	 * Called when analysis of a commit has started.
	 *
	 * @param commitId the commit hash being analyzed
	 */
	void onAnalysisStarted(String commitId);

	/**
	 * Called when analysis of a commit has completed successfully.
	 *
	 * @param commitId the commit hash that was analyzed
	 * @param rules    the inferred rules (may be empty)
	 */
	void onAnalysisComplete(String commitId, List<InferredRule> rules);

	/**
	 * Called when analysis of a commit has failed.
	 *
	 * @param commitId the commit hash that failed
	 * @param error    the exception that caused the failure
	 */
	void onAnalysisFailed(String commitId, Exception error);
}
