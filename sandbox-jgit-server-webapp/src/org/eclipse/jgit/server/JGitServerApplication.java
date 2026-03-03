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

import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.config.HibernateConfig;
import org.eclipse.jgit.server.config.RepositoryManagerConfig;
import org.eclipse.jgit.server.resolver.HibernateRepositoryResolver;
import org.eclipse.jgit.server.rest.AnalyticsResource;
import org.eclipse.jgit.server.rest.HealthResource;
import org.eclipse.jgit.server.rest.RepositoryResource;
import org.eclipse.jgit.server.rest.SearchResource;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Main application entry point for the JGit database-backed server.
 * <p>
 * Starts an embedded Jetty server with:
 * <ul>
 * <li>Git Smart HTTP protocol on port 8443 (configurable)</li>
 * <li>REST API on port 8080 (configurable)</li>
 * </ul>
 * Configuration is read from environment variables.
 */
public class JGitServerApplication {

	private static final Logger LOG = Logger
			.getLogger(JGitServerApplication.class.getName());

	private static final int DEFAULT_REST_PORT = 8080;

	private static final int DEFAULT_GIT_PORT = 8443;

	private Server server;

	private HibernateSessionFactoryProvider sessionFactoryProvider;

	private HibernateRepositoryResolver repositoryResolver;

	/**
	 * Main entry point.
	 *
	 * @param args
	 *            command line arguments (unused)
	 * @throws Exception
	 *             if server startup fails
	 */
	public static void main(String[] args) throws Exception {
		JGitServerApplication app = new JGitServerApplication();
		app.start();
		app.join();
	}

	/**
	 * Start the server.
	 *
	 * @throws Exception
	 *             if server startup fails
	 */
	public void start() throws Exception {
		sessionFactoryProvider = HibernateConfig
				.createSessionFactoryProvider();
		repositoryResolver = new HibernateRepositoryResolver(
				sessionFactoryProvider);

		// Auto-create default repositories
		String defaultRepos = System.getenv("JGIT_DEFAULT_REPOS"); //$NON-NLS-1$
		if (defaultRepos != null && !defaultRepos.isEmpty()) {
			RepositoryManagerConfig.initDefaultRepositories(
					sessionFactoryProvider, defaultRepos);
		}

		int restPort = getIntEnv("JGIT_REST_PORT", DEFAULT_REST_PORT); //$NON-NLS-1$
		int gitPort = getIntEnv("JGIT_GIT_PORT", DEFAULT_GIT_PORT); //$NON-NLS-1$

		server = new Server();

		// REST API connector
		ServerConnector restConnector = new ServerConnector(server);
		restConnector.setPort(restPort);
		restConnector.setName("rest"); //$NON-NLS-1$

		// Git HTTP connector
		ServerConnector gitConnector = new ServerConnector(server);
		gitConnector.setPort(gitPort);
		gitConnector.setName("git"); //$NON-NLS-1$

		server.addConnector(restConnector);
		server.addConnector(gitConnector);

		ContextHandlerCollection contexts = new ContextHandlerCollection();

		// REST API context
		ServletContextHandler restContext = new ServletContextHandler(
				ServletContextHandler.SESSIONS);
		restContext.setContextPath("/api"); //$NON-NLS-1$
		restContext.setVirtualHosts(List.of("@rest")); //$NON-NLS-1$

		restContext.addServlet(new ServletHolder("health", //$NON-NLS-1$
				new HealthResource(sessionFactoryProvider)), "/health"); //$NON-NLS-1$
		restContext.addServlet(
				new ServletHolder("repos", //$NON-NLS-1$
						new RepositoryResource(sessionFactoryProvider,
								repositoryResolver)),
				"/repos/*"); //$NON-NLS-1$
		restContext.addServlet(
				new ServletHolder("search", //$NON-NLS-1$
						new SearchResource(sessionFactoryProvider)),
				"/search/*"); //$NON-NLS-1$
		restContext.addServlet(
				new ServletHolder("analytics", //$NON-NLS-1$
						new AnalyticsResource(sessionFactoryProvider)),
				"/analytics/*"); //$NON-NLS-1$

		// Git Smart HTTP context
		ServletContextHandler gitContext = new ServletContextHandler(
				ServletContextHandler.SESSIONS);
		gitContext.setContextPath("/git"); //$NON-NLS-1$
		gitContext.setVirtualHosts(List.of("@git")); //$NON-NLS-1$

		gitContext.addServlet(
				new ServletHolder(createGitServlet(repositoryResolver)),
				"/*"); //$NON-NLS-1$

		contexts.addHandler(restContext);
		contexts.addHandler(gitContext);
		server.setHandler(contexts);

		server.start();
		LOG.log(Level.INFO, "JGit Server started"); //$NON-NLS-1$
		LOG.log(Level.INFO, "  REST API: http://0.0.0.0:{0}/api/", //$NON-NLS-1$
				Integer.toString(restPort));
		LOG.log(Level.INFO,
				"  Git HTTP: http://0.0.0.0:{0}/git/", //$NON-NLS-1$
				Integer.toString(gitPort));
	}

	/**
	 * Start the server with explicit Hibernate properties and ports.
	 * <p>
	 * Useful for integration testing with Testcontainers where the database
	 * connection URL and ports are determined at runtime.
	 *
	 * @param hibernateProperties
	 *            Hibernate configuration properties
	 * @param restPort
	 *            REST API port (0 for dynamic allocation)
	 * @param gitPort
	 *            Git HTTP port (0 for dynamic allocation)
	 * @throws Exception
	 *             if server startup fails
	 */
	public void start(Properties hibernateProperties, int restPort,
			int gitPort) throws Exception {
		sessionFactoryProvider = HibernateConfig
				.createSessionFactoryProvider(hibernateProperties);
		repositoryResolver = new HibernateRepositoryResolver(
				sessionFactoryProvider);

		server = new Server();

		// REST API connector
		ServerConnector restConnector = new ServerConnector(server);
		restConnector.setPort(restPort);
		restConnector.setName("rest"); //$NON-NLS-1$

		// Git HTTP connector
		ServerConnector gitConnector = new ServerConnector(server);
		gitConnector.setPort(gitPort);
		gitConnector.setName("git"); //$NON-NLS-1$

		server.addConnector(restConnector);
		server.addConnector(gitConnector);

		ContextHandlerCollection contexts = new ContextHandlerCollection();

		// REST API context
		ServletContextHandler restContext = new ServletContextHandler(
				ServletContextHandler.SESSIONS);
		restContext.setContextPath("/api"); //$NON-NLS-1$
		restContext.setVirtualHosts(List.of("@rest")); //$NON-NLS-1$

		restContext.addServlet(new ServletHolder("health", //$NON-NLS-1$
				new HealthResource(sessionFactoryProvider)), "/health"); //$NON-NLS-1$
		restContext.addServlet(
				new ServletHolder("repos", //$NON-NLS-1$
						new RepositoryResource(sessionFactoryProvider,
								repositoryResolver)),
				"/repos/*"); //$NON-NLS-1$
		restContext.addServlet(
				new ServletHolder("search", //$NON-NLS-1$
						new SearchResource(sessionFactoryProvider)),
				"/search/*"); //$NON-NLS-1$
		restContext.addServlet(
				new ServletHolder("analytics", //$NON-NLS-1$
						new AnalyticsResource(sessionFactoryProvider)),
				"/analytics/*"); //$NON-NLS-1$

		// Git Smart HTTP context
		ServletContextHandler gitContext = new ServletContextHandler(
				ServletContextHandler.SESSIONS);
		gitContext.setContextPath("/git"); //$NON-NLS-1$
		gitContext.setVirtualHosts(List.of("@git")); //$NON-NLS-1$

		gitContext.addServlet(
				new ServletHolder(createGitServlet(repositoryResolver)),
				"/*"); //$NON-NLS-1$

		contexts.addHandler(restContext);
		contexts.addHandler(gitContext);
		server.setHandler(contexts);

		server.start();
		LOG.log(Level.INFO, "JGit Server started (test mode)"); //$NON-NLS-1$
		LOG.log(Level.INFO, "  REST API: http://0.0.0.0:{0}/api/", //$NON-NLS-1$
				Integer.toString(getRestPort()));
		LOG.log(Level.INFO,
				"  Git HTTP: http://0.0.0.0:{0}/git/", //$NON-NLS-1$
				Integer.toString(getGitPort()));
	}

	/**
	 * Wait for the server to stop.
	 *
	 * @throws InterruptedException
	 *             if interrupted while waiting
	 */
	public void join() throws InterruptedException {
		if (server != null) {
			server.join();
		}
	}

	/**
	 * Stop the server and release resources.
	 *
	 * @throws Exception
	 *             if an error occurs during shutdown
	 */
	public void stop() throws Exception {
		if (server != null) {
			server.stop();
		}
		if (repositoryResolver != null) {
			repositoryResolver.close();
		}
		if (sessionFactoryProvider != null) {
			sessionFactoryProvider.close();
		}
	}

	/**
	 * Get the REST API port the server is listening on.
	 *
	 * @return the REST port, or -1 if not started
	 */
	public int getRestPort() {
		if (server != null) {
			for (var connector : server.getConnectors()) {
				if (connector instanceof ServerConnector sc
						&& "rest".equals(sc.getName())) { //$NON-NLS-1$
					return sc.getLocalPort();
				}
			}
		}
		return -1;
	}

	/**
	 * Get the Git HTTP port the server is listening on.
	 *
	 * @return the Git port, or -1 if not started
	 */
	public int getGitPort() {
		if (server != null) {
			for (var connector : server.getConnectors()) {
				if (connector instanceof ServerConnector sc
						&& "git".equals(sc.getName())) { //$NON-NLS-1$
					return sc.getLocalPort();
				}
			}
		}
		return -1;
	}

	/**
	 * Get the repository resolver.
	 *
	 * @return the resolver
	 */
	public HibernateRepositoryResolver getRepositoryResolver() {
		return repositoryResolver;
	}

	private static int getIntEnv(String name, int defaultValue) {
		String val = System.getenv(name);
		if (val != null && !val.isEmpty()) {
			try {
				return Integer.parseInt(val);
			} catch (NumberFormatException e) {
				LOG.log(Level.WARNING,
						"Invalid integer for {0}: {1}, using default {2}", //$NON-NLS-1$
						new Object[] { name, val,
								Integer.toString(defaultValue) });
			}
		}
		return defaultValue;
	}

	/**
	 * Create a ReceivePackFactory that allows anonymous push operations.
	 *
	 * @return a factory that creates ReceivePack instances without requiring
	 *         authentication
	 */
	private static ReceivePackFactory<HttpServletRequest> createReceivePackFactory() {
		return (HttpServletRequest req, Repository db) -> new ReceivePack(db);
	}

	private static GitServlet createGitServlet(
			HibernateRepositoryResolver resolver) {
		GitServlet gitServlet = new GitServlet();
		gitServlet.setRepositoryResolver(resolver);
		gitServlet.setReceivePackFactory(createReceivePackFactory());
		gitServlet.setReceivePackErrorHandler(
				(req, rsp, r) -> {
					try {
						r.receive();
					} catch (Exception e) {
						LOG.log(Level.SEVERE,
								"ReceivePack error for " //$NON-NLS-1$
										+ req.getRequestURI(),
								e);
						throw e;
					}
				});
		return gitServlet;
	}
}
