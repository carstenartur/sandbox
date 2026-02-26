/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.jdt.triggerpattern.mining.llm;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.sandbox.jdt.internal.ui.preferences.LlmPreferencePage;
import org.sandbox.jdt.triggerpattern.llm.AiRuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.llm.LlmClient;
import org.sandbox.jdt.triggerpattern.llm.LlmClientFactory;
import org.sandbox.jdt.triggerpattern.llm.LlmProvider;

/**
 * Eclipse-level service that provides access to the AI rule inference engine.
 *
 * <p>This service wraps {@link LlmClientFactory} and manages the lifecycle of
 * the underlying {@link LlmClient}. It reads the LLM provider configuration
 * from Eclipse preferences first, falling back to environment variables when
 * preferences are empty.</p>
 *
 * <p>The service is a lazy-initialized singleton. The LLM client is created
 * on first access and reused for subsequent calls. Call {@link #shutdown()}
 * to release resources when the Eclipse workbench shuts down.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * EclipseLlmService service = EclipseLlmService.getInstance();
 * AiRuleInferenceEngine engine = service.getEngine();
 * engine.inferRule(before, after).ifPresent(eval -&gt; ...);
 * </pre>
 *
 * @since 1.2.6
 */
public class EclipseLlmService {

	private static volatile EclipseLlmService instance;

	private LlmClient llmClient;
	private AiRuleInferenceEngine engine;

	private EclipseLlmService() {
		// lazy initialization
	}

	/**
	 * Returns the singleton instance.
	 *
	 * @return the service instance
	 */
	public static EclipseLlmService getInstance() {
		if (instance == null) {
			synchronized (EclipseLlmService.class) {
				if (instance == null) {
					instance = new EclipseLlmService();
				}
			}
		}
		return instance;
	}

	/**
	 * Returns the AI rule inference engine, creating the LLM client if needed.
	 *
	 * <p>The LLM provider is resolved from Eclipse preferences first,
	 * falling back to environment variables if preferences are empty.</p>
	 *
	 * @return the inference engine
	 */
	public synchronized AiRuleInferenceEngine getEngine() {
		if (engine == null) {
			llmClient = createClientFromPreferences();
			engine = new AiRuleInferenceEngine(llmClient);
		}
		return engine;
	}

	/**
	 * Returns whether the LLM service is configured and available.
	 *
	 * <p>Checks if an API key is configured in preferences or environment.</p>
	 *
	 * @return {@code true} if an LLM provider can be configured
	 */
	public boolean isAvailable() {
		return hasPreferenceApiKey() || hasAnyEnvApiKey();
	}

	/**
	 * Shuts down the service and releases the underlying LLM client.
	 */
	public synchronized void shutdown() {
		if (llmClient != null) {
			llmClient.close();
			llmClient = null;
			engine = null;
		}
	}

	/**
	 * Resets the singleton (useful for testing or after preference changes).
	 */
	public static synchronized void reset() {
		if (instance != null) {
			instance.shutdown();
			instance = null;
		}
	}

	/**
	 * Creates an LLM client using Eclipse preferences, falling back to env vars.
	 */
	private static LlmClient createClientFromPreferences() {
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		String apiKey = prefs.get(LlmPreferencePage.PREF_API_KEY, ""); //$NON-NLS-1$
		String provider = prefs.get(LlmPreferencePage.PREF_PROVIDER, ""); //$NON-NLS-1$

		if (!apiKey.isBlank() && !provider.isBlank()) {
			return LlmClientFactory.create(LlmProvider.fromString(provider));
		}

		// Fallback to environment variables
		return LlmClientFactory.createFromEnvironment(null);
	}

	private static boolean hasPreferenceApiKey() {
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		String apiKey = prefs.get(LlmPreferencePage.PREF_API_KEY, ""); //$NON-NLS-1$
		return !apiKey.isBlank();
	}

	private static boolean hasAnyEnvApiKey() {
		return envSet("GEMINI_API_KEY") //$NON-NLS-1$
				|| envSet("OPENAI_API_KEY") //$NON-NLS-1$
				|| envSet("DEEPSEEK_API_KEY") //$NON-NLS-1$
				|| envSet("DASHSCOPE_API_KEY") //$NON-NLS-1$
				|| envSet("LLAMA_API_KEY") //$NON-NLS-1$
				|| envSet("MISTRAL_API_KEY"); //$NON-NLS-1$
	}

	private static boolean envSet(String key) {
		String value = System.getenv(key);
		return value != null && !value.isBlank();
	}
}
