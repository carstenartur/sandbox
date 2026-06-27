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
import org.eclipse.jgit.storage.hibernate.entity.GitCommitIndex;
import org.hibernate.Session;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;

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

		boolean dbOk = false;
		boolean searchOk = false;
		String dbError = null;
		String searchError = null;

		// Check database connectivity
		try (Session session = provider.getSessionFactory().openSession()) {
			session.createNativeQuery("SELECT 1", Integer.class) //$NON-NLS-1$
					.uniqueResult();
			dbOk = true;
		} catch (Exception e) {
			dbError = e.getMessage();
		}

		// Check search backend
		try (Session session = provider.getSessionFactory().openSession()) {
			SearchSession searchSession = Search.session(session);
			searchSession.search(GitCommitIndex.class)
					.where(f -> f.matchAll())
					.fetch(0, 0);
			searchOk = true;
		} catch (Exception e) {
			searchError = e.getMessage();
		}

		boolean allOk = dbOk && searchOk;
		resp.setStatus(allOk ? HttpServletResponse.SC_OK
				: HttpServletResponse.SC_SERVICE_UNAVAILABLE);

		try (PrintWriter w = resp.getWriter()) {
			StringBuilder json = new StringBuilder();
			json.append("{\"status\":\""); //$NON-NLS-1$
			json.append(allOk ? "UP" : "DOWN"); //$NON-NLS-1$ //$NON-NLS-2$
			json.append("\",\"database\":{\"status\":\""); //$NON-NLS-1$
			json.append(dbOk ? "UP" : "DOWN"); //$NON-NLS-1$ //$NON-NLS-2$
			json.append("\""); //$NON-NLS-1$
			if (dbError != null) {
				json.append(",\"error\":\""); //$NON-NLS-1$
				json.append(escapeJson(dbError));
				json.append("\""); //$NON-NLS-1$
			}
			json.append("},\"search\":{\"status\":\""); //$NON-NLS-1$
			json.append(searchOk ? "UP" : "DOWN"); //$NON-NLS-1$ //$NON-NLS-2$
			json.append("\""); //$NON-NLS-1$
			if (searchError != null) {
				json.append(",\"error\":\""); //$NON-NLS-1$
				json.append(escapeJson(searchError));
				json.append("\""); //$NON-NLS-1$
			}
			json.append("}}"); //$NON-NLS-1$
			w.write(json.toString());
		}
	}

	private static String escapeJson(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"':
				sb.append("\\\""); //$NON-NLS-1$
				break;
			case '\\':
				sb.append("\\\\"); //$NON-NLS-1$
				break;
			case '\n':
				sb.append("\\n"); //$NON-NLS-1$
				break;
			case '\r':
				sb.append("\\r"); //$NON-NLS-1$
				break;
			case '\t':
				sb.append("\\t"); //$NON-NLS-1$
				break;
			default:
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c)); //$NON-NLS-1$
				} else {
					sb.append(c);
				}
				break;
			}
		}
		return sb.toString();
	}
}
