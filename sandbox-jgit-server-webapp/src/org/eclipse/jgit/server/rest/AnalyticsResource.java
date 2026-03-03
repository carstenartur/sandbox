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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.storage.hibernate.service.GitDatabaseQueryService;
import org.eclipse.jgit.storage.hibernate.service.GitDatabaseQueryService.AuthorStats;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * REST endpoint for repository analytics.
 * <ul>
 * <li>{@code GET /api/analytics/authors?repo=...} — author commit
 * statistics</li>
 * <li>{@code GET /api/analytics/objects?repo=...} — object type counts</li>
 * <li>{@code GET /api/analytics/packs?repo=...} — pack file statistics</li>
 * </ul>
 */
public class AnalyticsResource extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger
			.getLogger(AnalyticsResource.class.getName());

	private final HibernateSessionFactoryProvider provider;

	private final Gson gson = new Gson();

	/**
	 * Create an analytics endpoint.
	 *
	 * @param provider
	 *            the session factory provider
	 */
	public AnalyticsResource(HibernateSessionFactoryProvider provider) {
		this.provider = provider;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		resp.setContentType("application/json"); //$NON-NLS-1$
		resp.setCharacterEncoding("UTF-8"); //$NON-NLS-1$

		String pathInfo = req.getPathInfo();
		if (pathInfo == null) {
			pathInfo = "/"; //$NON-NLS-1$
		}

		String repo = req.getParameter("repo"); //$NON-NLS-1$
		if (repo == null || repo.isEmpty()) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Parameter 'repo' is required\"}"); //$NON-NLS-1$
			}
			return;
		}

		try {
			GitDatabaseQueryService queryService = new GitDatabaseQueryService(
					provider.getSessionFactory());

			if (pathInfo.startsWith("/authors")) { //$NON-NLS-1$
				handleAuthorStats(queryService, repo, resp);
			} else if (pathInfo.startsWith("/objects")) { //$NON-NLS-1$
				handleObjectStats(queryService, repo, resp);
			} else if (pathInfo.startsWith("/packs")) { //$NON-NLS-1$
				handlePackStats(queryService, repo, resp);
			} else {
				resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
				try (PrintWriter w = resp.getWriter()) {
					w.write("{\"error\":\"Unknown analytics endpoint\"}"); //$NON-NLS-1$
				}
			}
		} catch (Exception e) {
			LOG.log(Level.WARNING, "Analytics error", e); //$NON-NLS-1$
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Analytics query failed\"}"); //$NON-NLS-1$
			}
		}
	}

	private void handleAuthorStats(GitDatabaseQueryService queryService,
			String repo, HttpServletResponse resp) throws IOException {
		List<AuthorStats> stats = queryService.getAuthorStatistics(repo);

		JsonObject response = new JsonObject();
		response.addProperty("repository", repo); //$NON-NLS-1$

		JsonArray authors = new JsonArray();
		for (AuthorStats as : stats) {
			JsonObject author = new JsonObject();
			author.addProperty("name", as.getAuthorName()); //$NON-NLS-1$
			author.addProperty("email", as.getAuthorEmail()); //$NON-NLS-1$
			author.addProperty("commits", as.getCommitCount()); //$NON-NLS-1$
			authors.add(author);
		}
		response.add("authors", authors); //$NON-NLS-1$

		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void handleObjectStats(GitDatabaseQueryService queryService,
			String repo, HttpServletResponse resp) throws IOException {
		JsonObject response = new JsonObject();
		response.addProperty("repository", repo); //$NON-NLS-1$

		JsonObject counts = new JsonObject();
		counts.addProperty("commits", //$NON-NLS-1$
				queryService.countObjectsByType(repo, 1));
		counts.addProperty("trees", //$NON-NLS-1$
				queryService.countObjectsByType(repo, 2));
		counts.addProperty("blobs", //$NON-NLS-1$
				queryService.countObjectsByType(repo, 3));
		counts.addProperty("tags", //$NON-NLS-1$
				queryService.countObjectsByType(repo, 4));
		response.add("objectCounts", counts); //$NON-NLS-1$

		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void handlePackStats(GitDatabaseQueryService queryService,
			String repo, HttpServletResponse resp) throws IOException {
		JsonObject response = new JsonObject();
		response.addProperty("repository", repo); //$NON-NLS-1$
		response.addProperty("packCount", //$NON-NLS-1$
				queryService.countPacks(repo));
		response.addProperty("totalSizeBytes", //$NON-NLS-1$
				queryService.getTotalPackSize(repo));

		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}
}
