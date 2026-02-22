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

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * REST client for the OpenAI Chat Completions API.
 *
 * <p>Sends prompts to the OpenAI API and parses the response into
 * {@link CommitEvaluation} objects.</p>
 */
public class OpenAiClient extends OpenAiCompatibleClient {

private static final String DEFAULT_MODEL = "gpt-4o-mini"; //$NON-NLS-1$
private static final String API_URL = "https://api.openai.com/v1/chat/completions"; //$NON-NLS-1$
private static final String API_KEY_ENV = "OPENAI_API_KEY"; //$NON-NLS-1$
private static final String MODEL_ENV = "OPENAI_MODEL"; //$NON-NLS-1$
/** Default maximum duration (in seconds) with no successful API call before aborting. */
public static final int DEFAULT_MAX_FAILURE_DURATION_SECONDS = 300;

/**
 * Creates a client reading the API key from the OPENAI_API_KEY environment variable.
 */
public OpenAiClient() {
this(System.getenv(API_KEY_ENV));
}

/**
 * Creates a client with the given API key.
 *
 * @param apiKey the OpenAI API key
 */
public OpenAiClient(String apiKey) {
super(API_URL, apiKey, resolveModel(), "OpenAI"); //$NON-NLS-1$
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
super(API_URL, apiKey, model, "OpenAI", httpClient); //$NON-NLS-1$
}

private static String resolveModel() {
String envModel = System.getenv(MODEL_ENV);
if (envModel != null) {
envModel = envModel.trim();
}
return (envModel != null && !envModel.isBlank()) ? envModel : DEFAULT_MODEL;
}

@Override
protected String getApiKeyEnvVar() {
return API_KEY_ENV;
}
}
