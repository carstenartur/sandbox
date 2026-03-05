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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.hibernate.entity.FilePathHistory;
import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;
import org.eclipse.jgit.storage.hibernate.search.BlobIndexData;
import org.eclipse.jgit.storage.hibernate.search.EmbeddingService;
import org.eclipse.jgit.storage.hibernate.search.FileTypeStrategy;
import org.eclipse.jgit.storage.hibernate.search.FileTypeStrategyRegistry;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Indexes Java source blobs from Git commits into {@link JavaBlobIndex}
 * entities.
 * <p>
 * Complements {@link CommitIndexer} by walking each commit's tree and
 * extracting structural metadata from {@code .java} files using
 * {@link JavaBlobExtractor}. Blobs larger than the configured maximum size
 * are skipped. Entities are persisted in configurable batches.
 * </p>
 */
public class BlobIndexer {

	private static final Logger LOG = Logger
			.getLogger(BlobIndexer.class.getName());

	/** Default maximum blob size in bytes (1 MB). */
	public static final int DEFAULT_MAX_BLOB_SIZE = 1024 * 1024;

	/** Default batch size for persist operations. */
	public static final int DEFAULT_BATCH_SIZE = 50;

	private static final int BINARY_CHECK_SIZE = 8192;

	private static final Set<String> BINARY_EXTENSIONS = Set.of(
			".class", ".jar", ".png", ".jpg", ".jpeg", ".gif", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			".zip", ".tar", ".gz", ".bz2", ".pdf", ".so", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			".dll", ".exe", ".ico", ".war", ".ear"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

	private final SessionFactory sessionFactory;

	private final String repositoryName;

	private final FileTypeStrategyRegistry strategyRegistry;

	private final int maxBlobSize;

	private final int batchSize;

	private final EmbeddingService embeddingService;

	/**
	 * Create a new blob indexer with default settings.
	 *
	 * @param sessionFactory
	 *            the Hibernate session factory
	 * @param repositoryName
	 *            the repository name for partitioning
	 */
	public BlobIndexer(SessionFactory sessionFactory,
			String repositoryName) {
		this(sessionFactory, repositoryName, getMaxBlobSizeFromEnv(),
				getBatchSizeFromEnv(), new EmbeddingService());
	}

	/**
	 * Create a new blob indexer with custom settings.
	 *
	 * @param sessionFactory
	 *            the Hibernate session factory
	 * @param repositoryName
	 *            the repository name for partitioning
	 * @param maxBlobSize
	 *            the maximum blob size in bytes to index
	 * @param batchSize
	 *            number of entities to persist per transaction batch
	 */
	public BlobIndexer(SessionFactory sessionFactory,
			String repositoryName, int maxBlobSize, int batchSize) {
		this(sessionFactory, repositoryName, maxBlobSize, batchSize,
				new EmbeddingService());
	}

	/**
	 * Create a new blob indexer with custom settings and embedding service.
	 *
	 * @param sessionFactory
	 *            the Hibernate session factory
	 * @param repositoryName
	 *            the repository name for partitioning
	 * @param maxBlobSize
	 *            the maximum blob size in bytes to index
	 * @param batchSize
	 *            number of entities to persist per transaction batch
	 * @param embeddingService
	 *            the embedding service for semantic vector generation
	 */
	public BlobIndexer(SessionFactory sessionFactory,
			String repositoryName, int maxBlobSize, int batchSize,
			EmbeddingService embeddingService) {
		this.sessionFactory = sessionFactory;
		this.repositoryName = repositoryName;
		this.strategyRegistry = new FileTypeStrategyRegistry();
		this.maxBlobSize = maxBlobSize;
		this.batchSize = batchSize;
		this.embeddingService = embeddingService;
	}

	/**
	 * Index all Java blobs in a commit's tree.
	 *
	 * @param repo
	 *            the repository to read objects from
	 * @param commitId
	 *            the commit object ID whose tree will be walked
	 * @return the number of blobs indexed
	 * @throws IOException
	 *             if an error occurs reading objects
	 */
	public int indexCommitBlobs(Repository repo, ObjectId commitId)
			throws IOException {
		LOG.log(Level.INFO, "Starting blob indexing for commit {0} in {1}", //$NON-NLS-1$
				new Object[] { commitId.name(), repositoryName });
		Set<String> alreadyIndexed = loadIndexedBlobOids();
		List<JavaBlobIndex> batch = new ArrayList<>();
		List<FilePathHistory> historyBatch = new ArrayList<>();
		int count = 0;
		try (RevWalk rw = new RevWalk(repo)) {
			RevCommit commit = rw.parseCommit(commitId);
			String commitAuthor = commit.getAuthorIdent() != null
					? commit.getAuthorIdent().getName() : null;
			java.time.Instant commitDate = commit.getAuthorIdent() != null
					? commit.getAuthorIdent().getWhenAsInstant() : null;
			try (ObjectReader reader = repo.newObjectReader();
					TreeWalk tw = new TreeWalk(reader)) {
				tw.addTree(commit.getTree());
				tw.setRecursive(true);
				boolean allFileTypes = isAllFileTypesEnabled();
				boolean trackHistory = isFilePathHistoryEnabled();
				Set<String> skipExts = getSkipExtensions();
				while (tw.next()) {
					String path = tw.getPathString();
					// Track file path history if enabled
					if (trackHistory) {
						FilePathHistory fph = new FilePathHistory();
						fph.setRepositoryName(repositoryName);
						fph.setCommitObjectId(commitId.name());
						fph.setFilePath(path);
						fph.setBlobObjectId(tw.getObjectId(0).name());
						fph.setFileType(detectFileType(path));
						fph.setCommitTime(commitDate);
						historyBatch.add(fph);
						if (historyBatch.size() >= batchSize) {
							persistHistoryBatch(historyBatch);
							historyBatch.clear();
						}
					}
					if (isBinaryExtension(path, skipExts)) {
						continue;
					}
					if (!allFileTypes
							&& !path.endsWith(".java")) { //$NON-NLS-1$
						continue;
					}
					ObjectLoader loader = reader.open(tw.getObjectId(0));
					if (loader.getSize() > maxBlobSize) {
						LOG.log(Level.FINE,
								"Skipping blob too large ({0} bytes): {1}", //$NON-NLS-1$
								new Object[] {
										Long.valueOf(loader.getSize()),
										path });
						continue;
					}
					String blobOid = tw.getObjectId(0).name();
					if (alreadyIndexed.contains(blobOid)) {
						continue;
					}
					byte[] bytes = loader.getBytes();
					if (isBinaryContent(bytes)) {
						LOG.log(Level.FINE,
								"Skipping binary blob: {0}", path); //$NON-NLS-1$
						continue;
					}
					String source = new String(bytes,
							StandardCharsets.UTF_8);
					FileTypeStrategy strategy = strategyRegistry
							.getStrategy(path);
					BlobIndexData blobData = strategy.extract(source,
							path);
					JavaBlobIndex idx = toBlobIndex(blobData, path,
							blobOid, commitId.name(), commitAuthor,
							commitDate);
					batch.add(idx);
					alreadyIndexed.add(blobOid);
					count++;
					if (batch.size() >= batchSize) {
						persistBatch(batch);
						batch.clear();
					}
				}
			}
		}
		if (!batch.isEmpty()) {
			persistBatch(batch);
		}
		if (!historyBatch.isEmpty()) {
			persistHistoryBatch(historyBatch);
		}
		LOG.log(Level.INFO,
				"Completed blob indexing for commit {0}: {1} blobs indexed", //$NON-NLS-1$
				new Object[] { commitId.name(), Integer.valueOf(count) });
		return count;
	}

	private void persistHistoryBatch(List<FilePathHistory> entities) {
		try (Session session = sessionFactory.openSession()) {
			session.beginTransaction();
			for (FilePathHistory fph : entities) {
				session.persist(fph);
			}
			session.getTransaction().commit();
		}
	}

	private static String detectFileType(String path) {
		int dot = path.lastIndexOf('.');
		if (dot >= 0) {
			return path.substring(dot + 1).toLowerCase();
		}
		return "unknown"; //$NON-NLS-1$
	}

	private void persistBatch(List<JavaBlobIndex> entities) {
		try (Session session = sessionFactory.openSession()) {
			session.beginTransaction();
			for (JavaBlobIndex idx : entities) {
				session.persist(idx);
			}
			session.getTransaction().commit();
		}
	}

	/**
	 * Pre-load the set of already-indexed blob OIDs for this repository.
	 * <p>
	 * Deduplicates on blob OID alone so that the same content is only indexed
	 * once regardless of how many commits reference it. The
	 * {@code commitObjectId} stored in the resulting {@link JavaBlobIndex}
	 * represents the first commit where the blob was encountered.
	 * </p>
	 * <p>
	 * This avoids one query per blob during indexing. For repositories with
	 * very large numbers of indexed blobs, this set may consume significant
	 * memory.
	 * </p>
	 *
	 * @return set of blob OID hex strings already in the index
	 */
	private Set<String> loadIndexedBlobOids() {
		try (Session session = sessionFactory.openSession()) {
			List<String> oids = session.createQuery(
					"SELECT j.blobObjectId FROM JavaBlobIndex j WHERE j.repositoryName = :repo", //$NON-NLS-1$
					String.class)
					.setParameter("repo", repositoryName) //$NON-NLS-1$
					.getResultList();
			return new HashSet<>(oids);
		}
	}

	/**
	 * Detect binary content by checking for null bytes in the first 8 KB.
	 *
	 * @param bytes
	 *            the file content
	 * @return {@code true} if the content appears to be binary
	 */
	public static boolean isBinaryContent(byte[] bytes) {
		int checkLen = Math.min(bytes.length, BINARY_CHECK_SIZE);
		for (int i = 0; i < checkLen; i++) {
			if (bytes[i] == 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean isBinaryExtension(String path,
			Set<String> skipExts) {
		int dot = path.lastIndexOf('.');
		if (dot >= 0) {
			return skipExts
					.contains(path.substring(dot).toLowerCase());
		}
		return false;
	}

	private static int getMaxBlobSizeFromEnv() {
		String val = System.getenv("JGIT_INDEX_MAX_BLOB_SIZE"); //$NON-NLS-1$
		if (val != null) {
			try {
				return Integer.parseInt(val);
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		return DEFAULT_MAX_BLOB_SIZE;
	}

	private static int getBatchSizeFromEnv() {
		String val = System.getenv("JGIT_INDEX_BATCH_SIZE"); //$NON-NLS-1$
		if (val != null) {
			try {
				return Integer.parseInt(val);
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		return DEFAULT_BATCH_SIZE;
	}

	private static boolean isAllFileTypesEnabled() {
		String val = System.getenv("JGIT_INDEX_ALL_FILE_TYPES"); //$NON-NLS-1$
		return val == null || !"false".equalsIgnoreCase(val); //$NON-NLS-1$
	}

	private static boolean isFilePathHistoryEnabled() {
		String val = System.getenv("JGIT_INDEX_FILE_PATH_HISTORY"); //$NON-NLS-1$
		return val == null || !"false".equalsIgnoreCase(val); //$NON-NLS-1$
	}

	private static Set<String> getSkipExtensions() {
		String val = System.getenv("JGIT_INDEX_SKIP_EXTENSIONS"); //$NON-NLS-1$
		if (val != null && !val.isEmpty()) {
			Set<String> exts = new HashSet<>();
			for (String ext : val.split(",")) { //$NON-NLS-1$
				exts.add(ext.trim().toLowerCase());
			}
			return exts;
		}
		return BINARY_EXTENSIONS;
	}

	private JavaBlobIndex toBlobIndex(BlobIndexData data, String filePath,
			String blobOid, String commitOid, String commitAuthor,
			java.time.Instant commitDate) {
		JavaBlobIndex idx = new JavaBlobIndex();
		idx.setRepositoryName(repositoryName);
		idx.setBlobObjectId(blobOid);
		idx.setCommitObjectId(commitOid);
		idx.setFileType(data.getFileType());
		idx.setFilePath(filePath);
		idx.setPackageName(data.getPackageOrNamespace());
		idx.setDeclaredTypes(data.getDeclaredTypes());
		idx.setFullyQualifiedNames(data.getFullyQualifiedNames());
		idx.setDeclaredMethods(data.getDeclaredMethods());
		idx.setDeclaredFields(data.getDeclaredFields());
		idx.setExtendsTypes(data.getExtendsTypes());
		idx.setImplementsTypes(data.getImplementsTypes());
		idx.setImportStatements(data.getImportStatements());
		idx.setSourceSnippet(data.getSourceSnippet());
		idx.setProjectName(data.getProjectName());
		idx.setSimpleClassName(data.getSimpleClassName());
		idx.setTypeKind(data.getTypeKind());
		idx.setVisibility(data.getVisibility());
		idx.setAnnotations(data.getAnnotations());
		idx.setLineCount(data.getLineCount());
		idx.setTypeDocumentation(data.getTypeDocumentation());
		idx.setMethodSignatures(data.getMethodSignatures());
		idx.setReferencedTypes(data.getReferencedTypes());
		idx.setStringLiterals(data.getStringLiterals());
		idx.setHasMainMethod(data.isHasMainMethod());
		idx.setCommitAuthor(commitAuthor);
		idx.setCommitDate(commitDate);
		// Generate semantic embedding if service is available
		try {
			String embeddingText = EmbeddingService.buildEmbeddingText(
					data.getSimpleClassName(),
					data.getTypeDocumentation(),
					data.getMethodSignatures(),
					data.getPackageOrNamespace());
			float[] embedding = embeddingService.embed(embeddingText);
			if (embedding != null) {
				idx.setSemanticEmbedding(embedding);
				idx.setHasEmbedding(true);
			}
		} catch (Exception e) {
			LOG.log(Level.FINE,
					"Embedding generation failed for blob {0}", //$NON-NLS-1$
					blobOid);
		}
		return idx;
	}
}
