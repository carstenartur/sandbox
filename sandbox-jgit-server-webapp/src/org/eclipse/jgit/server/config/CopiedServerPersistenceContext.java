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

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.hibernate.SessionFactory;

/** Temporary ownership adapter for the copied Sandbox persistence bootstrap. */
final class CopiedServerPersistenceContext implements ServerPersistenceContext {

	private final HibernateSessionFactoryProvider owner;

	CopiedServerPersistenceContext(Properties properties) {
		this(new HibernateSessionFactoryProvider(Objects.requireNonNull(properties, "properties"))); //$NON-NLS-1$
	}

	CopiedServerPersistenceContext(HibernateSessionFactoryProvider owner) {
		this.owner= Objects.requireNonNull(owner, "owner"); //$NON-NLS-1$
	}

	@Override
	public SessionFactory sessionFactory() {
		return owner.getSessionFactory();
	}

	@Override
	public void close() {
		owner.close();
	}
}
