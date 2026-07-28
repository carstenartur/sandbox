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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.Test;

import io.github.carstenartur.jgit.storage.hibernate.HibernateGitStorage;
import io.github.carstenartur.jgit.storage.hibernate.HibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryDeletionResult;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;

/** Contract tests for the released Core repository adapter. */
public class ReleasedCoreRepositoryServiceTest {

	@Test
	public void normalizesCachesMetadataAndClosesExternalStorage() throws Exception {
		Path root= Files.createTempDirectory("released-core-service"); //$NON-NLS-1$
		RecordingFactory factory= new RecordingFactory(root);
		InMemorySandboxRepositoryMetadataStore metadata= new InMemorySandboxRepositoryMetadataStore();
		ReleasedCoreRepositoryService service= new ReleasedCoreRepositoryService(factory, metadata);
		try {
			Repository first= service.openOrCreate("/demo.git"); //$NON-NLS-1$
			Repository second= service.openOrCreate("demo"); //$NON-NLS-1$

			assertSame(first, second);
			assertEquals(1, factory.openCount.get());
			assertTrue(service.isOpen("/demo.git")); //$NON-NLS-1$
			assertEquals("demo", service.setDescription("demo.git", "Example").name()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertEquals("Example", service.info("/demo").description()); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals("Example", metadata.description("demo")); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			service.close();
			deleteRecursively(root);
		}
		assertEquals(1, factory.closeCount.get());
	}

	private static void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (var paths= Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	private static final class RecordingFactory implements HibernateRepositoryFactory {

		private final Path root;
		private final AtomicInteger openCount= new AtomicInteger();
		private final AtomicInteger closeCount= new AtomicInteger();

		RecordingFactory(Path root) {
			this.root= root;
		}

		@Override
		public HibernateGitStorage open(RepositoryName repositoryName) {
			openCount.incrementAndGet();
			try {
				Repository repository= new FileRepositoryBuilder()
						.setGitDir(root.resolve(repositoryName.value() + ".git").toFile()) //$NON-NLS-1$
						.setBare()
						.build();
				repository.create(true);
				return new HibernateGitStorage() {
					@Override
					public Repository repository() {
						return repository;
					}

					@Override
					public void close() {
						repository.close();
						closeCount.incrementAndGet();
					}
				};
			} catch (IOException exception) {
				throw new UncheckedIOException(exception);
			}
		}

		@Override
		public RepositoryDeletionResult deleteRepository(RepositoryName repositoryName) {
			throw new UnsupportedOperationException("Deletion is outside this adapter test."); //$NON-NLS-1$
		}
	}
}
