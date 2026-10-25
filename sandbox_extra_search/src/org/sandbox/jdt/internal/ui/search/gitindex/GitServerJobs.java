/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/** Dialogs and background execution for explicit server mutations. */
final class GitServerJobs {
	private GitServerJobs() { }

	static String repository(ExecutionEvent event, Shell shell) {
		var part= HandlerUtil.getActivePart(event);
		String initial= part instanceof GitResultsView view ? view.repositoryName() : ""; //$NON-NLS-1$
		var dialog= new InputDialog(shell, "Server repository", "Repository name on the configured server:", initial, //$NON-NLS-1$ //$NON-NLS-2$
				value -> value.matches("[A-Za-z0-9][A-Za-z0-9._-]*") ? null : "Use letters, numbers, dots, underscores or hyphens."); //$NON-NLS-1$ //$NON-NLS-2$
		if (dialog.open() != Window.OK) return null;
		if (part instanceof GitResultsView view) view.repository.setText(dialog.getValue());
		return dialog.getValue();
	}

	@FunctionalInterface interface Operation { String run(IProgressMonitor monitor) throws Exception; }

	static void schedule(Shell shell, String title, Operation operation) {
		var display= shell.getDisplay();
		Job job= Job.create(title, monitor -> {
			try {
				if (monitor.isCanceled()) return Status.CANCEL_STATUS;
				String message= operation.run(monitor);
				if (monitor.isCanceled()) return Status.CANCEL_STATUS;
				display.asyncExec(() -> { if (!shell.isDisposed()) MessageDialog.openInformation(shell, title, message); });
				return Status.OK_STATUS;
			} catch (OperationCanceledException cancelled) {
				return Status.CANCEL_STATUS;
			} catch (Exception failure) {
				if (monitor.isCanceled()) return Status.CANCEL_STATUS;
				return Status.error(title + ": " + failure.getMessage(), failure); //$NON-NLS-1$
			} finally { monitor.done(); }
		});
		job.setUser(true);
		job.schedule();
	}
}
