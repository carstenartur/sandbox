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
package org.eclipse.jgit.server.internal;

import java.util.Objects;

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.hibernate.SessionFactory;

/** Creates temporary copied-backend views without transferring factory ownership. */
public final class NonOwningHibernateSessionFactoryProvider {

	private NonOwningHibernateSessionFactoryProvider() {
	}

	/**
	 * Returns a provider view whose {@code close()} deliberately leaves the native
	 * application-owned factory open.
	 */
	public static HibernateSessionFactoryProvider view(SessionFactory sessionFactory) {
		SessionFactory applicationOwnedFactory= Objects.requireNonNull(sessionFactory, "sessionFactory"); //$NON-NLS-1$
		return new HibernateSessionFactoryProvider(applicationOwnedFactory) {
			@Override
			public void close() {
				// The enclosing ServerPersistenceContext owns the native factory.
			}
		};
	}
}
