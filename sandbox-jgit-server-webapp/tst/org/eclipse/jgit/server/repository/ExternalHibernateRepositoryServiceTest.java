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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
		assertEquals(1, factory.openCalls.get());
		assertTrue(factory.storages.containsKey("demo")); //$NON-NLS-1$
		assertTrue(service.isOpen("demo.git")); //$NON-NLS-1$

		service.close();
		assertTrue(factory.storages.get("demo").closed); //$NON-NLS-1$
	}

	@Test
	public void concurrentNormalizedOpensShareOneFactoryHandle() throws Exception {
		FakeFactory factory= new FakeFactory();
		ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(factory);
		ExecutorService executor= Executors.newFixedThreadPool(8);
		CountDownLatch start= new CountDownLatch(1);
		try {
			List<Future<Repository>> futures= new ArrayList<>();
			for (int index= 0; index < 32; index++) {
				String name= index % 2 == 0 ? "/demo.git" : "demo"; //$NON-NLS-1$ //$NON-NLS-2$
				futures.add(executor.submit(() -> {
					start.await();
					return service.openOrCreate(name);
				}));
			}
			start.countDown();
			Repository first= futures.get(0).get();
			for (Future<Repository> future : futures) {
				assertSame(first, future.get());
			}
			assertEquals(1, factory.openCalls.get());
			assertEquals(1, factory.storages.size());
		} finally {
			executor.shutdownNow();
			service.close();
		}
	}

	@Test
	public void shutdownClosesAStorageWhoseOpenWasAlreadyInFlight() throws Exception {
		FakeFactory factory= new FakeFactory();
		factory.openStarted= new CountDownLatch(1);
		factory.allowOpen= new CountDownLatch(1);
		ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(factory);
		ExecutorService executor= Executors.newFixedThreadPool(2);
		Future<Repository> opening= executor.submit(() -> service.openOrCreate("demo")); //$NON-NLS-1$
		Future<?> closing= null;
		try {
			assertTrue(factory.openStarted.await(5, TimeUnit.SECONDS));
			CountDownLatch closeThreadStarted= new CountDownLatch(1);
			closing= executor.submit(() -> {
				closeThreadStarted.countDown();
				service.close();
			});
			assertTrue(closeThreadStarted.await(5, TimeUnit.SECONDS));

			factory.allowOpen.countDown();
			Repository opened= opening.get(5, TimeUnit.SECONDS);
			closing.get(5, TimeUnit.SECONDS);

			FakeStorage storage= factory.storages.get("demo"); //$NON-NLS-1$
			assertSame(opened, storage.repository());
			assertTrue(storage.closed);
			assertEquals(1, storage.closeCalls.get());
			assertFalse(service.isOpen("demo")); //$NON-NLS-1$
			assertThrows(IllegalStateException.class,
					() -> service.openOrCreate("demo")); //$NON-NLS-1$
		} finally {
			factory.allowOpen.countDown();
			if (closing != null) {
				closing.get(5, TimeUnit.SECONDS);
			}
			executor.shutdownNow();
		}
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

	@Test
	public void closeIsIdempotentAndRejectsFurtherRepositoryAccess() throws Exception {
		FakeFactory factory= new FakeFactory();
		ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(factory);
		service.openOrCreate("demo"); //$NON-NLS-1$

		service.close();
		service.close();

		assertFalse(service.isOpen("demo")); //$NON-NLS-1$
		assertEquals(1, factory.storages.get("demo").closeCalls.get()); //$NON-NLS-1$
		assertThrows(IllegalStateException.class,
				() -> service.openOrCreate("demo")); //$NON-NLS-1$
		assertThrows(IllegalStateException.class,
				() -> service.info("demo")); //$NON-NLS-1$
		assertThrows(IllegalStateException.class,
				() -> service.setDescription("demo", "description")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void closeDoesNotHoldLifecycleLockWhileClosingStorage() throws Exception {
		FakeFactory factory= new FakeFactory();
		ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(factory);
		service.openOrCreate("demo"); //$NON-NLS-1$
		FakeStorage storage= factory.storages.get("demo"); //$NON-NLS-1$
		storage.closeStarted= new CountDownLatch(1);
		storage.allowClose= new CountDownLatch(1);
		ExecutorService executor= Executors.newSingleThreadExecutor();
		Future<?> closing= executor.submit(service::close);
		try {
			assertTrue(storage.closeStarted.await(5, TimeUnit.SECONDS));
			assertFalse(service.isOpen("demo")); //$NON-NLS-1$
			assertThrows(IllegalStateException.class,
					() -> service.openOrCreate("demo")); //$NON-NLS-1$
		} finally {
			storage.allowClose.countDown();
			closing.get(5, TimeUnit.SECONDS);
			executor.shutdownNow();
		}
	}

	@Test
	public void closesEveryStorageWhenOneCloseFails() throws Exception {
		FakeFactory factory= new FakeFactory();
		ExternalHibernateRepositoryService service= new ExternalHibernateRepositoryService(factory);
		service.openOrCreate("first"); //$NON-NLS-1$
		service.openOrCreate("second"); //$NON-NLS-1$
		factory.storages.get("first").closeFailure= new IllegalStateException("first close failed"); //$NON-NLS-1$ //$NON-NLS-2$
		factory.storages.get("second").closeFailure= new IllegalArgumentException("second close failed"); //$NON-NLS-1$ //$NON-NLS-2$

		IllegalStateException failure= assertThrows(IllegalStateException.class, service::close);

		assertEquals("first close failed", failure.getMessage()); //$NON-NLS-1$
		assertEquals(1, failure.getSuppressed().length);
		assertEquals("second close failed", failure.getSuppressed()[0].getMessage()); //$NON-NLS-1$
		assertTrue(factory.storages.get("first").closed); //$NON-NLS-1$
		assertTrue(factory.storages.get("second").closed); //$NON-NLS-1$
		assertFalse(service.isOpen("first")); //$NON-NLS-1$
		assertFalse(service.isOpen("second")); //$NON-NLS-1$
		service.close();
	}

	private static final class FakeFactory implements HibernateRepositoryFactory {

		private final Map<String, FakeStorage> storages= new LinkedHashMap<>();
		private final AtomicInteger openCalls= new AtomicInteger();
		private CountDownLatch openStarted;
		private CountDownLatch allowOpen;

		@Override
		public HibernateGitStorage open(RepositoryName repositoryName) {
			openCalls.incrementAndGet();
			if (openStarted != null) {
				openStarted.countDown();
			}
			if (allowOpen != null) {
				try {
					allowOpen.await();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while opening storage.", exception); //$NON-NLS-1$
				}
			}
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
		private final AtomicInteger closeCalls= new AtomicInteger();
		private boolean closed;
		private RuntimeException closeFailure;
		private CountDownLatch closeStarted;
		private CountDownLatch allowClose;

		private FakeStorage(Repository repository) {
			this.repository= repository;
		}

		@Override
		public Repository repository() {
			return repository;
		}

		@Override
		public void close() {
			closeCalls.incrementAndGet();
			if (closeStarted != null) {
				closeStarted.countDown();
			}
			if (allowClose != null) {
				try {
					allowClose.await();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while closing storage.", exception); //$NON-NLS-1$
				}
			}
			closed= true;
			repository.close();
			if (closeFailure != null) {
				throw closeFailure;
			}
		}
	}
}
