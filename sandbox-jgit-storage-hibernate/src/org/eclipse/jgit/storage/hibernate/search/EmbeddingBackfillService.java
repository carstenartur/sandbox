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
package org.eclipse.jgit.storage.hibernate.search;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Service for backfilling semantic embeddings on existing
 * {@link JavaBlobIndex} entries that were indexed before embedding support was
 * added.
 * <p>
 * Processes entries in configurable batches to avoid excessive memory usage.
 * Each batch reads entries with {@code hasEmbedding = false}, computes their
 * embedding via {@link EmbeddingService}, and updates them in a single
 * transaction.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>
 * EmbeddingService embeddingService = new EmbeddingService();
 * EmbeddingBackfillService.backfill(sessionFactory, embeddingService, 100);
 * </pre>
 *
 * <h3>Configuration</h3>
 * <ul>
 * <li>{@code JGIT_BACKFILL_ON_STARTUP} — set to {@code true} to trigger
 * backfill automatically when the session factory is created
 * (default: {@code false})</li>
 * <li>{@code JGIT_EMBEDDING_BATCH_SIZE} — batch size for backfill processing
 * (default: {@code 100})</li>
 * </ul>
 */
public class EmbeddingBackfillService {

	private static final Logger LOG = Logger
			.getLogger(EmbeddingBackfillService.class.getName());

	/** Default batch size for backfill processing. */
	public static final int DEFAULT_BATCH_SIZE = 100;

	private EmbeddingBackfillService() {
		// utility class
	}

	/**
	 * Backfill embeddings for all entries without embeddings.
	 *
	 * @param sessionFactory
	 *            the Hibernate session factory
	 * @param embeddingService
	 *            the embedding service to generate vectors
	 * @param batchSize
	 *            number of entries to process per batch
	 * @return the number of entries updated
	 */
	public static int backfill(SessionFactory sessionFactory,
			EmbeddingService embeddingService, int batchSize) {
		if (!embeddingService.isAvailable()) {
			LOG.log(Level.INFO,
					"Embedding service not available — skipping backfill"); //$NON-NLS-1$
			return 0;
		}
		int totalUpdated = 0;
		List<JavaBlobIndex> batch;
		do {
			batch = fetchBatch(sessionFactory, batchSize);
			if (batch.isEmpty()) {
				break;
			}
			int updated = processBatch(sessionFactory, embeddingService,
					batch);
			totalUpdated += updated;
			LOG.log(Level.INFO,
					"Backfill progress: {0} entries updated so far", //$NON-NLS-1$
					Integer.valueOf(totalUpdated));
		} while (!batch.isEmpty());
		LOG.log(Level.INFO,
				"Backfill complete: {0} entries updated", //$NON-NLS-1$
				Integer.valueOf(totalUpdated));
		return totalUpdated;
	}

	/**
	 * Backfill embeddings using default batch size from environment or
	 * default.
	 *
	 * @param sessionFactory
	 *            the Hibernate session factory
	 * @param embeddingService
	 *            the embedding service to generate vectors
	 * @return the number of entries updated
	 */
	public static int backfill(SessionFactory sessionFactory,
			EmbeddingService embeddingService) {
		return backfill(sessionFactory, embeddingService,
				getBatchSizeFromEnv());
	}

	/**
	 * Check if backfill on startup is enabled.
	 *
	 * @return {@code true} if {@code JGIT_BACKFILL_ON_STARTUP} is set to
	 *         {@code true}
	 */
	public static boolean isBackfillOnStartupEnabled() {
		String val = System.getenv("JGIT_BACKFILL_ON_STARTUP"); //$NON-NLS-1$
		return "true".equalsIgnoreCase(val); //$NON-NLS-1$
	}

	private static List<JavaBlobIndex> fetchBatch(
			SessionFactory sessionFactory, int batchSize) {
		try (Session session = sessionFactory.openSession()) {
			return session.createQuery(
					"FROM JavaBlobIndex j WHERE j.hasEmbedding = false", //$NON-NLS-1$
					JavaBlobIndex.class)
					.setMaxResults(batchSize)
					.getResultList();
		}
	}

	private static int processBatch(SessionFactory sessionFactory,
			EmbeddingService embeddingService,
			List<JavaBlobIndex> entries) {
		int updated = 0;
		try (Session session = sessionFactory.openSession()) {
			session.beginTransaction();
			for (JavaBlobIndex entry : entries) {
				String embeddingText = EmbeddingService
						.buildEmbeddingText(
								entry.getSimpleClassName(),
								entry.getTypeDocumentation(),
								entry.getMethodSignatures(),
								entry.getPackageName());
				float[] embedding = embeddingService
						.embed(embeddingText);
				if (embedding != null) {
					JavaBlobIndex managed = session
							.merge(entry);
					managed.setSemanticEmbedding(embedding);
					managed.setHasEmbedding(true);
					updated++;
				}
			}
			session.getTransaction().commit();
		}
		return updated;
	}

	private static int getBatchSizeFromEnv() {
		String val = System.getenv("JGIT_EMBEDDING_BATCH_SIZE"); //$NON-NLS-1$
		if (val != null) {
			try {
				return Integer.parseInt(val);
			} catch (NumberFormatException e) {
				LOG.log(Level.WARNING,
						"Invalid JGIT_EMBEDDING_BATCH_SIZE value: {0} — using default {1}", //$NON-NLS-1$
						new Object[] { val,
								Integer.valueOf(DEFAULT_BATCH_SIZE) });
			}
		}
		return DEFAULT_BATCH_SIZE;
	}
}
