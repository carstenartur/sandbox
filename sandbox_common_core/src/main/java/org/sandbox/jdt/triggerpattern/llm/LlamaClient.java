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
 * REST client for the Meta Llama API (OpenAI-compatible format).
 *
 * <p>Uses the Llama chat completions endpoint with the {@code llama-4-maverick}
 * model by default.</p>
 */
public class LlamaClient extends OpenAiCompatibleClient {

private static final String DEFAULT_MODEL = "llama-4-maverick"; //$NON-NLS-1$
private static final String API_URL = "https://api.llama.com/v1/chat/completions"; //$NON-NLS-1$
private static final String API_KEY_ENV = "LLAMA_API_KEY"; //$NON-NLS-1$
private static final String MODEL_ENV = "LLAMA_MODEL"; //$NON-NLS-1$

/**
 * Creates a client reading the API key from the LLAMA_API_KEY environment variable.
 */
public LlamaClient() {
this(System.getenv(API_KEY_ENV));
}

/**
 * Creates a client with the given API key.
 *
 * @param apiKey the Llama API key
 */
public LlamaClient(String apiKey) {
super(API_URL, apiKey, resolveModel(), "Llama"); //$NON-NLS-1$
}

/**
 * Creates a client with the given API key and HTTP client (for testing).
 *
 * @param apiKey     the Llama API key
 * @param httpClient the HTTP client to use
 */
public LlamaClient(String apiKey, HttpClient httpClient) {
this(apiKey, httpClient, resolveModel());
}

/**
 * Creates a client with the given API key, HTTP client, and model (for testing).
 *
 * @param apiKey     the Llama API key
 * @param httpClient the HTTP client to use
 * @param model      the model name to use
 */
public LlamaClient(String apiKey, HttpClient httpClient, String model) {
super(API_URL, apiKey, model, "Llama", httpClient); //$NON-NLS-1$
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
