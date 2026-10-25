/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import java.util.Objects;
import java.util.Properties;
import org.eclipse.jgit.server.analysis.*;
import org.eclipse.jgit.server.history.*;
import org.eclipse.jgit.server.repository.*;
import org.eclipse.jgit.server.search.SandboxSourceSearch;
import org.hibernate.SessionFactory;
import io.github.carstenartur.jgit.storage.hibernate.DefaultHibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import io.github.carstenartur.jgit.storage.hibernate.search.SearchEntities;
import io.github.carstenartur.jgit.storage.hibernate.search.SearchRepositoryDeletionParticipant;
import io.github.carstenartur.jgit.storage.hibernate.search.analysis.GitTextAnalysis;

/** Owns released persistence, historical generations and the optional model for one server. */
final class ExternalServerPersistenceContext implements ServerPersistenceContext {
    private static final String LUCENE = "lucene";
    private static final String LOCAL_FILESYSTEM = "local-filesystem";
    private final HibernateSessionFactoryProvider owner;
    private final SandboxRepositoryService repositories;
    private final SandboxHistorySearch history;
    private final SandboxHistoryMaintenance maintenance;
    private final AnalysisGenerations generations;
    private final EmbeddingEncoder encoder;
    private final SandboxSourceSearch sources;
    private final SandboxFileHistory files;
    private boolean closed;

    ExternalServerPersistenceContext(Properties properties) {
        this(properties, AnalysisSettings.fromEnvironment());
    }
    private ExternalServerPersistenceContext(Properties properties, AnalysisSettings settings) {
        this(new HibernateSessionFactoryProvider(serverProperties(Objects.requireNonNull(properties)),
                SearchEntities.annotatedClasses()), settings, new LocalEmbeddingEncoder());
    }
    ExternalServerPersistenceContext(HibernateSessionFactoryProvider owner) {
        this(owner, AnalysisSettings.fromEnvironment(), new LocalEmbeddingEncoder());
    }
    ExternalServerPersistenceContext(HibernateSessionFactoryProvider owner, AnalysisSettings settings, EmbeddingEncoder encoder) {
        this.owner = Objects.requireNonNull(owner);
        this.encoder = Objects.requireNonNull(encoder);
        AnalysisGenerations acquiredGenerations = null;
        SandboxRepositoryService acquiredRepositories = null;
        try {
            acquiredGenerations = new AnalysisGenerations(settings.retainedGenerations(), settings.concurrentBuilds());
            this.generations = acquiredGenerations;
            var factory = owner.getSessionFactory();
            this.history = new ReleasedSandboxHistorySearch(factory);
            this.maintenance = new ReleasedSandboxHistoryMaintenance(factory, generations);
            acquiredRepositories = new ExternalHibernateRepositoryService(new DefaultHibernateRepositoryFactory(factory,
                    java.util.List.of(new SearchRepositoryDeletionParticipant())), maintenance, factory);
            this.repositories = acquiredRepositories;
            this.sources = new ReleasedSandboxSourceSearch(factory, repositories, generations, settings, encoder);
            this.files = new ReleasedSandboxFileHistory(factory, repositories, settings.filePathHistory());
        } catch (RuntimeException | Error failure) {
            try { closeAll(acquiredGenerations, encoder, acquiredRepositories, owner); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    @Override public SessionFactory sessionFactory() { return owner.getSessionFactory(); }
    @Override public SandboxRepositoryService repositoryService() { return repositories; }
    @Override public SandboxHistorySearch historySearch() { return history; }
    @Override public SandboxHistoryMaintenance historyMaintenance() { return maintenance; }
    @Override public SandboxSourceSearch sourceSearch() { return sources; }
    @Override public SandboxFileHistory fileHistory() { return files; }
    @Override public SandboxStorageStatistics storageStatistics() { return new ReleasedSandboxStorageStatistics(owner.getSessionFactory(), repositories); }

    /** Drain readers before their model, repository leases and database can be closed. */
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        closeAll(generations, encoder, repositories, owner);
    }
    private static void closeAll(AutoCloseable... resources) {
        Throwable failure = null;
        for (var resource : resources) {
            if (resource == null) continue;
            try { resource.close(); }
            catch (Exception | Error cleanup) {
                if (failure == null) failure = cleanup;
                else failure.addSuppressed(cleanup);
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure != null) throw new IllegalStateException("Cannot close server persistence resources", failure);
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
        if (!copy.containsKey(GitTextAnalysis.BACKEND_CONFIGURER_PROPERTY)
                && !copy.containsKey(GitTextAnalysis.PROFILE_ID_PROPERTY)) {
            String configurer = "elasticsearch".equalsIgnoreCase(backend)
                    ? SandboxHistoryElasticsearchAnalysisConfigurer.class.getName()
                    : SandboxHistoryAnalysisConfigurer.class.getName();
            GitTextAnalysis.configure(copy, "class:" + configurer, "sandbox-commit-message-v1");
        }

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
