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
package org.eclipse.jgit.storage.hibernate.repository;

import java.io.IOException;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryBuilder;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.hibernate.SessionFactory;

/**
 * Builder for creating {@link HibernateRepository} instances.
 * <p>
 * Example usage:
 *
 * <pre>
 * Properties props = new Properties();
 * props.put("hibernate.connection.url", "jdbc:h2:mem:test");
 * props.put("hibernate.connection.driver_class", "org.h2.Driver");
 * props.put("hibernate.hbm2ddl.auto", "create-drop");
 *
 * HibernateSessionFactoryProvider provider = new HibernateSessionFactoryProvider(
 * 		props);
 *
 * HibernateRepository repo = new HibernateRepositoryBuilder()
 * 		.setSessionFactoryProvider(provider).setRepositoryName("my-repo")
 * 		.build();
 * </pre>
 */
public class HibernateRepositoryBuilder extends
		DfsRepositoryBuilder<HibernateRepositoryBuilder, HibernateRepository> {

	private HibernateSessionFactoryProvider sessionFactoryProvider;

	private String repositoryName;

	/**
	 * Set the Hibernate session factory provider.
	 *
	 * @param provider
	 *            the session factory provider
	 * @return this builder
	 */
	public HibernateRepositoryBuilder setSessionFactoryProvider(
			HibernateSessionFactoryProvider provider) {
		this.sessionFactoryProvider = provider;
		return self();
	}

	/**
	 * Get the session factory provider.
	 *
	 * @return the session factory provider
	 */
	public HibernateSessionFactoryProvider getSessionFactoryProvider() {
		return sessionFactoryProvider;
	}

	/**
	 * Set the repository name used to partition data in the database.
	 *
	 * @param name
	 *            the repository name
	 * @return this builder
	 */
	public HibernateRepositoryBuilder setRepositoryName(String name) {
		this.repositoryName = name;
		return self();
	}

	/**
	 * Get the repository name.
	 *
	 * @return the repository name
	 */
	public String getRepositoryName() {
		return repositoryName;
	}

	/**
	 * Get the Hibernate session factory.
	 *
	 * @return the session factory
	 */
	public SessionFactory getSessionFactory() {
		return sessionFactoryProvider != null
				? sessionFactoryProvider.getSessionFactory()
				: null;
	}

	@Override
	public HibernateRepository build() throws IOException {
		if (sessionFactoryProvider == null) {
			throw new IllegalArgumentException(
					"sessionFactoryProvider must be set before calling build()"); //$NON-NLS-1$
		}
		if (repositoryName == null || repositoryName.isEmpty()) {
			throw new IllegalArgumentException(
					"repositoryName must be set before calling build()"); //$NON-NLS-1$
		}
		return new HibernateRepository(this);
	}
}
