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
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;
import org.sandbox.jdt.triggerpattern.mining.analysis.RuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.mining.git.GitHistoryProvider;

/**
 * Eclipse {@link Job} that analyzes a single commit in the background,
 * inferring transformation rules from its Java file changes.
 *
 * @since 1.2.6
 */
public class CommitAnalysisJob extends Job {

	private final CommitTableEntry entry;
	private final GitHistoryProvider gitProvider;
	private final Path repositoryPath;
	private final RuleInferenceEngine engine;
	private final Runnable onComplete;

	/**
	 * Creates a new analysis job.
	 *
	 * @param entry          the commit table entry to analyze
	 * @param gitProvider    the git history provider
	 * @param repositoryPath path to the Git repository
	 * @param engine         the rule inference engine
	 * @param onComplete     callback to run (on any thread) when analysis finishes
	 */
	public CommitAnalysisJob(CommitTableEntry entry, GitHistoryProvider gitProvider,
			Path repositoryPath, RuleInferenceEngine engine, Runnable onComplete) {
		super("Analyzing commit " + entry.getCommitInfo().shortId()); //$NON-NLS-1$
		this.entry = entry;
		this.gitProvider = gitProvider;
		this.repositoryPath = repositoryPath;
		this.engine = engine;
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
			List<InferredRule> rules = engine.inferFromCommit(
					gitProvider, repositoryPath, entry.getCommitInfo().id());

			if (monitor.isCanceled()) {
				entry.setStatus(AnalysisStatus.PENDING);
				return Status.CANCEL_STATUS;
			}

			if (rules.isEmpty()) {
				entry.setStatus(AnalysisStatus.NO_RULES);
			} else {
				entry.setInferredRules(rules);
				entry.setStatus(AnalysisStatus.DONE);
			}
		} catch (Exception e) {
			entry.setStatus(AnalysisStatus.FAILED);
		}

		if (onComplete != null) {
			onComplete.run();
		}

		return Status.OK_STATUS;
	}
}
