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

/** REST client for the OpenAI Chat Completions API. */
public class OpenAiClient extends OpenAiCompatibleClient {

private static final String DEFAULT_MODEL = "gpt-4o-mini"; //$NON-NLS-1$
private static final String API_URL = "https://api.openai.com/v1/chat/completions"; //$NON-NLS-1$
private static final String API_KEY_ENV = "OPENAI_API_KEY"; //$NON-NLS-1$
private static final String MODEL_ENV = "OPENAI_MODEL"; //$NON-NLS-1$
public static final int DEFAULT_MAX_FAILURE_DURATION_SECONDS = 300;

public OpenAiClient() {
this(System.getenv(API_KEY_ENV));
}

public OpenAiClient(String apiKey) {
super(API_URL, apiKey, resolveModel(), "OpenAI"); //$NON-NLS-1$
}

public OpenAiClient(String apiKey, HttpClient httpClient) {
this(apiKey, httpClient, resolveModel());
}

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

@Override
protected String getMaxTokensParameterName() {
return "max_completion_tokens"; //$NON-NLS-1$
}
}
