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
package org.sandbox.mining.core.llm;

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
 * REST client for the OpenAI Chat Completions API.
 *
 * <p>Sends prompts to the OpenAI API and parses the response into
 * {@link CommitEvaluation} objects.</p>
 */
public class OpenAiClient implements LlmClient {

private static final String DEFAULT_MODEL = "gpt-4o-mini"; //$NON-NLS-1$
private static final String API_URL = "https://api.openai.com/v1/chat/completions"; //$NON-NLS-1$
private static final int MAX_RETRIES = 5;
private static final int INITIAL_BACKOFF_MS = 5000;
private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
/** Default maximum duration (in seconds) with no successful API call before aborting. */
public static final int DEFAULT_MAX_FAILURE_DURATION_SECONDS = 300;

private final String apiKey;
private final String model;
private final HttpClient httpClient;
private final Gson gson;
private int dailyRequestCount;
private Instant lastSuccessfulCall;
private Duration maxFailureDuration;

/**
 * Creates a client reading the API key from the OPENAI_API_KEY environment variable.
 */
public OpenAiClient() {
this(System.getenv("OPENAI_API_KEY")); //$NON-NLS-1$
}

/**
 * Creates a client with the given API key.
 *
 * @param apiKey the OpenAI API key
 */
public OpenAiClient(String apiKey) {
this(apiKey, HttpClient.newBuilder()
.connectTimeout(Duration.ofSeconds(30))
.build());
}

/**
 * Creates a client with the given API key and HTTP client (for testing).
 *
 * @param apiKey     the OpenAI API key
 * @param httpClient the HTTP client to use
 */
public OpenAiClient(String apiKey, HttpClient httpClient) {
this(apiKey, httpClient, resolveModel());
}

/**
 * Creates a client with the given API key, HTTP client, and model (for testing).
 *
 * @param apiKey     the OpenAI API key
 * @param httpClient the HTTP client to use
 * @param model      the OpenAI model name to use
 */
public OpenAiClient(String apiKey, HttpClient httpClient, String model) {
this.apiKey = apiKey;
this.model = model;
this.httpClient = httpClient;
this.gson = new GsonBuilder().create();
this.lastSuccessfulCall = Instant.now();
this.maxFailureDuration = Duration.ofSeconds(DEFAULT_MAX_FAILURE_DURATION_SECONDS);
}

private static String resolveModel() {
String envModel = System.getenv("OPENAI_MODEL"); //$NON-NLS-1$
if (envModel != null) {
envModel = envModel.trim();
}
return (envModel != null && !envModel.isBlank()) ? envModel : DEFAULT_MODEL;
}

/**
 * Returns the OpenAI model name being used.
 *
 * @return the model name
 */
public String getModel() {
return model;
}

@Override
public int getDailyRequestCount() {
return dailyRequestCount;
}

@Override
public boolean hasRemainingQuota() {
// OpenAI uses pay-per-use; no hard daily limit enforced here
return true;
}

@Override
public boolean isApiUnavailable() {
return Duration.between(lastSuccessfulCall, Instant.now()).compareTo(maxFailureDuration) > 0;
}

@Override
public void setMaxFailureDuration(Duration maxFailureDuration) {
this.maxFailureDuration = maxFailureDuration;
}

@Override
public Duration getMaxFailureDuration() {
return maxFailureDuration;
}

@Override
public CommitEvaluation evaluate(String prompt, String commitHash,
String commitMessage, String repoUrl) throws IOException {
if (apiKey == null || apiKey.isBlank()) {
System.err.println("OPENAI_API_KEY not set, skipping evaluation"); //$NON-NLS-1$
return null;
}

String requestBody = buildRequestBody(prompt);
String responseBody = sendWithRetry(requestBody);

if (responseBody == null) {
return null;
}

dailyRequestCount++;
return parseResponse(responseBody, commitHash, commitMessage, repoUrl);
}

@Override
public List<CommitEvaluation> evaluateBatch(String prompt, List<String> commitHashes,
List<String> commitMessages, String repoUrl) throws IOException {
List<CommitEvaluation> results = new ArrayList<>();
if (apiKey == null || apiKey.isBlank()) {
System.err.println("OPENAI_API_KEY not set, skipping batch evaluation"); //$NON-NLS-1$
return results;
}

String requestBody = buildRequestBody(prompt);
String responseBody = sendWithRetry(requestBody);

if (responseBody == null) {
return results;
}

dailyRequestCount++;
return parseBatchResponse(responseBody, commitHashes, commitMessages, repoUrl);
}

/**
 * Builds the JSON request body for the OpenAI Chat Completions API.
 *
 * @param prompt the prompt text
 * @return the JSON request body
 */
public String buildRequestBody(String prompt) {
JsonObject message = new JsonObject();
message.addProperty("role", "user"); //$NON-NLS-1$ //$NON-NLS-2$
message.addProperty("content", prompt); //$NON-NLS-1$

JsonArray messages = new JsonArray();
messages.add(message);

JsonObject request = new JsonObject();
request.addProperty("model", model); //$NON-NLS-1$
request.add("messages", messages); //$NON-NLS-1$

return gson.toJson(request);
}

private String sendWithRetry(String requestBody) throws IOException {
int backoffMs = INITIAL_BACKOFF_MS;

for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
try {
HttpRequest request = HttpRequest.newBuilder()
.uri(URI.create(API_URL))
.header("Content-Type", "application/json") //$NON-NLS-1$ //$NON-NLS-2$
.header("Authorization", "Bearer " + apiKey) //$NON-NLS-1$ //$NON-NLS-2$
.timeout(REQUEST_TIMEOUT)
.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
.build();

HttpResponse<String> response = httpClient.send(request,
HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

if (response.statusCode() == 200) {
lastSuccessfulCall = Instant.now();
return response.body();
}

if (response.statusCode() == 429) {
System.err.println("OpenAI rate limited (429), attempt " + (attempt + 1) + "/" + MAX_RETRIES //$NON-NLS-1$ //$NON-NLS-2$
+ ", backing off " + backoffMs + "ms"); //$NON-NLS-1$ //$NON-NLS-2$
Thread.sleep(backoffMs);
backoffMs *= 2;
continue;
}

System.err.println("OpenAI API error: " + response.statusCode() //$NON-NLS-1$
+ " - " + response.body()); //$NON-NLS-1$
return null;

} catch (InterruptedException e) {
Thread.currentThread().interrupt();
throw new IOException("Interrupted during OpenAI API call", e); //$NON-NLS-1$
}
}

System.err.println("Max retries exceeded for OpenAI API call"); //$NON-NLS-1$
return null;
}

/**
 * Parses the OpenAI API response JSON into a CommitEvaluation.
 *
 * @param responseBody  the raw API response
 * @param commitHash    the commit hash
 * @param commitMessage the commit message
 * @param repoUrl       the repository URL
 * @return the evaluation result, or null on parse failure
 */
public CommitEvaluation parseResponse(String responseBody, String commitHash,
String commitMessage, String repoUrl) {
try {
JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
JsonArray choices = root.getAsJsonArray("choices"); //$NON-NLS-1$
if (choices == null || choices.isEmpty()) {
return null;
}
JsonObject firstChoice = choices.get(0).getAsJsonObject();
JsonObject message = firstChoice.getAsJsonObject("message"); //$NON-NLS-1$
if (message == null) {
return null;
}
String text = message.get("content").getAsString(); //$NON-NLS-1$
String json = GeminiClient.extractJson(text);
JsonObject eval = JsonParser.parseString(json).getAsJsonObject();

return new CommitEvaluation(
commitHash,
commitMessage,
repoUrl,
Instant.now(),
getBooleanOrDefault(eval, "relevant", false), //$NON-NLS-1$
getStringOrNull(eval, "irrelevantReason"), //$NON-NLS-1$
getBooleanOrDefault(eval, "isDuplicate", false), //$NON-NLS-1$
getStringOrNull(eval, "duplicateOf"), //$NON-NLS-1$
getIntOrDefault(eval, "reusability", 0), //$NON-NLS-1$
getIntOrDefault(eval, "codeImprovement", 0), //$NON-NLS-1$
getIntOrDefault(eval, "implementationEffort", 0), //$NON-NLS-1$
parseTrafficLight(getStringOrNull(eval, "trafficLight")), //$NON-NLS-1$
getStringOrNull(eval, "category"), //$NON-NLS-1$
getBooleanOrDefault(eval, "isNewCategory", false), //$NON-NLS-1$
getStringOrNull(eval, "categoryReason"), //$NON-NLS-1$
getBooleanOrDefault(eval, "canImplementInCurrentDsl", false), //$NON-NLS-1$
getStringOrNull(eval, "dslRule"), //$NON-NLS-1$
getStringOrNull(eval, "targetHintFile"), //$NON-NLS-1$
getStringOrNull(eval, "languageChangeNeeded"), //$NON-NLS-1$
getStringOrNull(eval, "dslRuleAfterChange"), //$NON-NLS-1$
getStringOrNull(eval, "summary")); //$NON-NLS-1$
} catch (Exception e) {
System.err.println("Failed to parse OpenAI response: " + e.getMessage()); //$NON-NLS-1$
return null;
}
}

/**
 * Parses a batch response from OpenAI into a list of CommitEvaluations.
 *
 * @param responseBody   the raw API response
 * @param commitHashes   the commit hashes (in order)
 * @param commitMessages the commit messages (in order)
 * @param repoUrl        the repository URL
 * @return list of evaluations
 */
public List<CommitEvaluation> parseBatchResponse(String responseBody, List<String> commitHashes,
List<String> commitMessages, String repoUrl) {
List<CommitEvaluation> results = new ArrayList<>();
try {
JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
JsonArray choices = root.getAsJsonArray("choices"); //$NON-NLS-1$
if (choices == null || choices.isEmpty()) {
return results;
}
JsonObject firstChoice = choices.get(0).getAsJsonObject();
JsonObject message = firstChoice.getAsJsonObject("message"); //$NON-NLS-1$
if (message == null) {
return results;
}
String text = message.get("content").getAsString(); //$NON-NLS-1$
String json = GeminiClient.extractJson(text);
JsonArray evalArray = JsonParser.parseString(json).getAsJsonArray();
int evalCount = evalArray.size();
int commitCount = Math.min(commitHashes.size(), commitMessages.size());
if (evalCount != commitCount) {
System.err.println("Warning: OpenAI batch response count (" + evalCount //$NON-NLS-1$
+ ") does not match commit count (" + commitCount + "). Processing min of both."); //$NON-NLS-1$
}
int limit = Math.min(evalCount, commitCount);
for (int i = 0; i < limit; i++) {
String commitHash = commitHashes.get(i);
String commitMessage = commitMessages.get(i);
try {
JsonObject eval = evalArray.get(i).getAsJsonObject();
results.add(new CommitEvaluation(
commitHash,
commitMessage,
repoUrl,
Instant.now(),
getBooleanOrDefault(eval, "relevant", false), //$NON-NLS-1$
getStringOrNull(eval, "irrelevantReason"), //$NON-NLS-1$
getBooleanOrDefault(eval, "isDuplicate", false), //$NON-NLS-1$
getStringOrNull(eval, "duplicateOf"), //$NON-NLS-1$
getIntOrDefault(eval, "reusability", 0), //$NON-NLS-1$
getIntOrDefault(eval, "codeImprovement", 0), //$NON-NLS-1$
getIntOrDefault(eval, "implementationEffort", 0), //$NON-NLS-1$
parseTrafficLight(getStringOrNull(eval, "trafficLight")), //$NON-NLS-1$
getStringOrNull(eval, "category"), //$NON-NLS-1$
getBooleanOrDefault(eval, "isNewCategory", false), //$NON-NLS-1$
getStringOrNull(eval, "categoryReason"), //$NON-NLS-1$
getBooleanOrDefault(eval, "canImplementInCurrentDsl", false), //$NON-NLS-1$
getStringOrNull(eval, "dslRule"), //$NON-NLS-1$
getStringOrNull(eval, "targetHintFile"), //$NON-NLS-1$
getStringOrNull(eval, "languageChangeNeeded"), //$NON-NLS-1$
getStringOrNull(eval, "dslRuleAfterChange"), //$NON-NLS-1$
getStringOrNull(eval, "summary"))); //$NON-NLS-1$
} catch (Exception e) {
System.err.println("Failed to parse OpenAI batch evaluation at index " + i + ": " + e.getMessage()); //$NON-NLS-1$
}
}
} catch (Exception e) {
System.err.println("Failed to parse batch OpenAI response: " + e.getMessage()); //$NON-NLS-1$
}
return results;
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

@Override
public void close() {
httpClient.close();
}
}
