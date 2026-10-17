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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.jupiter.api.Test;

/** Cleanup must not start while imported-project build or refresh jobs can close Java model buffers. */
class CodeCleanupApplicationWrapperImportSettleTest {

	@Test
	void pendingBuildAndRefreshJobsFinishBeforeCleanupStarts() throws Exception {
		AtomicBoolean built= new AtomicBoolean();
		AtomicBoolean refreshed= new AtomicBoolean();
		Job build= familyJob(ResourcesPlugin.FAMILY_AUTO_BUILD, built);
		Job refresh= familyJob(ResourcesPlugin.FAMILY_AUTO_REFRESH, refreshed);
		build.schedule(200);
		refresh.schedule(200);

		CodeCleanupApplicationWrapper.awaitWorkspaceSettled(new NullProgressMonitor());

		assertTrue(built.get());
		assertTrue(refreshed.get());
		assertEquals(Job.NONE, build.getState());
		assertEquals(Job.NONE, refresh.getState());
	}

	private static Job familyJob(Object family, AtomicBoolean completed) {
		return new Job("settle-" + family) { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return Status.CANCEL_STATUS;
				}
				completed.set(true);
				return Status.OK_STATUS;
			}

			@Override
			public boolean belongsTo(Object candidate) {
				return family == candidate;
			}
		};
	}
}
