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
package org.sandbox.jgit.db.service;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jgit.lib.Repository;

/**
 * Orchestrates the indexing of a Git repository. Coordinates the
 * {@link IncrementalIndexer} with the commit and blob indexing services from
 * {@code sandbox-jgit-storage-hibernate}.
 *
 * <p>
 * Phase 1 implementation provides the service interface and scheduling
 * infrastructure. The actual Hibernate-based indexing will be connected in Phase
 * 1b when the storage module is bundled as an OSGi dependency.
 * </p>
 */
public class RepositoryIndexService {

	private static final ILog LOG = Platform.getLog(RepositoryIndexService.class);

	private final IncrementalIndexer incrementalIndexer;

	/**
	 * Creates a new repository index service.
	 */
	public RepositoryIndexService() {
		this.incrementalIndexer = new IncrementalIndexer();
	}

	/**
	 * Indexes a single repository, processing only new commits since the last
	 * indexed state.
	 *
	 * @param repository the JGit repository to index
	 * @param monitor    progress monitor, may be {@code null}
	 */
	public void indexRepository(Repository repository, IProgressMonitor monitor) {
		String repoName = repository.getDirectory().getName();
		LOG.info("Git Database Index: Starting index of " + repoName); //$NON-NLS-1$
		incrementalIndexer.indexNewCommits(repository, monitor);
		LOG.info("Git Database Index: Finished index of " + repoName); //$NON-NLS-1$
	}

	/**
	 * Performs a full reindex of a repository, discarding any previous index
	 * state.
	 *
	 * @param repository the JGit repository to reindex
	 * @param monitor    progress monitor, may be {@code null}
	 */
	public void reindexRepository(Repository repository, IProgressMonitor monitor) {
		String repoName = repository.getDirectory().getName();
		LOG.info("Git Database Index: Starting full reindex of " + repoName); //$NON-NLS-1$
		incrementalIndexer.fullReindex(repository, monitor);
		LOG.info("Git Database Index: Finished full reindex of " + repoName); //$NON-NLS-1$
	}
}
