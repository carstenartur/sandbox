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

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;

/**
 * Singleton service that manages the {@link SemanticSearchClient} connecting to
 * the {@code sandbox-jgit-server-webapp} REST backend.
 *
 * <h2>Architecture</h2>
 *
 * <pre>
 * Eclipse Plugin (sandbox_extra_search)
 * └── EmbeddedSearchService (this class — lazy singleton)
 *     └── SemanticSearchClient → REST HTTP calls → sandbox-jgit-server-webapp
 *         ├── GitSearchView       → searchBySymbol / searchByType / ...
 *         ├── JavaTypeHistoryView → getFileHistory()
 *         └── CommitAnalyticsView → getAuthorStatistics()
 * </pre>
 *
 * <p>
 * No dependency on Hibernate, DJL, Lucene, HSQLDB, or Jakarta Persistence.
 * Start the backend with: {@code cd sandbox-jgit-server-webapp &amp;&amp; mvn spring-boot:run}
 * </p>
 *
 * @see SemanticSearchClient
 * @see RepositoryIndexService
 */
public class EmbeddedSearchService {

	private static final ILog LOG= Platform.getLog(EmbeddedSearchService.class);

	private static EmbeddedSearchService instance;

	private boolean initialized;

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
	 * Initializes the REST search client. Safe to call multiple times —
	 * subsequent calls are no-ops.
	 *
	 * @param stateLocation the Eclipse plugin state location (unused, kept for
	 *            API compatibility)
	 */
	public void initialize(IPath stateLocation) {
		if (initialized) {
			return;
		}
		SemanticSearchClient.getInstance();
		LOG.info("Git Search: REST client initialized (backend: " //$NON-NLS-1$
				+ SemanticSearchClient.DEFAULT_BASE_URL + ")"); //$NON-NLS-1$
		initialized= true;
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
	 * Returns whether the REST backend is reachable.
	 *
	 * @return {@code true} if the health endpoint responds with HTTP 200
	 */
	public boolean isAvailable() {
		return initialized && SemanticSearchClient.getInstance().isAvailable();
	}

	/**
	 * Shuts down the service and releases resources. Called when the plugin is
	 * stopped.
	 */
	public void shutdown() {
		if (!initialized) {
			return;
		}
		initialized= false;
		LOG.info("Git Search: REST search service shut down"); //$NON-NLS-1$
	}

	/**
	 * Returns the REST search client, or {@code null} if not initialized.
	 *
	 * @return the {@link SemanticSearchClient}, or {@code null}
	 */
	public SemanticSearchClient getSearchClient() {
		if (!initialized) {
			return null;
		}
		return SemanticSearchClient.getInstance();
	}
}
