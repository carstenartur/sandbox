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
 * REST client for the Alibaba Cloud DashScope API (Qwen models, OpenAI-compatible format).
 *
 * <p>Uses the DashScope compatible-mode endpoint with the {@code qwen-max}
 * model by default.</p>
 */
public class QwenClient extends OpenAiCompatibleClient {

private static final String DEFAULT_MODEL = "qwen-max"; //$NON-NLS-1$
private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"; //$NON-NLS-1$
private static final String API_KEY_ENV = "DASHSCOPE_API_KEY"; //$NON-NLS-1$
private static final String MODEL_ENV = "QWEN_MODEL"; //$NON-NLS-1$

/**
 * Creates a client reading the API key from the DASHSCOPE_API_KEY environment variable.
 */
public QwenClient() {
this(System.getenv(API_KEY_ENV));
}

/**
 * Creates a client with the given API key.
 *
 * @param apiKey the DashScope API key
 */
public QwenClient(String apiKey) {
super(API_URL, apiKey, resolveModel(), "Qwen"); //$NON-NLS-1$
}

/**
 * Creates a client with the given API key and HTTP client (for testing).
 *
 * @param apiKey     the DashScope API key
 * @param httpClient the HTTP client to use
 */
public QwenClient(String apiKey, HttpClient httpClient) {
this(apiKey, httpClient, resolveModel());
}

/**
 * Creates a client with the given API key, HTTP client, and model (for testing).
 *
 * @param apiKey     the DashScope API key
 * @param httpClient the HTTP client to use
 * @param model      the model name to use
 */
public QwenClient(String apiKey, HttpClient httpClient, String model) {
super(API_URL, apiKey, model, "Qwen", httpClient); //$NON-NLS-1$
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
