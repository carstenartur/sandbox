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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.history.SandboxHistoryMaintenance;

import io.github.carstenartur.jgit.storage.hibernate.HibernateGitStorage;
import io.github.carstenartur.jgit.storage.hibernate.HibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryDeletionResult;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;

/**
 * Adapts the released {@code jgit-storage-hibernate-core} factory to the
 * application-owned repository service contract.
 *
 * <p>The adapter owns every storage handle it opens. Application and transport
 * code receive only public JGit {@link Repository} instances and Sandbox
 * metadata. The production cut-over can therefore replace the copied backend
 * without changing REST or Smart HTTP callers.</p>
 */
public final class ExternalHibernateRepositoryService implements SandboxRepositoryService {

	private final HibernateRepositoryFactory repositoryFactory;
	private final SandboxHistoryMaintenance maintenance;
	private final Object lifecycleLock= new Object();
	private final Map<String, HibernateGitStorage> storages= new LinkedHashMap<>();
	private boolean closed;

	/** Creates an adapter for the released public repository factory. */
	public ExternalHibernateRepositoryService(HibernateRepositoryFactory repositoryFactory) {
		this(repositoryFactory, SandboxHistoryMaintenance.NONE);
	}

	/** Shares projection coordination with this context's index refresh and push hooks. */
	public ExternalHibernateRepositoryService(HibernateRepositoryFactory repositoryFactory, SandboxHistoryMaintenance maintenance) {
		this.repositoryFactory= Objects.requireNonNull(repositoryFactory, "repositoryFactory"); //$NON-NLS-1$
		this.maintenance= Objects.requireNonNull(maintenance);
	}

	@Override
	public Repository openOrCreate(String name) throws IOException {
		synchronized (lifecycleLock) {
			return storageLocked(normalize(name)).repository();
		}
	}

	@Override
	public SandboxRepositoryInfo info(String name) throws IOException {
		synchronized (lifecycleLock) {
			String normalized= normalize(name);
			Repository repository= storageLocked(normalized).repository();
			return new SandboxRepositoryInfo(normalized, repository.getGitwebDescription());
		}
	}

	@Override
	public SandboxRepositoryInfo setDescription(String name, String description) throws IOException {
		synchronized (lifecycleLock) {
			String normalized= normalize(name);
			Repository repository= storageLocked(normalized).repository();
			repository.setGitwebDescription(description);
			return new SandboxRepositoryInfo(normalized, repository.getGitwebDescription());
		}
	}

	@Override
	public SandboxRepositoryDeletionResult delete(String name) {
		synchronized (lifecycleLock) {
			if (closed) {
				throw new IllegalStateException("Repository service is already closed."); //$NON-NLS-1$
			}
			String normalized= normalize(name);
			if (storages.containsKey(normalized)) {
				throw new IllegalStateException(
						"Repository is open in this process and cannot be deleted: " + normalized); //$NON-NLS-1$
			}
			RepositoryDeletionResult result= Objects.requireNonNull(
					maintenance.withExclusiveProjectionAccess(normalized,
							() -> repositoryFactory.deleteRepository(new RepositoryName(normalized))),
					"repository deletion result for " + normalized); //$NON-NLS-1$
			return new SandboxRepositoryDeletionResult(
					result.packRows(), result.reflogRows(), result.projectionRows());
		}
	}

	@Override
	public boolean isOpen(String name) {
		synchronized (lifecycleLock) {
			return !closed && storages.containsKey(normalize(name));
		}
	}

	@Override
	public void close() {
		List<HibernateGitStorage> storagesToClose;
		synchronized (lifecycleLock) {
			if (closed) {
				return;
			}
			closed= true;
			storagesToClose= new ArrayList<>(storages.values());
			storages.clear();
		}

		RuntimeException failure= null;
		for (HibernateGitStorage storage : storagesToClose) {
			try {
				storage.close();
			} catch (RuntimeException exception) {
				if (failure == null) {
					failure= exception;
				} else {
					failure.addSuppressed(exception);
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	private HibernateGitStorage storageLocked(String name) {
		if (closed) {
			throw new IllegalStateException("Repository service is already closed."); //$NON-NLS-1$
		}
		return storages.computeIfAbsent(name,
				key -> repositoryFactory.open(new RepositoryName(key)));
	}

	private static String normalize(String name) {
		return SandboxRepositoryNames.normalize(name);
	}
}
