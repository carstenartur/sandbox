/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.eclipse.jgit.storage.hibernate.service;

import java.io.IOException;
import java.time.Instant;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.hibernate.entity.GitCommitIndex;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Populates {@link GitCommitIndex} entities from Git commit data.
 * <p>
 * This service walks commits and persists {@link GitCommitIndex} entities
 * with commit metadata including author, message, timestamp, parent IDs,
 * and changed file paths. Hibernate Search automatically indexes these
 * entities for full-text search when they are persisted.
 */
public class CommitIndexer {

	private final SessionFactory sessionFactory;

	private final String repositoryName;

	/**
	 * Create a new commit indexer.
	 *
	 * @param sessionFactory
	 *            the Hibernate session factory
	 * @param repositoryName
	 *            the repository name for partitioning
	 */
	public CommitIndexer(SessionFactory sessionFactory,
			String repositoryName) {
		this.sessionFactory = sessionFactory;
		this.repositoryName = repositoryName;
	}

	/**
	 * Index a single commit by its ObjectId.
	 *
	 * @param repo
	 *            the repository to read the commit from
	 * @param commitId
	 *            the commit object ID
	 * @throws IOException
	 *             if an error occurs reading the commit
	 */
	public void indexCommit(Repository repo, ObjectId commitId)
			throws IOException {
		try (RevWalk rw = new RevWalk(repo)) {
			RevCommit commit = rw.parseCommit(commitId);
			indexRevCommit(repo, commit);
		}
	}

	/**
	 * Index a parsed RevCommit.
	 *
	 * @param repo
	 *            the repository
	 * @param commit
	 *            the parsed commit
	 * @throws IOException
	 *             if an error occurs reading tree data
	 */
	public void indexRevCommit(Repository repo, RevCommit commit)
			throws IOException {
		GitCommitIndex idx = new GitCommitIndex();
		idx.setRepositoryName(repositoryName);
		idx.setObjectId(commit.getId().name());
		idx.setCommitMessage(commit.getFullMessage());
		idx.setAuthorName(commit.getAuthorIdent().getName());
		idx.setAuthorEmail(commit.getAuthorIdent().getEmailAddress());
		idx.setCommitTime(
				Instant.ofEpochSecond(commit.getCommitTime()));

		// Serialize parent IDs
		StringBuilder parentIds = new StringBuilder();
		for (int i = 0; i < commit.getParentCount(); i++) {
			if (i > 0) {
				parentIds.append(',');
			}
			parentIds.append(commit.getParent(i).getId().name());
		}
		idx.setParentIds(parentIds.toString());

		// Collect changed paths from the tree
		String changedPaths = collectPaths(repo, commit);
		idx.setChangedPaths(changedPaths);

		try (Session session = sessionFactory.openSession()) {
			session.beginTransaction();
			session.persist(idx);
			session.getTransaction().commit();
		}
	}

	private String collectPaths(Repository repo, RevCommit commit)
			throws IOException {
		StringBuilder paths = new StringBuilder();
		try (ObjectReader reader = repo.newObjectReader();
				TreeWalk tw = new TreeWalk(reader)) {
			tw.addTree(commit.getTree());
			tw.setRecursive(true);
			while (tw.next()) {
				if (paths.length() > 0) {
					paths.append('\n');
				}
				paths.append(tw.getPathString());
			}
		}
		return paths.toString();
	}

	/**
	 * Index all commits reachable from a given ref.
	 *
	 * @param repo
	 *            the repository
	 * @param tipId
	 *            the tip commit ObjectId to start walking from
	 * @param maxCount
	 *            maximum number of commits to index, or -1 for unlimited
	 * @return the number of commits indexed
	 * @throws IOException
	 *             if an error occurs
	 */
	public int indexCommitsFrom(Repository repo, ObjectId tipId,
			int maxCount) throws IOException {
		int count = 0;
		try (RevWalk rw = new RevWalk(repo)) {
			rw.markStart(rw.parseCommit(tipId));
			for (RevCommit commit : rw) {
				if (maxCount >= 0 && count >= maxCount) {
					break;
				}
				if (!isAlreadyIndexed(commit.getId().name())) {
					indexRevCommit(repo, commit);
					count++;
				}
			}
		}
		return count;
	}

	private boolean isAlreadyIndexed(String objectId) {
		try (Session session = sessionFactory.openSession()) {
			Long count = session.createQuery(
					"SELECT COUNT(c) FROM GitCommitIndex c WHERE c.repositoryName = :repo AND c.objectId = :oid", //$NON-NLS-1$
					Long.class)
					.setParameter("repo", repositoryName) //$NON-NLS-1$
					.setParameter("oid", objectId) //$NON-NLS-1$
					.uniqueResult();
			return count != null && count > 0;
		}
	}
}
