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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * <li>{@code JGIT_DB_PASSWORD_FILE} — path to a file containing the
 * database password (Docker secrets pattern). Takes precedence over
 * {@code JGIT_DB_PASSWORD}.</li>
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
		String password = readPasswordFromFileOrEnv();
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

	/**
	 * Read the database password from a file (Docker secrets pattern) or fall
	 * back to the environment variable.
	 * <p>
	 * If {@code JGIT_DB_PASSWORD_FILE} is set and the file exists, the
	 * password is read from the file (leading/trailing whitespace stripped).
	 * Otherwise, {@code JGIT_DB_PASSWORD} is used.
	 *
	 * @return the database password, or empty string if not configured
	 */
	private static String readPasswordFromFileOrEnv() {
		String passwordFile = System.getenv("JGIT_DB_PASSWORD_FILE"); //$NON-NLS-1$
		if (passwordFile != null && !passwordFile.isEmpty()) {
			try {
				Path path = Path.of(passwordFile);
				long size = Files.size(path);
				if (size > 4096) {
					LOG.log(Level.WARNING,
							"Password file too large ({0} bytes), ignoring: {1}", //$NON-NLS-1$
							new Object[] { Long.toString(size),
									passwordFile });
				} else {
					return Files.readString(path,
							StandardCharsets.UTF_8).trim();
				}
			} catch (IOException e) {
				LOG.log(Level.WARNING,
						"Failed to read password from file: {0}", //$NON-NLS-1$
						passwordFile);
			}
		}
		return getEnvOrDefault("JGIT_DB_PASSWORD", ""); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
