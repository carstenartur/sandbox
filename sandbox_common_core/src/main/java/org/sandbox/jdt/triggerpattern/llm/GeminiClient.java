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
package org.sandbox.jdt.triggerpattern.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * REST client for the Google Gemini API.
 *
 * <p>Sends prompts to the Gemini API and parses the response into
 * {@link CommitEvaluation} objects. Includes rate limiting (15s delay)
 * and exponential backoff on HTTP 429 responses.</p>
 */
public class GeminiClient implements LlmClient {

	private static final String DEFAULT_MODEL = "gemini-2.5-flash";
	private static final String API_URL_TEMPLATE =
			"https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
	private static final int RATE_LIMIT_DELAY_MS = 15000;
	/** Maximum number of API requests allowed per day (leaves buffer from 20 RPD limit). */
	public static final int MAX_DAILY_REQUESTS = 18;
	private static final int MAX_RETRIES = 5;
	private static final int INITIAL_BACKOFF_MS = 5000;
	private static final int POST_FAILURE_COOLDOWN_MS = 60000;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
	/** Default maximum duration (in seconds) with no successful API call before aborting. */
	public static final int DEFAULT_MAX_FAILURE_DURATION_SECONDS = 300;

	private final String apiKey;
	private final String model;
	private final HttpClient httpClient;
	private final Gson gson;
	private long lastRequestTime;
	private int dailyRequestCount;
	private Instant lastSuccessfulCall;
	private Duration maxFailureDuration;
	private int rateLimitDelayMs = RATE_LIMIT_DELAY_MS;
	private int consecutive429Batches;
	private boolean lastResponseTruncated;

	/**
	 * Creates a client reading the API key from the GEMINI_API_KEY environment variable.
	 */
	public GeminiClient() {
		this(System.getenv("GEMINI_API_KEY"));
	}

	/**
	 * Creates a client with the given API key.
	 *
	 * @param apiKey the Gemini API key
	 */
	public GeminiClient(String apiKey) {
		this(apiKey, HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(30))
				.build());
	}

	/**
	 * Creates a client with the given API key and HTTP client (for testing).
	 *
	 * @param apiKey     the Gemini API key
	 * @param httpClient the HTTP client to use
	 */
	public GeminiClient(String apiKey, HttpClient httpClient) {
		this(apiKey, httpClient, resolveModel());
	}

	/**
	 * Creates a client with the given API key, HTTP client, and model (for testing).
	 *
	 * @param apiKey     the Gemini API key
	 * @param httpClient the HTTP client to use
	 * @param model      the Gemini model name to use
	 */
	public GeminiClient(String apiKey, HttpClient httpClient, String model) {
		this.apiKey = apiKey;
		this.model = model;
		this.httpClient = httpClient;
		this.gson = new GsonBuilder().create();
		this.lastSuccessfulCall = Instant.now();
		this.maxFailureDuration = Duration.ofSeconds(DEFAULT_MAX_FAILURE_DURATION_SECONDS);
		String debug = System.getenv("GEMINI_DEBUG");
		if ("true".equalsIgnoreCase(debug)) {
			System.out.println("Gemini model: " + this.model);
		}
	}

	private static String resolveModel() {
		String envModel = System.getenv("GEMINI_MODEL");
		if (envModel != null) {
			envModel = envModel.trim();
		}
		return (envModel != null && !envModel.isBlank()) ? envModel : DEFAULT_MODEL;
	}

	/**
	 * Returns the Gemini model name being used.
	 *
	 * @return the model name
	 */
	@Override
	public String getModel() {
		return model;
	}

	/**
	 * Returns the number of API requests used in this session.
	 *
	 * @return the daily request count
	 */
	@Override

	public int getDailyRequestCount() {
		return dailyRequestCount;
	}

	/**
	 * Returns true if the daily API quota has not yet been exhausted.
	 *
	 * @return true if more requests can be made today
	 */
	@Override

	public boolean hasRemainingQuota() {
		return dailyRequestCount < MAX_DAILY_REQUESTS;
	}

	/**
	 * Returns true if no successful API call has been made for longer than
	 * the configured maximum failure duration.
	 *
	 * @return true if the API should be considered unavailable
	 */
	@Override

	public boolean isApiUnavailable() {
		return Duration.between(lastSuccessfulCall, Instant.now()).compareTo(maxFailureDuration) > 0;
	}

	/**
	 * Sets the maximum duration without a successful API call before the client
	 * is considered unavailable.
	 *
	 * @param maxFailureDuration the maximum failure duration
	 */
	@Override

	public void setMaxFailureDuration(Duration maxFailureDuration) {
		this.maxFailureDuration = maxFailureDuration;
	}

	/**
	 * Returns the configured maximum failure duration.
	 *
	 * @return the maximum failure duration
	 */
	@Override

	public Duration getMaxFailureDuration() {
		return maxFailureDuration;
	}

	/**
	 * Returns true if the last API response was truncated (finishReason=MAX_TOKENS).
	 *
	 * @return true if truncation was detected
	 */
	@Override
	public boolean wasLastResponseTruncated() {
		return lastResponseTruncated;
	}

	/**
	 * Evaluates a commit by sending a prompt to the Gemini API.
	 *
	 * @param prompt        the constructed prompt
	 * @param commitHash    the commit hash
	 * @param commitMessage the commit message
	 * @param repoUrl       the repository URL
	 * @return the evaluation result, or null if the API call fails
	 * @throws IOException if an I/O error occurs
	 */
	@Override

	public CommitEvaluation evaluate(String prompt, String commitHash,
			String commitMessage, String repoUrl) throws IOException {
		if (apiKey == null || apiKey.isBlank()) {
			System.err.println("GEMINI_API_KEY not set, skipping evaluation");
			return null;
		}

		rateLimit();

		String requestBody = buildRequestBody(prompt);
		String responseBody = sendWithRetry(requestBody);

		if (responseBody == null) {
			return null;
		}

		dailyRequestCount++;
		return parseResponse(responseBody, commitHash, commitMessage, repoUrl);
	}

	/**
	 * Evaluates a batch of commits in a single API call.
	 *
	 * @param prompt         the batch prompt
	 * @param commitHashes   the commit hashes (in order)
	 * @param commitMessages the commit messages (in order)
	 * @param repoUrl        the repository URL
	 * @return list of evaluations (one per commit, in order), never null
	 * @throws IOException if an I/O error occurs
	 */
	@Override

	public List<CommitEvaluation> evaluateBatch(String prompt, List<String> commitHashes,
			List<String> commitMessages, String repoUrl) throws IOException {
		List<CommitEvaluation> results = new ArrayList<>();
		if (apiKey == null || apiKey.isBlank()) {
			System.err.println("GEMINI_API_KEY not set, skipping batch evaluation");
			return results;
		}

		rateLimit();

		String requestBody = buildRequestBody(prompt);
		String responseBody = sendWithRetry(requestBody);

		if (responseBody == null) {
			return results;
		}

		dailyRequestCount++;
		return parseBatchResponse(responseBody, commitHashes, commitMessages, repoUrl);
	}

	/**
	 * Parses a batch response JSON array into a list of CommitEvaluations.
	 * Each element in the array is matched by position to the corresponding commit.
	 *
	 * @param responseBody   the raw API response
	 * @param commitHashes   the commit hashes (in order)
	 * @param commitMessages the commit messages (in order)
	 * @param repoUrl        the repository URL
	 * @return list of evaluations (one per commit, in order)
	 */
	public List<CommitEvaluation> parseBatchResponse(String responseBody, List<String> commitHashes,
			List<String> commitMessages, String repoUrl) {
		List<CommitEvaluation> results = new ArrayList<>();
		lastResponseTruncated = false;
		try {
			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			JsonArray candidates = root.getAsJsonArray("candidates");
			if (candidates == null || candidates.isEmpty()) {
				return results;
			}
			JsonObject firstCandidate = candidates.get(0).getAsJsonObject();

			// Check finishReason before parsing content
			String finishReason = getStringOrNull(firstCandidate, "finishReason"); //$NON-NLS-1$
			if ("MAX_TOKENS".equals(finishReason)) { //$NON-NLS-1$
				System.err.println("Warning: Gemini response truncated (finishReason=MAX_TOKENS)"); //$NON-NLS-1$
				lastResponseTruncated = true;
			}
			if ("SAFETY".equals(finishReason)) { //$NON-NLS-1$
				System.err.println("Warning: Gemini refused (finishReason=SAFETY), skipping batch"); //$NON-NLS-1$
				return results;
			}

			JsonObject content = firstCandidate.getAsJsonObject("content");
			if (content == null) {
				return results;
			}
			JsonArray parts = content.getAsJsonArray("parts");
			if (parts == null || parts.isEmpty()) {
				return results;
			}
			String text = parts.get(0).getAsJsonObject().get("text").getAsString();
			String json = extractJson(text);
			JsonArray evalArray;
			try {
				evalArray = JsonParser.parseString(json).getAsJsonArray();
			} catch (Exception parseEx) {
				// Try repair for truncated responses
				String repaired = repairTruncatedJson(json);
				try {
					evalArray = JsonParser.parseString(repaired).getAsJsonArray();
					System.err.println("Recovered partial batch response after JSON repair"); //$NON-NLS-1$
				} catch (Exception e2) {
					throw parseEx; // Rethrow original
				}
			}
			int evalCount = evalArray.size();
			int commitCount = Math.min(commitHashes.size(), commitMessages.size());
			if (evalCount != commitCount) {
				System.err.println("Warning: Gemini batch response count (" + evalCount //$NON-NLS-1$
						+ ") does not match commit count (" + commitCount + "). Processing min of both."); //$NON-NLS-1$
			}
			int limit = Math.min(evalCount, commitCount);
			for (int i = 0; i < limit; i++) {
				String commitHash = commitHashes.get(i);
				String commitMessage = commitMessages.get(i);
				try {
					JsonObject eval = evalArray.get(i).getAsJsonObject();
					results.add(createEvaluation(eval, commitHash, commitMessage, repoUrl));
				} catch (Exception e) {
					System.err.println("Failed to parse batch evaluation at index " + i + ": " + e.getMessage());
				}
			}
		} catch (Exception e) {
			System.err.println("Failed to parse batch Gemini response: " + e.getMessage());
			if (responseBody != null && Boolean.parseBoolean(System.getenv("GEMINI_DEBUG"))) { //$NON-NLS-1$
				System.err.println("Raw response (first 500 chars): "
						+ responseBody.substring(0, Math.min(500, responseBody.length())));
			}
		}
		return results;
	}

	/**
	 * Builds the JSON request body for the Gemini API.
	 *
	 * @param prompt the prompt text
	 * @return the JSON request body
	 */
	public String buildRequestBody(String prompt) {
		JsonObject content = new JsonObject();
		JsonArray parts = new JsonArray();
		JsonObject textPart = new JsonObject();
		textPart.addProperty("text", prompt);
		parts.add(textPart);
		content.add("parts", parts);

		JsonArray contents = new JsonArray();
		contents.add(content);

		JsonObject request = new JsonObject();
		request.add("contents", contents);

		return gson.toJson(request);
	}

	private String sendWithRetry(String requestBody) throws IOException {
		int backoffMs = INITIAL_BACKOFF_MS;
		boolean allAttempts429 = true;

		for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(String.format(API_URL_TEMPLATE, model, apiKey)))
						.header("Content-Type", "application/json")
						.timeout(REQUEST_TIMEOUT)
						.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
						.build();

				HttpResponse<String> response = httpClient.send(request,
						HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

				if (response.statusCode() == 200) {
					lastSuccessfulCall = Instant.now();
					consecutive429Batches = 0;
					// Gradually reduce delay after success
					if (rateLimitDelayMs > RATE_LIMIT_DELAY_MS) {
						rateLimitDelayMs = Math.max(RATE_LIMIT_DELAY_MS, rateLimitDelayMs / 2);
					}
					return response.body();
				}

				if (response.statusCode() == 429) {
					String retryAfter = response.headers().firstValue("Retry-After").orElse(null); //$NON-NLS-1$
					long waitMs = backoffMs;
					boolean usedRetryAfter = false;
					if (retryAfter != null) {
						try {
							long seconds = Long.parseLong(retryAfter.trim());
							if (seconds >= 0) {
								waitMs = seconds * 1000;
								usedRetryAfter = true;
							}
						} catch (NumberFormatException nfe) {
							System.err.println("Invalid Retry-After header value '" + retryAfter + "', using exponential backoff instead."); //$NON-NLS-1$ //$NON-NLS-2$
						}
					}
					System.err.println("Rate limited (429), attempt " + (attempt + 1) + "/" + MAX_RETRIES //$NON-NLS-1$ //$NON-NLS-2$
							+ ", waiting " + waitMs + "ms" //$NON-NLS-1$ //$NON-NLS-2$
							+ (usedRetryAfter ? " (from Retry-After header)" : " (exponential backoff)")); //$NON-NLS-1$ //$NON-NLS-2$
					Thread.sleep(waitMs);
					backoffMs *= 2;
					continue;
				}

				allAttempts429 = false;
				System.err.println("Gemini API error: " + response.statusCode()
						+ " - " + response.body());
				return null;

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted during Gemini API call", e);
			}
		}

		// Track consecutive 429 batches for faster abort
		if (allAttempts429) {
			consecutive429Batches++;
			if (consecutive429Batches >= 2) {
				System.err.println("Two consecutive batches entirely rate-limited (429). " //$NON-NLS-1$
						+ "Marking API as unavailable."); //$NON-NLS-1$
				// Force unavailable by backdating lastSuccessfulCall
				lastSuccessfulCall = Instant.now().minus(maxFailureDuration.plusSeconds(1));
			}
			// Increase rate limit delay after 429 cascade
			rateLimitDelayMs = Math.min(rateLimitDelayMs * 2, 120000);
		}

		System.err.println("Max retries exceeded for Gemini API call, entering post-failure cooldown of "
				+ POST_FAILURE_COOLDOWN_MS + "ms");
		try {
			Thread.sleep(POST_FAILURE_COOLDOWN_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return null;
	}

	/**
	 * Parses the Gemini API response JSON into a CommitEvaluation.
	 */
	public CommitEvaluation parseResponse(String responseBody, String commitHash,
			String commitMessage, String repoUrl) {
		lastResponseTruncated = false;
		try {
			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			JsonArray candidates = root.getAsJsonArray("candidates");
			if (candidates == null || candidates.isEmpty()) {
				return null;
			}

			JsonObject firstCandidate = candidates.get(0).getAsJsonObject();

			// Check finishReason before parsing content
			String finishReason = getStringOrNull(firstCandidate, "finishReason"); //$NON-NLS-1$
			if ("MAX_TOKENS".equals(finishReason)) { //$NON-NLS-1$
				System.err.println("Warning: Gemini response truncated (finishReason=MAX_TOKENS)"); //$NON-NLS-1$
				lastResponseTruncated = true;
			}
			if ("SAFETY".equals(finishReason)) { //$NON-NLS-1$
				System.err.println("Warning: Gemini refused (finishReason=SAFETY), skipping commit"); //$NON-NLS-1$
				return null;
			}

			JsonObject content = firstCandidate.getAsJsonObject("content");
			if (content == null) {
				return null;
			}

			JsonArray parts = content.getAsJsonArray("parts");
			if (parts == null || parts.isEmpty()) {
				return null;
			}

			String text = parts.get(0).getAsJsonObject().get("text").getAsString();

			// Extract JSON from text (may be wrapped in markdown code blocks)
			String json = extractJson(text);
			JsonObject eval = JsonParser.parseString(json).getAsJsonObject();

			return createEvaluation(eval, commitHash, commitMessage, repoUrl);
		} catch (Exception e) {
			System.err.println("Failed to parse Gemini response: " + e.getMessage());
			if (responseBody != null && Boolean.parseBoolean(System.getenv("GEMINI_DEBUG"))) {
				System.err.println("Raw response (first 500 chars): "
						+ responseBody.substring(0, Math.min(500, responseBody.length())));
			}
			return null;
		}
	}

	public static String extractJson(String text) {
		// Try to extract JSON from markdown code blocks
		int jsonStart = text.indexOf("```json");
		if (jsonStart >= 0) {
			int contentStart = text.indexOf('\n', jsonStart) + 1;
			int contentEnd = text.indexOf("```", contentStart);
			if (contentEnd > contentStart) {
				return text.substring(contentStart, contentEnd).trim();
			}
			// Truncated code block (no closing ```): take rest of text
			return repairTruncatedJson(text.substring(contentStart).trim());
		}

		// Try plain code blocks
		jsonStart = text.indexOf("```");
		if (jsonStart >= 0) {
			int contentStart = text.indexOf('\n', jsonStart) + 1;
			int contentEnd = text.indexOf("```", contentStart);
			if (contentEnd > contentStart) {
				return text.substring(contentStart, contentEnd).trim();
			}
			// Truncated code block (no closing ```): take rest of text
			return repairTruncatedJson(text.substring(contentStart).trim());
		}

		// Assume the text itself is JSON; attempt repair if truncated
		return repairTruncatedJson(text.trim());
	}

	/**
	 * Attempts to repair truncated JSON by closing unclosed brackets, braces,
	 * and strings. This handles the common case where Gemini's response is
	 * cut off mid-JSON due to token limits.
	 *
	 * @param json the potentially truncated JSON string
	 * @return the repaired JSON string
	 */
	public static String repairTruncatedJson(String json) {
		if (json == null || json.isEmpty()) {
			return json;
		}
		// Try parsing first; if it works, no repair needed
		try {
			JsonParser.parseString(json);
			return json;
		} catch (Exception e) {
			// Fall through to repair
		}

		StringBuilder sb = new StringBuilder(json);
		// Remove trailing comma if present (common in truncated arrays/objects)
		String trimmed = sb.toString().trim();
		if (trimmed.endsWith(",")) { //$NON-NLS-1$
			sb = new StringBuilder(trimmed.substring(0, trimmed.length() - 1));
		}

		// Close unclosed strings, objects, and arrays
		boolean inString = false;
		int openBraces = 0;
		int openBrackets = 0;
		for (int i = 0; i < sb.length(); i++) {
			char ch = sb.charAt(i);
			if (ch == '\\' && inString && i + 1 < sb.length()) {
				i++; // skip escaped character
				continue;
			}
			if (ch == '"') {
				inString = !inString;
			} else if (!inString) {
				if (ch == '{') {
					openBraces++;
				} else if (ch == '}') {
					openBraces--;
				} else if (ch == '[') {
					openBrackets++;
				} else if (ch == ']') {
					openBrackets--;
				}
			}
		}
		if (inString) {
			sb.append('"');
		}
		// Remove trailing comma after closing string
		trimmed = sb.toString().trim();
		if (trimmed.endsWith(",")) { //$NON-NLS-1$
			sb = new StringBuilder(trimmed.substring(0, trimmed.length() - 1));
		}
		for (int i = 0; i < openBraces; i++) {
			sb.append('}');
		}
		for (int i = 0; i < openBrackets; i++) {
			sb.append(']');
		}
		return sb.toString();
	}

	private static CommitEvaluation.TrafficLight parseTrafficLight(String value) {
		if (value == null) {
			return CommitEvaluation.TrafficLight.NOT_APPLICABLE;
		}
		try {
			return CommitEvaluation.TrafficLight.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException e) {
			return CommitEvaluation.TrafficLight.NOT_APPLICABLE;
		}
	}

	/**
	 * Creates a CommitEvaluation from a parsed JSON object with null-safe field handling.
	 */
	private static CommitEvaluation createEvaluation(JsonObject eval, String commitHash,
			String commitMessage, String repoUrl) {
		boolean relevant = getBooleanOrDefault(eval, "relevant", false); //$NON-NLS-1$
		String category = getStringOrNull(eval, "category"); //$NON-NLS-1$
		// Default category for relevant commits without one
		if (relevant && (category == null || category.isBlank())) {
			System.err.println("Warning: relevant commit " + commitHash //$NON-NLS-1$
					+ " has no category, defaulting to 'Uncategorized'"); //$NON-NLS-1$
			category = "Uncategorized"; //$NON-NLS-1$
		}
		String dslRule = sanitizeDslRule(getStringOrNull(eval, "dslRule")); //$NON-NLS-1$
		String dslRuleAfterChange = sanitizeDslRule(getStringOrNull(eval, "dslRuleAfterChange")); //$NON-NLS-1$
		return new CommitEvaluation(
				commitHash,
				commitMessage,
				repoUrl,
				Instant.now(),
				relevant,
				getStringOrNull(eval, "irrelevantReason"), //$NON-NLS-1$
				getBooleanOrDefault(eval, "isDuplicate", false), //$NON-NLS-1$
				getStringOrNull(eval, "duplicateOf"), //$NON-NLS-1$
				getIntOrDefault(eval, "reusability", 0), //$NON-NLS-1$
				getIntOrDefault(eval, "codeImprovement", 0), //$NON-NLS-1$
				getIntOrDefault(eval, "implementationEffort", 0), //$NON-NLS-1$
				parseTrafficLight(getStringOrNull(eval, "trafficLight")), //$NON-NLS-1$
				category,
				getBooleanOrDefault(eval, "isNewCategory", false), //$NON-NLS-1$
				getStringOrNull(eval, "categoryReason"), //$NON-NLS-1$
				getBooleanOrDefault(eval, "canImplementInCurrentDsl", false), //$NON-NLS-1$
				dslRule,
				getStringOrNull(eval, "targetHintFile"), //$NON-NLS-1$
				getStringOrNull(eval, "languageChangeNeeded"), //$NON-NLS-1$
				dslRuleAfterChange,
				getStringOrNull(eval, "summary"), //$NON-NLS-1$
				null);
	}

	/**
	 * Strips XML tag hallucinations from DSL rule strings.
	 * LLMs sometimes wrap rules in {@code <trigger>}, {@code <import>},
	 * or {@code <pattern>} tags despite being told not to.
	 *
	 * @param dslRule the raw DSL rule string, or {@code null}
	 * @return the sanitized DSL rule, or {@code null} if the input was {@code null}
	 *         or if the sanitized result is empty after stripping tags and trimming
	 */
	static String sanitizeDslRule(String dslRule) {
		if (dslRule == null) {
			return null;
		}
		String result = dslRule;
		result = result.replaceAll("</?trigger>", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
		result = result.replaceAll("</?pattern>", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
		result = result.replaceAll("<import>[^<]*</import>", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
		return result.isEmpty() ? null : result;
	}

	private static String getStringOrNull(JsonObject obj, String key) {
		JsonElement element = obj.get(key);
		return (element != null && !element.isJsonNull()) ? element.getAsString() : null;
	}

	private static boolean getBooleanOrDefault(JsonObject obj, String key, boolean defaultValue) {
		JsonElement element = obj.get(key);
		return (element != null && !element.isJsonNull()) ? element.getAsBoolean() : defaultValue;
	}

	private static int getIntOrDefault(JsonObject obj, String key, int defaultValue) {
		JsonElement element = obj.get(key);
		return (element != null && !element.isJsonNull()) ? element.getAsInt() : defaultValue;
	}

	private void rateLimit() {
		long now = System.currentTimeMillis();
		long elapsed = now - lastRequestTime;
		if (elapsed < rateLimitDelayMs && lastRequestTime > 0) {
			try {
				Thread.sleep(rateLimitDelayMs - elapsed);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		lastRequestTime = System.currentTimeMillis();
	}

	@Override

	public void close() {
		httpClient.close();
	}
}
