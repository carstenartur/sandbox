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
import org.eclipse.jgit.server.repository.CopiedHibernateRepositoryService;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.hibernate.SessionFactory;

import jakarta.servlet.http.HttpServletRequest;

/** Resolves Smart HTTP repositories through the application-owned service boundary. */
public class HibernateRepositoryResolver
		implements RepositoryResolver<HttpServletRequest>, AutoCloseable {

	private final HibernateSessionFactoryProvider sessionFactoryProvider;
	private final SandboxRepositoryService repositories;

	/** Creates a resolver from the application-owned native session factory. */
	public HibernateRepositoryResolver(SessionFactory sessionFactory) {
		this(null, new CopiedHibernateRepositoryService(sessionFactory));
	}

	/**
	 * Creates a resolver using the temporary copied-backend adapter.
	 *
	 * @deprecated application wiring should pass the native {@link SessionFactory}
	 */
	@Deprecated(forRemoval = true)
	public HibernateRepositoryResolver(HibernateSessionFactoryProvider sessionFactoryProvider) {
		this(sessionFactoryProvider, new CopiedHibernateRepositoryService(sessionFactoryProvider));
	}

	/** Creates a resolver for an application-owned repository service. */
	public HibernateRepositoryResolver(SandboxRepositoryService repositories) {
		this(null, repositories);
	}

	private HibernateRepositoryResolver(HibernateSessionFactoryProvider sessionFactoryProvider,
			SandboxRepositoryService repositories) {
		this.sessionFactoryProvider= sessionFactoryProvider;
		this.repositories= Objects.requireNonNull(repositories, "repositories"); //$NON-NLS-1$
	}

	@Override
	public Repository open(HttpServletRequest request, String name) throws ServiceNotEnabledException {
		try {
			return repositories.openOrCreate(name);
		} catch (IOException | RuntimeException exception) {
			throw new ServiceNotEnabledException(exception.getMessage());
		}
	}

	/**
	 * Transitional source-compatible access for existing server tests and callers.
	 * New application code must use {@link #getRepositoryService()} and the public
	 * {@link Repository} type. The generic return erases to {@code Repository} and
	 * introduces no copied-backend type into this resolver.
	 */
	@Deprecated(forRemoval = true)
	@SuppressWarnings("unchecked")
	public <R extends Repository> R getOrCreateRepository(String name) throws IOException {
		return (R) repositories.openOrCreate(name);
	}

	/** Returns whether this resolver already owns an open repository handle. */
	public boolean hasRepository(String name) {
		return repositories.isOpen(name);
	}

	/** Returns the application-owned repository service used by REST resources. */
	public SandboxRepositoryService getRepositoryService() {
		return repositories;
	}

	/**
	 * Returns the legacy provider while Search and analytics still use the copied
	 * implementation. Repository lifecycle code must use {@link #getRepositoryService()}.
	 */
	@Deprecated(forRemoval = true)
	public HibernateSessionFactoryProvider getSessionFactoryProvider() {
		if (sessionFactoryProvider == null) {
			throw new IllegalStateException("This resolver was created without a legacy session factory provider."); //$NON-NLS-1$
		}
		return sessionFactoryProvider;
	}

	@Override
	public void close() {
		repositories.close();
	}
}
