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
package org.eclipse.jgit.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jgit.server.config.ServerPersistenceContext;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.hibernate.SessionFactory;
import org.junit.Test;

/** Lifecycle regressions for application-owned persistence startup. */
public class JGitServerApplicationLifecycleTest {

	@Test
	public void closesPersistenceContextWhenNativeFactoryAccessFails() throws Exception {
		AtomicBoolean closed= new AtomicBoolean();
		ServerPersistenceContext context= new ServerPersistenceContext() {
			@Override
			public SessionFactory sessionFactory() {
				throw new IllegalStateException("factory unavailable"); //$NON-NLS-1$
			}

			@Override
			public SandboxRepositoryService repositoryService() {
				throw new AssertionError("Repository service must not be requested after factory failure"); //$NON-NLS-1$
			}

			@Override
			public void close() {
				closed.set(true);
			}
		};
		JGitServerApplication application= new JGitServerApplication();
		Method start= JGitServerApplication.class.getDeclaredMethod("start", //$NON-NLS-1$
				ServerPersistenceContext.class, int.class, int.class, String.class, String.class, boolean.class);
		start.setAccessible(true);

		InvocationTargetException failure= assertThrows(InvocationTargetException.class,
				() -> start.invoke(application, context, 0, 0, null, null, false));
		assertEquals("factory unavailable", failure.getCause().getMessage()); //$NON-NLS-1$
		assertTrue("The application-owned persistence context must be closed", closed.get()); //$NON-NLS-1$
		assertThrows(IllegalStateException.class, application::getSessionFactory);
	}
}
