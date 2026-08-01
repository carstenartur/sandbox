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
package org.eclipse.jgit.server.resolver;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.repository.SandboxRepositoryInfo;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.junit.Test;

/** Ownership contracts for repository resolvers. */
public class HibernateRepositoryResolverOwnershipTest {

	@Test
	public void doesNotCloseApplicationOwnedRepositoryService() {
		AtomicInteger closeCount= new AtomicInteger();
		SandboxRepositoryService repositories= new SandboxRepositoryService() {
			@Override
			public Repository openOrCreate(String name) throws IOException {
				throw new UnsupportedOperationException();
			}

			@Override
			public SandboxRepositoryInfo info(String name) throws IOException {
				throw new UnsupportedOperationException();
			}

			@Override
			public SandboxRepositoryInfo setDescription(String name, String description) throws IOException {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean isOpen(String name) {
				return false;
			}

			@Override
			public void close() {
				closeCount.incrementAndGet();
			}
		};

		HibernateRepositoryResolver resolver= new HibernateRepositoryResolver(repositories);
		resolver.close();

		assertEquals("The application context, not its resolver, owns the supplied service", //$NON-NLS-1$
				0, closeCount.get());
	}
}
