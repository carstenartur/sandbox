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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import io.github.carstenartur.jgit.storage.hibernate.HibernateGitStorage;
import io.github.carstenartur.jgit.storage.hibernate.HibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryDeletionResult;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;

/** Tests the application adapter against only the released public Core API. */
public class ExternalHibernateRepositoryServiceTest {

	@Test
	public void normalizesAndCachesFactoryOwnedStorage() throws Exception {
		FakeFactory factory= new FakeFactory();
		ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(factory);

		Repository first= service.openOrCreate("/demo.git"); //$NON-NLS-1$
		Repository second= service.openOrCreate("demo"); //$NON-NLS-1$

		assertSame(first, second);
		assertEquals(1, factory.storages.size());
		assertTrue(factory.storages.containsKey("demo")); //$NON-NLS-1$
		assertTrue(service.isOpen("demo.git")); //$NON-NLS-1$

		service.close();
		assertTrue(factory.storages.get("demo").closed); //$NON-NLS-1$
	}

	@Test
	public void readsAndUpdatesDescriptionThroughPublicRepository() throws Exception {
		FakeFactory factory= new FakeFactory();
		try (ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(factory)) {
			SandboxRepositoryInfo updated= service.setDescription("demo", "Repository description"); //$NON-NLS-1$ //$NON-NLS-2$

			assertEquals("demo", updated.name()); //$NON-NLS-1$
			assertEquals("Repository description", updated.description()); //$NON-NLS-1$
			assertEquals(updated, service.info("/demo.git")); //$NON-NLS-1$
		}
	}

	private static final class FakeFactory implements HibernateRepositoryFactory {

		private final Map<String, FakeStorage> storages= new LinkedHashMap<>();

		@Override
		public HibernateGitStorage open(RepositoryName repositoryName) {
			FakeStorage storage= new FakeStorage(new InMemoryRepository(
					new DfsRepositoryDescription(repositoryName.value())));
			storages.put(repositoryName.value(), storage);
			return storage;
		}

		@Override
		public RepositoryDeletionResult deleteRepository(RepositoryName repositoryName) {
			throw new UnsupportedOperationException(repositoryName.value());
		}
	}

	private static final class FakeStorage implements HibernateGitStorage {

		private final Repository repository;
		private boolean closed;

		private FakeStorage(Repository repository) {
			this.repository= repository;
		}

		@Override
		public Repository repository() {
			return repository;
		}

		@Override
		public void close() {
			closed= true;
			repository.close();
		}
	}
}
