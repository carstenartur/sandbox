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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.lib.Repository;

import io.github.carstenartur.jgit.storage.hibernate.HibernateGitStorage;
import io.github.carstenartur.jgit.storage.hibernate.HibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;

/**
 * Repository service backed exclusively by the released
 * {@code jgit-storage-hibernate-core} public API.
 *
 * <p>This adapter is intentionally independent of SessionFactory construction and
 * schema migration. Applications may activate it only after those concerns have
 * been completed through the external library's migration contract.</p>
 */
public final class ReleasedCoreRepositoryService implements SandboxRepositoryService {

	private final HibernateRepositoryFactory repositoryFactory;
	private final SandboxRepositoryMetadataStore metadataStore;
	private final Map<String, HibernateGitStorage> storages= new ConcurrentHashMap<>();

	/** Creates an adapter for an already configured and migrated Core factory. */
	public ReleasedCoreRepositoryService(HibernateRepositoryFactory repositoryFactory,
			SandboxRepositoryMetadataStore metadataStore) {
		this.repositoryFactory= Objects.requireNonNull(repositoryFactory, "repositoryFactory"); //$NON-NLS-1$
		this.metadataStore= Objects.requireNonNull(metadataStore, "metadataStore"); //$NON-NLS-1$
	}

	@Override
	public Repository openOrCreate(String name) throws IOException {
		String normalized= SandboxRepositoryNames.normalize(name);
		return storage(normalized).repository();
	}

	@Override
	public SandboxRepositoryInfo info(String name) throws IOException {
		String normalized= SandboxRepositoryNames.normalize(name);
		storage(normalized);
		return new SandboxRepositoryInfo(normalized, metadataStore.description(normalized));
	}

	@Override
	public SandboxRepositoryInfo setDescription(String name, String description) throws IOException {
		String normalized= SandboxRepositoryNames.normalize(name);
		storage(normalized);
		metadataStore.setDescription(normalized, description);
		return new SandboxRepositoryInfo(normalized, metadataStore.description(normalized));
	}

	@Override
	public boolean isOpen(String name) {
		return storages.containsKey(SandboxRepositoryNames.normalize(name));
	}

	@Override
	public void close() {
		RuntimeException failure= null;
		for (HibernateGitStorage storage : storages.values()) {
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
		storages.clear();
		if (failure != null) {
			throw failure;
		}
	}

	private HibernateGitStorage storage(String normalizedName) {
		return storages.computeIfAbsent(normalizedName, this::openStorage);
	}

	private HibernateGitStorage openStorage(String normalizedName) {
		HibernateGitStorage storage= Objects.requireNonNull(
				repositoryFactory.open(new RepositoryName(normalizedName)),
				"repositoryFactory returned null storage"); //$NON-NLS-1$
		if (storage.repository() == null) {
			storage.close();
			throw new IllegalStateException("Released Core storage returned a null JGit repository."); //$NON-NLS-1$
		}
		return storage;
	}
}
