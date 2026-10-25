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
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.server.repository.SandboxStorageStatistics;
import org.eclipse.jgit.server.repository.CopiedSandboxStorageStatistics;
import org.eclipse.jgit.server.history.CopiedSandboxHistorySearch;
import org.eclipse.jgit.server.history.SandboxHistorySearch;
import org.eclipse.jgit.server.history.SandboxHistorySearch.AuthorCount;
import org.hibernate.SessionFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** REST endpoint for repository analytics. */
public class AnalyticsResource extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = Logger.getLogger(AnalyticsResource.class.getName());
	private final SandboxStorageStatistics statistics;
	private final SandboxHistorySearch history;
	private final Gson gson = new Gson();

	/** Creates an analytics endpoint from the application-owned native factory. */
	public AnalyticsResource(SessionFactory sessionFactory) {
		this(sessionFactory, new CopiedSandboxHistorySearch(sessionFactory));
	}

	/** Creates analytics with the selected history projection. */
	public AnalyticsResource(SessionFactory sessionFactory, SandboxHistorySearch history) {
		this(history, new CopiedSandboxStorageStatistics(sessionFactory));
	}

	/** Uses the selected backend for history and physical inventory. */
	public AnalyticsResource(SandboxHistorySearch history, SandboxStorageStatistics statistics) {
		this.statistics= Objects.requireNonNull(statistics);
		this.history= Objects.requireNonNull(history);
	}

	/** @deprecated application wiring should pass the native {@link SessionFactory}. */
	@Deprecated(forRemoval = true)
	public AnalyticsResource(HibernateSessionFactoryProvider provider) {
		this(Objects.requireNonNull(provider, "provider").getSessionFactory()); //$NON-NLS-1$
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
			if (pathInfo.startsWith("/authors")) { //$NON-NLS-1$
				handleAuthorStats(repo, resp);
			} else if (pathInfo.startsWith("/objects")) { //$NON-NLS-1$
				handleObjectStats(repo, resp);
			} else if (pathInfo.startsWith("/packs")) { //$NON-NLS-1$
				handlePackStats(repo, resp);
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

	private void handleAuthorStats(String repo,
			HttpServletResponse resp) throws IOException {
		List<AuthorCount> stats = history.authors(repo);
		JsonObject response = new JsonObject();
		response.addProperty("repository", repo); //$NON-NLS-1$
		JsonArray authors = new JsonArray();
		for (AuthorCount as : stats) {
			JsonObject author = new JsonObject();
			author.addProperty("name", as.name()); //$NON-NLS-1$
			author.addProperty("email", as.email()); //$NON-NLS-1$
			author.addProperty("commits", as.commits()); //$NON-NLS-1$
			authors.add(author);
		}
		response.add("authors", authors); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void handleObjectStats(String repo,
			HttpServletResponse resp) throws IOException {
		JsonObject response = new JsonObject();
		response.addProperty("repository", repo); //$NON-NLS-1$
		var inventory = statistics.objects(repo);
		JsonObject counts = new JsonObject();
		counts.addProperty("commits", inventory.commits()); //$NON-NLS-1$
		counts.addProperty("trees", inventory.trees()); //$NON-NLS-1$
		counts.addProperty("blobs", inventory.blobs()); //$NON-NLS-1$
		counts.addProperty("tags", inventory.tags()); //$NON-NLS-1$
		response.add("objectCounts", counts); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void handlePackStats(String repo,
			HttpServletResponse resp) throws IOException {
		JsonObject response = new JsonObject();
		response.addProperty("repository", repo); //$NON-NLS-1$
		var inventory = statistics.packs(repo);
		response.addProperty("packCount", inventory.packCount()); //$NON-NLS-1$
		response.addProperty("totalSizeBytes", inventory.totalSizeBytes()); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}
}
