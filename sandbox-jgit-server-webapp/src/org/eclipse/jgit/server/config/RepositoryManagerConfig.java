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

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.storage.hibernate.repository.HibernateRepository;
import org.eclipse.jgit.storage.hibernate.repository.HibernateRepositoryBuilder;

/**
 * Manages automatic creation of default Git repositories on server startup.
 * <p>
 * Reads the {@code JGIT_DEFAULT_REPOS} environment variable (comma-separated
 * list of repository names) and ensures each one exists in the database.
 */
public class RepositoryManagerConfig {

	private static final Logger LOG = Logger
			.getLogger(RepositoryManagerConfig.class.getName());

	private RepositoryManagerConfig() {
		// utility class
	}

	/**
	 * Initialize default repositories from a comma-separated list of names.
	 * <p>
	 * Each repository is created if it does not already exist. Existing
	 * repositories are not modified.
	 *
	 * @param provider
	 *            the session factory provider
	 * @param repoList
	 *            comma-separated list of repository names
	 */
	public static void initDefaultRepositories(
			HibernateSessionFactoryProvider provider, String repoList) {
		String[] repos = repoList.split(","); //$NON-NLS-1$
		for (String repoName : repos) {
			String name = repoName.trim();
			if (!name.isEmpty()) {
				try {
					createRepositoryIfAbsent(provider, name);
					LOG.log(Level.INFO,
							"Initialized default repository: {0}", //$NON-NLS-1$
							name);
				} catch (IOException e) {
					LOG.log(Level.WARNING,
							"Failed to create default repository: " //$NON-NLS-1$
									+ name,
							e);
				}
			}
		}
	}

	/**
	 * Create a repository if it doesn't already exist.
	 *
	 * @param provider
	 *            the session factory provider
	 * @param repositoryName
	 *            the name of the repository to create
	 * @return the repository (existing or newly created)
	 * @throws IOException
	 *             if creation fails
	 */
	public static HibernateRepository createRepositoryIfAbsent(
			HibernateSessionFactoryProvider provider,
			String repositoryName) throws IOException {
		return new HibernateRepositoryBuilder()
				.setSessionFactoryProvider(provider)
				.setRepositoryName(repositoryName).build();
	}
}
