/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.internal.ui.search.gitindex;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Lightweight HTTP client for the {@code sandbox-jgit-server-webapp} REST API.
 *
 * <h2>Architecture</h2>
 *
 * <pre>
 * Eclipse Plugin (sandbox_extra_search)
 * └── SemanticSearchClient  (this class — REST HTTP client)
 *     │   uses java.net.http.HttpClient (JDK built-in, no extra dependencies)
 *     └── sandbox-jgit-server-webapp REST API
 *         ├── GET  /api/search/types?repo=&amp;q=
 *         ├── GET  /api/search/symbols?repo=&amp;q=
 *         ├── GET  /api/search/paths?repo=&amp;q=
 *         ├── GET  /api/search/commits?repo=&amp;q=
 *         ├── GET  /api/search/semantic?repo=&amp;q=&amp;limit=
 *         ├── GET  /api/search/hybrid?repo=&amp;q=&amp;limit=
 *         ├── GET  /api/search/similar?repo=&amp;blobId=&amp;limit=
 *         ├── GET  /api/search/filehistory?repo=&amp;path=
 *         ├── GET  /api/analytics/authors?repo=
 *         └── GET  /api/health
 * </pre>
 *
 * <p>
 * No dependency on Hibernate, DJL, Lucene, HSQLDB, or Jakarta Persistence.
 * </p>
 *
 * @see EmbeddedSearchService
 */
public class SemanticSearchClient {


	/** Default base URL for the REST backend. */
	public static final String DEFAULT_BASE_URL= "http://localhost:8080"; //$NON-NLS-1$

	private static final Object INSTANCE_LOCK= new Object();

	private static volatile SemanticSearchClient instance;

	private final String baseUrl;

	private final HttpClient httpClient;

	private SemanticSearchClient(String baseUrl) {
		this.baseUrl= GitSearchPreferences.validateUrl(baseUrl);
		this.httpClient= HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.build();
	}

	/**
	 * Returns the singleton instance using the default base URL.
	 *
	 * @return the singleton instance
	 */
	public static SemanticSearchClient getInstance() {
		SemanticSearchClient current= instance;
		if (current == null) {
			synchronized (INSTANCE_LOCK) {
				current= instance;
				if (current == null) {
					current= new SemanticSearchClient(GitSearchPreferences.restUrl());
					instance= current;
				}
			}
		}
		return current;
	}

	/**
	 * Re-initializes the singleton with a new base URL. Call this when the
	 * preference changes.
	 *
	 * @param baseUrl the base URL for the REST backend (e.g.
	 *            {@code http://localhost:8080})
	 */
	public static void initialize(String baseUrl) {
		synchronized (INSTANCE_LOCK) {
			instance= new SemanticSearchClient(
					baseUrl != null ? baseUrl : DEFAULT_BASE_URL);
		}
	}

	/**
	 * Checks whether the REST backend is reachable.
	 *
	 * @return {@code true} if the health endpoint returns HTTP 200
	 */
	public boolean isAvailable() {
		try {
			HttpRequest req= HttpRequest.newBuilder()
					.uri(URI.create(baseUrl + "/api/health")) //$NON-NLS-1$
					.timeout(Duration.ofSeconds(3))
					.GET()
					.build();
			HttpResponse<String> resp= httpClient.send(req,
					HttpResponse.BodyHandlers.ofString());
			return resp.statusCode() == 200;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Searches for Java types by name.
	 *
	 * @param repoName   the repository name (required server repository name)
	 * @param query      the search query
	 * @param maxResults maximum number of results
	 * @return list of matching search hits
	 */
	public List<SearchHit> searchByType(String repoName, String query,
			int maxResults) {
		String url= buildUrl("/api/search/types", repoName, query, maxResults); //$NON-NLS-1$
		return getSearchHits(url);
	}

	/**
	 * Searches for Java symbols (methods and fields).
	 *
	 * @param repoName   the repository name (required server repository name)
	 * @param query      the search query
	 * @param maxResults maximum number of results
	 * @return list of matching search hits
	 */
	public List<SearchHit> searchBySymbol(String repoName, String query,
			int maxResults) {
		String url= buildUrl("/api/search/symbols", repoName, query, maxResults); //$NON-NLS-1$
		return getSearchHits(url);
	}

	/**
	 * Searches for commits by changed file path.
	 *
	 * @param repoName   the repository name (required server repository name)
	 * @param query      the path query
	 * @param maxResults maximum number of results
	 * @return list of matching search hits
	 */
	public List<SearchHit> searchByChangedPath(String repoName, String query,
			int maxResults) {
		String url= buildUrl("/api/search/paths", repoName, query, maxResults); //$NON-NLS-1$
		return getSearchHits(url);
	}

	/**
	 * Searches commit messages.
	 *
	 * @param repoName   the repository name (required server repository name)
	 * @param query      the search query
	 * @param maxResults maximum number of results
	 * @return list of matching search hits
	 */
	public List<SearchHit> searchCommitMessages(String repoName, String query,
			int maxResults) {
		String url= buildUrl("/api/search/commits", repoName, query, maxResults); //$NON-NLS-1$
		return getSearchHits(url);
	}

	/**
	 * Performs a semantic (vector) search.
	 *
	 * @param repoName   the repository name (required server repository name)
	 * @param query      the natural language query
	 * @param maxResults maximum number of results
	 * @return list of matching search hits
	 */
	public List<SearchHit> semanticSearch(String repoName, String query,
			int maxResults) {
		String url= buildUrl("/api/search/semantic", repoName, query, maxResults); //$NON-NLS-1$
		return getSearchHits(url);
	}

	/**
	 * Performs a hybrid (full-text + semantic) search.
	 *
	 * @param repoName   the repository name (required server repository name)
	 * @param query      the natural language query
	 * @param maxResults maximum number of results
	 * @return list of matching search hits
	 */
	public List<SearchHit> hybridSearch(String repoName, String query,
			int maxResults) {
		String url= buildUrl("/api/search/hybrid", repoName, query, maxResults); //$NON-NLS-1$
		return getSearchHits(url);
	}

	/**
	 * Finds code similar to a given blob.
	 *
	 * @param repoName   the repository name (required server repository name)
	 * @param blobId     the blob object ID
	 * @param maxResults maximum number of results
	 * @return list of matching search hits
	 */
	public List<SearchHit> findSimilarCode(String repoName, String blobId,
			int maxResults) {
		requireRepository(repoName);
		// q= is sent alongside blobId= because the backend validates that
		// both repo and q are present for all /api/search/* requests.
		String url= baseUrl + "/api/search/similar?repo=" //$NON-NLS-1$
				+ encode(repoName) + "&q=" + encode(blobId) //$NON-NLS-1$
				+ "&blobId=" + encode(blobId) + "&limit=" + maxResults; //$NON-NLS-1$ //$NON-NLS-2$
		return getSearchHits(url);
	}

	/**
	 * Returns the file history for a given path.
	 *
	 * @param repoName the repository name (required server repository name)
	 * @param path     the file path
	 * @return list of matching search hits (each represents a commit touching
	 *         the path)
	 */
	public List<SearchHit> getFileHistory(String repoName, String path) {
		requireRepository(repoName);
		// q= is sent alongside path= because the backend validates that
		// both repo and q are present for all /api/search/* requests.
		String url= baseUrl + "/api/search/filehistory?repo=" //$NON-NLS-1$
				+ encode(repoName) + "&path=" + encode(path) //$NON-NLS-1$
				+ "&q=" + encode(path); //$NON-NLS-1$
		return getFileHistoryHits(url);
	}

	/**
	 * Returns author commit statistics.
	 *
	 * @param repoName the repository name
	 * @return list of author statistics
	 */
	public List<AuthorStats> getAuthorStatistics(String repoName) {
		requireRepository(repoName);
		String url= baseUrl + "/api/analytics/authors?repo=" + encode(repoName); //$NON-NLS-1$
		return getAuthorStats(url);
	}

	// ---- private helpers ---------------------------------------------------

	private String buildUrl(String path, String repoName, String query,
			int limit) {
		requireRepository(repoName);
		return baseUrl + path + "?repo=" + encode(repoName) //$NON-NLS-1$
				+ "&q=" + encode(query) + "&limit=" + limit; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String encode(String value) {
		if (value == null) {
			return ""; //$NON-NLS-1$
		}
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/** Search annotation facets rather than treating annotations as method names. */
	public List<SearchHit> searchAnnotations(String repository, String query, int limit) {
		return getSearchHits(buildUrl("/api/search/annotations", repository, query, limit)); //$NON-NLS-1$
	}

	/** Explicit, authenticated rebuild of one server-side repository. */
	public String rebuild(String repository, String token) {
		requireRepository(repository);
		if (token == null || token.isBlank()) throw new IllegalArgumentException("Admin token is required."); //$NON-NLS-1$
		HttpRequest request= HttpRequest.newBuilder(URI.create(baseUrl + "/api/admin/rebuild?repo=" + encode(repository))) //$NON-NLS-1$
				.timeout(Duration.ofMinutes(10)).header("Authorization", "Bearer " + token) //$NON-NLS-1$ //$NON-NLS-2$
				.POST(HttpRequest.BodyPublishers.noBody()).build();
		JsonObject result= JsonParser.parseString(send(request)).getAsJsonObject();
		if (!result.has("indexedCommits")) throw new IllegalStateException("Invalid rebuild response."); //$NON-NLS-1$ //$NON-NLS-2$
		return "Index rebuilt: " + result.get("indexedCommits").getAsLong() + " commits."; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	static void requireRepository(String repository) {
		if (repository == null || repository.isBlank()) throw new IllegalArgumentException("Repository is required."); //$NON-NLS-1$
	}

	private List<SearchHit> getSearchHits(String url) {
		List<SearchHit> hits= new ArrayList<>();
		for (var element : array(get(url), "results")) { //$NON-NLS-1$
			JsonObject item= element.getAsJsonObject();
			SearchHit hit= new SearchHit();
			hit.setPath(value(item, "filePath", "changedPaths")); //$NON-NLS-1$ //$NON-NLS-2$
			hit.setClassName(value(item, "declaredTypes")); //$NON-NLS-1$
			hit.setMethodName(value(item, "declaredMethods")); //$NON-NLS-1$
			hit.setCommitHash(value(item, "commitObjectId", "objectId")); //$NON-NLS-1$ //$NON-NLS-2$
			hit.setContent(value(item, "message", "packageName")); //$NON-NLS-1$ //$NON-NLS-2$
			hit.setAuthor(value(item, "author")); //$NON-NLS-1$
			hit.setBlobId(value(item, "blobObjectId")); //$NON-NLS-1$
			hit.setCommitTime(value(item, "commitTime")); //$NON-NLS-1$
			hits.add(hit);
		}
		return hits;
	}

	private List<SearchHit> getFileHistoryHits(String url) {
		return getSearchHits(url);
	}

	private List<AuthorStats> getAuthorStats(String url) {
		List<AuthorStats> authors= new ArrayList<>();
		for (var element : array(get(url), "authors")) { //$NON-NLS-1$
			var item= element.getAsJsonObject();
			authors.add(new AuthorStats(value(item, "name"), value(item, "email"), //$NON-NLS-1$ //$NON-NLS-2$
					item.get("commits").getAsInt(), 0)); //$NON-NLS-1$
		}
		return authors;
	}

	private String get(String url) {
		return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
				.header("Accept", "application/json").GET().build()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private String send(HttpRequest request) {
		try {
			var response= httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("Search server returned HTTP " + response.statusCode() //$NON-NLS-1$
						+ ". Check the server, repository and authorization."); //$NON-NLS-1$
			}
			return response.body();
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new org.eclipse.core.runtime.OperationCanceledException();
		} catch (IOException failure) {
			throw new IllegalStateException("Cannot reach the search server. Check Sandbox Git Search preferences.", failure); //$NON-NLS-1$
		}
	}

	private static JsonArray array(String body, String name) {
		try {
			var root= JsonParser.parseString(body).getAsJsonObject();
			if (!root.has(name) || !root.get(name).isJsonArray()) throw new IllegalArgumentException();
			return root.getAsJsonArray(name);
		} catch (RuntimeException invalid) {
			throw new IllegalStateException("Invalid response from the search server.", invalid); //$NON-NLS-1$
		}
	}

	private static String value(JsonObject object, String... fields) {
		for (String field : fields) {
			if (object.has(field) && !object.get(field).isJsonNull()) return object.get(field).getAsString();
		}
		return ""; //$NON-NLS-1$
	}
}
