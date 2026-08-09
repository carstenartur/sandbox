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
import java.time.Duration;

/** Factory for creating {@link LlmClient} instances. */
public class LlmClientFactory {

private LlmClientFactory() {
}

public static LlmClient create(LlmProvider provider) {
return create(provider, null, LlmInferenceSettings.unspecified());
}

public static LlmClient create(LlmProvider provider, String apiKey) {
return create(provider, apiKey, LlmInferenceSettings.unspecified());
}

/**
 * Creates a provider client and applies the provider-independent inference
 * settings. A blank API key still falls back to the provider-specific environment
 * variable.
 */
public static LlmClient create(LlmProvider provider, String apiKey, LlmInferenceSettings settings) {
LlmInferenceSettings effectiveSettings = settings != null ? settings : LlmInferenceSettings.unspecified();
String effectiveApiKey = apiKey == null || apiKey.isBlank() ? environmentApiKey(provider) : apiKey;
LlmClient client = switch (provider) {
case GEMINI -> effectiveSettings.modelName() == null
		? new GeminiClient(effectiveApiKey)
		: new GeminiClient(effectiveApiKey, newHttpClient(), effectiveSettings.modelName());
case OPENAI -> new OpenAiClient(effectiveApiKey);
case DEEPSEEK -> new DeepSeekClient(effectiveApiKey);
case QWEN -> new QwenClient(effectiveApiKey);
case LLAMA -> new LlamaClient(effectiveApiKey);
case MISTRAL -> new MistralClient(effectiveApiKey);
};
client.applyInferenceSettings(effectiveSettings);
return client;
}

public static LlmClient createFromEnvironment(String explicitProvider) {
return createFromEnvironment(explicitProvider, LlmInferenceSettings.unspecified());
}

/** Creates a client by auto-detecting the provider while preserving inference settings. */
public static LlmClient createFromEnvironment(String explicitProvider, LlmInferenceSettings settings) {
if (explicitProvider != null && !explicitProvider.isBlank()) {
return create(LlmProvider.fromString(explicitProvider), null, settings);
}

String envProvider = System.getenv("LLM_PROVIDER"); //$NON-NLS-1$
if (envProvider != null && !envProvider.isBlank()) {
return create(LlmProvider.fromString(envProvider), null, settings);
}

for (LlmProvider provider : new LlmProvider[] {
		LlmProvider.OPENAI, LlmProvider.GEMINI, LlmProvider.DEEPSEEK,
		LlmProvider.QWEN, LlmProvider.LLAMA, LlmProvider.MISTRAL }) {
	String key = environmentApiKey(provider);
	if (key != null && !key.isBlank()) {
		return create(provider, key, settings);
	}
}
return create(LlmProvider.GEMINI, null, settings);
}

private static String environmentApiKey(LlmProvider provider) {
return System.getenv(switch (provider) {
case GEMINI -> "GEMINI_API_KEY"; //$NON-NLS-1$
case OPENAI -> "OPENAI_API_KEY"; //$NON-NLS-1$
case DEEPSEEK -> "DEEPSEEK_API_KEY"; //$NON-NLS-1$
case QWEN -> "DASHSCOPE_API_KEY"; //$NON-NLS-1$
case LLAMA -> "LLAMA_API_KEY"; //$NON-NLS-1$
case MISTRAL -> "MISTRAL_API_KEY"; //$NON-NLS-1$
});
}

private static HttpClient newHttpClient() {
return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
}
}
