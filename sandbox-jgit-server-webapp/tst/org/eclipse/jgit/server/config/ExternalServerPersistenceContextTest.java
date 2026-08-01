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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.repository.ExternalHibernateRepositoryService;
import org.hibernate.SessionFactory;
import org.junit.Test;

import io.github.carstenartur.jgit.storage.hibernate.schema.CoreSchemaMigrations;

/** Integration contracts for the prepared external-Core persistence context. */
public class ExternalServerPersistenceContextTest {

	private static final AtomicInteger DATABASE_COUNTER= new AtomicInteger();

	@Test
	public void registersReleasedCoreAndOnlyCompatibleSandboxProjections()
			throws Exception {
		Properties properties= properties(databaseUrl(), "create-drop"); //$NON-NLS-1$
		SessionFactory sessionFactory;

		try (ServerPersistenceContext context=
				new ExternalServerPersistenceContext(properties)) {
			sessionFactory= context.sessionFactory();
			List<String> entityNames= sessionFactory.getMetamodel().getEntities()
					.stream().map(entity -> entity.getJavaType().getName())
					.sorted().toList();

			assertEquals(List.of(
					"io.github.carstenartur.jgit.storage.hibernate.entity.GitPackChunkEntity", //$NON-NLS-1$
					"io.github.carstenartur.jgit.storage.hibernate.entity.GitPackEntity", //$NON-NLS-1$
					"io.github.carstenartur.jgit.storage.hibernate.entity.GitReflogEntity", //$NON-NLS-1$
					"io.github.carstenartur.jgit.storage.hibernate.entity.GitRepositoryLockEntity", //$NON-NLS-1$
					"org.eclipse.jgit.storage.hibernate.entity.FilePathHistory", //$NON-NLS-1$
					"org.eclipse.jgit.storage.hibernate.entity.GitCommitIndex", //$NON-NLS-1$
					"org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex"), //$NON-NLS-1$
					entityNames);
			assertFalse(entityNames.contains(
					"org.eclipse.jgit.storage.hibernate.entity.GitPackEntity")); //$NON-NLS-1$
			assertFalse(entityNames.contains(
					"org.eclipse.jgit.storage.hibernate.entity.GitReflogEntity")); //$NON-NLS-1$
			assertFalse(entityNames.contains(
					"org.eclipse.jgit.storage.hibernate.entity.GitObjectEntity")); //$NON-NLS-1$
			assertFalse(entityNames.contains(
					"org.eclipse.jgit.storage.hibernate.entity.GitRefEntity")); //$NON-NLS-1$
			assertTrue(context.repositoryService()
					instanceof ExternalHibernateRepositoryService);

			Repository repository= context.repositoryService()
					.openOrCreate("external-context"); //$NON-NLS-1$
			assertNotNull(repository);
			assertTrue(repository.isBare());
			assertFalse(sessionFactory.isClosed());
		}

		assertTrue(sessionFactory.isClosed());
	}

	@Test
	public void guardedFactoryStartsOnlyAfterSchemaAndHistoryExist()
			throws Exception {
		String url= databaseUrl();
		Properties createProperties= properties(url, "create"); //$NON-NLS-1$
		try (ServerPersistenceContext ignored=
				new ExternalServerPersistenceContext(createProperties)) {
			// Disposable test bootstrap creates the exact mapped schema.
		}
		createSuccessfulHistory(createProperties);

		Properties validateProperties= properties(url, "validate"); //$NON-NLS-1$
		try (ServerPersistenceContext context= HibernateConfig
				.createExternalPersistenceContext(validateProperties)) {
			assertNotNull(context.repositoryService()
					.openOrCreate("guarded-external-context")); //$NON-NLS-1$
		}
	}

	private static Properties properties(String url, String ddlAuto) {
		Properties properties= new Properties();
		properties.setProperty("hibernate.connection.url", url); //$NON-NLS-1$
		properties.setProperty("hibernate.connection.username", "sa"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.connection.password", ""); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.hbm2ddl.auto", ddlAuto); //$NON-NLS-1$
		properties.setProperty("hibernate.show_sql", "false"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.search.backend.type", "lucene"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.search.backend.directory.type", "local-heap"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.search.backend.analysis.configurer", //$NON-NLS-1$
				"class:org.eclipse.jgit.storage.hibernate.search.JavaSourceAnalysisConfigurer"); //$NON-NLS-1$
		properties.setProperty("hibernate.cache.use_second_level_cache", "false"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.connection.provider_class", //$NON-NLS-1$
				"org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl"); //$NON-NLS-1$
		return properties;
	}

	private static String databaseUrl() {
		return "jdbc:h2:mem:external-server-context-" //$NON-NLS-1$
				+ DATABASE_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1"; //$NON-NLS-1$
	}

	private static void createSuccessfulHistory(Properties properties)
			throws Exception {
		Class.forName(properties.getProperty("hibernate.connection.driver_class")); //$NON-NLS-1$
		try (Connection connection= DriverManager.getConnection(
				properties.getProperty("hibernate.connection.url"), //$NON-NLS-1$
				properties.getProperty("hibernate.connection.username"), //$NON-NLS-1$
				properties.getProperty("hibernate.connection.password")); //$NON-NLS-1$
				Statement statement= connection.createStatement()) {
			statement.execute("CREATE TABLE " //$NON-NLS-1$
					+ CoreSchemaMigrations.SCHEMA_HISTORY_TABLE
					+ " (installed_rank INT PRIMARY KEY, version VARCHAR(50), success BOOLEAN NOT NULL)"); //$NON-NLS-1$
			statement.execute("INSERT INTO " //$NON-NLS-1$
					+ CoreSchemaMigrations.SCHEMA_HISTORY_TABLE
					+ " (installed_rank, version, success) VALUES (1, '0.1.15', TRUE)"); //$NON-NLS-1$
		}
	}
}
