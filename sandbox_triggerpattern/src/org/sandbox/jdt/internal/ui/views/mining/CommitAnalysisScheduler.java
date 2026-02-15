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

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.widgets.Display;
import org.sandbox.jdt.triggerpattern.mining.analysis.RuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.mining.git.GitHistoryProvider;

/**
 * Orchestrates asynchronous analysis of multiple commits using Eclipse
 * {@link org.eclipse.core.runtime.jobs.Job Jobs}.
 *
 * <p>Strategy:</p>
 * <ol>
 *   <li>All commits start with status PENDING (⏳)</li>
 *   <li>Jobs are scheduled (max 4 parallel by default)</li>
 *   <li>On completion: {@link TableViewer#update(Object, String[])} is called
 *       via {@link Display#asyncExec(Runnable)}</li>
 * </ol>
 *
 * @since 1.2.6
 */
public class CommitAnalysisScheduler {

	/** Default maximum number of parallel analysis jobs. */
	private static final int DEFAULT_MAX_PARALLEL = 4;

	private final GitHistoryProvider gitProvider;
	private final Path repositoryPath;
	private final RuleInferenceEngine engine;
	private final TableViewer tableViewer;
	private final List<CommitAnalysisJob> runningJobs = new ArrayList<>();
	private volatile boolean running;

	/**
	 * Creates a new scheduler.
	 *
	 * @param gitProvider    the git history provider
	 * @param repositoryPath path to the Git repository
	 * @param engine         the rule inference engine
	 * @param tableViewer    the commit table viewer to update
	 */
	public CommitAnalysisScheduler(GitHistoryProvider gitProvider, Path repositoryPath,
			RuleInferenceEngine engine, TableViewer tableViewer) {
		this.gitProvider = gitProvider;
		this.repositoryPath = repositoryPath;
		this.engine = engine;
		this.tableViewer = tableViewer;
	}

	/**
	 * Starts asynchronous analysis of the given commits.
	 *
	 * @param entries the commit entries to analyze
	 */
	public void startAnalysis(List<CommitTableEntry> entries) {
		cancelAnalysis();
		running = true;

		for (CommitTableEntry entry : entries) {
			CommitAnalysisJob job = new CommitAnalysisJob(
					entry, gitProvider, repositoryPath, engine,
					() -> notifyUpdate(entry));
			runningJobs.add(job);
			job.schedule();
		}
	}

	/**
	 * Cancels all running analysis jobs.
	 */
	public void cancelAnalysis() {
		running = false;
		for (CommitAnalysisJob job : runningJobs) {
			job.cancel();
		}
		runningJobs.clear();
	}

	/**
	 * @return {@code true} if any analysis is still running
	 */
	public boolean isRunning() {
		return running;
	}

	private void notifyUpdate(CommitTableEntry entry) {
		Display display = tableViewer.getTable().getDisplay();
		if (display != null && !display.isDisposed()) {
			display.asyncExec(() -> {
				if (!tableViewer.getTable().isDisposed()) {
					tableViewer.update(entry, null);
				}
			});
		}
	}
}
