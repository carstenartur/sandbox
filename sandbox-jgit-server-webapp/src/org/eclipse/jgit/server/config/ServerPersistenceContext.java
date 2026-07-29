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

import org.hibernate.SessionFactory;

/**
 * Application-owned persistence context used by server resources and repository
 * adapters.
 *
 * <p>The boundary exposes only the native Hibernate contract and lifecycle. It
 * deliberately does not expose copied or released JGit storage implementation
 * types, so entity registration can be replaced independently from callers.</p>
 */
public interface ServerPersistenceContext extends AutoCloseable {

	/** Returns the application-owned native Hibernate session factory. */
	SessionFactory sessionFactory();

	/** Closes the owned session factory and its connection/search resources. */
	@Override
	void close();
}
