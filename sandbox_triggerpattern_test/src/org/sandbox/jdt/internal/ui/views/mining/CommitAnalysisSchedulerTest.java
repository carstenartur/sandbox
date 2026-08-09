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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitAnalysisResult.AnalysisStatus;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;
import org.sandbox.jdt.triggerpattern.mining.git.GitHistoryProvider;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/** Verifies bounded execution, cancellation, completion and failure semantics. */
public class CommitAnalysisSchedulerTest {

	private static final String SCREENSHOT_PROPERTY = "sandbox.help.screenshot.mode"; //$NON-NLS-1$
	private static final Path REPOSITORY = Path.of("repository"); //$NON-NLS-1$

	private Display display;
	private Shell shell;
	private TableViewer tableViewer;
	private CommitAnalysisScheduler scheduler;
	private String previousScreenshotProperty;

	@BeforeEach
	public void setUp() {
		previousScreenshotProperty = System.getProperty(SCREENSHOT_PROPERTY);
		System.setProperty(SCREENSHOT_PROPERTY, Boolean.TRUE.toString());
		EclipseLlmService.reset();
		display = Display.getDefault();
		display.syncExec(() -> {
			shell = new Shell(display);
			tableViewer = new TableViewer(shell, SWT.NONE);
		});
	}

	@AfterEach
	public void tearDown() {
		if (scheduler != null) {
			scheduler.cancelAnalysis();
		}
		display.syncExec(() -> {
			if (shell != null && !shell.isDisposed()) {
				shell.dispose();
			}
		});
		if (previousScreenshotProperty == null) {
			System.clearProperty(SCREENSHOT_PROPERTY);
		} else {
			System.setProperty(SCREENSHOT_PROPERTY, previousScreenshotProperty);
		}
		EclipseLlmService.reset();
	}

	@Test
	public void completesNaturallyAndResetsRunningState() throws Exception {
		TrackingProvider provider = new TrackingProvider(false, false);
		List<CommitTableEntry> entries = entries(3);
		scheduler = createScheduler(provider);

		scheduler.startAnalysis(entries);
		await(() -> !scheduler.isRunning());

		CommitAnalysisScheduler.Progress progress = scheduler.getProgress();
		assertEquals(3, provider.calls.get());
		assertEquals(1, provider.maxActive.get());
		assertEquals(3, progress.completed());
		assertEquals(3, progress.total());
		assertEquals(0, progress.active());
		assertEquals(0, progress.queued());
		assertFalse(progress.running());
		assertFalse(progress.cancelled());
		assertTrue(entries.stream().allMatch(entry -> entry.getStatus() == AnalysisStatus.NO_RULES));
	}

	@Test
	public void exposesOneActiveCommitAndKeepsRemainingWorkQueued() throws Exception {
		TrackingProvider provider = new TrackingProvider(true, false);
		scheduler = createScheduler(provider);

		scheduler.startAnalysis(entries(3));
		assertTrue(provider.firstEntered.await(5, TimeUnit.SECONDS), "First commit was not started"); //$NON-NLS-1$

		CommitAnalysisScheduler.Progress progress = scheduler.getProgress();
		assertEquals(1, provider.calls.get());
		assertEquals(1, provider.maxActive.get());
		assertEquals(0, progress.completed());
		assertEquals(1, progress.active());
		assertEquals(2, progress.queued());
		assertTrue(progress.running());

		provider.releaseFirst.countDown();
		await(() -> !scheduler.isRunning());
		assertEquals(3, provider.calls.get());
		assertEquals(1, provider.maxActive.get());
	}

	@Test
	public void cancellationInterruptsActiveCommitAndStopsQueuedCommits() throws Exception {
		TrackingProvider provider = new TrackingProvider(true, false);
		List<CommitTableEntry> entries = entries(3);
		scheduler = createScheduler(provider);

		scheduler.startAnalysis(entries);
		assertTrue(provider.firstEntered.await(5, TimeUnit.SECONDS), "First commit was not started"); //$NON-NLS-1$
		try {
			scheduler.cancelAnalysis();
			await(() -> provider.interruptions.get() == 1
					&& entries.get(0).getStatus() == AnalysisStatus.PENDING);

			CommitAnalysisScheduler.Progress progress = scheduler.getProgress();
			assertEquals(1, provider.calls.get());
			assertEquals(0, provider.active.get());
			assertEquals(1, provider.interruptions.get());
			assertEquals(AnalysisStatus.PENDING, entries.get(0).getStatus());
			assertFalse(progress.running());
			assertTrue(progress.cancelled());
			assertEquals(0, progress.completed());
		} finally {
			// Ensures a failing cancellation assertion cannot strand the test worker.
			provider.releaseFirst.countDown();
		}
	}

	@Test
	public void replacementWaitsForCancelledWorkerToExit() throws Exception {
		NonCooperativeProvider provider = new NonCooperativeProvider();
		List<CommitTableEntry> firstRun = entries(1);
		List<CommitTableEntry> replacementRun = entries(1);
		scheduler = createScheduler(provider);

		scheduler.startAnalysis(firstRun);
		assertTrue(provider.firstEntered.await(5, TimeUnit.SECONDS), "First commit was not started"); //$NON-NLS-1$
		try {
			scheduler.startAnalysis(replacementRun);
			assertTrue(provider.interrupted.await(5, TimeUnit.SECONDS), "First worker was not interrupted"); //$NON-NLS-1$
			assertFalse(provider.secondEntered.await(250, TimeUnit.MILLISECONDS),
					"Replacement overlapped the still-running cancelled worker"); //$NON-NLS-1$

			provider.releaseFirst.countDown();
			assertTrue(provider.secondEntered.await(5, TimeUnit.SECONDS), "Replacement did not start"); //$NON-NLS-1$
			await(() -> !scheduler.isRunning());

			assertEquals(2, provider.calls.get());
			assertEquals(AnalysisStatus.PENDING, firstRun.get(0).getStatus());
			assertEquals(AnalysisStatus.NO_RULES, replacementRun.get(0).getStatus());
		} finally {
			provider.releaseFirst.countDown();
		}
	}

	@Test
	public void failedCommitCompletesQueueWithActionableEntryState() throws Exception {
		TrackingProvider provider = new TrackingProvider(false, true);
		CommitTableEntry entry = entries(1).get(0);
		scheduler = createScheduler(provider);

		scheduler.startAnalysis(List.of(entry));
		await(() -> !scheduler.isRunning());

		assertEquals(AnalysisStatus.FAILED, entry.getStatus());
		assertEquals("synthetic provider failure", entry.getFailureMessage()); //$NON-NLS-1$
		assertEquals(1, scheduler.getProgress().completed());
	}

	private CommitAnalysisScheduler createScheduler(GitHistoryProvider provider) {
		AtomicReference<CommitAnalysisScheduler> result = new AtomicReference<>();
		display.syncExec(() -> result.set(new CommitAnalysisScheduler(provider, REPOSITORY, tableViewer)));
		return result.get();
	}

	private static List<CommitTableEntry> entries(int count) {
		return java.util.stream.IntStream.range(0, count)
				.mapToObj(index -> new CommitTableEntry(new CommitInfo(
						"commit-" + index, //$NON-NLS-1$
						"c" + index, //$NON-NLS-1$
						"Commit " + index, //$NON-NLS-1$
						"Sandbox", //$NON-NLS-1$
						LocalDateTime.of(2026, 1, 1, 0, index),
						1)))
				.toList();
	}

	private static void await(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() >= deadline) {
				fail("Timed out waiting for scheduler state"); //$NON-NLS-1$
			}
			Thread.sleep(10);
		}
	}

	private static final class TrackingProvider implements GitHistoryProvider {
		private final boolean blockFirst;
		private final boolean fail;
		private final CountDownLatch firstEntered = new CountDownLatch(1);
		private final CountDownLatch releaseFirst = new CountDownLatch(1);
		private final AtomicInteger calls = new AtomicInteger();
		private final AtomicInteger active = new AtomicInteger();
		private final AtomicInteger maxActive = new AtomicInteger();
		private final AtomicInteger interruptions = new AtomicInteger();

		private TrackingProvider(boolean blockFirst, boolean fail) {
			this.blockFirst = blockFirst;
			this.fail = fail;
		}

		@Override
		public List<CommitInfo> getHistory(Path repositoryPath, int maxCommits) {
			return List.of();
		}

		@Override
		public List<FileDiff> getDiffs(Path repositoryPath, String commitId) {
			int call = calls.incrementAndGet();
			int nowActive = active.incrementAndGet();
			maxActive.accumulateAndGet(nowActive, Math::max);
			try {
				if (fail) {
					throw new IllegalStateException("synthetic provider failure"); //$NON-NLS-1$
				}
				if (blockFirst && call == 1) {
					firstEntered.countDown();
					try {
						releaseFirst.await();
					} catch (InterruptedException e) {
						interruptions.incrementAndGet();
						Thread.currentThread().interrupt();
					}
				}
				return List.of();
			} finally {
				active.decrementAndGet();
			}
		}

		@Override
		public String getFileContent(Path repositoryPath, String commitId, String filePath) {
			return null;
		}
	}

	private static final class NonCooperativeProvider implements GitHistoryProvider {
		private final CountDownLatch firstEntered = new CountDownLatch(1);
		private final CountDownLatch interrupted = new CountDownLatch(1);
		private final CountDownLatch releaseFirst = new CountDownLatch(1);
		private final CountDownLatch secondEntered = new CountDownLatch(1);
		private final AtomicInteger calls = new AtomicInteger();

		@Override
		public List<CommitInfo> getHistory(Path repositoryPath, int maxCommits) {
			return List.of();
		}

		@Override
		public List<FileDiff> getDiffs(Path repositoryPath, String commitId) {
			int call = calls.incrementAndGet();
			if (call == 1) {
				firstEntered.countDown();
				boolean released = false;
				while (!released) {
					try {
						releaseFirst.await();
						released = true;
					} catch (InterruptedException e) {
						// Deliberately ignore interruption to model a non-cooperative provider.
						interrupted.countDown();
					}
				}
			} else if (call == 2) {
				secondEntered.countDown();
			}
			return List.of();
		}

		@Override
		public String getFileContent(Path repositoryPath, String commitId, String filePath) {
			return null;
		}
	}
}
