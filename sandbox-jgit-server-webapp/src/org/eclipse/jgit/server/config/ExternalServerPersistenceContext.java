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
package org.eclipse.jgit.server.config;

import java.util.Objects;
import java.util.Properties;

import org.eclipse.jgit.server.repository.ExternalHibernateRepositoryService;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.hibernate.SessionFactory;
import org.sandbox.jgit.storage.integration.SandboxProjectionEntities;

import io.github.carstenartur.jgit.storage.hibernate.DefaultHibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;

/**
 * Application-owned persistence context backed by the released generic Core
 * implementation.
 *
 * <p>This context is intentionally not the normal server default yet. It
 * registers the released Core mappings exactly once together with the explicit
 * compatible Sandbox projection list. Production construction must go through
 * {@link HibernateConfig#createExternalPersistenceContext(Properties)}, which
 * verifies the Flyway history and Hibernate validation mode before bootstrap.</p>
 */
final class ExternalServerPersistenceContext implements ServerPersistenceContext {

	private final HibernateSessionFactoryProvider owner;
	private final SandboxRepositoryService repositories;

	ExternalServerPersistenceContext(Properties properties) {
		this(new HibernateSessionFactoryProvider(
				copyOf(Objects.requireNonNull(properties, "properties")), //$NON-NLS-1$
				SandboxProjectionEntities.annotatedClasses()));
	}

	ExternalServerPersistenceContext(HibernateSessionFactoryProvider owner) {
		this.owner= Objects.requireNonNull(owner, "owner"); //$NON-NLS-1$
		this.repositories= new ExternalHibernateRepositoryService(
				new DefaultHibernateRepositoryFactory(owner.getSessionFactory()));
	}

	@Override
	public SessionFactory sessionFactory() {
		return owner.getSessionFactory();
	}

	@Override
	public SandboxRepositoryService repositoryService() {
		return repositories;
	}

	@Override
	public void close() {
		RuntimeException failure= null;
		try {
			repositories.close();
		} catch (RuntimeException exception) {
			failure= exception;
		}
		try {
			owner.close();
		} catch (RuntimeException exception) {
			if (failure == null) {
				failure= exception;
			} else {
				failure.addSuppressed(exception);
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	private static Properties copyOf(Properties properties) {
		Properties copy= new Properties();
		copy.putAll(properties);
		return copy;
	}
}
