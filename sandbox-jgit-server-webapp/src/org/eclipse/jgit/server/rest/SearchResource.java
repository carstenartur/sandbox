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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import org.eclipse.jgit.storage.hibernate.entity.FilePathHistory;
import org.eclipse.jgit.storage.hibernate.entity.GitCommitIndex;
import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;
import org.eclipse.jgit.storage.hibernate.search.EmbeddingService;
import org.eclipse.jgit.storage.hibernate.service.ApiChangeEntry;
import org.eclipse.jgit.storage.hibernate.service.ApiDiffResult;
import org.eclipse.jgit.storage.hibernate.service.GitDatabaseQueryService;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * REST endpoint for full-text search over Git commit data and Java source.
 * <ul>
 * <li>{@code GET /api/search/commits?repo=...&amp;q=...} — search commit
 * messages</li>
 * <li>{@code GET /api/search/paths?repo=...&amp;q=...} — search changed
 * paths</li>
 * <li>{@code GET /api/search/types?repo=...&amp;q=...} — search Java
 * types by name or FQN</li>
 * <li>{@code GET /api/search/symbols?repo=...&amp;q=...} — search methods
 * and fields</li>
 * <li>{@code GET /api/search/hierarchy?repo=...&amp;q=...} — find types
 * by supertype</li>
 * <li>{@code GET /api/search/source?repo=...&amp;q=...} — full-text
 * search across source snippets</li>
 * <li>{@code GET /api/search/semantic?repo=...&amp;q=...} — semantic
 * (vector) search using sentence embeddings</li>
 * <li>{@code GET /api/search/hybrid?repo=...&amp;q=...} — combined
 * full-text + semantic search with rank fusion</li>
 * <li>{@code GET /api/search/similar?repo=...&amp;blobId=...} — find
 * semantically similar code to a given blob</li>
 * </ul>
 */
public class SearchResource extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger LOG = Logger
			.getLogger(SearchResource.class.getName());

	private final HibernateSessionFactoryProvider provider;

	private final EmbeddingService embeddingService;

	private final Gson gson = new Gson();

	private static final int MAX_QUERY_LENGTH = 1000;

	private static final int MAX_LIMIT = 100;

	private static final int DEFAULT_LIMIT = 20;

	/**
	 * Create a search endpoint.
	 *
	 * @param provider
	 *            the session factory provider
	 */
	public SearchResource(HibernateSessionFactoryProvider provider) {
		this.provider = provider;
		this.embeddingService = new EmbeddingService();
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
		String query = req.getParameter("q"); //$NON-NLS-1$
		int offset = parseIntParam(req, "offset", 0); //$NON-NLS-1$
		int limit = parseIntParam(req, "limit", DEFAULT_LIMIT); //$NON-NLS-1$

		// Paths that only need 'repo' (no 'q' required)
		boolean repoOnly = pathInfo.startsWith("/migration/deprecated") //$NON-NLS-1$
				|| pathInfo.startsWith("/deprecation-timeline") //$NON-NLS-1$
				|| pathInfo.startsWith("/analytics/authors") //$NON-NLS-1$
				|| pathInfo.startsWith("/analytics/monster-classes") //$NON-NLS-1$
				|| pathInfo.startsWith("/analytics/dead-code") //$NON-NLS-1$
				|| pathInfo.startsWith("/analytics/test-ratio"); //$NON-NLS-1$

		// Paths that need 'repo', 'commitA', 'commitB'
		boolean commitDiff = pathInfo.startsWith("/api-diff"); //$NON-NLS-1$

		if (repo == null || repo.isEmpty()) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Parameter 'repo' is required\"}"); //$NON-NLS-1$
			}
			return;
		}

		if (!repoOnly && !commitDiff
				&& (query == null || query.isEmpty())) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Parameters 'repo' and 'q' are required\"}"); //$NON-NLS-1$
			}
			return;
		}

		// Input validation: limit query length to prevent abuse
		if (query != null && query.length() > MAX_QUERY_LENGTH) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Query too long (max " //$NON-NLS-1$
						+ MAX_QUERY_LENGTH + " characters)\"}"); //$NON-NLS-1$
			}
			return;
		}

		// Enforce pagination limits
		if (offset < 0) {
			offset = 0;
		}
		if (limit < 1) {
			limit = DEFAULT_LIMIT;
		}
		if (limit > MAX_LIMIT) {
			limit = MAX_LIMIT;
		}

		// Sanitize repo and query parameters
		repo = sanitizeInput(repo);
		if (query != null) {
			query = sanitizeInput(query);
		}

		try {
			GitDatabaseQueryService queryService = new GitDatabaseQueryService(
					provider.getSessionFactory());
			queryService.setEmbeddingService(embeddingService);

			if (pathInfo.startsWith("/commits")) { //$NON-NLS-1$
				handleCommitSearch(queryService, repo, query, offset,
						limit, resp);
			} else if (pathInfo.startsWith("/paths")) { //$NON-NLS-1$
				handlePathSearch(queryService, repo, query, offset,
						limit, resp);
			} else if (pathInfo.startsWith("/types")) { //$NON-NLS-1$
				String module = req.getParameter("module"); //$NON-NLS-1$
				if (module != null && !module.isEmpty()) {
					module = sanitizeInput(module);
					handleTypeSearchWithModule(queryService, repo, query,
							module, offset, limit, resp);
				} else {
					handleTypeSearch(queryService, repo, query, offset,
							limit, resp);
				}
			} else if (pathInfo.startsWith("/symbols")) { //$NON-NLS-1$
				handleSymbolSearch(queryService, repo, query, offset,
						limit, resp);
			} else if (pathInfo.startsWith("/hierarchy")) { //$NON-NLS-1$
				handleHierarchySearch(queryService, repo, query, offset,
						limit, resp);
			} else if (pathInfo.startsWith("/source")) { //$NON-NLS-1$
				handleSourceSearch(queryService, repo, query, offset,
						limit, resp);
			} else if (pathInfo.startsWith("/annotations")) { //$NON-NLS-1$
				handleAnnotationSearch(queryService, repo, query, offset,
						limit, resp);
			} else if (pathInfo.startsWith("/docs")) { //$NON-NLS-1$
				handleSearch(queryService, repo, query, offset, limit,
						resp, "docs"); //$NON-NLS-1$
			} else if (pathInfo.startsWith("/references")) { //$NON-NLS-1$
				handleSearch(queryService, repo, query, offset, limit,
						resp, "references"); //$NON-NLS-1$
			} else if (pathInfo.startsWith("/strings")) { //$NON-NLS-1$
				handleSearch(queryService, repo, query, offset, limit,
						resp, "strings"); //$NON-NLS-1$
			} else if (pathInfo.startsWith("/filepaths")) { //$NON-NLS-1$
				handleFilePathSearch(queryService, repo, query, offset,
						limit, resp);
			} else if (pathInfo.startsWith("/filehistory")) { //$NON-NLS-1$
				String path = req.getParameter("path"); //$NON-NLS-1$
				if (path != null && !path.isEmpty()) {
					path = sanitizeInput(path);
					handleFileHistory(queryService, repo, path, offset,
							limit, resp);
				} else {
					handleFilePathSearch(queryService, repo, query,
							offset, limit, resp);
				}
			} else if (pathInfo.startsWith("/fqn")) { //$NON-NLS-1$
				String fqnFileType = req.getParameter("fileType"); //$NON-NLS-1$
				if (fqnFileType != null) {
					fqnFileType = sanitizeInput(fqnFileType);
				}
				handleFqnSearch(queryService, repo, query, fqnFileType,
						offset, limit, resp);
			} else if (pathInfo.startsWith("/semantic")) { //$NON-NLS-1$
				handleSemanticSearch(queryService, repo, query, limit,
						resp);
			} else if (pathInfo.startsWith("/hybrid")) { //$NON-NLS-1$
				handleHybridSearch(queryService, repo, query, limit,
						resp);
			} else if (pathInfo.startsWith("/similar")) { //$NON-NLS-1$
				String blobId = req.getParameter("blobId"); //$NON-NLS-1$
				if (blobId == null || blobId.isEmpty()) {
					resp.setStatus(
							HttpServletResponse.SC_BAD_REQUEST);
					try (PrintWriter w = resp.getWriter()) {
						w.write("{\"error\":\"Parameter 'blobId' is required for similar search\"}"); //$NON-NLS-1$
					}
				} else {
					blobId = sanitizeInput(blobId);
					handleSimilarSearch(queryService, repo, blobId,
							limit, resp);
				}
			} else if (pathInfo.startsWith("/migration/summary")) { //$NON-NLS-1$
				handleMigrationImpactSummary(queryService, repo, query,
						resp);
			} else if (pathInfo.startsWith("/migration/deprecated")) { //$NON-NLS-1$
				handleDeprecatedApiUsage(queryService, repo, offset,
						limit, resp);
			} else if (pathInfo.startsWith("/migration")) { //$NON-NLS-1$
				handleMigrationImpact(queryService, repo, query, offset,
						limit, resp);
			} else if (pathInfo.startsWith("/api-diff/public")) { //$NON-NLS-1$
				handlePublicApiChanges(queryService, repo, req, resp);
			} else if (pathInfo.startsWith("/api-diff")) { //$NON-NLS-1$
				handleApiDiff(queryService, repo, req, resp);
			} else if (pathInfo.startsWith("/deprecation-timeline")) { //$NON-NLS-1$
				handleDeprecationTimeline(queryService, repo, offset,
						limit, resp);
			} else if (pathInfo.startsWith("/analytics/authors")) { //$NON-NLS-1$
				handleAuthorTypeStatistics(queryService, repo, resp);
			} else if (pathInfo.startsWith("/analytics/complexity")) { //$NON-NLS-1$
				handleCodeComplexityTrend(queryService, repo, query,
						resp);
			} else if (pathInfo.startsWith("/analytics/monster-classes")) { //$NON-NLS-1$
				int threshold = parseIntParam(req, "threshold", 500); //$NON-NLS-1$
				handleMonsterClasses(queryService, repo, threshold,
						offset, limit, resp);
			} else if (pathInfo.startsWith("/analytics/dead-code")) { //$NON-NLS-1$
				handleDeadCodeCandidates(queryService, repo, resp);
			} else if (pathInfo.startsWith("/analytics/test-ratio")) { //$NON-NLS-1$
				handleTestCoverageProxy(queryService, repo, resp);
			} else {
				// Default: search commits
				handleCommitSearch(queryService, repo, query, offset,
						limit, resp);
			}
		} catch (RuntimeException | IOException e) {
			LOG.log(Level.WARNING, "Search error", e); //$NON-NLS-1$
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Search failed\"}"); //$NON-NLS-1$
			}
		}
	}

	private void handleCommitSearch(GitDatabaseQueryService queryService,
			String repo, String query, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<GitCommitIndex> results = queryService
				.searchCommitMessages(repo, query, offset, limit);

		JsonObject response = new JsonObject();
		response.addProperty("query", query); //$NON-NLS-1$
		response.addProperty("repository", repo); //$NON-NLS-1$
		response.addProperty("totalResults", results.size()); //$NON-NLS-1$

		JsonArray items = new JsonArray();
		for (GitCommitIndex ci : results) {
			JsonObject item = new JsonObject();
			item.addProperty("objectId", ci.getObjectId()); //$NON-NLS-1$
			item.addProperty("message", ci.getCommitMessage()); //$NON-NLS-1$
			item.addProperty("author", ci.getAuthorName()); //$NON-NLS-1$
			item.addProperty("authorEmail", ci.getAuthorEmail()); //$NON-NLS-1$
			items.add(item);
		}
		response.add("results", items); //$NON-NLS-1$

		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void handlePathSearch(GitDatabaseQueryService queryService,
			String repo, String query, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<GitCommitIndex> results = queryService
				.searchByChangedPath(repo, query, offset, limit);

		JsonObject response = new JsonObject();
		response.addProperty("query", query); //$NON-NLS-1$
		response.addProperty("repository", repo); //$NON-NLS-1$
		response.addProperty("totalResults", results.size()); //$NON-NLS-1$

		JsonArray items = new JsonArray();
		for (GitCommitIndex ci : results) {
			JsonObject item = new JsonObject();
			item.addProperty("objectId", ci.getObjectId()); //$NON-NLS-1$
			item.addProperty("message", ci.getCommitMessage()); //$NON-NLS-1$
			item.addProperty("changedPaths", ci.getChangedPaths()); //$NON-NLS-1$
			items.add(item);
		}
		response.add("results", items); //$NON-NLS-1$

		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void handleTypeSearch(GitDatabaseQueryService queryService,
			String repo, String query, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<JavaBlobIndex> results = queryService.searchByType(repo,
				query, offset, limit);
		writeJavaBlobResponse(results, repo, query, offset, limit, resp);
	}

	private void handleTypeSearchWithModule(
			GitDatabaseQueryService queryService, String repo,
			String query, String module, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<JavaBlobIndex> results = queryService
				.searchByTypeWithModule(repo, query, module, offset, limit);
		writeJavaBlobResponse(results, repo, query, offset, limit, resp);
	}

	private void handleAnnotationSearch(
			GitDatabaseQueryService queryService, String repo,
			String query, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<JavaBlobIndex> results = queryService
				.searchByAnnotation(repo, query, offset, limit);
		writeJavaBlobResponse(results, repo, query, offset, limit, resp);
	}

	private void handleSymbolSearch(GitDatabaseQueryService queryService,
			String repo, String query, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<JavaBlobIndex> results = queryService.searchBySymbol(repo,
				query, offset, limit);
		writeJavaBlobResponse(results, repo, query, offset, limit, resp);
	}

	private void handleHierarchySearch(GitDatabaseQueryService queryService,
			String repo, String query, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<JavaBlobIndex> results = queryService.searchByHierarchy(repo,
				query, offset, limit);
		writeJavaBlobResponse(results, repo, query, offset, limit, resp);
	}

	private void handleSourceSearch(GitDatabaseQueryService queryService,
			String repo, String query, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<JavaBlobIndex> results = queryService
				.searchSourceContent(repo, query, offset, limit);
		writeJavaBlobResponse(results, repo, query, offset, limit, resp);
	}

	private void handleSearch(GitDatabaseQueryService service,
			String repo, String query, int offset, int limit,
			HttpServletResponse resp, String type) throws IOException {
		List<JavaBlobIndex> results;
		switch (type) {
		case "docs": //$NON-NLS-1$
			results = service.searchByDocumentation(repo, query,
					offset, limit);
			break;
		case "references": //$NON-NLS-1$
			results = service.searchByReferencedType(repo, query,
					offset, limit);
			break;
		case "strings": //$NON-NLS-1$
			results = service.searchByStringLiteral(repo, query,
					offset, limit);
			break;
		default:
			results = List.of();
			break;
		}
		writeJavaBlobResponse(results, repo, query, offset, limit, resp);
	}

	private void handleFilePathSearch(GitDatabaseQueryService queryService,
			String repo, String query, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<FilePathHistory> results = queryService
				.searchFilePath(repo, query, offset, limit);
		writeFilePathResponse(results, repo, query, offset, limit, resp);
	}

	private void handleFileHistory(GitDatabaseQueryService queryService,
			String repo, String path, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<FilePathHistory> results = queryService
				.getFileHistory(repo, path, offset, limit);
		writeFilePathResponse(results, repo, path, offset, limit, resp);
	}

	private void handleFqnSearch(GitDatabaseQueryService queryService,
			String repo, String query, String fileType, int offset,
			int limit, HttpServletResponse resp) throws IOException {
		List<JavaBlobIndex> results = queryService
				.searchFqnAcrossTypes(repo, query, fileType, offset, limit);
		writeJavaBlobResponse(results, repo, query, offset, limit, resp);
	}

	private void handleSemanticSearch(
			GitDatabaseQueryService queryService, String repo,
			String query, int topK, HttpServletResponse resp)
			throws IOException {
		List<JavaBlobIndex> results = queryService.semanticSearch(repo,
				query, topK);
		writeJavaBlobResponse(results, repo, query, 0, topK, resp);
	}

	private void handleHybridSearch(
			GitDatabaseQueryService queryService, String repo,
			String query, int topK, HttpServletResponse resp)
			throws IOException {
		List<JavaBlobIndex> results = queryService.hybridSearch(repo,
				query, topK);
		writeJavaBlobResponse(results, repo, query, 0, topK, resp);
	}

	private void handleSimilarSearch(
			GitDatabaseQueryService queryService, String repo,
			String blobId, int topK, HttpServletResponse resp)
			throws IOException {
		List<JavaBlobIndex> results = queryService.findSimilarCode(repo,
				blobId, topK);
		writeJavaBlobResponse(results, repo, blobId, 0, topK, resp);
	}

	private void handleMigrationImpact(GitDatabaseQueryService queryService,
			String repo, String importPrefix, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<JavaBlobIndex> results = queryService.getMigrationImpact(
				repo, importPrefix, offset, limit);
		writeJavaBlobResponse(results, repo, importPrefix, offset, limit,
				resp);
	}

	private void handleMigrationImpactSummary(
			GitDatabaseQueryService queryService, String repo,
			String importPrefix, HttpServletResponse resp)
			throws IOException {
		Map<String, Object> summary = queryService
				.getMigrationImpactSummary(repo, importPrefix);
		JsonObject response = new JsonObject();
		response.addProperty("repository", repo); //$NON-NLS-1$
		response.addProperty("importPrefix", importPrefix); //$NON-NLS-1$
		for (Map.Entry<String, Object> entry : summary.entrySet()) {
			Object val = entry.getValue();
			if (val instanceof Number) {
				response.addProperty(entry.getKey(),
						((Number) val).longValue());
			} else if (val != null) {
				response.addProperty(entry.getKey(), val.toString());
			} else {
				response.add(entry.getKey(), null);
			}
		}
		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void handleDeprecatedApiUsage(
			GitDatabaseQueryService queryService, String repo,
			int offset, int limit, HttpServletResponse resp)
			throws IOException {
		List<JavaBlobIndex> results = queryService.getDeprecatedApiUsage(
				repo, offset, limit);
		writeJavaBlobResponse(results, repo, "Deprecated", offset, limit, //$NON-NLS-1$
				resp);
	}

	private void handleApiDiff(GitDatabaseQueryService queryService,
			String repo, HttpServletRequest req,
			HttpServletResponse resp) throws IOException {
		String commitA = req.getParameter("commitA"); //$NON-NLS-1$
		String commitB = req.getParameter("commitB"); //$NON-NLS-1$
		if (commitA == null || commitA.isEmpty() || commitB == null
				|| commitB.isEmpty()) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Parameters 'commitA' and 'commitB' are required\"}"); //$NON-NLS-1$
			}
			return;
		}
		commitA = sanitizeInput(commitA);
		commitB = sanitizeInput(commitB);
		ApiDiffResult diff = queryService.getApiDiff(repo, commitA,
				commitB);
		writeApiDiffResponse(diff, resp);
	}

	private void handlePublicApiChanges(GitDatabaseQueryService queryService,
			String repo, HttpServletRequest req,
			HttpServletResponse resp) throws IOException {
		String commitA = req.getParameter("commitA"); //$NON-NLS-1$
		String commitB = req.getParameter("commitB"); //$NON-NLS-1$
		if (commitA == null || commitA.isEmpty() || commitB == null
				|| commitB.isEmpty()) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			try (PrintWriter w = resp.getWriter()) {
				w.write("{\"error\":\"Parameters 'commitA' and 'commitB' are required\"}"); //$NON-NLS-1$
			}
			return;
		}
		commitA = sanitizeInput(commitA);
		commitB = sanitizeInput(commitB);
		ApiDiffResult diff = queryService.getPublicApiChanges(repo,
				commitA, commitB);
		writeApiDiffResponse(diff, resp);
	}

	private void handleDeprecationTimeline(
			GitDatabaseQueryService queryService, String repo,
			int offset, int limit, HttpServletResponse resp)
			throws IOException {
		List<JavaBlobIndex> results = queryService.getDeprecationTimeline(
				repo, offset, limit);
		writeJavaBlobResponse(results, repo, "deprecation-timeline", //$NON-NLS-1$
				offset, limit, resp);
	}

	private void handleAuthorTypeStatistics(
			GitDatabaseQueryService queryService, String repo,
			HttpServletResponse resp) throws IOException {
		List<Object[]> rows = queryService.getAuthorTypeStatistics(repo);
		JsonObject response = new JsonObject();
		response.addProperty("repository", repo); //$NON-NLS-1$
		response.addProperty("totalResults", rows.size()); //$NON-NLS-1$
		JsonArray items = new JsonArray();
		for (Object[] row : rows) {
			JsonObject item = new JsonObject();
			item.addProperty("author", //$NON-NLS-1$
					row[0] != null ? row[0].toString() : null);
			item.addProperty("typeKind", //$NON-NLS-1$
					row[1] != null ? row[1].toString() : null);
			item.addProperty("count", //$NON-NLS-1$
					row[2] != null ? ((Number) row[2]).longValue() : 0L);
			items.add(item);
		}
		response.add("results", items); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void handleCodeComplexityTrend(
			GitDatabaseQueryService queryService, String repo,
			String packagePrefix, HttpServletResponse resp)
			throws IOException {
		List<Object[]> rows = queryService.getCodeComplexityTrend(repo,
				packagePrefix);
		JsonObject response = new JsonObject();
		response.addProperty("repository", repo); //$NON-NLS-1$
		response.addProperty("packagePrefix", packagePrefix); //$NON-NLS-1$
		response.addProperty("totalResults", rows.size()); //$NON-NLS-1$
		JsonArray items = new JsonArray();
		for (Object[] row : rows) {
			JsonObject item = new JsonObject();
			item.addProperty("commitDate", //$NON-NLS-1$
					row[0] != null ? row[0].toString() : null);
			item.addProperty("avgLineCount", //$NON-NLS-1$
					row[1] != null ? ((Number) row[1]).doubleValue() : 0.0);
			items.add(item);
		}
		response.add("results", items); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void handleMonsterClasses(GitDatabaseQueryService queryService,
			String repo, int threshold, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		List<JavaBlobIndex> results = queryService.getMonsterClasses(repo,
				threshold, offset, limit);
		writeJavaBlobResponse(results, repo,
				"lineCount>=" + threshold, offset, limit, resp); //$NON-NLS-1$
	}

	private void handleDeadCodeCandidates(
			GitDatabaseQueryService queryService, String repo,
			HttpServletResponse resp) throws IOException {
		List<JavaBlobIndex> results = queryService
				.getDeadCodeCandidates(repo);
		writeJavaBlobResponse(results, repo, "dead-code", 0, //$NON-NLS-1$
				results.size(), resp);
	}

	private void handleTestCoverageProxy(
			GitDatabaseQueryService queryService, String repo,
			HttpServletResponse resp) throws IOException {
		List<Object[]> rows = queryService.getTestCoverageProxy(repo);
		JsonObject response = new JsonObject();
		response.addProperty("repository", repo); //$NON-NLS-1$
		response.addProperty("totalResults", rows.size()); //$NON-NLS-1$
		JsonArray items = new JsonArray();
		for (Object[] row : rows) {
			JsonObject item = new JsonObject();
			item.addProperty("packageName", //$NON-NLS-1$
					row[0] != null ? row[0].toString() : null);
			item.addProperty("testTypeCount", //$NON-NLS-1$
					row[1] != null ? ((Number) row[1]).longValue() : 0L);
			item.addProperty("totalTypeCount", //$NON-NLS-1$
					row[2] != null ? ((Number) row[2]).longValue() : 0L);
			items.add(item);
		}
		response.add("results", items); //$NON-NLS-1$
		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void writeApiDiffResponse(ApiDiffResult diff,
			HttpServletResponse resp) throws IOException {
		JsonObject response = new JsonObject();

		JsonArray added = new JsonArray();
		for (JavaBlobIndex jbi : diff.getAddedFiles()) {
			added.add(toJsonObject(jbi));
		}
		response.add("addedFiles", added); //$NON-NLS-1$

		JsonArray removed = new JsonArray();
		for (JavaBlobIndex jbi : diff.getRemovedFiles()) {
			removed.add(toJsonObject(jbi));
		}
		response.add("removedFiles", removed); //$NON-NLS-1$

		JsonArray changed = new JsonArray();
		for (ApiChangeEntry ce : diff.getChangedFiles()) {
			JsonObject item = new JsonObject();
			item.add("before", toJsonObject(ce.getBefore())); //$NON-NLS-1$
			item.add("after", toJsonObject(ce.getAfter())); //$NON-NLS-1$
			item.addProperty("changeDescription", //$NON-NLS-1$
					ce.getChangeDescription());
			changed.add(item);
		}
		response.add("changedFiles", changed); //$NON-NLS-1$

		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private static JsonObject toJsonObject(JavaBlobIndex jbi) {
		if (jbi == null) {
			return new JsonObject();
		}
		JsonObject item = new JsonObject();
		item.addProperty("blobObjectId", jbi.getBlobObjectId()); //$NON-NLS-1$
		item.addProperty("commitObjectId", jbi.getCommitObjectId()); //$NON-NLS-1$
		item.addProperty("filePath", jbi.getFilePath()); //$NON-NLS-1$
		item.addProperty("packageName", jbi.getPackageName()); //$NON-NLS-1$
		item.addProperty("declaredTypes", jbi.getDeclaredTypes()); //$NON-NLS-1$
		item.addProperty("fullyQualifiedNames", //$NON-NLS-1$
				jbi.getFullyQualifiedNames());
		item.addProperty("declaredMethods", jbi.getDeclaredMethods()); //$NON-NLS-1$
		item.addProperty("visibility", jbi.getVisibility()); //$NON-NLS-1$
		return item;
	}

	private void writeFilePathResponse(List<FilePathHistory> results,
			String repo, String query, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		JsonObject response = new JsonObject();
		response.addProperty("query", query); //$NON-NLS-1$
		response.addProperty("repository", repo); //$NON-NLS-1$
		response.addProperty("offset", offset); //$NON-NLS-1$
		response.addProperty("limit", limit); //$NON-NLS-1$
		response.addProperty("totalResults", results.size()); //$NON-NLS-1$

		JsonArray items = new JsonArray();
		for (FilePathHistory fph : results) {
			JsonObject item = new JsonObject();
			item.addProperty("commitObjectId", //$NON-NLS-1$
					fph.getCommitObjectId());
			item.addProperty("filePath", fph.getFilePath()); //$NON-NLS-1$
			item.addProperty("blobObjectId", //$NON-NLS-1$
					fph.getBlobObjectId());
			item.addProperty("fileType", fph.getFileType()); //$NON-NLS-1$
			if (fph.getCommitTime() != null) {
				item.addProperty("commitTime", //$NON-NLS-1$
						fph.getCommitTime().toString());
			}
			items.add(item);
		}
		response.add("results", items); //$NON-NLS-1$

		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private void writeJavaBlobResponse(List<JavaBlobIndex> results,
			String repo, String query, int offset, int limit,
			HttpServletResponse resp) throws IOException {
		JsonObject response = new JsonObject();
		response.addProperty("query", query); //$NON-NLS-1$
		response.addProperty("repository", repo); //$NON-NLS-1$
		response.addProperty("offset", offset); //$NON-NLS-1$
		response.addProperty("limit", limit); //$NON-NLS-1$
		response.addProperty("totalResults", results.size()); //$NON-NLS-1$

		JsonArray items = new JsonArray();
		for (JavaBlobIndex jbi : results) {
			JsonObject item = new JsonObject();
			item.addProperty("blobObjectId", jbi.getBlobObjectId()); //$NON-NLS-1$
			item.addProperty("commitObjectId", //$NON-NLS-1$
					jbi.getCommitObjectId());
			item.addProperty("filePath", jbi.getFilePath()); //$NON-NLS-1$
			item.addProperty("packageName", jbi.getPackageName()); //$NON-NLS-1$
			item.addProperty("declaredTypes", //$NON-NLS-1$
					jbi.getDeclaredTypes());
			item.addProperty("fullyQualifiedNames", //$NON-NLS-1$
					jbi.getFullyQualifiedNames());
			item.addProperty("declaredMethods", //$NON-NLS-1$
					jbi.getDeclaredMethods());
			item.addProperty("declaredFields", //$NON-NLS-1$
					jbi.getDeclaredFields());
			item.addProperty("extendsTypes", //$NON-NLS-1$
					jbi.getExtendsTypes());
			item.addProperty("implementsTypes", //$NON-NLS-1$
					jbi.getImplementsTypes());
			items.add(item);
		}
		response.add("results", items); //$NON-NLS-1$

		resp.setStatus(HttpServletResponse.SC_OK);
		try (PrintWriter w = resp.getWriter()) {
			w.write(gson.toJson(response));
		}
	}

	private static int parseIntParam(HttpServletRequest req,
			String paramName, int defaultValue) {
		String value = req.getParameter(paramName);
		if (value == null || value.isEmpty()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	/**
	 * Sanitize user input by stripping control characters.
	 *
	 * @param input
	 *            the raw input string
	 * @return the sanitized string
	 */
	private static String sanitizeInput(String input) {
		if (input == null) {
			return null;
		}
		// Strip control characters except common whitespace
		return input.replaceAll("[\\p{Cntrl}&&[^\t\n\r]]", ""); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
