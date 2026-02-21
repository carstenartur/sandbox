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

/**
 * Factory for creating {@link LlmClient} instances.
 */
public class LlmClientFactory {

private LlmClientFactory() {
// utility class
}

/**
 * Creates an {@link LlmClient} for the given provider.
 *
 * @param provider the LLM provider to use
 * @return the configured client
 */
public static LlmClient create(LlmProvider provider) {
return switch (provider) {
case GEMINI -> new GeminiClient();
case OPENAI -> new OpenAiClient();
};
}

/**
 * Creates an {@link LlmClient} by auto-detecting the provider.
 *
 * <p>Priority order:
 * <ol>
 *   <li>Explicit CLI argument ({@code explicitProvider} parameter, non-blank)</li>
 *   <li>Environment variable {@code LLM_PROVIDER}</li>
 *   <li>Auto-detect from available API keys:
 *     <ul>
 *       <li>If {@code OPENAI_API_KEY} is set → {@link LlmProvider#OPENAI}</li>
 *       <li>If {@code GEMINI_API_KEY} is set → {@link LlmProvider#GEMINI}</li>
 *     </ul>
 *   </li>
 *   <li>Default: {@link LlmProvider#GEMINI}</li>
 * </ol>
 *
 * @param explicitProvider explicit provider name from CLI (may be null or blank)
 * @return the configured client
 */
public static LlmClient createFromEnvironment(String explicitProvider) {
// 1. Explicit CLI argument
if (explicitProvider != null && !explicitProvider.isBlank()) {
return create(LlmProvider.fromString(explicitProvider));
}

// 2. Environment variable
String envProvider = System.getenv("LLM_PROVIDER"); //$NON-NLS-1$
if (envProvider != null && !envProvider.isBlank()) {
return create(LlmProvider.fromString(envProvider));
}

// 3. Auto-detect from available API keys
String openAiKey = System.getenv("OPENAI_API_KEY"); //$NON-NLS-1$
if (openAiKey != null && !openAiKey.isBlank()) {
return create(LlmProvider.OPENAI);
}
String geminiKey = System.getenv("GEMINI_API_KEY"); //$NON-NLS-1$
if (geminiKey != null && !geminiKey.isBlank()) {
return create(LlmProvider.GEMINI);
}

// 4. Default: GEMINI
return create(LlmProvider.GEMINI);
}
}
