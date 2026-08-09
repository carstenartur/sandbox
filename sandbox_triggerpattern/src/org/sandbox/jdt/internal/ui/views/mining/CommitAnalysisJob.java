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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.sandbox.jdt.triggerpattern.llm.AiRuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;
import org.sandbox.jdt.triggerpattern.mining.analysis.DiffHunk;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;
import org.sandbox.jdt.triggerpattern.mining.git.GitHistoryProvider;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/**
 * Analyzes a single commit using AI-powered inference to generate DSL rules
 * from Java file changes.
 *
 * <p>The class remains a {@link Job} for compatibility, but the package-visible
 * {@link #analyze(IProgressMonitor)} method also lets
 * {@link CommitAnalysisScheduler} execute commits through one bounded,
 * user-visible queue instead of scheduling an unbounded set of provider calls.</p>
 *
 * @since 1.2.6
 */
public class CommitAnalysisJob extends Job {

	private final CommitTableEntry entry;
	private final GitHistoryProvider gitProvider;
	private final Path repositoryPath;
	private final Runnable onComplete;

	public CommitAnalysisJob(CommitTableEntry entry, GitHistoryProvider gitProvider,
			Path repositoryPath, Runnable onComplete) {
		super("Analyzing commit " + entry.getCommitInfo().shortId()); //$NON-NLS-1$
		this.entry = entry;
		this.gitProvider = gitProvider;
		this.repositoryPath = repositoryPath;
		this.onComplete = onComplete;
		setSystem(true);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		return analyze(monitor);
	}

	IStatus analyze(IProgressMonitor monitor) {
		entry.setStatus(AnalysisStatus.ANALYZING);
		entry.setFailureMessage(""); //$NON-NLS-1$
		try {
			if (monitor.isCanceled()) {
				entry.setStatus(AnalysisStatus.PENDING);
				return Status.CANCEL_STATUS;
			}

			List<FileDiff> diffs = gitProvider.getDiffs(repositoryPath, entry.getCommitInfo().id());
			if (monitor.isCanceled()) {
				entry.setStatus(AnalysisStatus.PENDING);
				return Status.CANCEL_STATUS;
			}

			EclipseLlmService llmService = EclipseLlmService.getInstance();
			if (llmService.isAvailable()) {
				analyzeWithAi(llmService.getEngine(), diffs, monitor);
			} else {
				entry.setStatus(AnalysisStatus.NO_RULES);
			}
			return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
		} catch (Exception e) {
			String message = e.getMessage();
			entry.setFailureMessage(message == null || message.isBlank()
					? e.getClass().getSimpleName()
					: message);
			entry.setStatus(AnalysisStatus.FAILED);
			return Status.error("Commit analysis failed", e); //$NON-NLS-1$
		} finally {
			if (onComplete != null) {
				onComplete.run();
			}
		}
	}

	private void analyzeWithAi(AiRuleInferenceEngine engine, List<FileDiff> diffs,
			IProgressMonitor monitor) {
		List<CommitEvaluation> evaluations = new ArrayList<>();

		for (FileDiff diff : diffs) {
			if (monitor.isCanceled()) {
				entry.setStatus(AnalysisStatus.PENDING);
				return;
			}
			String unifiedDiff = buildUnifiedDiff(diff);
			engine.inferRuleFromDiff(unifiedDiff)
					.filter(e -> e.dslRule() != null && !e.dslRule().isBlank())
					.ifPresent(evaluations::add);
		}

		if (evaluations.isEmpty()) {
			entry.setStatus(AnalysisStatus.NO_RULES);
		} else {
			entry.setEvaluations(evaluations);
			entry.setStatus(AnalysisStatus.DONE);
		}
	}

	public static String buildUnifiedDiff(FileDiff diff) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- a/").append(diff.filePath()).append('\n'); //$NON-NLS-1$
		sb.append("+++ b/").append(diff.filePath()).append('\n'); //$NON-NLS-1$
		for (DiffHunk hunk : diff.hunks()) {
			String[] beforeLines = hunk.beforeText().split("\n", -1); //$NON-NLS-1$
			String[] afterLines = hunk.afterText().split("\n", -1); //$NON-NLS-1$

			sb.append("@@ -").append(hunk.beforeStartLine()) //$NON-NLS-1$
					.append(',').append(beforeLines.length)
					.append(" +").append(hunk.afterStartLine()) //$NON-NLS-1$
					.append(',').append(afterLines.length)
					.append(" @@\n"); //$NON-NLS-1$

			for (String markedLine : buildHunkLines(beforeLines, afterLines)) {
				sb.append(markedLine).append('\n');
			}
		}
		return sb.toString();
	}

	public static List<String> buildHunkLines(String[] beforeLines, String[] afterLines) {
		int n = beforeLines.length;
		int m = afterLines.length;

		int[][] dp = new int[n + 1][m + 1];
		for (int i = n - 1; i >= 0; i--) {
			for (int j = m - 1; j >= 0; j--) {
				if (beforeLines[i].equals(afterLines[j])) {
					dp[i][j] = dp[i + 1][j + 1] + 1;
				} else {
					dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
				}
			}
		}

		List<String> result = new ArrayList<>();
		int i = 0;
		int j = 0;
		while (i < n && j < m) {
			if (beforeLines[i].equals(afterLines[j])) {
				result.add(" " + beforeLines[i]); //$NON-NLS-1$
				i++;
				j++;
			} else if (dp[i + 1][j] >= dp[i][j + 1]) {
				result.add("-" + beforeLines[i]); //$NON-NLS-1$
				i++;
			} else {
				result.add("+" + afterLines[j]); //$NON-NLS-1$
				j++;
			}
		}
		while (i < n) {
			result.add("-" + beforeLines[i]); //$NON-NLS-1$
			i++;
		}
		while (j < m) {
			result.add("+" + afterLines[j]); //$NON-NLS-1$
			j++;
		}
		return result;
	}
}
