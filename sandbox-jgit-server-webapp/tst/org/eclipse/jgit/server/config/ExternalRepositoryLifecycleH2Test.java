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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.junit.Test;

/** Real H2 lifecycle coverage for the released Core-backed repository adapter. */
public class ExternalRepositoryLifecycleH2Test {

	private static final AtomicInteger DATABASE_COUNTER= new AtomicInteger();
	private static final String MAIN_REF= "refs/heads/main"; //$NON-NLS-1$

	@Test
	public void normalizesIsolatesAndReopensRepositories() throws Exception {
		String url= databaseUrl();
		ObjectId storedObject;
		Properties createProperties= properties(url, "create"); //$NON-NLS-1$

		try (ServerPersistenceContext context=
				new ExternalServerPersistenceContext(createProperties)) {
			SandboxRepositoryService repositories= context.repositoryService();
			Repository first= repositories.openOrCreate("/first.git"); //$NON-NLS-1$
			Repository alias= repositories.openOrCreate("first"); //$NON-NLS-1$
			Repository second= repositories.openOrCreate("second"); //$NON-NLS-1$

			assertSame(first, alias);
			assertTrue(repositories.isOpen("first.git")); //$NON-NLS-1$
			assertTrue(repositories.isOpen("/second")); //$NON-NLS-1$

			try (ObjectInserter inserter= first.newObjectInserter()) {
				storedObject= inserter.insert(Constants.OBJ_BLOB,
						"external-core-lifecycle".getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
				inserter.flush();
			}
			RefUpdate update= first.updateRef(MAIN_REF);
			update.setNewObjectId(storedObject);
			assertEquals(RefUpdate.Result.NEW, update.update());
			assertNull(second.exactRef(MAIN_REF));
		}

		Properties validateProperties= properties(url, "validate"); //$NON-NLS-1$
		try (ServerPersistenceContext context=
				new ExternalServerPersistenceContext(validateProperties)) {
			Repository reopened= context.repositoryService().openOrCreate("first.git"); //$NON-NLS-1$
			Repository isolated= context.repositoryService().openOrCreate("second"); //$NON-NLS-1$

			assertEquals(storedObject, reopened.resolve(MAIN_REF));
			assertNull(isolated.exactRef(MAIN_REF));
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
		return "jdbc:h2:mem:external-repository-lifecycle-" //$NON-NLS-1$
				+ DATABASE_COUNTER.incrementAndGet() + ";DB_CLOSE_DELAY=-1"; //$NON-NLS-1$
	}
}
