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

import java.net.http.HttpClient;

/**
 * REST client for the Mistral AI API (OpenAI-compatible format).
 *
 * <p>Uses the Mistral chat completions endpoint with the {@code mistral-large-latest}
 * model by default.</p>
 */
public class MistralClient extends OpenAiCompatibleClient {

private static final String DEFAULT_MODEL = "mistral-large-latest"; //$NON-NLS-1$
private static final String API_URL = "https://api.mistral.ai/v1/chat/completions"; //$NON-NLS-1$
private static final String API_KEY_ENV = "MISTRAL_API_KEY"; //$NON-NLS-1$
private static final String MODEL_ENV = "MISTRAL_MODEL"; //$NON-NLS-1$

/**
 * Creates a client reading the API key from the MISTRAL_API_KEY environment variable.
 */
public MistralClient() {
this(System.getenv(API_KEY_ENV));
}

/**
 * Creates a client with the given API key.
 *
 * @param apiKey the Mistral API key
 */
public MistralClient(String apiKey) {
super(API_URL, apiKey, resolveModel(), "Mistral"); //$NON-NLS-1$
}

/**
 * Creates a client with the given API key and HTTP client (for testing).
 *
 * @param apiKey     the Mistral API key
 * @param httpClient the HTTP client to use
 */
public MistralClient(String apiKey, HttpClient httpClient) {
this(apiKey, httpClient, resolveModel());
}

/**
 * Creates a client with the given API key, HTTP client, and model (for testing).
 *
 * @param apiKey     the Mistral API key
 * @param httpClient the HTTP client to use
 * @param model      the model name to use
 */
public MistralClient(String apiKey, HttpClient httpClient, String model) {
super(API_URL, apiKey, model, "Mistral", httpClient); //$NON-NLS-1$
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
