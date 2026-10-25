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
package org.eclipse.jgit.server.rest;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.server.history.SandboxHistoryMaintenance;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.eclipse.jgit.server.repository.SandboxRepositoryNames;
import org.hibernate.SessionFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** REST endpoint for administrative operations. */
public class AdminResource extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = Logger.getLogger(AdminResource.class.getName());
	private final SessionFactory sessionFactory;
	private final SandboxHistoryMaintenance maintenance;
	private final SandboxRepositoryService repositories;
	private final Supplier<String> adminToken;

	/** Creates an admin endpoint from the application-owned native factory. */
	public AdminResource(SessionFactory sessionFactory) {
		this(sessionFactory, SandboxHistoryMaintenance.NONE);
	}

	/** Uses the selected context's writer coordination when refreshing projections. */
	public AdminResource(SessionFactory sessionFactory, SandboxHistoryMaintenance maintenance) {
		this(sessionFactory, maintenance, null);
	}

	/** Uses scoped existing-only readers for an explicit Git-derived rebuild. */
	public AdminResource(SessionFactory sessionFactory, SandboxHistoryMaintenance maintenance, SandboxRepositoryService repositories) {
		this(sessionFactory, maintenance, repositories, () -> System.getenv("JGIT_ADMIN_TOKEN"));
	}

	AdminResource(SessionFactory sessionFactory, SandboxHistoryMaintenance maintenance,
			SandboxRepositoryService repositories, Supplier<String> adminToken) {
		this.sessionFactory= Objects.requireNonNull(sessionFactory, "sessionFactory"); //$NON-NLS-1$
		this.maintenance= Objects.requireNonNull(maintenance);
		this.repositories= repositories;
		this.adminToken= Objects.requireNonNull(adminToken);
	}

	/** @deprecated application wiring should pass the native {@link SessionFactory}. */
	@Deprecated(forRemoval = true)
	public AdminResource(HibernateSessionFactoryProvider provider) {
		this(Objects.requireNonNull(provider, "provider").getSessionFactory()); //$NON-NLS-1$
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setContentType("application/json"); //$NON-NLS-1$
		resp.setCharacterEncoding("UTF-8"); //$NON-NLS-1$
		if (!isAuthorized(req)) {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			resp.setHeader("WWW-Authenticate", "Bearer realm=\"admin\""); //$NON-NLS-1$ //$NON-NLS-2$
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Unauthorized. Set JGIT_ADMIN_TOKEN and pass as Bearer token.\"}"); //$NON-NLS-1$
			}
			return;
		}
		String pathInfo = req.getPathInfo();
		if (pathInfo == null) {
			pathInfo = "/"; //$NON-NLS-1$
		}
		if (pathInfo.startsWith("/reindex")) { //$NON-NLS-1$
			handleReindex(resp);
		} else if (pathInfo.equals("/rebuild")) {
			handleRebuild(req, resp);
		} else {
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Unknown admin endpoint\"}"); //$NON-NLS-1$
			}
		}
	}

	private void handleRebuild(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String name = request.getParameter("repo");
		if (name == null || name.isBlank()) {
			writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing repository");
			return;
		}
		final String canonical;
		try {
			canonical = SandboxRepositoryNames.normalize(name);
		} catch (IllegalArgumentException invalid) {
			writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid repository");
			return;
		}
		try {
			if (repositories == null) throw new UnsupportedOperationException("No existing-only reader configured");
			var result = repositories.withExistingRepository(name, repository -> {
				try {
					return maintenance.rebuild(canonical, repository);
				} catch (IOException | RuntimeException failure) {
					// Missing objects/configuration failures during an opened repository's rebuild
					// must not be confused with a missing repository identity or invalid input.
					throw new IOException("Git-derived rebuild failed", failure);
				}
			});
			var body = new com.google.gson.JsonObject();
			body.addProperty("status", "History rebuild completed");
			body.addProperty("repository", name);
			body.addProperty("removedProjections", result.removedProjections());
			body.addProperty("indexedCommits", result.indexedCommits());
			response.setStatus(HttpServletResponse.SC_OK);
			try (var writer = response.getWriter()) { writer.write(body.toString()); }
		} catch (FileNotFoundException missing) {
			writeError(response, HttpServletResponse.SC_NOT_FOUND, "Repository not found");
		} catch (UnsupportedOperationException unavailable) {
			writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "History rebuild unavailable");
		} catch (IOException | RuntimeException failure) {
			LOG.log(Level.WARNING, "Git-history rebuild failed", failure);
			writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "History rebuild failed");
		}
	}

	private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		var body = new com.google.gson.JsonObject(); body.addProperty("error", message);
		try (var writer = response.getWriter()) { writer.write(body.toString()); }
	}

	private void handleReindex(HttpServletResponse resp) throws IOException {
		try {
			maintenance.refreshIndexes(sessionFactory);
			resp.setStatus(HttpServletResponse.SC_OK);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"status\":\"Re-indexing completed\"}"); //$NON-NLS-1$
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.log(Level.WARNING, "Re-indexing interrupted", e); //$NON-NLS-1$
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Re-indexing was interrupted\"}"); //$NON-NLS-1$
			}
		} catch (Exception e) {
			LOG.log(Level.WARNING, "Re-indexing failed", e); //$NON-NLS-1$
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Re-indexing failed\"}"); //$NON-NLS-1$
			}
		}
	}

	private boolean isAuthorized(HttpServletRequest req) {
		String expectedToken = adminToken.get();
		if (expectedToken == null || expectedToken.isEmpty()) {
			LOG.log(Level.WARNING, "Admin access denied: JGIT_ADMIN_TOKEN is not set"); //$NON-NLS-1$
			return false;
		}
		String authHeader = req.getHeader("Authorization"); //$NON-NLS-1$
		if (authHeader == null || !authHeader.startsWith("Bearer ")) { //$NON-NLS-1$
			return false;
		}
		String token = authHeader.substring("Bearer ".length()).trim(); //$NON-NLS-1$
		return MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.UTF_8),
				token.getBytes(StandardCharsets.UTF_8));
	}
}
