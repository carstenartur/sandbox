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
package org.eclipse.jgit.server.config;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.repository.CopiedHibernateRepositoryService;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;

/** Creates configured default repositories through the application boundary. */
public final class RepositoryManagerConfig {

	private static final Logger LOG= Logger.getLogger(RepositoryManagerConfig.class.getName());

	private RepositoryManagerConfig() {
	}

	/** Initializes comma-separated repository identities through the shared service. */
	public static void initDefaultRepositories(SandboxRepositoryService repositories, String repositoryList) {
		for (String repositoryName : repositoryList.split(",")) { //$NON-NLS-1$
			String name= repositoryName.strip();
			if (name.isEmpty()) {
				continue;
			}
			try {
				repositories.openOrCreate(name);
				LOG.log(Level.INFO, "Initialized default repository: {0}", name); //$NON-NLS-1$
			} catch (IOException | RuntimeException exception) {
				LOG.log(Level.WARNING, "Failed to create default repository: " + name, exception); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Compatibility entry point for standalone provisioning callers. The
	 * temporary adapter is closed after all repository identities are persisted.
	 */
	public static void initDefaultRepositories(HibernateSessionFactoryProvider provider, String repositoryList) {
		try (CopiedHibernateRepositoryService repositories= new CopiedHibernateRepositoryService(provider)) {
			initDefaultRepositories(repositories, repositoryList);
		}
	}

	/** Opens or creates one repository through the application boundary. */
	public static Repository createRepositoryIfAbsent(SandboxRepositoryService repositories, String repositoryName)
			throws IOException {
		return repositories.openOrCreate(repositoryName);
	}
}
