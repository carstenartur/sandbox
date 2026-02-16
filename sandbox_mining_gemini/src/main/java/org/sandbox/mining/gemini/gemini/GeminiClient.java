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
package org.sandbox.mining.gemini.gemini;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

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
 * {@link CommitEvaluation} objects. Includes rate limiting (4s delay)
 * and exponential backoff on HTTP 429 responses.</p>
 */
public class GeminiClient implements AutoCloseable {

	private static final String API_URL_TEMPLATE =
			"https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent?key=%s";
	private static final int RATE_LIMIT_DELAY_MS = 4000;
	private static final int MAX_RETRIES = 3;
	private static final int INITIAL_BACKOFF_MS = 5000;
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

	private final String apiKey;
	private final HttpClient httpClient;
	private final Gson gson;
	private long lastRequestTime;

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
		this.apiKey = apiKey;
		this.httpClient = httpClient;
		this.gson = new GsonBuilder().create();
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

		return parseResponse(responseBody, commitHash, commitMessage, repoUrl);
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

		for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(String.format(API_URL_TEMPLATE, apiKey)))
						.header("Content-Type", "application/json")
						.timeout(REQUEST_TIMEOUT)
						.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
						.build();

				HttpResponse<String> response = httpClient.send(request,
						HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

				if (response.statusCode() == 200) {
					return response.body();
				}

				if (response.statusCode() == 429) {
					System.err.println("Rate limited (429), backing off " + backoffMs + "ms");
					Thread.sleep(backoffMs);
					backoffMs *= 2;
					continue;
				}

				System.err.println("Gemini API error: " + response.statusCode()
						+ " - " + response.body());
				return null;

			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted during Gemini API call", e);
			}
		}

		System.err.println("Max retries exceeded for Gemini API call");
		return null;
	}

	/**
	 * Parses the Gemini API response JSON into a CommitEvaluation.
	 */
	public CommitEvaluation parseResponse(String responseBody, String commitHash,
			String commitMessage, String repoUrl) {
		try {
			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			JsonArray candidates = root.getAsJsonArray("candidates");
			if (candidates == null || candidates.isEmpty()) {
				return null;
			}

			JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
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

			return new CommitEvaluation(
					commitHash,
					commitMessage,
					repoUrl,
					Instant.now(),
					getBooleanOrDefault(eval, "relevant", false),
					getStringOrNull(eval, "irrelevantReason"),
					getBooleanOrDefault(eval, "isDuplicate", false),
					getStringOrNull(eval, "duplicateOf"),
					getIntOrDefault(eval, "reusability", 0),
					getIntOrDefault(eval, "codeImprovement", 0),
					getIntOrDefault(eval, "implementationEffort", 0),
					parseTrafficLight(getStringOrNull(eval, "trafficLight")),
					getStringOrNull(eval, "category"),
					getBooleanOrDefault(eval, "isNewCategory", false),
					getStringOrNull(eval, "categoryReason"),
					getBooleanOrDefault(eval, "canImplementInCurrentDsl", false),
					getStringOrNull(eval, "dslRule"),
					getStringOrNull(eval, "targetHintFile"),
					getStringOrNull(eval, "languageChangeNeeded"),
					getStringOrNull(eval, "dslRuleAfterChange"),
					getStringOrNull(eval, "summary"));
		} catch (Exception e) {
			System.err.println("Failed to parse Gemini response: " + e.getMessage());
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
		}

		// Try plain code blocks
		jsonStart = text.indexOf("```");
		if (jsonStart >= 0) {
			int contentStart = text.indexOf('\n', jsonStart) + 1;
			int contentEnd = text.indexOf("```", contentStart);
			if (contentEnd > contentStart) {
				return text.substring(contentStart, contentEnd).trim();
			}
		}

		// Assume the text itself is JSON
		return text.trim();
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
		if (elapsed < RATE_LIMIT_DELAY_MS && lastRequestTime > 0) {
			try {
				Thread.sleep(RATE_LIMIT_DELAY_MS - elapsed);
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
