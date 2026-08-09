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

import java.util.function.Function;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.sandbox.jdt.internal.ui.preferences.LlmPreferencePage;
import org.sandbox.jdt.internal.ui.preferences.LlmSecureCredentials;
import org.sandbox.jdt.triggerpattern.llm.AiRuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.llm.LlmClient;
import org.sandbox.jdt.triggerpattern.llm.LlmClientFactory;
import org.sandbox.jdt.triggerpattern.llm.LlmInferenceSettings;
import org.sandbox.jdt.triggerpattern.llm.LlmProvider;

/** Eclipse-level service that owns the shared AI rule-inference engine. */
public class EclipseLlmService {

	private static final String DOCUMENTATION_SCREENSHOT_PROPERTY = "sandbox.help.screenshot.mode"; //$NON-NLS-1$
	private static final String ENV_PROVIDER = "LLM_PROVIDER"; //$NON-NLS-1$
	private static final Object INSTANCE_LOCK = new Object();

	private static volatile EclipseLlmService instance;

	private final Object lifecycleLock = new Object();

	private LlmClient llmClient;
	private AiRuleInferenceEngine engine;

	private EclipseLlmService() {
	}

	public static EclipseLlmService getInstance() {
		EclipseLlmService current = instance;
		if (current == null) {
			synchronized (INSTANCE_LOCK) {
				current = instance;
				if (current == null) {
					current = new EclipseLlmService();
					instance = current;
				}
			}
		}
		return current;
	}

	public AiRuleInferenceEngine getEngine() {
		synchronized (lifecycleLock) {
			if (engine == null) {
				llmClient = createClientFromPreferences();
				engine = new AiRuleInferenceEngine(llmClient);
			}
			return engine;
		}
	}

	/** Documentation generation must never contact or unlock an external provider. */
	public boolean isAvailable() {
		if (Boolean.getBoolean(DOCUMENTATION_SCREENSHOT_PROPERTY)) {
			return false;
		}
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		String provider = prefs.get(LlmPreferencePage.PREF_PROVIDER, ""); //$NON-NLS-1$
		return hasCredentials(provider, LlmSecureCredentials.loadApiKey(), System::getenv);
	}

	/**
	 * Returns a non-secret explanation of how to make the currently selected
	 * provider available.
	 */
	public String configurationHint() {
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		String configuredProvider = prefs.get(LlmPreferencePage.PREF_PROVIDER, ""); //$NON-NLS-1$
		if (!configuredProvider.isBlank()) {
			try {
				LlmProvider provider = LlmProvider.fromString(configuredProvider);
				return "Store an API key in Java > LLM Rule Inference or set " //$NON-NLS-1$
						+ environmentVariable(provider) + "."; //$NON-NLS-1$
			} catch (IllegalArgumentException e) {
				return "Select a supported provider in Java > LLM Rule Inference."; //$NON-NLS-1$
			}
		}

		String environmentProvider = System.getenv(ENV_PROVIDER);
		if (environmentProvider != null && !environmentProvider.isBlank()) {
			try {
				LlmProvider provider = LlmProvider.fromString(environmentProvider);
				return "Set " + environmentVariable(provider) //$NON-NLS-1$
						+ " for the provider selected by LLM_PROVIDER, or configure Java > LLM Rule Inference."; //$NON-NLS-1$
			} catch (IllegalArgumentException e) {
				return "LLM_PROVIDER names an unsupported provider; configure Java > LLM Rule Inference instead."; //$NON-NLS-1$
			}
		}

		return "Configure Java > LLM Rule Inference or set a supported provider API-key environment variable."; //$NON-NLS-1$
	}

	public void shutdown() {
		synchronized (lifecycleLock) {
			if (llmClient != null) {
				llmClient.close();
				llmClient = null;
				engine = null;
			}
		}
	}

	public static void reset() {
		EclipseLlmService current;
		synchronized (INSTANCE_LOCK) {
			current = instance;
			instance = null;
		}
		if (current != null) {
			current.shutdown();
		}
	}

	static boolean hasCredentials(String configuredProvider, String secureApiKey,
			Function<String, String> environment) {
		if (configuredProvider != null && !configuredProvider.isBlank()) {
			try {
				LlmProvider provider = LlmProvider.fromString(configuredProvider);
				return (secureApiKey != null && !secureApiKey.isBlank())
						|| envSet(environmentVariable(provider), environment);
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		String environmentProvider = environment.apply(ENV_PROVIDER);
		if (environmentProvider != null && !environmentProvider.isBlank()) {
			try {
				return envSet(environmentVariable(LlmProvider.fromString(environmentProvider)), environment);
			} catch (IllegalArgumentException e) {
				return false;
			}
		}

		for (LlmProvider provider : LlmProvider.values()) {
			if (envSet(environmentVariable(provider), environment)) {
				return true;
			}
		}
		return false;
	}

	static String environmentVariable(LlmProvider provider) {
		return switch (provider) {
		case GEMINI -> "GEMINI_API_KEY"; //$NON-NLS-1$
		case OPENAI -> "OPENAI_API_KEY"; //$NON-NLS-1$
		case DEEPSEEK -> "DEEPSEEK_API_KEY"; //$NON-NLS-1$
		case QWEN -> "DASHSCOPE_API_KEY"; //$NON-NLS-1$
		case LLAMA -> "LLAMA_API_KEY"; //$NON-NLS-1$
		case MISTRAL -> "MISTRAL_API_KEY"; //$NON-NLS-1$
		};
	}

	private static LlmClient createClientFromPreferences() {
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		String provider = prefs.get(LlmPreferencePage.PREF_PROVIDER, ""); //$NON-NLS-1$
		String apiKey = LlmSecureCredentials.loadApiKey();
		String modelName = prefs.get(LlmPreferencePage.PREF_MODEL_NAME, ""); //$NON-NLS-1$
		LlmInferenceSettings settings = new LlmInferenceSettings(modelName, null, null);

		if (!provider.isBlank()) {
			return LlmClientFactory.create(
					LlmProvider.fromString(provider),
					apiKey.isBlank() ? null : apiKey,
					settings);
		}
		return LlmClientFactory.createFromEnvironment(null, settings);
	}

	private static boolean envSet(String key, Function<String, String> environment) {
		String value = environment.apply(key);
		return value != null && !value.isBlank();
	}
}
