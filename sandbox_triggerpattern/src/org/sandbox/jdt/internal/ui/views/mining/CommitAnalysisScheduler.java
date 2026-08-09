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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.widgets.Display;
import org.sandbox.jdt.triggerpattern.mining.git.GitHistoryProvider;

/**
 * Orchestrates AI-powered commit analysis through one bounded queue.
 *
 * <p>Only one commit is analyzed at a time. This deliberately trades raw
 * parallelism for predictable provider usage, cancellation, and rate-limit
 * behavior. The queue itself is one user-visible Eclipse {@link Job}, while the
 * commit table continues to expose per-commit state.</p>
 *
 * @since 1.2.6
 */
public class CommitAnalysisScheduler {

	/** Aggregate state suitable for a view status line or tests. */
	public record Progress(int completed, int total, int active, int queued,
			boolean running, boolean cancelled) {
	}

	private final GitHistoryProvider gitProvider;
	private final Path repositoryPath;
	private final TableViewer tableViewer;
	private final Consumer<Progress> progressListener;
	private final AtomicInteger completed = new AtomicInteger();

	private volatile boolean running;
	private volatile boolean cancelled;
	private volatile int total;
	private volatile long generation;
	private Job analysisJob;

	public CommitAnalysisScheduler(GitHistoryProvider gitProvider, Path repositoryPath,
			TableViewer tableViewer) {
		this(gitProvider, repositoryPath, tableViewer, progress -> {
		});
	}

	public CommitAnalysisScheduler(GitHistoryProvider gitProvider, Path repositoryPath,
			TableViewer tableViewer, Consumer<Progress> progressListener) {
		this.gitProvider = gitProvider;
		this.repositoryPath = repositoryPath;
		this.tableViewer = tableViewer;
		this.progressListener = progressListener != null ? progressListener : progress -> {
		};
	}

	/** Starts a bounded, user-visible analysis queue. */
	public synchronized void startAnalysis(List<CommitTableEntry> entries) {
		cancelAnalysis();
		long runGeneration = ++generation;
		List<CommitTableEntry> queue = List.copyOf(entries);
		total = queue.size();
		completed.set(0);
		cancelled = false;
		running = !queue.isEmpty();

		if (queue.isEmpty()) {
			notifyProgress(new Progress(0, 0, 0, 0, false, false), runGeneration);
			return;
		}

		notifyProgress(progress(0), runGeneration);
		analysisJob = new Job("Refactoring Mining: " + total + " commits") { //$NON-NLS-1$ //$NON-NLS-2$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask("Inferring TriggerPattern rules from commit history", total); //$NON-NLS-1$
				try {
					for (int index = 0; index < queue.size(); index++) {
						if (runGeneration != generation || monitor.isCanceled()) {
							if (runGeneration == generation) {
								cancelled = true;
							}
							return Status.CANCEL_STATUS;
						}

						CommitTableEntry entry = queue.get(index);
						monitor.subTask("Analyzing " + entry.getCommitInfo().shortId() + ": " //$NON-NLS-1$ //$NON-NLS-2$
								+ entry.getCommitInfo().message());
						notifyProgress(progress(1), runGeneration);

						CommitAnalysisJob commitJob = new CommitAnalysisJob(entry, gitProvider,
								repositoryPath, () -> notifyUpdate(entry));
						IStatus status = commitJob.analyze(monitor);
						if (runGeneration != generation || status.matches(IStatus.CANCEL) || monitor.isCanceled()) {
							if (runGeneration == generation) {
								cancelled = true;
							}
							return Status.CANCEL_STATUS;
						}

						completed.incrementAndGet();
						monitor.worked(1);
						notifyProgress(progress(0), runGeneration);
					}
					return Status.OK_STATUS;
				} finally {
					monitor.done();
					if (runGeneration == generation) {
						running = false;
						notifyProgress(progress(0), runGeneration);
					}
				}
			}
		};
		analysisJob.setUser(true);
		analysisJob.schedule();
	}

	/** Cancels both the active commit and all queued commits. */
	public synchronized void cancelAnalysis() {
		long cancelledGeneration = ++generation;
		if (analysisJob != null) {
			cancelled = true;
			analysisJob.cancel();
			analysisJob = null;
		}
		if (running) {
			running = false;
			notifyProgress(progress(0), cancelledGeneration);
		}
	}

	public boolean isRunning() {
		return running;
	}

	public Progress getProgress() {
		return progress(running ? 1 : 0);
	}

	private Progress progress(int active) {
		int boundedCompleted = Math.min(completed.get(), total);
		int queued = Math.max(0, total - boundedCompleted - active);
		return new Progress(boundedCompleted, total, active, queued, running, cancelled);
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

	private void notifyProgress(Progress progress, long progressGeneration) {
		Display display = tableViewer.getTable().getDisplay();
		if (display != null && !display.isDisposed()) {
			display.asyncExec(() -> {
				if (progressGeneration == generation) {
					progressListener.accept(progress);
				}
			});
		}
	}
}
