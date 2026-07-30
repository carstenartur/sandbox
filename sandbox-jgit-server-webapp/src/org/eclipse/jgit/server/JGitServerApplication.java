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
package org.eclipse.jgit.server;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.config.HibernateConfig;
import org.eclipse.jgit.server.config.RepositoryManagerConfig;
import org.eclipse.jgit.server.config.ServerPersistenceContext;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.eclipse.jgit.server.resolver.HibernateRepositoryResolver;
import org.eclipse.jgit.server.rest.AdminResource;
import org.eclipse.jgit.server.rest.AnalyticsResource;
import org.eclipse.jgit.server.rest.CorsFilter;
import org.eclipse.jgit.server.rest.HealthResource;
import org.eclipse.jgit.server.rest.NativeSearchResource;
import org.eclipse.jgit.server.rest.RepositoryResource;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.hibernate.SessionFactory;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;

/** Main application entry point for the JGit database-backed server. */
public class JGitServerApplication {

	private static final Logger LOG = Logger.getLogger(JGitServerApplication.class.getName());
	private static final int DEFAULT_REST_PORT = 8080;
	private static final int DEFAULT_GIT_PORT = 8443;

	private Server server;
	private ServerPersistenceContext persistenceContext;
	private HibernateRepositoryResolver repositoryResolver;

	public static void main(String[] args) throws Exception {
		JGitServerApplication app = new JGitServerApplication();
		app.start();
		app.join();
	}

	/** Starts the configured production server. */
	public void start() throws Exception {
		start(HibernateConfig.createPersistenceContext(),
				getIntEnv("JGIT_REST_PORT", DEFAULT_REST_PORT), //$NON-NLS-1$
				getIntEnv("JGIT_GIT_PORT", DEFAULT_GIT_PORT), //$NON-NLS-1$
				System.getenv("JGIT_CORS_ORIGINS"), //$NON-NLS-1$
				System.getenv("JGIT_DEFAULT_REPOS"), true); //$NON-NLS-1$
	}

	/** Starts a test server from explicit Hibernate properties and dynamic ports. */
	public void start(Properties hibernateProperties, int restPort, int gitPort) throws Exception {
		start(HibernateConfig.createPersistenceContext(hibernateProperties), restPort, gitPort, null, null, false);
	}

	private void start(ServerPersistenceContext context, int restPort, int gitPort,
			String corsOrigins, String defaultRepositories, boolean exposeVersionedApi) throws Exception {
		persistenceContext= Objects.requireNonNull(context, "context"); //$NON-NLS-1$
		try {
			SessionFactory sessionFactory= persistenceContext.sessionFactory();
			repositoryResolver= new HibernateRepositoryResolver(sessionFactory);
			SandboxRepositoryService repositories= repositoryResolver.getRepositoryService();
			if (defaultRepositories != null && !defaultRepositories.isBlank()) {
				RepositoryManagerConfig.initDefaultRepositories(repositories, defaultRepositories);
			}

			server= new Server();
			ServerConnector restConnector= connector("rest", restPort); //$NON-NLS-1$
			ServerConnector gitConnector= connector("git", gitPort); //$NON-NLS-1$
			server.addConnector(restConnector);
			server.addConnector(gitConnector);

			ContextHandlerCollection contexts= new ContextHandlerCollection();
			contexts.addHandler(createRestContext("/api", corsOrigins, sessionFactory, repositories)); //$NON-NLS-1$
			if (exposeVersionedApi) {
				contexts.addHandler(createRestContext("/api/v1", corsOrigins, sessionFactory, repositories)); //$NON-NLS-1$
			}
			contexts.addHandler(createGitContext());
			GzipHandler gzip= new GzipHandler();
			gzip.setHandler(contexts);
			server.setHandler(gzip);
			server.start();
		} catch (Exception failure) {
			closeAfterFailedStart(failure);
			throw failure;
		}

		LOG.log(Level.INFO, "JGit Server started"); //$NON-NLS-1$
		LOG.log(Level.INFO, "  REST API: http://0.0.0.0:{0}/api/", //$NON-NLS-1$
				Integer.toString(getRestPort()));
		if (exposeVersionedApi) {
			LOG.log(Level.INFO, "  REST API v1: http://0.0.0.0:{0}/api/v1/", //$NON-NLS-1$
					Integer.toString(getRestPort()));
		}
		LOG.log(Level.INFO, "  Git HTTP: http://0.0.0.0:{0}/git/", //$NON-NLS-1$
				Integer.toString(getGitPort()));
	}

	private ServerConnector connector(String name, int port) {
		ServerConnector connector= new ServerConnector(server);
		connector.setPort(port);
		connector.setName(name);
		return connector;
	}

	private ServletContextHandler createRestContext(String contextPath, String corsOrigins,
			SessionFactory sessionFactory, SandboxRepositoryService repositories) {
		ServletContextHandler context= new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath(contextPath);
		context.setVirtualHosts(List.of("@rest")); //$NON-NLS-1$
		if (corsOrigins != null && !corsOrigins.isBlank()) {
			context.addFilter(new FilterHolder(new CorsFilter(corsOrigins)), "/*", //$NON-NLS-1$
					EnumSet.allOf(DispatcherType.class));
		}

		context.addServlet(new ServletHolder("health", new HealthResource(sessionFactory)), "/health"); //$NON-NLS-1$ //$NON-NLS-2$
		context.addServlet(new ServletHolder("repos", new RepositoryResource(repositories)), "/repos/*"); //$NON-NLS-1$ //$NON-NLS-2$
		context.addServlet(new ServletHolder("search", new NativeSearchResource(sessionFactory)), "/search/*"); //$NON-NLS-1$ //$NON-NLS-2$
		context.addServlet(new ServletHolder("analytics", new AnalyticsResource(sessionFactory)), "/analytics/*"); //$NON-NLS-1$ //$NON-NLS-2$
		context.addServlet(new ServletHolder("admin", new AdminResource(sessionFactory)), "/admin/*"); //$NON-NLS-1$ //$NON-NLS-2$
		return context;
	}

	private ServletContextHandler createGitContext() {
		ServletContextHandler context= new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/git"); //$NON-NLS-1$
		context.setVirtualHosts(List.of("@git")); //$NON-NLS-1$
		context.addServlet(new ServletHolder(createGitServlet(repositoryResolver)), "/*"); //$NON-NLS-1$
		return context;
	}

	/** Waits for the server to stop. */
	public void join() throws InterruptedException {
		if (server != null) {
			server.join();
		}
	}

	/** Stops the server and closes repository handles before the owned factory. */
	public void stop() throws Exception {
		Exception failure= null;
		try {
			if (server != null) {
				server.stop();
			}
		} catch (Exception exception) {
			failure= exception;
		}
		try {
			if (repositoryResolver != null) {
				repositoryResolver.close();
			}
		} catch (RuntimeException exception) {
			if (failure == null) {
				failure= exception;
			} else {
				failure.addSuppressed(exception);
			}
		}
		try {
			if (persistenceContext != null) {
				persistenceContext.close();
			}
		} catch (RuntimeException exception) {
			if (failure == null) {
				failure= exception;
			} else {
				failure.addSuppressed(exception);
			}
		} finally {
			server= null;
			repositoryResolver= null;
			persistenceContext= null;
		}
		if (failure != null) {
			throw failure;
		}
	}

	private void closeAfterFailedStart(Exception failure) {
		try {
			stop();
		} catch (Exception cleanupFailure) {
			failure.addSuppressed(cleanupFailure);
		}
	}

	/** Returns the REST port, or -1 while not started. */
	public int getRestPort() {
		return getPort("rest"); //$NON-NLS-1$
	}

	/** Returns the Git Smart HTTP port, or -1 while not started. */
	public int getGitPort() {
		return getPort("git"); //$NON-NLS-1$
	}

	private int getPort(String connectorName) {
		if (server != null) {
			for (var connector : server.getConnectors()) {
				if (connector instanceof ServerConnector serverConnector
						&& connectorName.equals(serverConnector.getName())) {
					return serverConnector.getLocalPort();
				}
			}
		}
		return -1;
	}

	/** Returns the repository resolver used by the running application. */
	public HibernateRepositoryResolver getRepositoryResolver() {
		return repositoryResolver;
	}

	/** Returns the application-owned native Hibernate factory. */
	public SessionFactory getSessionFactory() {
		if (persistenceContext == null) {
			throw new IllegalStateException("Server persistence context is not started"); //$NON-NLS-1$
		}
		return persistenceContext.sessionFactory();
	}

	private static int getIntEnv(String name, int defaultValue) {
		String value= System.getenv(name);
		if (value != null && !value.isEmpty()) {
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				LOG.log(Level.WARNING, "Invalid integer for {0}: {1}, using default {2}", //$NON-NLS-1$
						new Object[] { name, value, Integer.toString(defaultValue) });
			}
		}
		return defaultValue;
	}

	private static ReceivePackFactory<HttpServletRequest> createReceivePackFactory() {
		return (request, repository) -> {
			ReceivePack receivePack= new ReceivePack(repository);
			long maxCommandBytes= 50L << 20;
			String configured= System.getenv("JGIT_RECEIVE_MAX_COMMAND_BYTES"); //$NON-NLS-1$
			if (configured != null && !configured.isEmpty()) {
				try {
					maxCommandBytes= Long.parseLong(configured);
				} catch (NumberFormatException exception) {
					LOG.log(Level.WARNING,
							"Invalid JGIT_RECEIVE_MAX_COMMAND_BYTES value: {0}, using default 50 MiB", //$NON-NLS-1$
							configured);
				}
			}
			receivePack.setMaxCommandBytes(maxCommandBytes);
			return receivePack;
		};
	}

	private static GitServlet createGitServlet(HibernateRepositoryResolver resolver) {
		GitServlet gitServlet= new GitServlet();
		gitServlet.setRepositoryResolver(resolver);
		gitServlet.setReceivePackFactory(createReceivePackFactory());
		gitServlet.setReceivePackErrorHandler((request, response, receivePack) -> {
			try {
				receivePack.receive();
			} catch (Exception exception) {
				LOG.log(Level.SEVERE, "ReceivePack error for " + request.getRequestURI(), exception); //$NON-NLS-1$
				throw exception;
			}
		});
		return gitServlet;
	}
}
