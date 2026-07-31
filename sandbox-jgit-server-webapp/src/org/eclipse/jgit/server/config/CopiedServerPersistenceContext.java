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

import org.eclipse.jgit.server.repository.CopiedHibernateRepositoryService;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.hibernate.SessionFactory;

/** Temporary ownership adapter for the copied Sandbox persistence bootstrap. */
final class CopiedServerPersistenceContext implements ServerPersistenceContext {

	private final HibernateSessionFactoryProvider owner;
	private final SandboxRepositoryService repositories;

	CopiedServerPersistenceContext(Properties properties) {
		this(new HibernateSessionFactoryProvider(Objects.requireNonNull(properties, "properties"))); //$NON-NLS-1$
	}

	CopiedServerPersistenceContext(HibernateSessionFactoryProvider owner) {
		this.owner= Objects.requireNonNull(owner, "owner"); //$NON-NLS-1$
		this.repositories= new CopiedHibernateRepositoryService(owner.getSessionFactory());
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
}
