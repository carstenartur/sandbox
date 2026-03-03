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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;

/**
 * Creates a Hibernate {@link HibernateSessionFactoryProvider} from environment
 * variables.
 * <p>
 * Supported environment variables:
 * <ul>
 * <li>{@code JGIT_DB_URL} — JDBC connection URL</li>
 * <li>{@code JGIT_DB_USER} — database username</li>
 * <li>{@code JGIT_DB_PASSWORD} — database password</li>
 * <li>{@code JGIT_DB_DIALECT} — Hibernate dialect class name</li>
 * <li>{@code JGIT_DB_DRIVER} — JDBC driver class name (optional)</li>
 * <li>{@code JGIT_DB_DDL_AUTO} — Hibernate DDL auto strategy (default:
 * update)</li>
 * </ul>
 */
public class HibernateConfig {

	private static final Logger LOG = Logger
			.getLogger(HibernateConfig.class.getName());

	private HibernateConfig() {
		// utility class
	}

	/**
	 * Create a session factory provider configured from environment variables.
	 *
	 * @return the configured session factory provider
	 */
	public static HibernateSessionFactoryProvider createSessionFactoryProvider() {
		Properties props = buildProperties();
		LOG.log(Level.INFO,
				"Creating Hibernate SessionFactory with URL: {0}", //$NON-NLS-1$
				props.getProperty("hibernate.connection.url")); //$NON-NLS-1$
		return new HibernateSessionFactoryProvider(props);
	}

	/**
	 * Create a session factory provider from explicit properties.
	 *
	 * @param properties
	 *            Hibernate configuration properties
	 * @return the configured session factory provider
	 */
	public static HibernateSessionFactoryProvider createSessionFactoryProvider(
			Properties properties) {
		return new HibernateSessionFactoryProvider(properties);
	}

	/**
	 * Build Hibernate properties from environment variables.
	 *
	 * @return the Hibernate configuration properties
	 */
	public static Properties buildProperties() {
		Properties props = new Properties();

		String url = getEnvOrDefault("JGIT_DB_URL", //$NON-NLS-1$
				"jdbc:h2:mem:jgit;DB_CLOSE_DELAY=-1"); //$NON-NLS-1$
		String user = getEnvOrDefault("JGIT_DB_USER", "sa"); //$NON-NLS-1$ //$NON-NLS-2$
		String password = getEnvOrDefault("JGIT_DB_PASSWORD", ""); //$NON-NLS-1$ //$NON-NLS-2$
		String dialect = getEnvOrDefault("JGIT_DB_DIALECT", //$NON-NLS-1$
				"org.hibernate.dialect.H2Dialect"); //$NON-NLS-1$
		String driver = System.getenv("JGIT_DB_DRIVER"); //$NON-NLS-1$
		String ddlAuto = getEnvOrDefault("JGIT_DB_DDL_AUTO", "update"); //$NON-NLS-1$ //$NON-NLS-2$

		props.put("hibernate.connection.url", url); //$NON-NLS-1$
		props.put("hibernate.connection.username", user); //$NON-NLS-1$
		props.put("hibernate.connection.password", password); //$NON-NLS-1$
		props.put("hibernate.dialect", dialect); //$NON-NLS-1$
		props.put("hibernate.hbm2ddl.auto", ddlAuto); //$NON-NLS-1$

		if (driver != null && !driver.isEmpty()) {
			props.put("hibernate.connection.driver_class", driver); //$NON-NLS-1$
		}

		// Reasonable defaults for connection pooling
		props.put("hibernate.connection.pool_size", "10"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.show_sql", "false"); //$NON-NLS-1$ //$NON-NLS-2$

		// Apply Hibernate Search / Elasticsearch configuration
		ElasticsearchConfig.applySearchProperties(props);

		return props;
	}

	private static String getEnvOrDefault(String name, String defaultValue) {
		String val = System.getenv(name);
		return (val != null && !val.isEmpty()) ? val : defaultValue;
	}
}
