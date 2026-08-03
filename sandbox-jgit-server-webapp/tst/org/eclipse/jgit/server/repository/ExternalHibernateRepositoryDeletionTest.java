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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

import io.github.carstenartur.jgit.storage.hibernate.HibernateGitStorage;
import io.github.carstenartur.jgit.storage.hibernate.HibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryDeletionResult;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;

/** Verifies the application policy around the released Core deletion API. */
public class ExternalHibernateRepositoryDeletionTest {

	@Test
	public void normalizesAndMapsCoreDeletionResultsWithoutLeakingCoreTypes() {
		FakeFactory factory= new FakeFactory();
		factory.deletionResults.add(new RepositoryDeletionResult(2, 3, 4));
		try (ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(factory)) {
			SandboxRepositoryDeletionResult result= service.delete(" /demo.git "); //$NON-NLS-1$

			assertEquals("demo", factory.lastDeletedRepository); //$NON-NLS-1$
			assertEquals(1, factory.deleteCalls.get());
			assertEquals(new SandboxRepositoryDeletionResult(2, 3, 4), result);
			assertTrue(result.deletedAnything());
		}
	}

	@Test
	public void rejectsDeletionWhileTheAdapterOwnsAnOpenHandle() throws Exception {
		FakeFactory factory= new FakeFactory();
		try (ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(factory)) {
			service.openOrCreate("demo"); //$NON-NLS-1$

			IllegalStateException failure= assertThrows(IllegalStateException.class,
					() -> service.delete("/demo.git")); //$NON-NLS-1$

			assertTrue(failure.getMessage().contains("demo")); //$NON-NLS-1$
			assertEquals(0, factory.deleteCalls.get());
			assertTrue(service.isOpen("demo")); //$NON-NLS-1$
		}
	}

	@Test
	public void repeatedDeletionIsIdempotent() {
		FakeFactory factory= new FakeFactory();
		factory.deletionResults.add(new RepositoryDeletionResult(1, 1, 0));
		factory.deletionResults.add(new RepositoryDeletionResult(0, 0, 0));
		try (ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(factory)) {
			assertTrue(service.delete("demo").deletedAnything()); //$NON-NLS-1$
			assertFalse(service.delete("demo.git").deletedAnything()); //$NON-NLS-1$
			assertEquals(2, factory.deleteCalls.get());
		}
	}

	@Test
	public void rejectsInvalidCountsAndDeletionAfterShutdown() {
		assertThrows(IllegalArgumentException.class,
				() -> new SandboxRepositoryDeletionResult(-1, 0, 0));
		ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(new FakeFactory());
		service.close();
		assertThrows(IllegalStateException.class, () -> service.delete("demo")); //$NON-NLS-1$
	}

	private static final class FakeFactory implements HibernateRepositoryFactory {

		private final Deque<RepositoryDeletionResult> deletionResults= new ArrayDeque<>();
		private final AtomicInteger deleteCalls= new AtomicInteger();
		private String lastDeletedRepository;

		@Override
		public HibernateGitStorage open(RepositoryName repositoryName) {
			return new FakeStorage(new InMemoryRepository(
					new DfsRepositoryDescription(repositoryName.value())));
		}

		@Override
		public RepositoryDeletionResult deleteRepository(RepositoryName repositoryName) {
			deleteCalls.incrementAndGet();
			lastDeletedRepository= repositoryName.value();
			return deletionResults.isEmpty()
					? new RepositoryDeletionResult(0, 0, 0)
					: deletionResults.removeFirst();
		}
	}

	private static final class FakeStorage implements HibernateGitStorage {

		private final Repository repository;

		private FakeStorage(Repository repository) {
			this.repository= repository;
		}

		@Override
		public Repository repository() {
			return repository;
		}

		@Override
		public void close() {
			repository.close();
		}
	}
}
