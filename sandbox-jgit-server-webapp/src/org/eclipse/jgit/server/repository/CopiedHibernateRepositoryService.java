/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.storage.hibernate.repository.HibernateRepository;
import org.eclipse.jgit.storage.hibernate.repository.HibernateRepositoryBuilder;

/**
 * Temporary adapter for the copied Sandbox Hibernate backend.
 *
 * <p>All copied builder and concrete repository dependencies are deliberately
 * confined to this class so the external Core factory can replace it without
 * changing REST, startup or Smart HTTP code.</p>
 */
public final class CopiedHibernateRepositoryService implements SandboxRepositoryService {

	private final HibernateSessionFactoryProvider sessionFactoryProvider;
	private final Map<String, HibernateRepository> repositories= new ConcurrentHashMap<>();

	/** Creates the temporary copied-backend adapter. */
	public CopiedHibernateRepositoryService(HibernateSessionFactoryProvider sessionFactoryProvider) {
		this.sessionFactoryProvider= Objects.requireNonNull(sessionFactoryProvider, "sessionFactoryProvider"); //$NON-NLS-1$
	}

	@Override
	public Repository openOrCreate(String name) throws IOException {
		return repository(name);
	}

	@Override
	public SandboxRepositoryInfo info(String name) throws IOException {
		String normalized= SandboxRepositoryNames.normalize(name);
		HibernateRepository repository= repository(normalized);
		return new SandboxRepositoryInfo(normalized, repository.getGitwebDescription());
	}

	@Override
	public SandboxRepositoryInfo setDescription(String name, String description) throws IOException {
		String normalized= SandboxRepositoryNames.normalize(name);
		HibernateRepository repository= repository(normalized);
		repository.setGitwebDescription(description);
		return new SandboxRepositoryInfo(normalized, repository.getGitwebDescription());
	}

	@Override
	public boolean isOpen(String name) {
		return repositories.containsKey(SandboxRepositoryNames.normalize(name));
	}

	@Override
	public void close() {
		for (HibernateRepository repository : repositories.values()) {
			repository.close();
		}
		repositories.clear();
	}

	private HibernateRepository repository(String name) throws IOException {
		String normalized= SandboxRepositoryNames.normalize(name);
		HibernateRepository existing= repositories.get(normalized);
		if (existing != null) {
			return existing;
		}
		HibernateRepository created= new HibernateRepositoryBuilder()
				.setSessionFactoryProvider(sessionFactoryProvider)
				.setRepositoryName(normalized)
				.setRepositoryDescription(new DfsRepositoryDescription(normalized))
				.build();
		HibernateRepository raced= repositories.putIfAbsent(normalized, created);
		if (raced != null) {
			created.close();
			return raced;
		}
		return created;
	}
}
