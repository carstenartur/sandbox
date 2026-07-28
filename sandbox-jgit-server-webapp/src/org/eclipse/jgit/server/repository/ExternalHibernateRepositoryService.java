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
	private final Map<String, HibernateGitStorage> storages= new ConcurrentHashMap<>();

	/** Creates an adapter for the released public repository factory. */
	public ExternalHibernateRepositoryService(HibernateRepositoryFactory repositoryFactory) {
		this.repositoryFactory= Objects.requireNonNull(repositoryFactory, "repositoryFactory"); //$NON-NLS-1$
	}

	@Override
	public Repository openOrCreate(String name) throws IOException {
		return storage(name).repository();
	}

	@Override
	public SandboxRepositoryInfo info(String name) throws IOException {
		String normalized= normalize(name);
		Repository repository= storage(normalized).repository();
		return new SandboxRepositoryInfo(normalized, repository.getGitwebDescription());
	}

	@Override
	public SandboxRepositoryInfo setDescription(String name, String description) throws IOException {
		String normalized= normalize(name);
		Repository repository= storage(normalized).repository();
		repository.setGitwebDescription(description);
		return new SandboxRepositoryInfo(normalized, repository.getGitwebDescription());
	}

	@Override
	public boolean isOpen(String name) {
		return storages.containsKey(normalize(name));
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

	private HibernateGitStorage storage(String name) {
		String normalized= normalize(name);
		return storages.computeIfAbsent(normalized,
				key -> repositoryFactory.open(new RepositoryName(key)));
	}

	private static String normalize(String name) {
		String normalized= Objects.requireNonNull(name, "name").strip(); //$NON-NLS-1$
		if (normalized.startsWith("/")) { //$NON-NLS-1$
			normalized= normalized.substring(1);
		}
		if (normalized.endsWith(".git")) { //$NON-NLS-1$
			normalized= normalized.substring(0, normalized.length() - 4);
		}
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Repository name must not be blank."); //$NON-NLS-1$
		}
		return normalized;
	}
}
