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
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.storage.hibernate.service.IndexMigrationService;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * REST endpoint for administrative operations.
 * <ul>
 * <li>{@code POST /api/admin/reindex} — trigger full re-indexing of all
 * Hibernate Search entities</li>
 * </ul>
 */
public class AdminResource extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger
			.getLogger(AdminResource.class.getName());

	private final HibernateSessionFactoryProvider provider;

	/**
	 * Create an admin endpoint.
	 *
	 * @param provider
	 *            the session factory provider
	 */
	public AdminResource(HibernateSessionFactoryProvider provider) {
		this.provider = provider;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		resp.setContentType("application/json"); //$NON-NLS-1$
		resp.setCharacterEncoding("UTF-8"); //$NON-NLS-1$

		if (!isAuthorized(req)) {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			resp.setHeader("WWW-Authenticate", //$NON-NLS-1$
					"Bearer realm=\"admin\""); //$NON-NLS-1$
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
		} else {
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Unknown admin endpoint\"}"); //$NON-NLS-1$
			}
		}
	}

	private void handleReindex(HttpServletResponse resp) throws IOException {
		try {
			IndexMigrationService migrationService = new IndexMigrationService(
					provider.getSessionFactory());
			migrationService.reindexAll();
			resp.setStatus(HttpServletResponse.SC_OK);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"status\":\"Re-indexing completed\"}"); //$NON-NLS-1$
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.log(Level.WARNING, "Re-indexing interrupted", e); //$NON-NLS-1$
			resp.setStatus(
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Re-indexing was interrupted\"}"); //$NON-NLS-1$
			}
		} catch (Exception e) {
			LOG.log(Level.WARNING, "Re-indexing failed", e); //$NON-NLS-1$
			resp.setStatus(
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Re-indexing failed\"}"); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Check if the request is authorized for admin operations.
	 * <p>
	 * When {@code JGIT_ADMIN_TOKEN} is set, the request must include an
	 * {@code Authorization: Bearer <token>} header matching that value.
	 * When the variable is not set, all requests are allowed (development
	 * mode).
	 *
	 * @param req
	 *            the HTTP request
	 * @return {@code true} if the request is authorized
	 */
	private static boolean isAuthorized(HttpServletRequest req) {
		String expectedToken = System.getenv("JGIT_ADMIN_TOKEN"); //$NON-NLS-1$
		if (expectedToken == null || expectedToken.isEmpty()) {
			return true; // No token configured — allow all (dev mode)
		}
		String authHeader = req.getHeader("Authorization"); //$NON-NLS-1$
		if (authHeader == null
				|| !authHeader.startsWith("Bearer ")) { //$NON-NLS-1$
			return false;
		}
		String token = authHeader
				.substring("Bearer ".length()).trim(); //$NON-NLS-1$
		return MessageDigest.isEqual(
				expectedToken.getBytes(StandardCharsets.UTF_8),
				token.getBytes(StandardCharsets.UTF_8));
	}
}
