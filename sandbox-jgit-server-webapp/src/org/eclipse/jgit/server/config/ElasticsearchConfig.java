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
package org.eclipse.jgit.server.config;

import java.util.Properties;

/**
 * Configures Hibernate Search backend from environment variables.
 * <p>
 * Supported environment variables:
 * <ul>
 * <li>{@code JGIT_SEARCH_BACKEND} — backend type: {@code "lucene"}
 * (default, in-memory) or {@code "elasticsearch"}</li>
 * <li>{@code JGIT_SEARCH_HOSTS} — Elasticsearch host URL(s), e.g.
 * {@code "http://elasticsearch:9200"} (only used when backend is
 * {@code "elasticsearch"})</li>
 * </ul>
 * <p>
 * When the backend is {@code "elasticsearch"}, the following Hibernate Search
 * properties are set:
 * <ul>
 * <li>{@code hibernate.search.backend.type} = {@code "elasticsearch"}</li>
 * <li>{@code hibernate.search.backend.hosts} = value of
 * {@code JGIT_SEARCH_HOSTS}</li>
 * </ul>
 * <p>
 * When the backend is {@code "lucene"} (or not set), the default in-memory
 * Lucene backend is used via {@code local-heap} directory type.
 */
public class ElasticsearchConfig {

	private ElasticsearchConfig() {
		// utility class
	}

	/**
	 * Apply Hibernate Search configuration properties based on environment
	 * variables.
	 *
	 * @param props
	 *            the Hibernate properties to augment with search backend
	 *            configuration
	 */
	public static void applySearchProperties(Properties props) {
		applySearchProperties(props, System.getenv("JGIT_SEARCH_BACKEND"), //$NON-NLS-1$
				System.getenv("JGIT_SEARCH_HOSTS")); //$NON-NLS-1$
	}

	/**
	 * Apply Hibernate Search configuration properties from explicit values.
	 * <p>
	 * This overload is useful for unit testing without requiring environment
	 * variable manipulation.
	 *
	 * @param props
	 *            the Hibernate properties to augment with search backend
	 *            configuration
	 * @param backendType
	 *            the backend type ({@code "lucene"} or
	 *            {@code "elasticsearch"}), may be {@code null}
	 * @param hosts
	 *            the Elasticsearch host URL(s), may be {@code null}
	 */
	public static void applySearchProperties(Properties props,
			String backendType, String hosts) {
		String backend = (backendType != null && !backendType.isEmpty())
				? backendType
				: "lucene"; //$NON-NLS-1$

		if ("elasticsearch".equalsIgnoreCase(backend)) { //$NON-NLS-1$
			props.put("hibernate.search.backend.type", //$NON-NLS-1$
					"elasticsearch"); //$NON-NLS-1$
			if (hosts != null && !hosts.isEmpty()) {
				props.put("hibernate.search.backend.hosts", hosts); //$NON-NLS-1$
			}
		}
		// When backend is "lucene" (default), HibernateSessionFactoryProvider
		// already defaults to lucene + local-heap, so no action needed.
	}

}
