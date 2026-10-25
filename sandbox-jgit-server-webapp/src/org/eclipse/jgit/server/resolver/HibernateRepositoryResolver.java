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
import java.util.Objects;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.eclipse.jgit.server.repository.SandboxRepositoryNames;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves Smart HTTP repositories through the application-owned service boundary. */
public class HibernateRepositoryResolver
		implements RepositoryResolver<HttpServletRequest>, AutoCloseable {
	private static final String REPOSITORY_NAME = HibernateRepositoryResolver.class.getName() + ".repositoryName";

    private final SandboxRepositoryService repositories;

    /** Borrows the context-owned service; HTTP requests own their own JGit references. */
    public HibernateRepositoryResolver(SandboxRepositoryService repositories) {
        this.repositories = Objects.requireNonNull(repositories, "repositories");
    }

	@Override
	public Repository open(HttpServletRequest request, String name) throws ServiceNotEnabledException {
		try {
			String normalized = SandboxRepositoryNames.normalize(name);
			Repository repository = repositories.openOrCreate(name);
			if (request != null) {
				request.setAttribute(REPOSITORY_NAME, normalized);
			}
			// JGit's RepositoryFilter closes this request reference in its finally block.
			repository.incrementOpen();
			return repository;
		} catch (IOException | RuntimeException exception) {
			throw new ServiceNotEnabledException(exception.getMessage());
		}
	}

	/** Returns the identity already resolved for this Smart HTTP request. */
	public static String repositoryName(HttpServletRequest request) {
		return (String) Objects.requireNonNull(request.getAttribute(REPOSITORY_NAME), "resolved repository name");
	}

    /** Opens a repository through the context-owned service. */
    public Repository getOrCreateRepository(String name) throws IOException {
        return repositories.openOrCreate(name);
    }

	/** Returns whether this resolver already owns an open repository handle. */
	public boolean hasRepository(String name) {
		return repositories.isOpen(name);
	}

	/** Returns the application-owned repository service used by REST resources. */
	public SandboxRepositoryService getRepositoryService() {
		return repositories;
	}

    @Override public void close() { /* The application context owns the service. */ }
}
