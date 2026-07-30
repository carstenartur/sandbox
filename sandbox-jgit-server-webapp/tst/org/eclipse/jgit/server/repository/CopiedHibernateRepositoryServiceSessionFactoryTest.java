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
package org.eclipse.jgit.server.repository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Properties;

import org.eclipse.jgit.server.config.HibernateConfig;
import org.eclipse.jgit.server.config.ServerPersistenceContext;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.hibernate.SessionFactory;
import org.junit.Test;

/** Verifies repository-handle ownership separately from persistence ownership. */
public class CopiedHibernateRepositoryServiceSessionFactoryTest {

	@Test
	public void closesRepositoriesWithoutClosingApplicationSessionFactory() throws Exception {
		Properties properties= new Properties();
		properties.setProperty("hibernate.connection.url", //$NON-NLS-1$
				"jdbc:h2:mem:copied-service-native-factory;DB_CLOSE_DELAY=-1"); //$NON-NLS-1$
		properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.hbm2ddl.auto", "create-drop"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.search.backend.directory.type", "local-heap"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.cache.use_second_level_cache", "false"); //$NON-NLS-1$ //$NON-NLS-2$

		SessionFactory sessionFactory;
		try (ServerPersistenceContext context= HibernateConfig.createPersistenceContext(properties)) {
			sessionFactory= context.sessionFactory();
			HibernateSessionFactoryProvider provider= CopiedHibernateRepositoryService.nonOwningProvider(sessionFactory);
			assertSame(sessionFactory, provider.getSessionFactory());
			provider.close();
			assertFalse(sessionFactory.isClosed());

			CopiedHibernateRepositoryService repositories= new CopiedHibernateRepositoryService(sessionFactory);
			assertNotNull(repositories.openOrCreate("native-factory")); //$NON-NLS-1$

			repositories.close();
			assertFalse(sessionFactory.isClosed());
		}
		assertTrue(sessionFactory.isClosed());
	}
}
