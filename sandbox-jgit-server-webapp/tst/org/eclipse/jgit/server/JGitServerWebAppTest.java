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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCache;
import org.eclipse.jgit.internal.storage.dfs.DfsBlockCacheConfig;
import org.eclipse.jgit.server.resolver.HibernateRepositoryResolver;
import org.eclipse.jgit.server.rest.AnalyticsResource;
import org.eclipse.jgit.server.rest.HealthResource;
import org.eclipse.jgit.server.rest.RepositoryResource;
import org.eclipse.jgit.server.rest.SearchResource;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the JGit server webapp REST endpoints using an embedded Jetty
 * server with H2 in-memory database.
 */
public class JGitServerWebAppTest {

	private static final AtomicInteger COUNTER = new AtomicInteger();

	private Server server;

	private int port;

	private HibernateSessionFactoryProvider provider;

	private HibernateRepositoryResolver resolver;

	/**
	 * Set up the test server.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Before
	public void setUp() throws Exception {
		DfsBlockCache.reconfigure(new DfsBlockCacheConfig());

		String dbName = "webapp-test-" + COUNTER.incrementAndGet(); //$NON-NLS-1$
		Properties props = new Properties();
		props.put("hibernate.connection.url", //$NON-NLS-1$
				"jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.connection.driver_class", "org.h2.Driver"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.dialect", //$NON-NLS-1$
				"org.hibernate.dialect.H2Dialect"); //$NON-NLS-1$
		props.put("hibernate.hbm2ddl.auto", "create-drop"); //$NON-NLS-1$ //$NON-NLS-2$
		props.put("hibernate.show_sql", "false"); //$NON-NLS-1$ //$NON-NLS-2$

		provider = new HibernateSessionFactoryProvider(props);
		resolver = new HibernateRepositoryResolver(provider);

		server = new Server();
		ServerConnector connector = new ServerConnector(server);
		connector.setPort(0); // dynamic port
		server.addConnector(connector);

		ServletContextHandler context = new ServletContextHandler(
				ServletContextHandler.SESSIONS);
		context.setContextPath("/api"); //$NON-NLS-1$

		context.addServlet(
				new ServletHolder(new HealthResource(provider)),
				"/health"); //$NON-NLS-1$
		context.addServlet(
				new ServletHolder(
						new RepositoryResource(provider, resolver)),
				"/repos/*"); //$NON-NLS-1$
		context.addServlet(
				new ServletHolder(new SearchResource(provider)),
				"/search/*"); //$NON-NLS-1$
		context.addServlet(
				new ServletHolder(new AnalyticsResource(provider)),
				"/analytics/*"); //$NON-NLS-1$

		server.setHandler(context);
		server.start();
		port = connector.getLocalPort();
	}

	/**
	 * Tear down the test server.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@After
	public void tearDown() throws Exception {
		if (server != null) {
			server.stop();
		}
		if (resolver != null) {
			resolver.close();
		}
		if (provider != null) {
			provider.close();
		}
	}

	/**
	 * Test health endpoint returns UP status.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testHealthEndpoint() throws Exception {
		HttpURLConnection conn = openGet("/api/health"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = readBody(conn);
		assertTrue(body.contains("\"status\":\"UP\"")); //$NON-NLS-1$
		assertTrue(body.contains("\"database\":{\"status\":\"UP\"}")); //$NON-NLS-1$
	}

	/**
	 * Test creating a repository via POST.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testCreateRepository() throws Exception {
		HttpURLConnection conn = openPost("/api/repos", //$NON-NLS-1$
				"{\"name\":\"test-repo\",\"description\":\"A test repo\"}"); //$NON-NLS-1$
		assertEquals(201, conn.getResponseCode());
		String body = readBody(conn);
		assertTrue(body.contains("\"name\":\"test-repo\"")); //$NON-NLS-1$
	}

	/**
	 * Test creating a repository with missing name returns 400.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testCreateRepositoryMissingName() throws Exception {
		HttpURLConnection conn = openPost("/api/repos", //$NON-NLS-1$
				"{\"description\":\"no name\"}"); //$NON-NLS-1$
		assertEquals(400, conn.getResponseCode());
	}

	/**
	 * Test getting a repository by name.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testGetRepository() throws Exception {
		// First create it
		openPost("/api/repos", //$NON-NLS-1$
				"{\"name\":\"get-test\"}"); //$NON-NLS-1$

		// Then get it
		HttpURLConnection conn = openGet("/api/repos/get-test"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = readBody(conn);
		assertTrue(body.contains("\"name\":\"get-test\"")); //$NON-NLS-1$
	}

	/**
	 * Test search endpoint with missing params returns 400.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testSearchCommitsNoParams() throws Exception {
		HttpURLConnection conn = openGet("/api/search/commits"); //$NON-NLS-1$
		assertEquals(400, conn.getResponseCode());
	}

	/**
	 * Test search on empty repo returns zero results.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testSearchCommitsEmptyRepo() throws Exception {
		// Create repo first
		openPost("/api/repos", //$NON-NLS-1$
				"{\"name\":\"search-test\"}"); //$NON-NLS-1$

		HttpURLConnection conn = openGet(
				"/api/search/commits?repo=search-test&q=hello"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = readBody(conn);
		assertTrue(body.contains("\"totalResults\":0")); //$NON-NLS-1$
	}

	/**
	 * Test analytics endpoint with missing repo param returns 400.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testAnalyticsNoRepoParam() throws Exception {
		HttpURLConnection conn = openGet("/api/analytics/authors"); //$NON-NLS-1$
		assertEquals(400, conn.getResponseCode());
	}

	/**
	 * Test author statistics on empty repo.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testAnalyticsAuthorsEmptyRepo() throws Exception {
		openPost("/api/repos", //$NON-NLS-1$
				"{\"name\":\"analytics-test\"}"); //$NON-NLS-1$

		HttpURLConnection conn = openGet(
				"/api/analytics/authors?repo=analytics-test"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = readBody(conn);
		assertTrue(body.contains("\"authors\"")); //$NON-NLS-1$
	}

	/**
	 * Test pack statistics on empty repo.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testAnalyticsPacksEmptyRepo() throws Exception {
		openPost("/api/repos", //$NON-NLS-1$
				"{\"name\":\"packs-test\"}"); //$NON-NLS-1$

		HttpURLConnection conn = openGet(
				"/api/analytics/packs?repo=packs-test"); //$NON-NLS-1$
		assertEquals(200, conn.getResponseCode());
		String body = readBody(conn);
		assertTrue(body.contains("\"packCount\"")); //$NON-NLS-1$
	}

	/**
	 * Test repository name normalization.
	 *
	 * @throws Exception
	 *             on failure
	 */
	@Test
	public void testResolverNormalizesRepoName() throws Exception {
		assertNotNull(resolver.getOrCreateRepository("test-norm")); //$NON-NLS-1$
		assertNotNull(resolver.getOrCreateRepository("/test-norm")); //$NON-NLS-1$
		assertNotNull(resolver.getOrCreateRepository("test-norm.git")); //$NON-NLS-1$
		assertTrue(resolver.hasRepository("test-norm")); //$NON-NLS-1$
	}

	/**
	 * Test HibernateConfig builds valid properties from defaults.
	 */
	@Test
	public void testHibernateConfigBuildProperties() {
		Properties props = org.eclipse.jgit.server.config.HibernateConfig
				.buildProperties();
		assertNotNull(props);
		// Should have defaults
		assertNotNull(
				props.getProperty("hibernate.connection.url")); //$NON-NLS-1$
		assertNotNull(props.getProperty("hibernate.dialect")); //$NON-NLS-1$
	}

	// === Helper methods ===

	private HttpURLConnection openGet(String path) throws IOException {
		URI uri = URI.create(
				"http://localhost:" + port + path); //$NON-NLS-1$
		HttpURLConnection conn = (HttpURLConnection) uri.toURL()
				.openConnection();
		conn.setRequestMethod("GET"); //$NON-NLS-1$
		return conn;
	}

	private HttpURLConnection openPost(String path, String body)
			throws IOException {
		URI uri = URI.create(
				"http://localhost:" + port + path); //$NON-NLS-1$
		HttpURLConnection conn = (HttpURLConnection) uri.toURL()
				.openConnection();
		conn.setRequestMethod("POST"); //$NON-NLS-1$
		conn.setRequestProperty("Content-Type", //$NON-NLS-1$
				"application/json"); //$NON-NLS-1$
		conn.setDoOutput(true);
		try (OutputStream os = conn.getOutputStream()) {
			os.write(body.getBytes(StandardCharsets.UTF_8));
		}
		return conn;
	}

	private String readBody(HttpURLConnection conn) throws IOException {
		int code = conn.getResponseCode();
		java.io.InputStream is = (code >= 200 && code < 300)
				? conn.getInputStream()
				: conn.getErrorStream();
		if (is == null) {
			return ""; //$NON-NLS-1$
		}
		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(is, StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null) {
				sb.append(line);
			}
			return sb.toString();
		}
	}
}
