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
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.util.Properties;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.storage.hibernate.search.EmbeddingService;
import org.eclipse.jgit.storage.hibernate.service.GitDatabaseQueryService;

/**
 * Singleton service that manages the embedded HSQLDB database and Lucene index
 * for Git repository indexing. All data access runs in-process — no REST, no
 * HikariCP, no network communication.
 *
 * <h2>Architecture</h2>
 *
 * <pre>
 * Eclipse JVM (single process)
 * ├── EmbeddedSearchService (this class — lazy singleton)
 * │   ├── HibernateSessionFactoryProvider → HSQLDB file: (in-process JDBC)
 * │   ├── GitDatabaseQueryService         → Hibernate Search → Lucene (local-filesystem)
 * │   └── CommitIndexer + BlobIndexer     → writes to HSQLDB + Lucene
 * └── Eclipse Views
 *     ├── GitSearchView       → calls queryService.searchBySymbol()
 *     ├── JavaTypeHistoryView → calls queryService.getFileHistory()
 *     └── CommitAnalyticsView → calls queryService.getAuthorStatistics()
 * </pre>
 *
 * <h2>Why HSQLDB Embedded</h2>
 * <ul>
 * <li>No external server process required</li>
 * <li>{@code jdbc:hsqldb:file:} runs as direct JVM method calls (~0ms
 * latency)</li>
 * <li>No connection pool needed — Hibernate's built-in
 * {@code DriverManagerConnectionProviderImpl} suffices</li>
 * <li>No JSON serialization — Java objects used directly</li>
 * <li>Lucene index stored on local filesystem in Eclipse state location</li>
 * </ul>
 *
 * <h2>Phase 1b Status</h2>
 * <p>
 * This class provides the configuration infrastructure. Actual Hibernate/HSQLDB
 * wiring requires bundling {@code sandbox-jgit-storage-hibernate} as an OSGi
 * dependency (tracked in TODO.md Phase 1b).
 * </p>
 *
 * @see RepositoryIndexService
 * @see IncrementalIndexer
 */
public class EmbeddedSearchService {

	private static final ILog LOG= Platform.getLog(EmbeddedSearchService.class);

	private static EmbeddedSearchService instance;

	private boolean initialized;

	private HibernateSessionFactoryProvider provider;

	private GitDatabaseQueryService queryService;

	private EmbeddingService embeddingService;

	private EmbeddedSearchService() {
		// singleton
	}

	/**
	 * Returns the singleton instance, creating it on first access.
	 *
	 * @return the embedded search service instance
	 */
	public static synchronized EmbeddedSearchService getInstance() {
		if (instance == null) {
			instance= new EmbeddedSearchService();
		}
		return instance;
	}

	/**
	 * Initializes the embedded HSQLDB database and Hibernate Search / Lucene
	 * index. Safe to call multiple times — subsequent calls are no-ops.
	 *
	 * <p>
	 * Phase 1b: Once {@code sandbox-jgit-storage-hibernate} is available as an
	 * OSGi bundle, this method will create the
	 * {@code HibernateSessionFactoryProvider} and
	 * {@code GitDatabaseQueryService}.
	 * </p>
	 *
	 * @param stateLocation the Eclipse plugin state location for database files
	 */
	public void initialize(IPath stateLocation) {
		if (initialized) {
			return;
		}
		Properties props= buildHsqldbProperties(stateLocation);
		LOG.info("Git Database Index: HSQLDB embedded configured at " //$NON-NLS-1$
				+ props.getProperty("hibernate.connection.url")); //$NON-NLS-1$
		try {
			provider= new HibernateSessionFactoryProvider(props);
			queryService= new GitDatabaseQueryService(provider.getSessionFactory());
			embeddingService= new EmbeddingService();
			queryService.setEmbeddingService(embeddingService);
		} catch (Exception e) {
			LOG.error("Git Database Index: Failed to initialize Hibernate/HSQLDB: " + e.getMessage(), e); //$NON-NLS-1$
		}
		initialized= true;
	}

	/**
	 * Builds Hibernate properties for HSQLDB embedded (in-process) mode. No
	 * HikariCP, no network, no external server.
	 *
	 * @param stateLocation the Eclipse plugin state location
	 * @return configured Hibernate properties
	 */
	static Properties buildHsqldbProperties(IPath stateLocation) {
		Properties props= new Properties();

		// HSQLDB embedded — in-process, no network, no TCP
		props.put("hibernate.connection.url", //$NON-NLS-1$
				"jdbc:hsqldb:file:" + stateLocation.append("hsqldb").append("gitindex") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						+ ";shutdown=true"); //$NON-NLS-1$
		props.put("hibernate.connection.driver_class", //$NON-NLS-1$
				"org.hsqldb.jdbc.JDBCDriver"); //$NON-NLS-1$
		props.put("hibernate.dialect", //$NON-NLS-1$
				"org.hibernate.dialect.HSQLDialect"); //$NON-NLS-1$
		props.put("hibernate.connection.username", "sa"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.connection.password", ""); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.hbm2ddl.auto", "update"); //$NON-NLS-1$ //$NON-NLS-2$

		// No connection pool — HSQLDB embedded = direct method calls in same JVM
		// Hibernate's built-in DriverManagerConnectionProvider is sufficient
		props.put("hibernate.connection.provider_class", //$NON-NLS-1$
				"org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl"); //$NON-NLS-1$

		// Lucene on local filesystem — persistent across Eclipse restarts
		props.put("hibernate.search.backend.type", "lucene"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.search.backend.directory.type", //$NON-NLS-1$
				"local-filesystem"); //$NON-NLS-1$
		props.put("hibernate.search.backend.directory.root", //$NON-NLS-1$
				stateLocation.append("lucene-index").toOSString()); //$NON-NLS-1$

		// No second-level cache needed — HSQLDB caches internally
		props.put("hibernate.cache.use_second_level_cache", "false"); //$NON-NLS-1$ //$NON-NLS-2$

		return props;
	}

	/**
	 * Returns whether the service has been initialized.
	 *
	 * @return {@code true} if initialized
	 */
	public boolean isInitialized() {
		return initialized;
	}

	/**
	 * Shuts down the embedded database and releases all resources. Called when
	 * the plugin is stopped.
	 */
	public void shutdown() {
		if (!initialized) {
			return;
		}
		if (provider != null) {
			provider.close();
			provider= null;
		}
		queryService= null;
		embeddingService= null;
		initialized= false;
		LOG.info("Git Database Index: Embedded search service shut down"); //$NON-NLS-1$
	}

	/**
	 * Returns the query service, or {@code null} if not initialized.
	 *
	 * @return the {@link GitDatabaseQueryService}, or {@code null}
	 */
	public GitDatabaseQueryService getQueryService() {
		return queryService;
	}

	/**
	 * Returns the embedding service, or {@code null} if not initialized.
	 *
	 * @return the {@link EmbeddingService}, or {@code null}
	 */
	public EmbeddingService getEmbeddingService() {
		return embeddingService;
	}

	/**
	 * Returns the Hibernate session factory provider, or {@code null} if not
	 * initialized.
	 *
	 * @return the {@link HibernateSessionFactoryProvider}, or {@code null}
	 */
	public HibernateSessionFactoryProvider getProvider() {
		return provider;
	}
}
