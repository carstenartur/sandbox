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
 * Eclipse {@link Job} that analyzes a single commit in the background,
 * using AI-powered inference to generate DSL rules from Java file changes.
 *
 * <p>When the LLM service is available, the job sends file diffs to the
 * AI engine. Otherwise it falls back to the deterministic
 * {@code RuleInferenceEngine}.</p>
 *
 * @since 1.2.6
 */
public class CommitAnalysisJob extends Job {

	private final CommitTableEntry entry;
	private final GitHistoryProvider gitProvider;
	private final Path repositoryPath;
	private final Runnable onComplete;

	/**
	 * Creates a new analysis job.
	 *
	 * @param entry          the commit table entry to analyze
	 * @param gitProvider    the git history provider
	 * @param repositoryPath path to the Git repository
	 * @param onComplete     callback to run (on any thread) when analysis finishes
	 */
	public CommitAnalysisJob(CommitTableEntry entry, GitHistoryProvider gitProvider,
			Path repositoryPath, Runnable onComplete) {
		super("Analyzing commit " + entry.getCommitInfo().shortId()); //$NON-NLS-1$
		this.entry = entry;
		this.gitProvider = gitProvider;
		this.repositoryPath = repositoryPath;
		this.onComplete = onComplete;
		setSystem(true); // Don't show in Progress view
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		entry.setStatus(AnalysisStatus.ANALYZING);

		if (monitor.isCanceled()) {
			entry.setStatus(AnalysisStatus.PENDING);
			return Status.CANCEL_STATUS;
		}

		try {
			List<FileDiff> diffs = gitProvider.getDiffs(repositoryPath, entry.getCommitInfo().id());

			if (monitor.isCanceled()) {
				entry.setStatus(AnalysisStatus.PENDING);
				return Status.CANCEL_STATUS;
			}

			EclipseLlmService llmService = EclipseLlmService.getInstance();
			if (llmService.isAvailable()) {
				analyzeWithAi(llmService.getEngine(), diffs, monitor);
			} else {
				// No LLM available — mark as no rules
				entry.setStatus(AnalysisStatus.NO_RULES);
			}
		} catch (Exception e) {
			entry.setStatus(AnalysisStatus.FAILED);
		}

		if (onComplete != null) {
			onComplete.run();
		}

		return Status.OK_STATUS;
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
			engine.inferRuleFromDiff(unifiedDiff).ifPresent(evaluations::add);
		}

		if (evaluations.isEmpty()) {
			entry.setStatus(AnalysisStatus.NO_RULES);
		} else {
			entry.setEvaluations(evaluations);
			entry.setStatus(AnalysisStatus.DONE);
		}
	}

	private static String buildUnifiedDiff(FileDiff diff) {
		StringBuilder sb = new StringBuilder();
		sb.append("--- a/").append(diff.filePath()).append('\n'); //$NON-NLS-1$
		sb.append("+++ b/").append(diff.filePath()).append('\n'); //$NON-NLS-1$
		for (DiffHunk hunk : diff.hunks()) {
			sb.append("@@ -").append(hunk.beforeStartLine()) //$NON-NLS-1$
					.append(',').append(hunk.beforeLineCount())
					.append(" +").append(hunk.afterStartLine()) //$NON-NLS-1$
					.append(',').append(hunk.afterLineCount())
					.append(" @@\n"); //$NON-NLS-1$
			for (String line : hunk.beforeText().split("\n", -1)) { //$NON-NLS-1$
				if (!line.isEmpty()) {
					sb.append('-').append(line).append('\n');
				}
			}
			for (String line : hunk.afterText().split("\n", -1)) { //$NON-NLS-1$
				if (!line.isEmpty()) {
					sb.append('+').append(line).append('\n');
				}
			}
		}
		return sb.toString();
	}
}
