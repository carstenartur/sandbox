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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.repository.ExternalHibernateRepositoryService;
import org.hibernate.SessionFactory;
import org.junit.Test;

import io.github.carstenartur.jgit.storage.hibernate.search.SearchEntities;
import io.github.carstenartur.jgit.storage.hibernate.search.analysis.GitTextAnalysis;
import io.github.carstenartur.jgit.storage.hibernate.search.service.GitHistorySearchService;
import io.github.carstenartur.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;

/** Integration contracts for the prepared external-Core persistence context. */
public class ExternalServerPersistenceContextTest {

	private static final AtomicInteger DATABASE_COUNTER= new AtomicInteger();

	@Test
	public void preservesExplicitTextProfilesWithOrWithoutAConfigurer() {
		for (boolean customConfigurer : List.of(false, true)) {
			Properties properties= properties(databaseUrl(), "create-drop");
			if (!customConfigurer) {
				properties.remove(GitTextAnalysis.BACKEND_CONFIGURER_PROPERTY);
			}
			properties.setProperty(GitTextAnalysis.PROFILE_ID_PROPERTY, "neutral-standard-v1");
			try (ServerPersistenceContext context= new ExternalServerPersistenceContext(properties)) {
				assertEquals("neutral-standard-v1", context.sessionFactory().getProperties()
						.get(GitTextAnalysis.PROFILE_ID_PROPERTY));
				assertEquals("class:org.eclipse.jgit.storage.hibernate.search.JavaSourceAnalysisConfigurer",
						context.sessionFactory().getProperties().get(GitTextAnalysis.BACKEND_CONFIGURER_PROPERTY));
				context.historySearch().checkSearch();
			}
		}
	}

	@Test
	public void closesOwnedFactoryWhenSearchConfigurationRejectsStartup() {
		Properties properties= properties(databaseUrl(), "create-drop");
		properties.setProperty(GitHistorySearchService.MAX_OFFSET_PROPERTY, "invalid");
		try (var owner= new HibernateSessionFactoryProvider(properties, SearchEntities.annotatedClasses())) {
			SessionFactory sessionFactory= owner.getSessionFactory();
			assertThrows(IllegalArgumentException.class, () -> new ExternalServerPersistenceContext(owner));
			assertTrue("A rejected context must release its already-created factory", sessionFactory.isClosed());
		}
	}

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
					"io.github.carstenartur.jgit.storage.hibernate.entity.GitRepositoryLifecycleEntity", //$NON-NLS-1$
					"io.github.carstenartur.jgit.storage.hibernate.entity.GitRepositoryLockEntity", //$NON-NLS-1$
					"io.github.carstenartur.jgit.storage.hibernate.search.entity.GitCommitIndex", //$NON-NLS-1$
					"org.eclipse.jgit.storage.hibernate.entity.FilePathHistory", //$NON-NLS-1$
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
		Properties createProperties= properties(url, "update");
		ReleasedSchemaHistoryTest.core(createProperties).migrate();
		ReleasedSchemaHistoryTest.search(createProperties).migrate(); //$NON-NLS-1$
		try (ServerPersistenceContext ignored=
				new ExternalServerPersistenceContext(createProperties)) {
			// Disposable transition fixture adds the two remaining copied Java projections.
		}

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

}
