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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Properties;

import org.hibernate.SessionFactory;
import org.junit.Test;

/** Contracts for application-owned persistence lifecycle. */
public class ServerPersistenceContextTest {

	@Test
	public void exposesOnlyNativeHibernateContract() throws Exception {
		Method sessionFactory= ServerPersistenceContext.class.getMethod("sessionFactory"); //$NON-NLS-1$
		assertEquals(SessionFactory.class, sessionFactory.getReturnType());
		assertFalse(Modifier.isPublic(CopiedServerPersistenceContext.class.getModifiers()));
		for (Method method : ServerPersistenceContext.class.getMethods()) {
			assertFalse(isStorageImplementation(method.getReturnType()));
			for (Class<?> parameterType : method.getParameterTypes()) {
				assertFalse(isStorageImplementation(parameterType));
			}
			for (Class<?> exceptionType : method.getExceptionTypes()) {
				assertFalse(isStorageImplementation(exceptionType));
			}
		}
	}

	@Test
	public void ownsAndClosesNativeSessionFactory() {
		Properties properties= new Properties();
		properties.setProperty("hibernate.connection.url", //$NON-NLS-1$
				"jdbc:h2:mem:server-persistence-context;DB_CLOSE_DELAY=-1"); //$NON-NLS-1$
		properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.hbm2ddl.auto", "create-drop"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.search.backend.directory.type", "local-heap"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.cache.use_second_level_cache", "false"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("hibernate.connection.provider_class", //$NON-NLS-1$
				"org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl"); //$NON-NLS-1$

		ServerPersistenceContext context= HibernateConfig.createPersistenceContext(properties);
		SessionFactory sessionFactory= context.sessionFactory();
		assertSame(sessionFactory, context.sessionFactory());
		assertFalse(sessionFactory.isClosed());

		context.close();
		assertTrue(sessionFactory.isClosed());
		context.close();
	}

	private static boolean isStorageImplementation(Class<?> type) {
		String name= type.getName();
		return name.startsWith("org.eclipse.jgit.storage.hibernate") //$NON-NLS-1$
				|| name.startsWith("io.github.carstenartur.jgit.storage.hibernate"); //$NON-NLS-1$
	}
}
