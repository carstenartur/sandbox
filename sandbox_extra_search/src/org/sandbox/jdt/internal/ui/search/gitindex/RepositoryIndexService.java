/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.io.File;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jgit.api.Git;

/** Explicit local import boundary. It never scans or uploads other workspace repositories. */
public class RepositoryIndexService {
	public void importRepository(File directory, String destination, IProgressMonitor monitor) throws Exception {
		try (Git git= Git.open(directory)) {
			new IncrementalIndexer(destination).indexNewCommits(git.getRepository(), monitor);
		}
	}
}
