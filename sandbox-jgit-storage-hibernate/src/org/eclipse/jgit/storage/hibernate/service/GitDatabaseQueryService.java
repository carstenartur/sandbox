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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.hibernate.entity.FilePathHistory;
import org.eclipse.jgit.storage.hibernate.entity.GitCommitIndex;
import org.eclipse.jgit.storage.hibernate.entity.GitObjectEntity;
import org.eclipse.jgit.storage.hibernate.entity.GitRefEntity;
import org.eclipse.jgit.storage.hibernate.entity.GitReflogEntity;
import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;
import org.eclipse.jgit.storage.hibernate.search.EmbeddingService;
import org.eclipse.jgit.storage.hibernate.search.RankFusionUtil;
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

	private EmbeddingService embeddingService;

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
	 * Set the embedding service for semantic search.
	 *
	 * @param embeddingService
	 *            the embedding service
	 */
	public void setEmbeddingService(EmbeddingService embeddingService) {
		this.embeddingService = embeddingService;
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
	 * Search blobs by annotation names.
	 *
	 * @param repo
	 *            the repository name
	 * @param query
	 *            the annotation search query
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return matching blob index entities
	 */
	public List<JavaBlobIndex> searchByAnnotation(String repo,
			String query, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match()
									.field("repositoryName") //$NON-NLS-1$
									.matching(repo))
							.must(f.match()
									.field("annotations") //$NON-NLS-1$
									.matching(query)))
					.fetchHits(offset, limit);
		}
	}

	/**
	 * Search blobs by type with optional module/project filter.
	 *
	 * @param repo
	 *            the repository name
	 * @param query
	 *            the type search query
	 * @param module
	 *            optional module/project name filter (may be null)
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return matching blob index entities
	 */
	public List<JavaBlobIndex> searchByTypeWithModule(String repo,
			String query, String module, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> {
						var bool = f.bool()
								.must(f.match()
										.field("repositoryName") //$NON-NLS-1$
										.matching(repo))
								.must(f.match()
										.fields("simpleClassName", //$NON-NLS-1$
												"declaredTypes", //$NON-NLS-1$
												"fullyQualifiedNames") //$NON-NLS-1$
										.matching(query));
						if (module != null && !module.isEmpty()) {
							bool = bool.must(f.match()
									.field("projectName") //$NON-NLS-1$
									.matching(module));
						}
						return bool;
					})
					.fetchHits(offset, limit);
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
	 * Search blobs by type documentation (Javadoc).
	 *
	 * @param repo
	 *            the repository name
	 * @param query
	 *            the documentation search query
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return matching blob index entities
	 */
	public List<JavaBlobIndex> searchByDocumentation(String repo,
			String query, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match()
									.field("repositoryName") //$NON-NLS-1$
									.matching(repo))
							.must(f.match()
									.field("typeDocumentation") //$NON-NLS-1$
									.matching(query)))
					.fetchHits(offset, limit);
		}
	}

	/**
	 * Search blobs by referenced type.
	 *
	 * @param repo
	 *            the repository name
	 * @param query
	 *            the referenced type search query
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return matching blob index entities
	 */
	public List<JavaBlobIndex> searchByReferencedType(String repo,
			String query, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match()
									.field("repositoryName") //$NON-NLS-1$
									.matching(repo))
							.must(f.match()
									.field("referencedTypes") //$NON-NLS-1$
									.matching(query)))
					.fetchHits(offset, limit);
		}
	}

	/**
	 * Search blobs by string literals.
	 *
	 * @param repo
	 *            the repository name
	 * @param query
	 *            the string literal search query
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return matching blob index entities
	 */
	public List<JavaBlobIndex> searchByStringLiteral(String repo,
			String query, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match()
									.field("repositoryName") //$NON-NLS-1$
									.matching(repo))
							.must(f.match()
									.field("stringLiterals") //$NON-NLS-1$
									.matching(query)))
					.fetchHits(offset, limit);
		}
	}

	/**
	 * Search file paths across all commits.
	 *
	 * @param repo
	 *            the repository name
	 * @param pathQuery
	 *            the file path search query
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return matching file path history entries
	 */
	public List<FilePathHistory> searchFilePath(String repo,
			String pathQuery, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(FilePathHistory.class)
					.where(f -> f.bool()
							.must(f.match()
									.field("repositoryName") //$NON-NLS-1$
									.matching(repo))
							.must(f.match()
									.field("filePath") //$NON-NLS-1$
									.matching(pathQuery)))
					.fetchHits(offset, limit);
		}
	}

	/**
	 * Get the history of a specific file across commits.
	 *
	 * @param repo
	 *            the repository name
	 * @param exactPath
	 *            the exact file path
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return file path history entries ordered by commit time
	 */
	public List<FilePathHistory> getFileHistory(String repo,
			String exactPath, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"FROM FilePathHistory f WHERE f.repositoryName = :repo " //$NON-NLS-1$
							+ "AND f.filePath = :path ORDER BY f.commitTime DESC", //$NON-NLS-1$
					FilePathHistory.class)
					.setParameter("repo", repo) //$NON-NLS-1$
					.setParameter("path", exactPath) //$NON-NLS-1$
					.setFirstResult(offset)
					.setMaxResults(limit)
					.getResultList();
		}
	}

	/**
	 * Search fully qualified names across all file types.
	 *
	 * @param repo
	 *            the repository name
	 * @param fqnQuery
	 *            the FQN search query
	 * @param fileType
	 *            optional file type filter (may be null)
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return matching blob index entities
	 */
	public List<JavaBlobIndex> searchFqnAcrossTypes(String repo,
			String fqnQuery, String fileType, int offset, int limit) {
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> {
						var bool = f.bool()
								.must(f.match()
										.field("repositoryName") //$NON-NLS-1$
										.matching(repo))
								.must(f.match()
										.field("fullyQualifiedNames") //$NON-NLS-1$
										.matching(fqnQuery));
						if (fileType != null && !fileType.isEmpty()) {
							bool = bool.must(f.match()
									.field("fileType") //$NON-NLS-1$
									.matching(fileType));
						}
						return bool;
					})
					.fetchHits(offset, limit);
		}
	}

	// --- Semantic search methods ---

	/**
	 * Semantic search: find code by natural language description.
	 * <p>
	 * Uses vector similarity (cosine) on pre-computed embeddings. Requires
	 * the embedding service to be configured via
	 * {@link #setEmbeddingService(EmbeddingService)}.
	 * </p>
	 *
	 * @param repoName
	 *            the repository name
	 * @param queryText
	 *            natural language query (e.g., "HTTP client with retry")
	 * @param topK
	 *            number of results
	 * @return matching Java blob index entries ranked by semantic similarity
	 */
	public List<JavaBlobIndex> semanticSearch(String repoName,
			String queryText, int topK) {
		if (embeddingService == null || !embeddingService.isAvailable()) {
			return List.of();
		}
		float[] queryVector = embeddingService.embed(queryText);
		if (queryVector == null) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.knn(topK)
							.field("semanticEmbedding") //$NON-NLS-1$
							.matching(queryVector)
							.filter(f.match()
									.field("repositoryName") //$NON-NLS-1$
									.matching(repoName)))
					.fetchHits(topK);
		}
	}

	/**
	 * Hybrid search: combines full-text and semantic search.
	 * <p>
	 * Returns the union of both result sets, re-ranked using Reciprocal Rank
	 * Fusion (RRF). Falls back to full-text only search if the embedding
	 * service is not available.
	 * </p>
	 *
	 * @param repoName
	 *            the repository name
	 * @param queryText
	 *            natural language or keyword query
	 * @param topK
	 *            number of results
	 * @return fused results ordered by combined relevance score
	 */
	public List<JavaBlobIndex> hybridSearch(String repoName,
			String queryText, int topK) {
		List<JavaBlobIndex> fulltextResults = searchSourceContent(
				repoName, queryText, 0, topK);

		List<JavaBlobIndex> semanticResults = semanticSearch(repoName,
				queryText, topK);

		if (semanticResults.isEmpty()) {
			return fulltextResults;
		}

		return RankFusionUtil.reciprocalRankFusion(semanticResults,
				fulltextResults, topK);
	}

	/**
	 * Find semantically similar code to a given blob.
	 * <p>
	 * Uses the embedding of the source blob to find nearest neighbors in the
	 * vector space. Returns an empty list if the source blob has no embedding
	 * or the embedding service is not available.
	 * </p>
	 *
	 * @param repoName
	 *            the repository name
	 * @param blobObjectId
	 *            the blob object ID of the source file
	 * @param topK
	 *            number of similar results to return
	 * @return similar Java blob index entries ranked by vector similarity
	 */
	public List<JavaBlobIndex> findSimilarCode(String repoName,
			String blobObjectId, int topK) {
		try (Session session = sessionFactory.openSession()) {
			JavaBlobIndex source = session.createQuery(
					"FROM JavaBlobIndex j WHERE j.repositoryName = :repo " //$NON-NLS-1$
							+ "AND j.blobObjectId = :blobOid " //$NON-NLS-1$
							+ "AND j.hasEmbedding = true", //$NON-NLS-1$
					JavaBlobIndex.class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("blobOid", blobObjectId) //$NON-NLS-1$
					.setMaxResults(1)
					.uniqueResult();
			if (source == null
					|| source.getSemanticEmbedding() == null) {
				return List.of();
			}
			float[] sourceVector = source.getSemanticEmbedding();
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.knn(topK + 1)
							.field("semanticEmbedding") //$NON-NLS-1$
							.matching(sourceVector)
							.filter(f.match()
									.field("repositoryName") //$NON-NLS-1$
									.matching(repoName)))
					.fetchHits(topK + 1)
					.stream()
					.filter(r -> !blobObjectId
							.equals(r.getBlobObjectId()))
					.limit(topK)
					.toList();
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

	// --- Feature 1: Migration Planning Queries ---

	/**
	 * Find all Java blob index entries whose import statements match a given
	 * prefix. Useful for planning framework migrations (e.g. from
	 * {@code javax.servlet} to {@code jakarta.servlet}).
	 *
	 * @param repoName
	 *            the repository name
	 * @param importPrefix
	 *            the import prefix to search for (e.g. {@code "javax.servlet"})
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return matching blob index entities
	 */
	public List<JavaBlobIndex> getMigrationImpact(String repoName,
			String importPrefix, int offset, int limit) {
		if (repoName == null || repoName.isEmpty() || importPrefix == null
				|| importPrefix.isEmpty()) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match()
									.field("repositoryName") //$NON-NLS-1$
									.matching(repoName))
							.must(f.match()
									.field("importStatements") //$NON-NLS-1$
									.matching(importPrefix)))
					.fetchHits(offset, limit);
		}
	}

	/**
	 * Returns aggregated migration impact statistics for a given import prefix.
	 * <p>
	 * The returned map contains:
	 * <ul>
	 * <li>{@code totalFiles} — total number of affected files</li>
	 * <li>{@code distinctPackages} — number of distinct package names</li>
	 * <li>{@code distinctAuthors} — number of distinct commit authors</li>
	 * <li>{@code earliestDate} — earliest commit date as ISO-8601 string</li>
	 * <li>{@code latestDate} — latest commit date as ISO-8601 string</li>
	 * </ul>
	 *
	 * @param repoName
	 *            the repository name
	 * @param importPrefix
	 *            the import prefix to search for
	 * @return summary statistics as a map
	 */
	public Map<String, Object> getMigrationImpactSummary(String repoName,
			String importPrefix) {
		List<JavaBlobIndex> affected = getMigrationImpact(repoName,
				importPrefix, 0, Integer.MAX_VALUE);
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("totalFiles", affected.size()); //$NON-NLS-1$
		Set<String> packages = new HashSet<>();
		Set<String> authors = new HashSet<>();
		Instant earliest = null;
		Instant latest = null;
		for (JavaBlobIndex entry : affected) {
			if (entry.getPackageName() != null) {
				packages.add(entry.getPackageName());
			}
			if (entry.getCommitAuthor() != null) {
				authors.add(entry.getCommitAuthor());
			}
			Instant date = entry.getCommitDate();
			if (date != null) {
				if (earliest == null || date.isBefore(earliest)) {
					earliest = date;
				}
				if (latest == null || date.isAfter(latest)) {
					latest = date;
				}
			}
		}
		summary.put("distinctPackages", packages.size()); //$NON-NLS-1$
		summary.put("distinctAuthors", authors.size()); //$NON-NLS-1$
		summary.put("earliestDate", //$NON-NLS-1$
				earliest != null ? earliest.toString() : null);
		summary.put("latestDate", //$NON-NLS-1$
				latest != null ? latest.toString() : null);
		return summary;
	}

	/**
	 * Find all Java blob index entries that use the {@code @Deprecated}
	 * annotation.
	 *
	 * @param repoName
	 *            the repository name
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return matching blob index entities with {@code @Deprecated} annotation
	 */
	public List<JavaBlobIndex> getDeprecatedApiUsage(String repoName,
			int offset, int limit) {
		if (repoName == null || repoName.isEmpty()) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match()
									.field("repositoryName") //$NON-NLS-1$
									.matching(repoName))
							.must(f.match()
									.field("annotations") //$NON-NLS-1$
									.matching("Deprecated"))) //$NON-NLS-1$
					.fetchHits(offset, limit);
		}
	}

	/**
	 * Count how many files in the repository import types matching the given
	 * prefix. Useful for dependency impact analysis.
	 *
	 * @param repoName
	 *            the repository name
	 * @param importPrefix
	 *            the import prefix to count (e.g. {@code "com.google.guava"})
	 * @return number of files that contain at least one matching import
	 */
	public long getImportFrequency(String repoName, String importPrefix) {
		if (repoName == null || repoName.isEmpty() || importPrefix == null
				|| importPrefix.isEmpty()) {
			return 0L;
		}
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> f.bool()
							.must(f.match()
									.field("repositoryName") //$NON-NLS-1$
									.matching(repoName))
							.must(f.match()
									.field("importStatements") //$NON-NLS-1$
									.matching(importPrefix)))
					.fetchTotalHitCount();
		}
	}

	/**
	 * Find all files that import from any of the given prefixes.
	 * <p>
	 * Useful for determining migration scope across multiple frameworks (e.g.
	 * "find everything using {@code javax.servlet} OR {@code javax.ws.rs}").
	 *
	 * @param repoName
	 *            the repository name
	 * @param importPrefixes
	 *            list of import prefixes to match
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return matching blob index entities
	 */
	public List<JavaBlobIndex> searchByMultipleImports(String repoName,
			List<String> importPrefixes, int offset, int limit) {
		if (repoName == null || repoName.isEmpty()
				|| importPrefixes == null || importPrefixes.isEmpty()) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			return searchSession.search(JavaBlobIndex.class)
					.where(f -> {
						var bool = f.bool()
								.must(f.match()
										.field("repositoryName") //$NON-NLS-1$
										.matching(repoName));
						var should = f.bool();
						for (String prefix : importPrefixes) {
							if (prefix != null && !prefix.isEmpty()) {
								should = should.should(f.match()
										.field("importStatements") //$NON-NLS-1$
										.matching(prefix));
							}
						}
						return bool.must(should);
					})
					.fetchHits(offset, limit);
		}
	}

	/**
	 * Find which files need updating for each old→new import mapping.
	 * <p>
	 * Takes a map of old import prefixes to new import prefixes (e.g.
	 * {@code {"javax.servlet" → "jakarta.servlet"}}) and returns the files
	 * affected by each mapping.
	 *
	 * @param repoName
	 *            the repository name
	 * @param oldToNewImportMap
	 *            map of old import prefix → new import prefix
	 * @return map of old prefix → list of files that need migration
	 */
	public Map<String, List<JavaBlobIndex>> getMigrationCandidates(
			String repoName,
			Map<String, String> oldToNewImportMap) {
		Map<String, List<JavaBlobIndex>> result = new LinkedHashMap<>();
		if (repoName == null || repoName.isEmpty()
				|| oldToNewImportMap == null || oldToNewImportMap.isEmpty()) {
			return result;
		}
		for (String oldPrefix : oldToNewImportMap.keySet()) {
			if (oldPrefix != null && !oldPrefix.isEmpty()) {
				List<JavaBlobIndex> affected = getMigrationImpact(
						repoName, oldPrefix, 0, Integer.MAX_VALUE);
				result.put(oldPrefix, affected);
			}
		}
		return result;
	}

	// --- Feature 2: API Evolution Tracker ---

	/**
	 * Compare {@link JavaBlobIndex} entries between two commits.
	 * <p>
	 * Returns an {@link ApiDiffResult} with:
	 * <ul>
	 * <li>files added in commit B (not in A)</li>
	 * <li>files removed from commit A (not in B)</li>
	 * <li>files with method signature or visibility changes</li>
	 * </ul>
	 *
	 * @param repoName
	 *            the repository name
	 * @param commitOidA
	 *            the first (older) commit object ID
	 * @param commitOidB
	 *            the second (newer) commit object ID
	 * @return the diff result
	 */
	public ApiDiffResult getApiDiff(String repoName, String commitOidA,
			String commitOidB) {
		if (repoName == null || repoName.isEmpty()
				|| commitOidA == null || commitOidA.isEmpty()
				|| commitOidB == null || commitOidB.isEmpty()) {
			return new ApiDiffResult(List.of(), List.of(), List.of());
		}
		List<JavaBlobIndex> entriesA = getEntriesForCommit(repoName,
				commitOidA);
		List<JavaBlobIndex> entriesB = getEntriesForCommit(repoName,
				commitOidB);

		Map<String, JavaBlobIndex> mapA = indexByFilePath(entriesA);
		Map<String, JavaBlobIndex> mapB = indexByFilePath(entriesB);

		List<JavaBlobIndex> added = new ArrayList<>();
		List<JavaBlobIndex> removed = new ArrayList<>();
		List<ApiChangeEntry> changed = new ArrayList<>();

		for (Map.Entry<String, JavaBlobIndex> entry : mapB.entrySet()) {
			if (!mapA.containsKey(entry.getKey())) {
				added.add(entry.getValue());
			}
		}
		for (Map.Entry<String, JavaBlobIndex> entry : mapA.entrySet()) {
			if (!mapB.containsKey(entry.getKey())) {
				removed.add(entry.getValue());
			} else {
				JavaBlobIndex before = entry.getValue();
				JavaBlobIndex after = mapB.get(entry.getKey());
				String desc = detectApiChanges(before, after);
				if (desc != null) {
					changed.add(new ApiChangeEntry(before, after, desc));
				}
			}
		}
		return new ApiDiffResult(added, removed, changed);
	}

	/**
	 * Find all entries with {@code @Deprecated} annotation ordered by commit
	 * date. Shows when deprecations were introduced over time.
	 *
	 * @param repoName
	 *            the repository name
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return deprecated entries ordered by commit date ascending
	 */
	public List<JavaBlobIndex> getDeprecationTimeline(String repoName,
			int offset, int limit) {
		if (repoName == null || repoName.isEmpty()) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"FROM JavaBlobIndex j WHERE j.repositoryName = :repo " //$NON-NLS-1$
							+ "AND j.annotations LIKE :dep " //$NON-NLS-1$
							+ "ORDER BY j.commitDate ASC", //$NON-NLS-1$
					JavaBlobIndex.class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("dep", "%Deprecated%") //$NON-NLS-1$
					.setFirstResult(offset)
					.setMaxResults(limit)
					.getResultList();
		}
	}

	/**
	 * Like {@link #getApiDiff(String, String, String)} but filtered to public
	 * API only. Detects breaking changes to publicly visible types.
	 *
	 * @param repoName
	 *            the repository name
	 * @param commitOidA
	 *            the first (older) commit object ID
	 * @param commitOidB
	 *            the second (newer) commit object ID
	 * @return the diff result for public API entries only
	 */
	public ApiDiffResult getPublicApiChanges(String repoName,
			String commitOidA, String commitOidB) {
		ApiDiffResult full = getApiDiff(repoName, commitOidA, commitOidB);
		List<JavaBlobIndex> addedPublic = full.getAddedFiles() == null
				? List.of()
				: full.getAddedFiles().stream()
						.filter(e -> isPublic(e))
						.toList();
		List<JavaBlobIndex> removedPublic = full.getRemovedFiles() == null
				? List.of()
				: full.getRemovedFiles().stream()
						.filter(e -> isPublic(e))
						.toList();
		List<ApiChangeEntry> changedPublic = full.getChangedFiles() == null
				? List.of()
				: full.getChangedFiles().stream()
						.filter(e -> isPublic(e.getBefore())
								|| isPublic(e.getAfter()))
						.toList();
		return new ApiDiffResult(addedPublic, removedPublic, changedPublic);
	}

	/**
	 * Track how a specific type's declared methods and method signatures
	 * changed over time across commits.
	 *
	 * @param repoName
	 *            the repository name
	 * @param fullyQualifiedTypeName
	 *            the fully qualified type name to track
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return entries for the type ordered by commit date ascending
	 */
	public List<JavaBlobIndex> getMethodEvolution(String repoName,
			String fullyQualifiedTypeName, int offset, int limit) {
		if (repoName == null || repoName.isEmpty()
				|| fullyQualifiedTypeName == null
				|| fullyQualifiedTypeName.isEmpty()) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"FROM JavaBlobIndex j WHERE j.repositoryName = :repo " //$NON-NLS-1$
							+ "AND j.fullyQualifiedNames LIKE :fqn " //$NON-NLS-1$
							+ "ORDER BY j.commitDate ASC", //$NON-NLS-1$
					JavaBlobIndex.class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("fqn", "%" + fullyQualifiedTypeName + "%") //$NON-NLS-1$ //$NON-NLS-2$
					.setFirstResult(offset)
					.setMaxResults(limit)
					.getResultList();
		}
	}

	// --- Feature 3: Developer Analytics ---

	/**
	 * Group entries by commit author and type kind, counting how many
	 * classes/interfaces/enums each author introduced.
	 * <p>
	 * Each element of the returned list is an {@code Object[]} with:
	 * {@code [commitAuthor, typeKind, count]}.
	 *
	 * @param repoName
	 *            the repository name
	 * @return aggregated statistics per author and type kind
	 */
	public List<Object[]> getAuthorTypeStatistics(String repoName) {
		if (repoName == null || repoName.isEmpty()) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"SELECT j.commitAuthor, j.typeKind, COUNT(j) " //$NON-NLS-1$
							+ "FROM JavaBlobIndex j WHERE j.repositoryName = :repo " //$NON-NLS-1$
							+ "GROUP BY j.commitAuthor, j.typeKind " //$NON-NLS-1$
							+ "ORDER BY j.commitAuthor, j.typeKind", //$NON-NLS-1$
					Object[].class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.getResultList();
		}
	}

	/**
	 * Return average line count per commit date for entries matching a package
	 * prefix. Shows how code size grows over time.
	 * <p>
	 * Each element of the returned list is an {@code Object[]} with:
	 * {@code [commitDate, avgLineCount]}.
	 *
	 * @param repoName
	 *            the repository name
	 * @param packagePrefix
	 *            package name prefix filter (e.g. {@code "com.example"})
	 * @return average line count per commit date ordered by date ascending
	 */
	public List<Object[]> getCodeComplexityTrend(String repoName,
			String packagePrefix) {
		if (repoName == null || repoName.isEmpty()) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			String hql;
			if (packagePrefix == null || packagePrefix.isEmpty()) {
				hql = "SELECT j.commitDate, AVG(j.lineCount) " //$NON-NLS-1$
						+ "FROM JavaBlobIndex j WHERE j.repositoryName = :repo " //$NON-NLS-1$
						+ "GROUP BY j.commitDate ORDER BY j.commitDate ASC"; //$NON-NLS-1$
				return session.createQuery(hql, Object[].class)
						.setParameter("repo", repoName) //$NON-NLS-1$
						.getResultList();
			}
			hql = "SELECT j.commitDate, AVG(j.lineCount) " //$NON-NLS-1$
					+ "FROM JavaBlobIndex j WHERE j.repositoryName = :repo " //$NON-NLS-1$
					+ "AND j.packageName LIKE :pkg " //$NON-NLS-1$
					+ "GROUP BY j.commitDate ORDER BY j.commitDate ASC"; //$NON-NLS-1$
			return session.createQuery(hql, Object[].class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("pkg", packagePrefix + "%") //$NON-NLS-1$
					.getResultList();
		}
	}

	/**
	 * Find {@link JavaBlobIndex} entries with a line count above the given
	 * threshold. Useful for identifying large classes that may need
	 * refactoring.
	 *
	 * @param repoName
	 *            the repository name
	 * @param lineCountThreshold
	 *            minimum line count (inclusive)
	 * @param offset
	 *            pagination offset
	 * @param limit
	 *            maximum results
	 * @return entries whose line count exceeds the threshold, ordered by line
	 *         count descending
	 */
	public List<JavaBlobIndex> getMonsterClasses(String repoName,
			int lineCountThreshold, int offset, int limit) {
		if (repoName == null || repoName.isEmpty()) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"FROM JavaBlobIndex j WHERE j.repositoryName = :repo " //$NON-NLS-1$
							+ "AND j.lineCount >= :threshold " //$NON-NLS-1$
							+ "ORDER BY j.lineCount DESC", //$NON-NLS-1$
					JavaBlobIndex.class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("threshold", lineCountThreshold) //$NON-NLS-1$
					.setFirstResult(offset)
					.setMaxResults(limit)
					.getResultList();
		}
	}

	/**
	 * Find types whose fully qualified names never appear in any other file's
	 * import statements within the same repository. These are potential dead
	 * code candidates.
	 *
	 * @param repoName
	 *            the repository name
	 * @return entries that are never imported by other files in the repository
	 */
	public List<JavaBlobIndex> getDeadCodeCandidates(String repoName) {
		if (repoName == null || repoName.isEmpty()) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			// Collect all import statements in the repo as a set of tokens
			List<String> allImports = session.createQuery(
					"SELECT j.importStatements FROM JavaBlobIndex j " //$NON-NLS-1$
							+ "WHERE j.repositoryName = :repo " //$NON-NLS-1$
							+ "AND j.importStatements IS NOT NULL", //$NON-NLS-1$
					String.class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.getResultList();
			// Build a set of individual imported names for precise matching
			Set<String> importedNames = new HashSet<>();
			for (String imports : allImports) {
				if (imports != null) {
					for (String token : FQN_SPLIT_PATTERN.split(imports)) {
						if (!token.isBlank()) {
							importedNames.add(token.trim());
						}
					}
				}
			}

			// Collect all types
			List<JavaBlobIndex> all = session.createQuery(
					"FROM JavaBlobIndex j WHERE j.repositoryName = :repo " //$NON-NLS-1$
							+ "AND j.fullyQualifiedNames IS NOT NULL", //$NON-NLS-1$
					JavaBlobIndex.class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.getResultList();

			List<JavaBlobIndex> deadCandidates = new ArrayList<>();
			for (JavaBlobIndex entry : all) {
				String fqns = entry.getFullyQualifiedNames();
				if (fqns == null || fqns.isBlank()) {
					continue;
				}
				boolean referenced = false;
				for (String fqn : FQN_SPLIT_PATTERN.split(fqns)) {
					if (!fqn.isBlank()
							&& importedNames.contains(fqn.trim())) {
						referenced = true;
						break;
					}
				}
				if (!referenced) {
					deadCandidates.add(entry);
				}
			}
			return deadCandidates;
		}
	}

	/**
	 * Count types annotated with {@code @Test} (test types) vs. total types
	 * per package. Returns a proxy for test coverage.
	 * <p>
	 * Each element of the returned list is an {@code Object[]} with:
	 * {@code [packageName, testTypeCount, totalTypeCount]}.
	 *
	 * @param repoName
	 *            the repository name
	 * @return per-package test type count and total type count
	 */
	public List<Object[]> getTestCoverageProxy(String repoName) {
		if (repoName == null || repoName.isEmpty()) {
			return List.of();
		}
		try (Session session = sessionFactory.openSession()) {
			// Total types per package
			List<Object[]> totals = session.createQuery(
					"SELECT j.packageName, COUNT(j) FROM JavaBlobIndex j " //$NON-NLS-1$
							+ "WHERE j.repositoryName = :repo " //$NON-NLS-1$
							+ "GROUP BY j.packageName", //$NON-NLS-1$
					Object[].class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.getResultList();

			// Test types per package (annotations contain "Test")
			List<Object[]> testCounts = session.createQuery(
					"SELECT j.packageName, COUNT(j) FROM JavaBlobIndex j " //$NON-NLS-1$
							+ "WHERE j.repositoryName = :repo " //$NON-NLS-1$
							+ "AND (j.annotations LIKE :test1 OR j.annotations LIKE :test2) " //$NON-NLS-1$
							+ "GROUP BY j.packageName", //$NON-NLS-1$
					Object[].class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("test1", "%Test%") //$NON-NLS-1$
					.setParameter("test2", "%@Test%") //$NON-NLS-1$
					.getResultList();

			Map<String, Long> testMap = new HashMap<>();
			for (Object[] row : testCounts) {
				testMap.put((String) row[0], (Long) row[1]);
			}

			List<Object[]> result = new ArrayList<>();
			for (Object[] row : totals) {
				String pkg = (String) row[0];
				Long total = (Long) row[1];
				Long testCount = testMap.getOrDefault(pkg, 0L);
				result.add(new Object[] { pkg, testCount, total });
			}
			return result;
		}
	}

	private static final java.util.regex.Pattern FQN_SPLIT_PATTERN = java.util.regex.Pattern
			.compile("[,\\s]+"); //$NON-NLS-1$

	// --- Private helpers ---

	private List<JavaBlobIndex> getEntriesForCommit(String repoName,
			String commitOid) {
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"FROM JavaBlobIndex j WHERE j.repositoryName = :repo " //$NON-NLS-1$
							+ "AND j.commitObjectId = :commit", //$NON-NLS-1$
					JavaBlobIndex.class)
					.setParameter("repo", repoName) //$NON-NLS-1$
					.setParameter("commit", commitOid) //$NON-NLS-1$
					.getResultList();
		}
	}

	private static Map<String, JavaBlobIndex> indexByFilePath(
			List<JavaBlobIndex> entries) {
		Map<String, JavaBlobIndex> map = new LinkedHashMap<>();
		for (JavaBlobIndex entry : entries) {
			if (entry.getFilePath() != null) {
				map.put(entry.getFilePath(), entry);
			}
		}
		return map;
	}

	private static String detectApiChanges(JavaBlobIndex before,
			JavaBlobIndex after) {
		List<String> changes = new ArrayList<>();
		if (!Objects.equals(before.getDeclaredMethods(),
				after.getDeclaredMethods())) {
			changes.add("methods changed"); //$NON-NLS-1$
		}
		if (!Objects.equals(before.getVisibility(),
				after.getVisibility())) {
			changes.add("visibility changed"); //$NON-NLS-1$
		}
		if (changes.isEmpty()) {
			return null;
		}
		return String.join(", ", changes); //$NON-NLS-1$
	}

	private static boolean isPublic(JavaBlobIndex entry) {
		if (entry == null) {
			return false;
		}
		String vis = entry.getVisibility();
		return vis != null && vis.contains("public"); //$NON-NLS-1$
	}
}
