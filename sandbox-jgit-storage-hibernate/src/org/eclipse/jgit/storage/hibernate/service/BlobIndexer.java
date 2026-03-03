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
import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;
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

	private final SessionFactory sessionFactory;

	private final String repositoryName;

	private final JavaBlobExtractor extractor;

	private final int maxBlobSize;

	private final int batchSize;

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
		this(sessionFactory, repositoryName, DEFAULT_MAX_BLOB_SIZE,
				DEFAULT_BATCH_SIZE);
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
		this.sessionFactory = sessionFactory;
		this.repositoryName = repositoryName;
		this.extractor = new JavaBlobExtractor();
		this.maxBlobSize = maxBlobSize;
		this.batchSize = batchSize;
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
		int count = 0;
		try (RevWalk rw = new RevWalk(repo)) {
			RevCommit commit = rw.parseCommit(commitId);
			try (ObjectReader reader = repo.newObjectReader();
					TreeWalk tw = new TreeWalk(reader)) {
				tw.addTree(commit.getTree());
				tw.setRecursive(true);
				while (tw.next()) {
					String path = tw.getPathString();
					if (!path.endsWith(".java")) { //$NON-NLS-1$
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
					JavaBlobIndex idx = extractor.extract(source, path,
							repositoryName, blobOid, commitId.name());
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
		LOG.log(Level.INFO,
				"Completed blob indexing for commit {0}: {1} blobs indexed", //$NON-NLS-1$
				new Object[] { commitId.name(), Integer.valueOf(count) });
		return count;
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
	 * This avoids one query per blob during indexing. For repositories with
	 * very large numbers of indexed blobs, this set may consume
	 * significant memory (approximately 80 bytes per entry).
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
}
