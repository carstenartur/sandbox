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

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.hibernate.SessionFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * REST endpoint for server health checks.
 * <p>
 * Returns the health status of the application and its database connection.
 * <ul>
 * <li>{@code GET /api/health} — returns health status as JSON</li>
 * </ul>
 */
public class HealthResource extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private final HibernateSessionFactoryProvider provider;

	/**
	 * Create a health check endpoint.
	 *
	 * @param provider
	 *            the session factory provider for database health checks
	 */
	public HealthResource(HibernateSessionFactoryProvider provider) {
		this.provider = provider;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		resp.setContentType("application/json"); //$NON-NLS-1$
		resp.setCharacterEncoding("UTF-8"); //$NON-NLS-1$

		boolean dbHealthy = checkDatabase();

		if (dbHealthy) {
			resp.setStatus(HttpServletResponse.SC_OK);
		} else {
			resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
		}

		try (PrintWriter w = resp.getWriter()) {
			w.write("{\"status\":\"" //$NON-NLS-1$
					+ (dbHealthy ? "UP" : "DOWN") //$NON-NLS-1$ //$NON-NLS-2$
					+ "\",\"database\":\"" //$NON-NLS-1$
					+ (dbHealthy ? "connected" : "disconnected") //$NON-NLS-1$ //$NON-NLS-2$
					+ "\"}"); //$NON-NLS-1$
		}
	}

	private boolean checkDatabase() {
		try {
			SessionFactory sf = provider.getSessionFactory();
			return sf != null && sf.isOpen();
		} catch (Exception e) {
			return false;
		}
	}
}
