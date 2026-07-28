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

import org.eclipse.jgit.server.repository.SandboxRepositoryInfo;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.eclipse.jgit.server.resolver.HibernateRepositoryResolver;
import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** REST endpoint for repository creation and metadata lookup. */
public class RepositoryResource extends HttpServlet {

	private static final long serialVersionUID= 1L;
	private static final Logger LOG= Logger.getLogger(RepositoryResource.class.getName());

	private final SandboxRepositoryService repositories;
	private final Gson gson= new Gson();

	/** Creates a resource using the application-owned repository boundary. */
	public RepositoryResource(SandboxRepositoryService repositories) {
		this.repositories= repositories;
	}

	/**
	 * Compatibility constructor while the application still wires the legacy
	 * Hibernate provider separately for health, search and analytics.
	 */
	@SuppressWarnings("unused")
	public RepositoryResource(HibernateSessionFactoryProvider provider, HibernateRepositoryResolver resolver) {
		this(resolver.getRepositoryService());
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		prepare(response);
		StringBuilder input= new StringBuilder();
		try (BufferedReader reader= request.getReader()) {
			String line;
			while ((line= reader.readLine()) != null) {
				input.append(line);
			}
		}

		try {
			JsonObject body= JsonParser.parseString(input.toString()).getAsJsonObject();
			String name= body.has("name") ? body.get("name").getAsString() : null; //$NON-NLS-1$ //$NON-NLS-2$
			if (name == null || name.isBlank()) {
				writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Repository name is required"); //$NON-NLS-1$
				return;
			}
			String normalized= name.strip();
			SandboxRepositoryInfo info= body.has("description") //$NON-NLS-1$
					? repositories.setDescription(normalized, body.get("description").getAsString()) //$NON-NLS-1$
					: repositories.info(normalized);
			response.setStatus(HttpServletResponse.SC_CREATED);
			writeInfo(response, info);
		} catch (Exception exception) {
			LOG.log(Level.WARNING, "Error creating repository", exception); //$NON-NLS-1$
			writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, exception.getMessage());
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		prepare(response);
		String pathInfo= request.getPathInfo();
		if (pathInfo == null || pathInfo.equals("/")) { //$NON-NLS-1$
			response.setStatus(HttpServletResponse.SC_OK);
			try (PrintWriter writer= response.getWriter()) {
				JsonObject message= new JsonObject();
				message.addProperty("message", "Use POST to create repos or GET /repos/{name} for info"); //$NON-NLS-1$ //$NON-NLS-2$
				writer.write(gson.toJson(message));
			}
			return;
		}

		String repositoryName= pathInfo.substring(1);
		try {
			SandboxRepositoryInfo info= repositories.info(repositoryName);
			response.setStatus(HttpServletResponse.SC_OK);
			writeInfo(response, info);
		} catch (Exception exception) {
			LOG.log(Level.WARNING, "Error retrieving repository: " + repositoryName, exception); //$NON-NLS-1$
			writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, exception.getMessage());
		}
	}

	private static void prepare(HttpServletResponse response) {
		response.setContentType("application/json"); //$NON-NLS-1$
		response.setCharacterEncoding("UTF-8"); //$NON-NLS-1$
	}

	private void writeInfo(HttpServletResponse response, SandboxRepositoryInfo info) throws IOException {
		try (PrintWriter writer= response.getWriter()) {
			JsonObject result= new JsonObject();
			result.addProperty("name", info.name()); //$NON-NLS-1$
			result.addProperty("description", info.description()); //$NON-NLS-1$
			writer.write(gson.toJson(result));
		}
	}

	private void writeError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		try (PrintWriter writer= response.getWriter()) {
			JsonObject error= new JsonObject();
			error.addProperty("error", message); //$NON-NLS-1$
			writer.write(gson.toJson(error));
		}
	}
}
