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

	private static final String LUCENE= "lucene"; //$NON-NLS-1$
	private static final String LOCAL_FILESYSTEM= "local-filesystem"; //$NON-NLS-1$

	private final HibernateSessionFactoryProvider owner;
	private final SandboxRepositoryService repositories;

	ExternalServerPersistenceContext(Properties properties) {
		this(new HibernateSessionFactoryProvider(
				serverProperties(Objects.requireNonNull(properties, "properties")), //$NON-NLS-1$
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

	private static Properties serverProperties(Properties properties) {
		Properties copy= new Properties();
		copy.putAll(properties);

		copy.putIfAbsent("hibernate.search.backend.type", LUCENE); //$NON-NLS-1$
		String backend= copy.getProperty("hibernate.search.backend.type"); //$NON-NLS-1$
		if (LUCENE.equalsIgnoreCase(backend)) {
			copy.putIfAbsent("hibernate.search.backend.directory.type", //$NON-NLS-1$
					LOCAL_FILESYSTEM);
			if (LOCAL_FILESYSTEM.equalsIgnoreCase(copy.getProperty(
					"hibernate.search.backend.directory.type"))) { //$NON-NLS-1$
				String root= System.getenv("JGIT_SEARCH_INDEX_DIR"); //$NON-NLS-1$
				if (root == null || root.isBlank()) {
					root= "jgit-search-index"; //$NON-NLS-1$
				}
				copy.putIfAbsent("hibernate.search.backend.directory.root", root); //$NON-NLS-1$
			}
		}
		copy.putIfAbsent("hibernate.search.backend.analysis.configurer", //$NON-NLS-1$
				"class:org.eclipse.jgit.storage.hibernate.search.JavaSourceAnalysisConfigurer"); //$NON-NLS-1$

		copy.putIfAbsent("hibernate.connection.provider_class", //$NON-NLS-1$
				"org.hibernate.hikaricp.internal.HikariCPConnectionProvider"); //$NON-NLS-1$
		copy.putIfAbsent("hibernate.hikari.minimumIdle", "5"); //$NON-NLS-1$ //$NON-NLS-2$
		copy.putIfAbsent("hibernate.hikari.maximumPoolSize", "20"); //$NON-NLS-1$ //$NON-NLS-2$
		copy.putIfAbsent("hibernate.hikari.idleTimeout", "300000"); //$NON-NLS-1$ //$NON-NLS-2$
		copy.putIfAbsent("hibernate.hikari.connectionTimeout", "20000"); //$NON-NLS-1$ //$NON-NLS-2$
		copy.putIfAbsent("hibernate.hikari.maxLifetime", "1200000"); //$NON-NLS-1$ //$NON-NLS-2$
		copy.putIfAbsent("hibernate.connection.handling_mode", //$NON-NLS-1$
				"DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION"); //$NON-NLS-1$
		copy.putIfAbsent("hibernate.cache.use_second_level_cache", "true"); //$NON-NLS-1$ //$NON-NLS-2$
		copy.putIfAbsent("hibernate.cache.region.factory_class", "jcache"); //$NON-NLS-1$ //$NON-NLS-2$
		return copy;
	}
}
