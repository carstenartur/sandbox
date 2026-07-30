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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.JGitServerApplication;
import org.eclipse.jgit.server.rest.AdminResource;
import org.eclipse.jgit.server.rest.AnalyticsResource;
import org.eclipse.jgit.server.rest.HealthResource;
import org.eclipse.jgit.server.rest.RepositoryResource;
import org.eclipse.jgit.server.resolver.HibernateRepositoryResolver;
import org.hibernate.SessionFactory;
import org.junit.Test;

import io.github.carstenartur.jgit.storage.hibernate.HibernateRepositoryFactory;

/** Contract tests for the application-owned repository lifecycle boundary. */
public class SandboxRepositoryServiceBoundaryTest {

	@Test
	public void exposesPublicJGitRepositoryContract() throws Exception {
		Method open= SandboxRepositoryService.class.getMethod("openOrCreate", String.class); //$NON-NLS-1$
		assertEquals(Repository.class, open.getReturnType());
		assertNotNull(RepositoryResource.class.getConstructor(SandboxRepositoryService.class));
		assertNotNull(HibernateRepositoryResolver.class.getConstructor(SandboxRepositoryService.class));
		assertNotNull(HibernateRepositoryResolver.class.getConstructor(SessionFactory.class));
		assertNotNull(HealthResource.class.getConstructor(SessionFactory.class));
		assertNotNull(AnalyticsResource.class.getConstructor(SessionFactory.class));
		assertNotNull(AdminResource.class.getConstructor(SessionFactory.class));
		assertNotNull(ExternalHibernateRepositoryService.class.getConstructor(HibernateRepositoryFactory.class));
		assertEquals(SessionFactory.class,
				JGitServerApplication.class.getMethod("getSessionFactory").getReturnType()); //$NON-NLS-1$
	}

	@Test
	public void applicationAndNativeResourcesHaveNoCopiedBackendField() {
		assertFalse(hasCopiedBackendField(JGitServerApplication.class));
		assertFalse(hasCopiedBackendField(RepositoryResource.class));
		assertFalse(hasCopiedBackendField(HealthResource.class));
		assertFalse(hasCopiedBackendField(AnalyticsResource.class));
		assertFalse(hasCopiedBackendField(AdminResource.class));
		assertFalse(hasCopiedBackendField(ExternalHibernateRepositoryService.class));
	}

	@Test
	public void validatesApplicationMetadataIdentity() {
		assertEquals("demo", new SandboxRepositoryInfo(" demo ", null).name()); //$NON-NLS-1$ //$NON-NLS-2$
		assertThrows(IllegalArgumentException.class, () -> new SandboxRepositoryInfo(" ", null)); //$NON-NLS-1$
	}

	private static boolean hasCopiedBackendField(Class<?> type) {
		return Arrays.stream(type.getDeclaredFields())
				.map(Field::getType)
				.map(Class::getName)
				.anyMatch(name -> name.startsWith("org.eclipse.jgit.storage.hibernate")); //$NON-NLS-1$
	}
}
