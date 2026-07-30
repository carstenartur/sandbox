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

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.storage.hibernate.repository.HibernateRepository;
import org.eclipse.jgit.storage.hibernate.repository.HibernateRepositoryBuilder;
import org.hibernate.SessionFactory;

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

	/** Creates the temporary adapter from an application-owned native factory. */
	public CopiedHibernateRepositoryService(SessionFactory sessionFactory) {
		this(nonOwningProvider(sessionFactory));
	}

	/**
	 * Creates the temporary copied-backend adapter.
	 *
	 * @deprecated application wiring should pass the native {@link SessionFactory}
	 */
	@Deprecated(forRemoval = true)
	public CopiedHibernateRepositoryService(HibernateSessionFactoryProvider sessionFactoryProvider) {
		this.sessionFactoryProvider= Objects.requireNonNull(sessionFactoryProvider, "sessionFactoryProvider"); //$NON-NLS-1$
	}

	@Override
	public Repository openOrCreate(String name) throws IOException {
		return repository(name);
	}

	@Override
	public SandboxRepositoryInfo info(String name) throws IOException {
		String normalized= normalize(name);
		HibernateRepository repository= repository(normalized);
		return new SandboxRepositoryInfo(normalized, repository.getGitwebDescription());
	}

	@Override
	public SandboxRepositoryInfo setDescription(String name, String description) throws IOException {
		String normalized= normalize(name);
		HibernateRepository repository= repository(normalized);
		repository.setGitwebDescription(description);
		return new SandboxRepositoryInfo(normalized, repository.getGitwebDescription());
	}

	@Override
	public boolean isOpen(String name) {
		return repositories.containsKey(normalize(name));
	}

	@Override
	public void close() {
		for (HibernateRepository repository : repositories.values()) {
			repository.close();
		}
		repositories.clear();
	}

	static HibernateSessionFactoryProvider nonOwningProvider(SessionFactory sessionFactory) {
		SessionFactory applicationOwnedFactory= Objects.requireNonNull(sessionFactory, "sessionFactory"); //$NON-NLS-1$
		return new HibernateSessionFactoryProvider(applicationOwnedFactory) {
			@Override
			public void close() {
				// The enclosing ServerPersistenceContext owns the native factory.
			}
		};
	}

	private HibernateRepository repository(String name) throws IOException {
		String normalized= normalize(name);
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
