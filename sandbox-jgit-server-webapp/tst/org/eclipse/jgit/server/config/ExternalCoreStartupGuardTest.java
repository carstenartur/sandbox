/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.config;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import io.github.carstenartur.jgit.storage.hibernate.schema.CoreSchemaMigrations;

/** Tests the read-only Flyway-history and validate-mode gate. */
public class ExternalCoreStartupGuardTest {

	private static final AtomicInteger DATABASE_COUNTER= new AtomicInteger();

	@Test
	public void rejectsAnyHibernateSchemaMutationModeBeforeConnecting() {
		Properties properties= properties("jdbc:invalid:not-opened"); //$NON-NLS-1$
		properties.setProperty("hibernate.hbm2ddl.auto", "update"); //$NON-NLS-1$ //$NON-NLS-2$

		IllegalStateException failure= assertThrows(IllegalStateException.class,
				() -> ExternalCoreStartupGuard.requireReady(properties));

		assertTrue(failure.getMessage().contains("hbm2ddl.auto=validate")); //$NON-NLS-1$
	}

	@Test
	public void rejectsDatabaseWithoutNormalCoreHistory() {
		Properties properties= properties(databaseUrl());

		IllegalStateException failure= assertThrows(IllegalStateException.class,
				() -> ExternalCoreStartupGuard.requireReady(properties));

		assertTrue(failure.getMessage().contains(
				CoreSchemaMigrations.SCHEMA_HISTORY_TABLE));
	}

	@Test
	public void rejectsUnsuccessfulFlywayHistoryRow() throws Exception {
		Properties properties= properties(databaseUrl());
		createHistory(properties, false);

		IllegalStateException failure= assertThrows(IllegalStateException.class,
				() -> ExternalCoreStartupGuard.requireReady(properties));

		assertTrue(failure.getMessage().contains("unsuccessful migration")); //$NON-NLS-1$
	}

	@Test
	public void acceptsSuccessfulVersionedCoreHistory() throws Exception {
		Properties properties= properties(databaseUrl());
		createHistory(properties, true);

		ExternalCoreStartupGuard.requireReady(properties);
	}

	@Test
	public void acceptsMetadataQuotedConfiguredSchema() throws Exception {
		Properties properties= properties(databaseUrl());
		String schema= "tenant\"one"; //$NON-NLS-1$
		properties.setProperty("hibernate.default_schema", schema); //$NON-NLS-1$
		Class.forName(properties.getProperty("hibernate.connection.driver_class")); //$NON-NLS-1$
		try (Connection connection= connection(properties);
				Statement statement= connection.createStatement()) {
			String quotedSchema= '"' + schema.replace("\"", "\"\"") + '"'; //$NON-NLS-1$ //$NON-NLS-2$
			statement.execute("CREATE SCHEMA " + quotedSchema); //$NON-NLS-1$
			statement.execute("CREATE TABLE " + quotedSchema + '.' //$NON-NLS-1$
					+ CoreSchemaMigrations.SCHEMA_HISTORY_TABLE
					+ " (installed_rank INT PRIMARY KEY, version VARCHAR(50), success BOOLEAN NOT NULL)"); //$NON-NLS-1$
			statement.execute("INSERT INTO " + quotedSchema + '.' //$NON-NLS-1$
					+ CoreSchemaMigrations.SCHEMA_HISTORY_TABLE
					+ " (installed_rank, version, success) VALUES (1, '0.1.15', TRUE)"); //$NON-NLS-1$
		}

		ExternalCoreStartupGuard.requireReady(properties);
	}

	private static Properties properties(String url) {
		Properties properties= new Properties();
		properties.setProperty("hibernate.connection.url", url); //$NON-NLS-1$
		properties.setProperty("hibernate.connection.username", "sa"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.connection.password", ""); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.hbm2ddl.auto", "validate"); //$NON-NLS-1$ //$NON-NLS-2$
		return properties;
	}

	private static String databaseUrl() {
		return "jdbc:h2:mem:external-core-history-" //$NON-NLS-1$
				+ DATABASE_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1"; //$NON-NLS-1$
	}

	private static void createHistory(Properties properties, boolean success)
			throws Exception {
		Class.forName(properties.getProperty("hibernate.connection.driver_class")); //$NON-NLS-1$
		try (Connection connection= connection(properties);
				Statement statement= connection.createStatement()) {
			statement.execute("CREATE TABLE " //$NON-NLS-1$
					+ CoreSchemaMigrations.SCHEMA_HISTORY_TABLE
					+ " (installed_rank INT PRIMARY KEY, version VARCHAR(50), success BOOLEAN NOT NULL)"); //$NON-NLS-1$
			statement.execute("INSERT INTO " //$NON-NLS-1$
					+ CoreSchemaMigrations.SCHEMA_HISTORY_TABLE
					+ " (installed_rank, version, success) VALUES (1, '0.1.15', " //$NON-NLS-1$
					+ success + ')');
		}
	}

	private static Connection connection(Properties properties) throws Exception {
		return DriverManager.getConnection(
				properties.getProperty("hibernate.connection.url"), //$NON-NLS-1$
				properties.getProperty("hibernate.connection.username"), //$NON-NLS-1$
				properties.getProperty("hibernate.connection.password")); //$NON-NLS-1$
	}
}
