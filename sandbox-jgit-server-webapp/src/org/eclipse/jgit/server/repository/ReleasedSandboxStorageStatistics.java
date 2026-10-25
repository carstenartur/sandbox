/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.repository;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;
import org.hibernate.SessionFactory;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryStorageStatistics;

/** Released inventory guarded by the application's existing-reader lifetime. */
public final class ReleasedSandboxStorageStatistics implements SandboxStorageStatistics {
	private final RepositoryStorageStatistics statistics;
	private final SandboxRepositoryService repositories;

	public ReleasedSandboxStorageStatistics(SessionFactory factory, SandboxRepositoryService repositories) {
		this.statistics= new RepositoryStorageStatistics(factory);
		this.repositories= Objects.requireNonNull(repositories);
	}

	@Override
	public ObjectCounts objects(String repository) throws IOException {
		try {
			return repositories.withExistingRepository(repository, ignored -> {
				var counts= statistics.objects(new RepositoryName(SandboxRepositoryNames.normalize(repository)));
				return new ObjectCounts(counts.commits(), counts.trees(), counts.blobs(), counts.tags());
			});
		} catch (FileNotFoundException missing) {
			return new ObjectCounts(0, 0, 0, 0);
		}
	}

	@Override
	public PackCounts packs(String repository) throws IOException {
		try {
			return repositories.withExistingRepository(repository, ignored -> {
				var counts= statistics.packs(new RepositoryName(SandboxRepositoryNames.normalize(repository)));
				return new PackCounts(counts.packCount(), counts.totalSizeBytes());
			});
		} catch (FileNotFoundException missing) {
			return new PackCounts(0, 0);
		}
	}
}
