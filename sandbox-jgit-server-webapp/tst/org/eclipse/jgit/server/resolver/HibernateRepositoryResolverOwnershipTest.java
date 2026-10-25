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
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.server.repository.SandboxRepositoryInfo;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.junit.Test;

/** Ownership contracts for repository resolvers. */
public class HibernateRepositoryResolverOwnershipTest {

	@Test
	public void eachHttpRequestOwnsAReferenceSeparateFromTheService() throws Exception {
		AtomicInteger closed = new AtomicInteger();
		Repository repository = new InMemoryRepository(new DfsRepositoryDescription("example")) {
			@Override
			protected void doClose() {
				closed.incrementAndGet();
				super.doClose();
			}
		};
		SandboxRepositoryService service = new SandboxRepositoryService() {
			@Override public Repository openOrCreate(String name) { return repository; }
			@Override public SandboxRepositoryInfo info(String name) { throw new UnsupportedOperationException(); }
			@Override public SandboxRepositoryInfo setDescription(String name, String value) { throw new UnsupportedOperationException(); }
			@Override public boolean isOpen(String name) { return true; }
			@Override public void close() { repository.close(); }
		};
		try {
			var resolver = new HibernateRepositoryResolver(service);
			resolver.open(null, "example.git").close();
			resolver.open(null, "example.git").close();
			assertEquals("Closing HTTP requests must retain the service's repository", 0, closed.get());
		} finally {
			service.close();
		}
		assertEquals(1, closed.get());
	}

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
