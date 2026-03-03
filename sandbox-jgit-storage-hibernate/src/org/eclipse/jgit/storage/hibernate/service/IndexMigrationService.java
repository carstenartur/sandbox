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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.hibernate.search.mapper.orm.session.SearchSession;

/**
 * Service for re-indexing Hibernate Search entities.
 * <p>
 * Wraps Hibernate Search's {@link MassIndexer} to re-index all entities or
 * specific entity types. This is needed after schema changes or analyzer
 * updates.
 * </p>
 */
public class IndexMigrationService {

	private static final Logger LOG = Logger
			.getLogger(IndexMigrationService.class.getName());

	private final SessionFactory sessionFactory;

	/**
	 * Create a new index migration service.
	 *
	 * @param sessionFactory
	 *            the Hibernate session factory
	 */
	public IndexMigrationService(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * Re-index all Hibernate Search entities.
	 *
	 * @throws InterruptedException
	 *             if the indexing is interrupted
	 */
	public void reindexAll() throws InterruptedException {
		LOG.log(Level.INFO, "Starting full re-index of all entities"); //$NON-NLS-1$
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			MassIndexer indexer = searchSession.massIndexer();
			indexer.startAndWait();
		}
		LOG.log(Level.INFO, "Full re-index completed"); //$NON-NLS-1$
	}

	/**
	 * Re-index a specific entity type.
	 *
	 * @param entityClass
	 *            the entity class to re-index
	 * @throws InterruptedException
	 *             if the indexing is interrupted
	 */
	public void reindexEntity(Class<?> entityClass)
			throws InterruptedException {
		LOG.log(Level.INFO, "Starting re-index of {0}", //$NON-NLS-1$
				entityClass.getSimpleName());
		try (Session session = sessionFactory.openSession()) {
			SearchSession searchSession = Search.session(session);
			MassIndexer indexer = searchSession.massIndexer(entityClass);
			indexer.startAndWait();
		}
		LOG.log(Level.INFO, "Re-index of {0} completed", //$NON-NLS-1$
				entityClass.getSimpleName());
	}
}
