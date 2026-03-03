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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.hibernate.entity.GitCommitIndex;
import org.eclipse.jgit.storage.hibernate.entity.GitObjectEntity;
import org.eclipse.jgit.storage.hibernate.entity.GitRefEntity;
import org.eclipse.jgit.storage.hibernate.entity.GitReflogEntity;
import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;

/**
 * Extended query service that leverages the relational database and Hibernate
 * Search for operations impossible with filesystem-based Git storage.
 * <p>
 * Full-text search across commit messages and changed paths is powered by
 * Hibernate Search with a Lucene backend. Other queries (statistics,
 * time-range, reflog, pack analytics) use standard HQL.
 */
public class GitDatabaseQueryService {

	private final SessionFactory sessionFactory;

	/**
	 * Create a new query service.
	 *
	 * @param sessionFactory
	 *            the Hibernate session factory
	 */
	public GitDatabaseQueryService(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * Search commit messages using Hibernate Search full-text query.
	 *
	 * @param repoName
	 *            the repository name
	 * @param query
	 *            the search query
	 * @return matching commit index entries
	 */
	public List<GitCommitIndex> searchCommitMessages(String repoName,
			String query) {
		return searchCommitMessages(repoName, query, 0, 20);
	}

	/**
	 * Search commit messages with pagination.
	 *
	 * @param repoName
	 *            the repository name
	 * @param query
	 *            the search query
	 * @param offset
	 *            the result offset
	 * @param limit
	 *            the maximum number of results
	 * @return matching commit index entries
	 */
	public List<GitCommitIndex> searchCommitMessages(String repoName,
			String query, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(GitCommitIndex.class)
					.where(f -> f.bool()
							.must(f.match().field("repositoryName") //$NON-NLS-1$
									.matching(repoName))
							.must(f.match().field("commitMessage") //$NON-NLS-1$
									.matching(query)))
					.fetch(offset, limit).hits();
		}
	}

	/**
	 * Find repositories containing a specific object.
	 *
	 * @param objectId
	 *            the SHA-1 hex string
	 * @return list of repository names containing this object
	 */
	public List<String> findRepositoriesContainingObject(String objectId) {
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"SELECT DISTINCT o.repositoryName FROM GitObjectEntity o WHERE o.objectId = :oid", //$NON-NLS-1$
					String.class).setParameter("oid", objectId) //$NON-NLS-1$
					.getResultList();
		}
	}

	/**
	 * Get commits between two timestamps.
	 *
	 * @param repoName
	 *            the repository name
	 * @param start
	 *            start of the time range (inclusive)
	 * @param end
	 *            end of the time range (inclusive)
	 * @return matching commit index entries
	 */
	public List<GitCommitIndex> getCommitsBetween(String repoName,
			Instant start, Instant end) {
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"FROM GitCommitIndex c WHERE c.repositoryName = :repo AND c.commitTime BETWEEN :start AND :end", //$NON-NLS-1$
					GitCommitIndex.class).setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("start", start).setParameter("end", end) //$NON-NLS-1$ //$NON-NLS-2$
					.getResultList();
		}
	}

	/**
	 * Get refs modified since a given timestamp.
	 *
	 * @param repoName
	 *            the repository name
	 * @param since
	 *            the cutoff timestamp
	 * @return refs modified after the given timestamp
	 */
	public List<GitRefEntity> getRefsModifiedSince(String repoName,
			Instant since) {
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"FROM GitRefEntity r WHERE r.repositoryName = :repo AND r.updatedAt >= :since", //$NON-NLS-1$
					GitRefEntity.class).setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("since", since).getResultList(); //$NON-NLS-1$
		}
	}

	/**
	 * Count objects by type in a repository.
	 *
	 * @param repoName
	 *            the repository name
	 * @param objectType
	 *            the object type constant
	 * @return count of objects of the given type
	 */
	public long countObjectsByType(String repoName, int objectType) {
		try (Session session = sessionFactory.openSession()) {
			Long count = session.createQuery(
					"SELECT COUNT(o) FROM GitObjectEntity o WHERE o.repositoryName = :repo AND o.objectType = :type", //$NON-NLS-1$
					Long.class).setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("type", objectType).uniqueResult(); //$NON-NLS-1$
			return count != null ? count : 0;
		}
	}

	/**
	 * Get all objects in a repository.
	 *
	 * @param repoName
	 *            the repository name
	 * @return all object entities
	 */
	public List<GitObjectEntity> getAllObjects(String repoName) {
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"FROM GitObjectEntity o WHERE o.repositoryName = :repo", //$NON-NLS-1$
					GitObjectEntity.class).setParameter("repo", repoName) //$NON-NLS-1$
					.getResultList();
		}
	}

	/**
	 * Search commits by changed path using Hibernate Search full-text query.
	 *
	 * @param repoName
	 *            the repository name
	 * @param pathPattern
	 *            the path pattern to search for
	 * @return matching commit index entries
	 */
	public List<GitCommitIndex> searchByChangedPath(String repoName,
			String pathPattern) {
		return searchByChangedPath(repoName, pathPattern, 0, 20);
	}

	/**
	 * Search commits by changed path with pagination.
	 *
	 * @param repoName
	 *            the repository name
	 * @param pathPattern
	 *            the path pattern to search for
	 * @param offset
	 *            the result offset
	 * @param limit
	 *            the maximum number of results
	 * @return matching commit index entries
	 */
	public List<GitCommitIndex> searchByChangedPath(String repoName,
			String pathPattern, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(GitCommitIndex.class)
					.where(f -> f.bool()
							.must(f.match().field("repositoryName") //$NON-NLS-1$
									.matching(repoName))
							.must(f.match().field("changedPaths") //$NON-NLS-1$
									.matching(pathPattern)))
					.fetch(offset, limit).hits();
		}
	}

	/**
	 * Get author statistics for a repository. Returns a list of
	 * [authorName, authorEmail, commitCount] arrays.
	 *
	 * @param repoName
	 *            the repository name
	 * @return list of author statistics as Object arrays
	 */
	public List<AuthorStats> getAuthorStatistics(String repoName) {
		try (Session session = sessionFactory.openSession()) {
			List<Object[]> rows = session.createQuery(
					"SELECT c.authorName, c.authorEmail, COUNT(c) FROM GitCommitIndex c WHERE c.repositoryName = :repo GROUP BY c.authorName, c.authorEmail ORDER BY COUNT(c) DESC", //$NON-NLS-1$
					Object[].class).setParameter("repo", repoName) //$NON-NLS-1$
					.getResultList();
			List<AuthorStats> result = new ArrayList<>(rows.size());
			for (Object[] row : rows) {
				result.add(new AuthorStats((String) row[0], (String) row[1],
						(Long) row[2]));
			}
			return result;
		}
	}

	/**
	 * Get reflog entries for a specific ref.
	 *
	 * @param repoName
	 *            the repository name
	 * @param refName
	 *            the reference name
	 * @param max
	 *            maximum number of entries to return
	 * @return reflog entities in reverse chronological order
	 */
	public List<GitReflogEntity> getReflogEntries(String repoName,
			String refName, int max) {
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"FROM GitReflogEntity r WHERE r.repositoryName = :repo AND r.refName = :ref ORDER BY r.id DESC", //$NON-NLS-1$
					GitReflogEntity.class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("ref", refName) //$NON-NLS-1$
					.setMaxResults(max).getResultList();
		}
	}

	/**
	 * Delete reflog entries older than a given timestamp.
	 *
	 * @param repoName
	 *            the repository name
	 * @param before
	 *            the cutoff timestamp
	 * @return number of entries deleted
	 */
	public int purgeReflogEntries(String repoName, Instant before) {
		try (Session session = sessionFactory.openSession()) {
			session.beginTransaction();
			int deleted = session.createMutationQuery(
					"DELETE FROM GitReflogEntity r WHERE r.repositoryName = :repo AND r.when < :before") //$NON-NLS-1$
					.setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("before", before) //$NON-NLS-1$
					.executeUpdate();
			session.getTransaction().commit();
			return deleted;
		}
	}

	/**
	 * Find pack names that are not referenced by any current pack description.
	 * <p>
	 * This can identify orphaned pack data left after failed operations.
	 *
	 * @param repoName
	 *            the repository name
	 * @return list of orphaned pack names
	 */
	public List<String> findOrphanedPacks(String repoName) {
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"SELECT DISTINCT p.packName FROM GitPackEntity p " //$NON-NLS-1$
							+ "WHERE p.repositoryName = :repo " //$NON-NLS-1$
							+ "AND NOT EXISTS (" //$NON-NLS-1$
							+ "SELECT 1 FROM GitPackEntity p2 " //$NON-NLS-1$
							+ "WHERE p2.repositoryName = :repo " //$NON-NLS-1$
							+ "AND p2.packName = p.packName " //$NON-NLS-1$
							+ "AND p2.packExtension = 'pack')", //$NON-NLS-1$
					String.class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.getResultList();
		}
	}

	/**
	 * Get the total count of pack files in a repository.
	 *
	 * @param repoName
	 *            the repository name
	 * @return the number of distinct pack files
	 */
	public long countPacks(String repoName) {
		try (Session session = sessionFactory.openSession()) {
			Long count = session.createQuery(
					"SELECT COUNT(DISTINCT p.packName) FROM GitPackEntity p WHERE p.repositoryName = :repo", //$NON-NLS-1$
					Long.class).setParameter("repo", repoName) //$NON-NLS-1$
					.uniqueResult();
			return count != null ? count : 0;
		}
	}

	/**
	 * Get the total storage size (in bytes) of all packs in a repository.
	 *
	 * @param repoName
	 *            the repository name
	 * @return the total pack data size in bytes
	 */
	public long getTotalPackSize(String repoName) {
		try (Session session = sessionFactory.openSession()) {
			Long size = session.createQuery(
					"SELECT COALESCE(SUM(p.fileSize), 0) FROM GitPackEntity p WHERE p.repositoryName = :repo", //$NON-NLS-1$
					Long.class).setParameter("repo", repoName) //$NON-NLS-1$
					.uniqueResult();
			return size != null ? size : 0;
		}
	}

	private static final int MAX_BLOB_SIZE_FOR_SEARCH = 1024 * 1024; // 1 MB

	/**
	 * Search commits whose tree blobs contain the given content string.
	 * <p>
	 * For each indexed commit in the repository, this method uses JGit's
	 * {@code RevWalk} and {@code TreeWalk} to read every blob in the commit
	 * tree and checks whether its UTF-8 text contains {@code contentQuery}.
	 * Blobs larger than 1 MB are skipped.
	 *
	 * @param repoName
	 *            the repository name
	 * @param contentQuery
	 *            the string to search for inside blob content
	 * @param repo
	 *            the {@link Repository} object used to read objects
	 * @return list of {@link GitCommitIndex} entries whose committed files
	 *         contain the search string
	 * @throws IOException
	 *             if an error occurs reading objects from the repository
	 */
	public List<GitCommitIndex> searchBlobContent(String repoName,
			String contentQuery, Repository repo) throws IOException {
		List<GitCommitIndex> allCommits;
		try (Session session = sessionFactory.openSession()) {
			allCommits = session.createQuery(
					"FROM GitCommitIndex c WHERE c.repositoryName = :repo", //$NON-NLS-1$
					GitCommitIndex.class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.getResultList();
		}
		List<GitCommitIndex> matches = new ArrayList<>();
		try (RevWalk rw = new RevWalk(repo)) {
			for (GitCommitIndex idx : allCommits) {
				RevCommit commit = rw
						.parseCommit(repo.resolve(idx.getObjectId()));
				try (ObjectReader reader = repo.newObjectReader();
						TreeWalk tw = new TreeWalk(reader)) {
					tw.addTree(commit.getTree());
					tw.setRecursive(true);
					while (tw.next()) {
						ObjectLoader loader = reader
								.open(tw.getObjectId(0));
						if (loader.getSize() > MAX_BLOB_SIZE_FOR_SEARCH) {
							continue;
						}
						byte[] bytes = loader.getBytes();
						String text = new String(bytes,
								StandardCharsets.UTF_8);
						if (text.contains(contentQuery)) {
							matches.add(idx);
							break;
						}
					}
				}
			}
		}
		return matches;
	}

	/**
	 * Search Java blob indices by type name (declared types or FQNs).
	 *
	 * @param repoName
	 *            the repository name
	 * @param query
	 *            the type name query
	 * @return matching Java blob index entries
	 */
	public List<JavaBlobIndex> searchByType(String repoName, String query) {
		return searchByType(repoName, query, 0, 20);
	}

	/**
	 * Search Java blob indices by type name with pagination and boosting.
	 *
	 * @param repoName
	 *            the repository name
	 * @param query
	 *            the type name query
	 * @param offset
	 *            the result offset
	 * @param limit
	 *            the maximum number of results
	 * @return matching Java blob index entries
	 */
	public List<JavaBlobIndex> searchByType(String repoName, String query,
			int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match().field("repositoryName") //$NON-NLS-1$
									.matching(repoName))
							.must(f.match()
									.field("fullyQualifiedNames") //$NON-NLS-1$
									.boost(2.0f)
									.field("declaredTypes") //$NON-NLS-1$
									.boost(1.5f)
									.matching(query)))
					.fetch(offset, limit).hits();
		}
	}

	/**
	 * Search Java blob indices by symbol name (methods and fields).
	 *
	 * @param repoName
	 *            the repository name
	 * @param query
	 *            the symbol name query
	 * @return matching Java blob index entries
	 */
	public List<JavaBlobIndex> searchBySymbol(String repoName, String query) {
		return searchBySymbol(repoName, query, 0, 20);
	}

	/**
	 * Search Java blob indices by symbol name with pagination and boosting.
	 *
	 * @param repoName
	 *            the repository name
	 * @param query
	 *            the symbol name query
	 * @param offset
	 *            the result offset
	 * @param limit
	 *            the maximum number of results
	 * @return matching Java blob index entries
	 */
	public List<JavaBlobIndex> searchBySymbol(String repoName, String query,
			int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match().field("repositoryName") //$NON-NLS-1$
									.matching(repoName))
							.must(f.match()
									.field("declaredMethods") //$NON-NLS-1$
									.boost(1.5f)
									.field("declaredFields") //$NON-NLS-1$
									.matching(query)))
					.fetch(offset, limit).hits();
		}
	}

	/**
	 * Search Java blob indices by type hierarchy (extends/implements).
	 *
	 * @param repoName
	 *            the repository name
	 * @param typeName
	 *            the type name to find subtypes of
	 * @return matching Java blob index entries
	 */
	public List<JavaBlobIndex> searchByHierarchy(String repoName,
			String typeName) {
		return searchByHierarchy(repoName, typeName, 0, 20);
	}

	/**
	 * Search Java blob indices by type hierarchy with pagination.
	 *
	 * @param repoName
	 *            the repository name
	 * @param typeName
	 *            the type name to find subtypes of
	 * @param offset
	 *            the result offset
	 * @param limit
	 *            the maximum number of results
	 * @return matching Java blob index entries
	 */
	public List<JavaBlobIndex> searchByHierarchy(String repoName,
			String typeName, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match().field("repositoryName") //$NON-NLS-1$
									.matching(repoName))
							.must(f.match()
									.field("extendsTypes") //$NON-NLS-1$
									.field("implementsTypes") //$NON-NLS-1$
									.matching(typeName)))
					.fetch(offset, limit).hits();
		}
	}

	/**
	 * Full-text search across Java source snippets.
	 *
	 * @param repoName
	 *            the repository name
	 * @param query
	 *            the search query
	 * @return matching Java blob index entries
	 */
	public List<JavaBlobIndex> searchSourceContent(String repoName,
			String query) {
		return searchSourceContent(repoName, query, 0, 20);
	}

	/**
	 * Full-text search across Java source snippets with pagination.
	 *
	 * @param repoName
	 *            the repository name
	 * @param query
	 *            the search query
	 * @param offset
	 *            the result offset
	 * @param limit
	 *            the maximum number of results
	 * @return matching Java blob index entries
	 */
	public List<JavaBlobIndex> searchSourceContent(String repoName,
			String query, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match().field("repositoryName") //$NON-NLS-1$
									.matching(repoName))
							.must(f.match().field("sourceSnippet") //$NON-NLS-1$
									.matching(query)))
					.fetch(offset, limit).hits();
		}
	}

	/**
	 * Author statistics record.
	 */
	public static class AuthorStats {
		private final String authorName;

		private final String authorEmail;

		private final long commitCount;

		/**
		 * Create author statistics.
		 *
		 * @param authorName
		 *            the author name
		 * @param authorEmail
		 *            the author email
		 * @param commitCount
		 *            the number of commits
		 */
		public AuthorStats(String authorName, String authorEmail,
				long commitCount) {
			this.authorName = authorName;
			this.authorEmail = authorEmail;
			this.commitCount = commitCount;
		}

		/**
		 * Get the author name.
		 *
		 * @return the authorName
		 */
		public String getAuthorName() {
			return authorName;
		}

		/**
		 * Get the author email.
		 *
		 * @return the authorEmail
		 */
		public String getAuthorEmail() {
			return authorEmail;
		}

		/**
		 * Get the commit count.
		 *
		 * @return the commitCount
		 */
		public long getCommitCount() {
			return commitCount;
		}
	}
}
