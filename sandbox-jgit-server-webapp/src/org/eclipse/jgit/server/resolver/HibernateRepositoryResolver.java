/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.eclipse.jgit.server.resolver;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.storage.hibernate.repository.HibernateRepository;
import org.eclipse.jgit.storage.hibernate.repository.HibernateRepositoryBuilder;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves Git repositories from the Hibernate database backend.
 * <p>
 * Repository instances are cached by name to avoid creating duplicate
 * connections to the same database-backed repository.
 */
public class HibernateRepositoryResolver
		implements RepositoryResolver<HttpServletRequest>, AutoCloseable {

	private final HibernateSessionFactoryProvider sessionFactoryProvider;

	private final Map<String, HibernateRepository> cache = new ConcurrentHashMap<>();

	/**
	 * Create a resolver backed by the given session factory provider.
	 *
	 * @param sessionFactoryProvider
	 *            the Hibernate session factory provider
	 */
	public HibernateRepositoryResolver(
			HibernateSessionFactoryProvider sessionFactoryProvider) {
		this.sessionFactoryProvider = sessionFactoryProvider;
	}

	@Override
	public Repository open(HttpServletRequest req, String name)
			throws ServiceNotEnabledException {
		String repoName = normalizeRepoName(name);
		try {
			return getOrCreateRepository(repoName);
		} catch (IOException e) {
			throw new ServiceNotEnabledException(e.getMessage());
		}
	}

	/**
	 * Get or create a repository by name.
	 *
	 * @param name
	 *            the repository name
	 * @return the repository
	 * @throws IOException
	 *             if repository creation fails
	 */
	public HibernateRepository getOrCreateRepository(String name)
			throws IOException {
		return cache.computeIfAbsent(name, n -> {
			try {
				return new HibernateRepositoryBuilder()
						.setSessionFactoryProvider(sessionFactoryProvider)
						.setRepositoryName(n)
						.setRepositoryDescription(
								new DfsRepositoryDescription(n))
						.build();
			} catch (IOException e) {
				throw new IllegalStateException(
						"Failed to create repository: " + n, e); //$NON-NLS-1$
			}
		});
	}

	/**
	 * Check if a repository with the given name exists in the cache.
	 *
	 * @param name
	 *            the repository name
	 * @return true if the repository is cached
	 */
	public boolean hasRepository(String name) {
		return cache.containsKey(normalizeRepoName(name));
	}

	/**
	 * Get the session factory provider.
	 *
	 * @return the session factory provider
	 */
	public HibernateSessionFactoryProvider getSessionFactoryProvider() {
		return sessionFactoryProvider;
	}

	@Override
	public void close() {
		for (HibernateRepository repo : cache.values()) {
			repo.close();
		}
		cache.clear();
	}

	private static String normalizeRepoName(String name) {
		if (name.startsWith("/")) { //$NON-NLS-1$
			name = name.substring(1);
		}
		if (name.endsWith(".git")) { //$NON-NLS-1$
			name = name.substring(0, name.length() - 4);
		}
		return name;
	}
}
