/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.io.IOException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;

/** Imports committed branches and tags; successful Smart HTTP pushes index on the server. */
public class IncrementalIndexer {
	private final String destination;

	public IncrementalIndexer(String destination) { this.destination= destination; }

	public void indexNewCommits(Repository repository, IProgressMonitor monitor) throws IOException, GitAPIException {
		if (monitor.isCanceled()) throw new OperationCanceledException();
		if (repository.getRefDatabase().getRefsByPrefix("refs/heads/", "refs/tags/").isEmpty()) //$NON-NLS-1$ //$NON-NLS-2$
			throw new IOException("No committed branches or tags to import."); //$NON-NLS-1$
		ProgressMonitor progress= new ProgressMonitor() {
			@Override public void start(int tasks) { monitor.beginTask("Importing committed Git history", IProgressMonitor.UNKNOWN); } //$NON-NLS-1$
			@Override public void beginTask(String title, int total) { monitor.subTask(title); }
			@Override public void update(int completed) { monitor.worked(completed); }
			@Override public void endTask() { }
			@Override public boolean isCancelled() { return monitor.isCanceled(); }
			@Override public void showDuration(boolean enabled) { }
		};
		// The caller owns the repository. Git.wrap must not close its borrowed handle.
		var results= Git.wrap(repository).push().setRemote(destination).setTimeout(60)
				.setRefSpecs(new RefSpec("refs/heads/*:refs/heads/*"), new RefSpec("refs/tags/*:refs/tags/*")) //$NON-NLS-1$ //$NON-NLS-2$
				.setProgressMonitor(progress).call();
		boolean advertised= false;
		for (var result : results) {
			for (var update : result.getRemoteUpdates()) {
				advertised= true;
				if (update.getStatus() != RemoteRefUpdate.Status.OK && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE)
					throw new IOException("Import rejected for " + update.getRemoteName() + ": " + update.getStatus()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		if (monitor.isCanceled()) throw new OperationCanceledException();
		if (!advertised) throw new IOException("No branches or tags were imported."); //$NON-NLS-1$
	}
}
