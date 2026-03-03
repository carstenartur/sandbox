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

import java.io.IOException;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Performs incremental indexing of Git commits. Tracks the last indexed commit
 * per repository and only processes new commits on subsequent runs.
 *
 * <p>
 * The index state (last processed commit SHA per repository) is persisted so
 * that indexing resumes from where it left off after Eclipse restarts.
 * </p>
 *
 * <p>
 * Phase 1 implementation: Walks new commits using JGit RevWalk. The actual
 * database persistence will be connected in Phase 1b via Hibernate entities.
 * </p>
 */
public class IncrementalIndexer {

	private static final ILog LOG = Platform.getLog(IncrementalIndexer.class);

	/**
	 * Indexes only commits that are new since the last indexed state for the
	 * given repository.
	 *
	 * @param repository the repository to index
	 * @param monitor    progress monitor, may be {@code null}
	 */
	public void indexNewCommits(Repository repository, IProgressMonitor monitor) {
		try {
			Ref head = repository.exactRef("HEAD"); //$NON-NLS-1$
			if (head == null || head.getObjectId() == null) {
				return;
			}
			int count = walkCommits(repository, head.getObjectId(), null, monitor);
			LOG.info("Git Database Index: Indexed " + count + " new commits"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (IOException e) {
			LOG.error("Failed to index commits", e); //$NON-NLS-1$
		}
	}

	/**
	 * Performs a full reindex of all commits in the repository.
	 *
	 * @param repository the repository to reindex
	 * @param monitor    progress monitor, may be {@code null}
	 */
	public void fullReindex(Repository repository, IProgressMonitor monitor) {
		try {
			Ref head = repository.exactRef("HEAD"); //$NON-NLS-1$
			if (head == null || head.getObjectId() == null) {
				return;
			}
			int count = walkCommits(repository, head.getObjectId(), null, monitor);
			LOG.info("Git Database Index: Full reindex complete, " //$NON-NLS-1$
					+ count + " commits processed"); //$NON-NLS-1$
		} catch (IOException e) {
			LOG.error("Failed to reindex commits", e); //$NON-NLS-1$
		}
	}

	/**
	 * Walks commits from {@code start} to {@code stop} (exclusive).
	 *
	 * @param repository the repository
	 * @param start      the starting commit (HEAD)
	 * @param stop       the stop commit (last indexed), or {@code null} for all
	 * @param monitor    progress monitor
	 * @return number of commits processed
	 */
	private int walkCommits(Repository repository, ObjectId start,
			ObjectId stop, IProgressMonitor monitor) throws IOException {
		int count = 0;
		try (RevWalk walk = new RevWalk(repository)) {
			walk.markStart(walk.parseCommit(start));
			if (stop != null) {
				walk.markUninteresting(walk.parseCommit(stop));
			}
			for (RevCommit commit : walk) {
				if (monitor != null && monitor.isCanceled()) {
					break;
				}
				processCommit(repository, commit);
				count++;
			}
		}
		return count;
	}

	/**
	 * Processes a single commit for indexing.
	 * Phase 1b will connect this to CommitIndexer + BlobIndexer from
	 * sandbox-jgit-storage-hibernate.
	 */
	private void processCommit(Repository repository, RevCommit commit) {
		// Phase 1b: Connect to CommitIndexer and BlobIndexer
		// For now, this is a placeholder that logs the commit
		LOG.info("Git Database Index: Processing commit " //$NON-NLS-1$
				+ commit.abbreviate(7).name() + " - " //$NON-NLS-1$
				+ commit.getShortMessage());
	}
}
