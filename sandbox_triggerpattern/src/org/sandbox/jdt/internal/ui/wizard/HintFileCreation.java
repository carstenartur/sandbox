/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite;

/** Creates new rule evidence under one workspace rule; never overwrites a resource. */
final class HintFileCreation {

	private HintFileCreation() {
	}

	static void create(IContainer container, String fileName, String content, RuleExampleSuite suite,
			IProgressMonitor monitor) throws CoreException {
		String examples = suite == null ? null : suite.toJson();
		IFile hint = container.getFile(new Path(fileName));
		IFile sidecar = container.getFile(new Path(fileName + ".examples.json")); //$NON-NLS-1$
		container.getWorkspace().run(progress -> {
			progress.beginTask("Creating " + fileName, examples == null ? 1 : 2); //$NON-NLS-1$
			boolean hintCreated = false;
			boolean sidecarCreated = false;
			try {
				checkCanceled(progress);
				requireAbsent(hint);
				if (examples != null) {
					requireAbsent(sidecar);
				}
				// A separate child monitor keeps resource-internal progress from completing
				// this transaction's first step before create() has returned successfully.
				hint.create(bytes(content), false, new NullProgressMonitor());
				hintCreated = true;
				progress.worked(1);
				checkCanceled(progress);
				if (examples != null) {
					sidecar.create(bytes(examples), false, new NullProgressMonitor());
					sidecarCreated = true;
					progress.worked(1);
					checkCanceled(progress);
				}
			} catch (CoreException | RuntimeException failure) {
				// Existing resources never set these flags. Cleanup must also work after
				// cancellation, so it deliberately receives a fresh monitor.
				if (sidecarCreated) {
					rollback(sidecar, failure);
				}
				if (hintCreated) {
					rollback(hint, failure);
				}
				throw failure;
			} finally {
				progress.done();
			}
		}, container, IWorkspace.AVOID_UPDATE, monitor);
	}

	private static ByteArrayInputStream bytes(String contents) {
		return new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
	}

	private static void requireAbsent(IFile file) throws CoreException {
		if (file.exists()) {
			throw new CoreException(new Status(IStatus.ERROR, "sandbox_triggerpattern", //$NON-NLS-1$
					"File already exists: " + file.getFullPath())); //$NON-NLS-1$
		}
	}

	private static void checkCanceled(IProgressMonitor monitor) {
		if (monitor.isCanceled() || Thread.currentThread().isInterrupted()) {
			throw new OperationCanceledException();
		}
	}

	private static void rollback(IFile file, Exception failure) {
		try {
			file.delete(true, new NullProgressMonitor());
		} catch (CoreException cleanupFailure) {
			failure.addSuppressed(cleanupFailure);
		}
	}
}
