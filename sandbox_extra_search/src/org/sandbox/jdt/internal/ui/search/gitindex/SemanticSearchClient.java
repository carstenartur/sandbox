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
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

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

	private static final ILog LOG= Platform.getLog(SemanticSearchClient.class);

	/** Default base URL for the REST backend. */
	public static final String DEFAULT_BASE_URL= "http://localhost:8080"; //$NON-NLS-1$

	private static SemanticSearchClient instance;

	private final String baseUrl;

	private final HttpClient httpClient;

	private SemanticSearchClient(String baseUrl) {
		this.baseUrl= baseUrl;
		this.httpClient= HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.build();
	}

	/**
	 * Returns the singleton instance using the default base URL.
	 *
	 * @return the singleton instance
	 */
	public static synchronized SemanticSearchClient getInstance() {
		if (instance == null) {
			instance= new SemanticSearchClient(DEFAULT_BASE_URL);
		}
		return instance;
	}

	/**
	 * Re-initializes the singleton with a new base URL. Call this when the
	 * preference changes.
	 *
	 * @param baseUrl the base URL for the REST backend (e.g.
	 *            {@code http://localhost:8080})
	 */
	public static synchronized void initialize(String baseUrl) {
		instance= new SemanticSearchClient(
				baseUrl != null ? baseUrl : DEFAULT_BASE_URL);
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
	 * @param repoName   the repository name (empty string for all)
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
	 * @param repoName   the repository name (empty string for all)
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
	 * @param repoName   the repository name (empty string for all)
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
	 * @param repoName   the repository name (empty string for all)
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
	 * @param repoName   the repository name (empty string for all)
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
	 * @param repoName   the repository name (empty string for all)
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
	 * @param repoName   the repository name (empty string for all)
	 * @param blobId     the blob object ID
	 * @param maxResults maximum number of results
	 * @return list of matching search hits
	 */
	public List<SearchHit> findSimilarCode(String repoName, String blobId,
			int maxResults) {
		String url= baseUrl + "/api/search/similar?repo=" //$NON-NLS-1$
				+ encode(repoName) + "&blobId=" + encode(blobId) //$NON-NLS-1$
				+ "&limit=" + maxResults; //$NON-NLS-1$
		return getSearchHits(url);
	}

	/**
	 * Returns the file history for a given path.
	 *
	 * @param repoName the repository name (empty string for all)
	 * @param path     the file path
	 * @return list of matching search hits (each represents a commit touching
	 *         the path)
	 */
	public List<SearchHit> getFileHistory(String repoName, String path) {
		String url= baseUrl + "/api/search/filehistory?repo=" //$NON-NLS-1$
				+ encode(repoName) + "&path=" + encode(path); //$NON-NLS-1$
		return getFileHistoryHits(url);
	}

	/**
	 * Returns author commit statistics.
	 *
	 * @param repoName the repository name
	 * @return list of author statistics
	 */
	public List<AuthorStats> getAuthorStatistics(String repoName) {
		String url= baseUrl + "/api/analytics/authors?repo=" + encode(repoName); //$NON-NLS-1$
		return getAuthorStats(url);
	}

	// ---- private helpers ---------------------------------------------------

	private String buildUrl(String path, String repoName, String query,
			int limit) {
		return baseUrl + path + "?repo=" + encode(repoName) //$NON-NLS-1$
				+ "&q=" + encode(query) + "&limit=" + limit; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String encode(String value) {
		if (value == null) {
			return ""; //$NON-NLS-1$
		}
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private List<SearchHit> getSearchHits(String url) {
		try {
			String body= get(url);
			if (body == null) {
				return Collections.emptyList();
			}
			return parseSearchHits(body);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.error("SemanticSearchClient: request interrupted: " + url, e); //$NON-NLS-1$
			return Collections.emptyList();
		} catch (IOException e) {
			LOG.error("SemanticSearchClient: request failed: " + url, e); //$NON-NLS-1$
			return Collections.emptyList();
		}
	}

	private List<SearchHit> getFileHistoryHits(String url) {
		try {
			String body= get(url);
			if (body == null) {
				return Collections.emptyList();
			}
			return parseFileHistoryHits(body);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.error("SemanticSearchClient: request interrupted: " + url, e); //$NON-NLS-1$
			return Collections.emptyList();
		} catch (IOException e) {
			LOG.error("SemanticSearchClient: request failed: " + url, e); //$NON-NLS-1$
			return Collections.emptyList();
		}
	}

	private List<AuthorStats> getAuthorStats(String url) {
		try {
			String body= get(url);
			if (body == null) {
				return Collections.emptyList();
			}
			return parseAuthorStats(body);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.error("SemanticSearchClient: request interrupted: " + url, e); //$NON-NLS-1$
			return Collections.emptyList();
		} catch (IOException e) {
			LOG.error("SemanticSearchClient: request failed: " + url, e); //$NON-NLS-1$
			return Collections.emptyList();
		}
	}

	private String get(String url)
			throws IOException, InterruptedException {
		HttpRequest req= HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("Accept", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
				.GET()
				.build();
		HttpResponse<String> resp= httpClient.send(req,
				HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) {
			LOG.warn("SemanticSearchClient: HTTP " + resp.statusCode() //$NON-NLS-1$
					+ " for " + url); //$NON-NLS-1$
			return null;
		}
		return resp.body();
	}

	/**
	 * Minimal JSON array parser for the "results" array in the search response.
	 * Extracts {@code filePath}, {@code declaredTypes}, {@code declaredMethods},
	 * and {@code commitObjectId} fields using simple string scanning.
	 *
	 * <p>
	 * This avoids a dependency on Gson or Jackson inside the Eclipse plugin.
	 * </p>
	 */
	private static List<SearchHit> parseSearchHits(String json) {
		List<SearchHit> hits= new ArrayList<>();
		int resultsStart= json.indexOf("\"results\""); //$NON-NLS-1$
		if (resultsStart < 0) {
			return hits;
		}
		int arrayStart= json.indexOf('[', resultsStart);
		int arrayEnd= findMatchingBracket(json, arrayStart);
		if (arrayStart < 0 || arrayEnd < 0) {
			return hits;
		}
		String array= json.substring(arrayStart + 1, arrayEnd);
		List<String> objects= splitJsonObjects(array);
		for (String obj : objects) {
			SearchHit hit= new SearchHit();
			hit.setPath(extractString(obj, "filePath")); //$NON-NLS-1$
			hit.setClassName(extractString(obj, "declaredTypes")); //$NON-NLS-1$
			hit.setMethodName(extractString(obj, "declaredMethods")); //$NON-NLS-1$
			hit.setCommitHash(extractString(obj, "commitObjectId")); //$NON-NLS-1$
			hit.setContent(extractString(obj, "packageName")); //$NON-NLS-1$
			hits.add(hit);
		}
		return hits;
	}

	/**
	 * Parses file history results (from the {@code /filehistory} endpoint).
	 */
	private static List<SearchHit> parseFileHistoryHits(String json) {
		List<SearchHit> hits= new ArrayList<>();
		int resultsStart= json.indexOf("\"results\""); //$NON-NLS-1$
		if (resultsStart < 0) {
			return hits;
		}
		int arrayStart= json.indexOf('[', resultsStart);
		int arrayEnd= findMatchingBracket(json, arrayStart);
		if (arrayStart < 0 || arrayEnd < 0) {
			return hits;
		}
		String array= json.substring(arrayStart + 1, arrayEnd);
		List<String> objects= splitJsonObjects(array);
		for (String obj : objects) {
			SearchHit hit= new SearchHit();
			hit.setPath(extractString(obj, "filePath")); //$NON-NLS-1$
			hit.setCommitHash(extractString(obj, "commitObjectId")); //$NON-NLS-1$
			hits.add(hit);
		}
		return hits;
	}

	/**
	 * Parses author statistics from the {@code /analytics/authors} endpoint.
	 */
	private static List<AuthorStats> parseAuthorStats(String json) {
		List<AuthorStats> result= new ArrayList<>();
		int authorsStart= json.indexOf("\"authors\""); //$NON-NLS-1$
		if (authorsStart < 0) {
			return result;
		}
		int arrayStart= json.indexOf('[', authorsStart);
		int arrayEnd= findMatchingBracket(json, arrayStart);
		if (arrayStart < 0 || arrayEnd < 0) {
			return result;
		}
		String array= json.substring(arrayStart + 1, arrayEnd);
		List<String> objects= splitJsonObjects(array);
		for (String obj : objects) {
			AuthorStats stats= new AuthorStats();
			stats.setAuthor(extractString(obj, "name")); //$NON-NLS-1$
			stats.setAuthorEmail(extractString(obj, "email")); //$NON-NLS-1$
			stats.setCommitCount(extractInt(obj, "commits")); //$NON-NLS-1$
			result.add(stats);
		}
		return result;
	}

	/**
	 * Finds the position of the closing bracket matching the opening bracket at
	 * {@code start}.
	 */
	private static int findMatchingBracket(String s, int start) {
		if (start < 0 || start >= s.length()) {
			return -1;
		}
		char open= s.charAt(start);
		char close= open == '[' ? ']' : '}';
		int depth= 0;
		boolean inString= false;
		for (int i= start; i < s.length(); i++) {
			char c= s.charAt(i);
			if (c == '\\' && inString) {
				i++; // skip escaped character
				continue;
			}
			if (c == '"') {
				inString= !inString;
			} else if (!inString) {
				if (c == open) {
					depth++;
				} else if (c == close) {
					depth--;
					if (depth == 0) {
						return i;
					}
				}
			}
		}
		return -1;
	}

	/**
	 * Splits a JSON array body (without outer brackets) into individual object
	 * strings.
	 */
	private static List<String> splitJsonObjects(String arrayBody) {
		List<String> result= new ArrayList<>();
		int i= 0;
		while (i < arrayBody.length()) {
			int objStart= arrayBody.indexOf('{', i);
			if (objStart < 0) {
				break;
			}
			int objEnd= findMatchingBracket(arrayBody, objStart);
			if (objEnd < 0) {
				break;
			}
			result.add(arrayBody.substring(objStart, objEnd + 1));
			i= objEnd + 1;
		}
		return result;
	}

	/**
	 * Extracts a string value for a given JSON field key from a JSON object
	 * fragment.
	 */
	private static String extractString(String obj, String key) {
		String search= "\"" + key + "\":\""; //$NON-NLS-1$ //$NON-NLS-2$
		int start= obj.indexOf(search);
		if (start < 0) {
			return null;
		}
		start+= search.length();
		StringBuilder sb= new StringBuilder();
		for (int i= start; i < obj.length(); i++) {
			char c= obj.charAt(i);
			if (c == '\\' && i + 1 < obj.length()) {
				char next= obj.charAt(i + 1);
				switch (next) {
					case '"':
						sb.append('"');
						break;
					case '\\':
						sb.append('\\');
						break;
					case 'n':
						sb.append('\n');
						break;
					case 'r':
						sb.append('\r');
						break;
					case 't':
						sb.append('\t');
						break;
					default:
						sb.append(next);
						break;
				}
				i++;
			} else if (c == '"') {
				break;
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * Extracts an integer value for a given JSON field key from a JSON object
	 * fragment.
	 */
	private static int extractInt(String obj, String key) {
		String search= "\"" + key + "\":"; //$NON-NLS-1$ //$NON-NLS-2$
		int start= obj.indexOf(search);
		if (start < 0) {
			return 0;
		}
		start+= search.length();
		// skip optional whitespace
		while (start < obj.length() && obj.charAt(start) == ' ') {
			start++;
		}
		int end= start;
		while (end < obj.length()) {
			char c= obj.charAt(end);
			if (c >= '0' && c <= '9') {
				end++;
			} else {
				break;
			}
		}
		if (start == end) {
			return 0;
		}
		try {
			return Integer.parseInt(obj.substring(start, end));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
