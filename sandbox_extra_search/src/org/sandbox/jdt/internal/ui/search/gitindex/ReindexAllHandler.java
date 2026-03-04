/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.io.File;
import java.util.Collection;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jgit.lib.Repository;

/**
 * Command handler that performs a full reindex of all EGit-managed repositories.
 * Unlike {@link IndexRepositoryHandler}, this discards any previous index state
 * and reindexes from scratch.
 */
public class ReindexAllHandler extends AbstractHandler {

	private static final ILog LOG= Platform.getLog(ReindexAllHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Job job= new Job("Reindexing All Git Repositories") { //$NON-NLS-1$
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				RepositoryIndexService service= new RepositoryIndexService();
				Collection<File> gitDirs= EGitRepositoryTracker.getAllRepositoryDirs();
				monitor.beginTask("Reindexing all repositories", gitDirs.size()); //$NON-NLS-1$
				for (File gitDir : gitDirs) {
					if (monitor.isCanceled()) {
						return Status.CANCEL_STATUS;
					}
					Repository repo= EGitRepositoryTracker.openRepository(gitDir);
					if (repo != null) {
						try {
							service.reindexRepository(repo, monitor);
						} finally {
							repo.close();
						}
					}
					monitor.worked(1);
				}
				monitor.done();
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();
		return null;
	}
}
