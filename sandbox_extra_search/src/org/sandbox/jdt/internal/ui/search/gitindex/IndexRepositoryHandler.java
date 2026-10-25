/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.ui.handlers.HandlerUtil;

/** Explicitly imports one local repository through normal non-forced Git push. */
public class IndexRepositoryHandler extends AbstractHandler {
	@Override public Object execute(ExecutionEvent event) {
		var shell= HandlerUtil.getActiveShell(event);
		String repository= GitServerJobs.repository(event, shell);
		if (repository == null) return null;
		DirectoryDialog source= new DirectoryDialog(shell);
		source.setMessage("Choose the local Git working directory or bare repository to import."); //$NON-NLS-1$
		String directory= source.open();
		if (directory == null) return null;
		try {
			String destination= GitSearchPreferences.gitUrl() + "/" + repository; //$NON-NLS-1$
			if (!MessageDialog.openConfirm(shell, "Import Repository", //$NON-NLS-1$
					"Upload committed branches and tags from:\n" + directory + "\n\nto:\n" + destination //$NON-NLS-1$ //$NON-NLS-2$
					+ "\n\nExisting refs are never force-updated or deleted. Uncommitted edits are not uploaded.")) return null; //$NON-NLS-1$
			GitServerJobs.schedule(shell, "Import Repository", monitor -> { //$NON-NLS-1$
				new RepositoryIndexService().importRepository(new File(directory), destination, monitor);
				return "Git history imported into " + repository + ". Check the server logs if search indexing failed."; //$NON-NLS-1$ //$NON-NLS-2$
			});
		} catch (IllegalArgumentException invalid) {
			MessageDialog.openError(shell, "Import Repository", invalid.getMessage()); //$NON-NLS-1$
		}
		return null;
	}
}
