/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.jupiter.api.Test;

/** Cleanup must wait for import work without waiting for future periodic refreshes. */
class CodeCleanupApplicationWrapperImportSettleTest {

	@Test
	void delayedBuildAndRefreshJobsFinishBeforeCleanupStarts() throws Exception {
		List<AtomicBoolean> completed= new ArrayList<>();
		List<Job> jobs= new ArrayList<>();
		for (Object family : families()) {
			AtomicBoolean finished= new AtomicBoolean();
			completed.add(finished);
			jobs.add(familyJob(family, () -> finished.set(true)));
		}
		try {
			jobs.forEach(job -> job.schedule(1_000));
			jobs.forEach(job -> assertEquals(Job.SLEEPING, job.getState()));

			CodeCleanupApplicationWrapper.awaitWorkspaceSettled(new NullProgressMonitor());

			completed.forEach(finished -> assertTrue(finished.get()));
			jobs.forEach(job -> assertEquals(Job.NONE, job.getState()));
		} finally {
			for (Job job : jobs) stop(job);
		}
	}

	@Test
	void aBuildsDelayedRefreshFinishesBeforeCleanupStarts() throws Exception {
		AtomicBoolean refreshed= new AtomicBoolean();
		Job refresh= familyJob(ResourcesPlugin.FAMILY_AUTO_REFRESH, () -> refreshed.set(true));
		Job build= familyJob(ResourcesPlugin.FAMILY_MANUAL_BUILD, () -> refresh.schedule(1_000));
		try {
			build.schedule(200);

			CodeCleanupApplicationWrapper.awaitWorkspaceSettled(new NullProgressMonitor());

			assertTrue(refreshed.get());
			assertEquals(Job.NONE, refresh.getState());
		} finally {
			stop(build);
			stop(refresh);
		}
	}

	@Test
	void aRecurringRefreshOnlyWaitsForItsPendingRun() throws Exception {
		AtomicBoolean refreshed= new AtomicBoolean();
		Job refresh= new Job("recurring-import-refresh") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				refreshed.set(true);
				schedule(60_000);
				return Status.OK_STATUS;
			}

			@Override
			public boolean belongsTo(Object family) {
				return family == ResourcesPlugin.FAMILY_AUTO_REFRESH;
			}
		};
		try {
			refresh.schedule(200);

			assertTimeoutPreemptively(Duration.ofSeconds(5),
					() -> CodeCleanupApplicationWrapper.awaitWorkspaceSettled(new NullProgressMonitor()));

			assertTrue(refreshed.get());
			assertEquals(Job.SLEEPING, refresh.getState());
		} finally {
			stop(refresh);
		}
	}

	@Test
	void canceledImportWaitStopsWithoutCancelingTheWorkspaceJob() throws Exception {
		Job build= familyJob(ResourcesPlugin.FAMILY_AUTO_BUILD, () -> { });
		NullProgressMonitor monitor= new NullProgressMonitor();
		monitor.setCanceled(true);
		try {
			build.schedule(60_000);

			assertThrows(OperationCanceledException.class,
					() -> CodeCleanupApplicationWrapper.awaitWorkspaceSettled(monitor));

			assertEquals(Job.SLEEPING, build.getState());
		} finally {
			stop(build);
		}
	}

	@Test
	void interruptedImportWaitReportsFailureAndRestoresTheInterruptFlag() throws Exception {
		CountDownLatch started= new CountDownLatch(1);
		CountDownLatch release= new CountDownLatch(1);
		Job build= familyJob(ResourcesPlugin.FAMILY_AUTO_BUILD, () -> {
			started.countDown();
			try {
				assertTrue(release.await(10, TimeUnit.SECONDS));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError(e);
			}
		});
		try {
			build.schedule();
			assertTrue(started.await(10, TimeUnit.SECONDS));
			Thread.currentThread().interrupt();

			IOException failure= assertThrows(IOException.class,
					() -> CodeCleanupApplicationWrapper.awaitWorkspaceSettled(new NullProgressMonitor()));

			assertInstanceOf(InterruptedException.class, failure.getCause());
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
			release.countDown();
			stop(build);
		}
	}

	private static List<Object> families() {
		return List.of(ResourcesPlugin.FAMILY_AUTO_REFRESH, ResourcesPlugin.FAMILY_MANUAL_REFRESH,
				ResourcesPlugin.FAMILY_AUTO_BUILD, ResourcesPlugin.FAMILY_MANUAL_BUILD);
	}

	private static Job familyJob(Object family, Runnable action) {
		return new Job("import-settle-regression") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				action.run();
				return Status.OK_STATUS;
			}

			@Override
			public boolean belongsTo(Object candidate) {
				return family == candidate;
			}
		};
	}

	private static void stop(Job job) throws InterruptedException {
		job.cancel();
		job.join();
	}
}
