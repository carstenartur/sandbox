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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.server.resolver.HibernateRepositoryResolver;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.storage.hibernate.repository.HibernateRepository;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * REST endpoint for repository CRUD operations.
 * <ul>
 * <li>{@code POST /api/repos} — create a new repository (body:
 * {"name":"...","description":"..."})</li>
 * <li>{@code GET /api/repos/{name}} — get repository info</li>
 * </ul>
 */
public class RepositoryResource extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger
			.getLogger(RepositoryResource.class.getName());

	private final HibernateRepositoryResolver resolver;

	private final Gson gson = new Gson();

	/**
	 * Create a repository resource endpoint.
	 *
	 * @param provider
	 *            the session factory provider (reserved for future use)
	 * @param resolver
	 *            the repository resolver
	 */
	@SuppressWarnings("unused")
	public RepositoryResource(HibernateSessionFactoryProvider provider,
			HibernateRepositoryResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		resp.setContentType("application/json"); //$NON-NLS-1$
		resp.setCharacterEncoding("UTF-8"); //$NON-NLS-1$

		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = req.getReader()) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
		}

		try {
			JsonObject body = JsonParser.parseString(sb.toString())
					.getAsJsonObject();
			String name = body.has("name") ? body.get("name").getAsString() //$NON-NLS-1$ //$NON-NLS-2$
					: null;

			if (name == null || name.trim().isEmpty()) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				try (PrintWriter w = resp.getWriter()) {
					JsonObject error = new JsonObject();
					error.addProperty("error", //$NON-NLS-1$
							"Repository name is required"); //$NON-NLS-1$
					w.write(gson.toJson(error));
				}
				return;
			}

			HibernateRepository repo = resolver
					.getOrCreateRepository(name.trim());
			String description = body.has("description") //$NON-NLS-1$
					? body.get("description").getAsString() //$NON-NLS-1$
					: null;
			if (description != null) {
				repo.setGitwebDescription(description);
			}

			resp.setStatus(HttpServletResponse.SC_CREATED);
			try (PrintWriter w = resp.getWriter()) {
				JsonObject result = new JsonObject();
				result.addProperty("name", name.trim()); //$NON-NLS-1$
				result.addProperty("description", //$NON-NLS-1$
						repo.getGitwebDescription());
				w.write(gson.toJson(result));
			}
		} catch (Exception e) {
			LOG.log(Level.WARNING, "Error creating repository", e); //$NON-NLS-1$
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try (PrintWriter w = resp.getWriter()) {
				JsonObject error = new JsonObject();
				error.addProperty("error", //$NON-NLS-1$
						e.getMessage());
				w.write(gson.toJson(error));
			}
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		resp.setContentType("application/json"); //$NON-NLS-1$
		resp.setCharacterEncoding("UTF-8"); //$NON-NLS-1$

		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.equals("/")) { //$NON-NLS-1$
			resp.setStatus(HttpServletResponse.SC_OK);
			try (PrintWriter w = resp.getWriter()) {
				JsonObject msg = new JsonObject();
				msg.addProperty("message", //$NON-NLS-1$
						"Use POST to create repos or GET /repos/{name} for info"); //$NON-NLS-1$
				w.write(gson.toJson(msg));
			}
			return;
		}

		String repoName = pathInfo.substring(1);
		try {
			HibernateRepository repo = resolver
					.getOrCreateRepository(repoName);

			JsonObject result = new JsonObject();
			result.addProperty("name", repoName); //$NON-NLS-1$
			result.addProperty("description", //$NON-NLS-1$
					repo.getGitwebDescription());

			resp.setStatus(HttpServletResponse.SC_OK);
			try (PrintWriter w = resp.getWriter()) {
				w.write(gson.toJson(result));
			}
		} catch (Exception e) {
			LOG.log(Level.WARNING, "Error retrieving repository: " //$NON-NLS-1$
					+ repoName, e);
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try (PrintWriter w = resp.getWriter()) {
				JsonObject error = new JsonObject();
				error.addProperty("error", //$NON-NLS-1$
						e.getMessage());
				w.write(gson.toJson(error));
			}
		}
	}

}
