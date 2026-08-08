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
import org.sandbox.jdt.internal.ui.preferences.LlmSecureCredentials;
import org.sandbox.jdt.triggerpattern.llm.AiRuleInferenceEngine;
import org.sandbox.jdt.triggerpattern.llm.LlmClient;
import org.sandbox.jdt.triggerpattern.llm.LlmClientFactory;
import org.sandbox.jdt.triggerpattern.llm.LlmInferenceSettings;
import org.sandbox.jdt.triggerpattern.llm.LlmProvider;

/** Eclipse-level service that owns the shared AI rule-inference engine. */
public class EclipseLlmService {

    private static final String DOCUMENTATION_SCREENSHOT_PROPERTY = "sandbox.help.screenshot.mode"; //$NON-NLS-1$
    private static final Object INSTANCE_LOCK= new Object();

    private static volatile EclipseLlmService instance;

    private final Object lifecycleLock= new Object();

    private LlmClient llmClient;
    private AiRuleInferenceEngine engine;

    private EclipseLlmService() {
    }

    public static EclipseLlmService getInstance() {
        EclipseLlmService current= instance;
        if (current == null) {
            synchronized (INSTANCE_LOCK) {
                current= instance;
                if (current == null) {
                    current= new EclipseLlmService();
                    instance= current;
                }
            }
        }
        return current;
    }

    public AiRuleInferenceEngine getEngine() {
        synchronized (lifecycleLock) {
            if (engine == null) {
                llmClient= createClientFromPreferences();
                engine= new AiRuleInferenceEngine(llmClient);
            }
            return engine;
        }
    }

    /** Documentation generation must never contact or unlock an external provider. */
    public boolean isAvailable() {
        if (Boolean.getBoolean(DOCUMENTATION_SCREENSHOT_PROPERTY)) {
            return false;
        }
        return hasSecureApiKey() || hasAnyEnvApiKey();
    }

    public void shutdown() {
        synchronized (lifecycleLock) {
            if (llmClient != null) {
                llmClient.close();
                llmClient= null;
                engine= null;
            }
        }
    }

    public static void reset() {
        EclipseLlmService current;
        synchronized (INSTANCE_LOCK) {
            current= instance;
            instance= null;
        }
        if (current != null) {
            current.shutdown();
        }
    }

    private static LlmClient createClientFromPreferences() {
        IEclipsePreferences prefs= InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
        String provider= prefs.get(LlmPreferencePage.PREF_PROVIDER, ""); //$NON-NLS-1$
        String apiKey= LlmSecureCredentials.loadApiKey();
        String modelName= prefs.get(LlmPreferencePage.PREF_MODEL_NAME, ""); //$NON-NLS-1$
        LlmInferenceSettings settings= new LlmInferenceSettings(modelName, null, null);

        if (!provider.isBlank()) {
            return LlmClientFactory.create(
                    LlmProvider.fromString(provider),
                    apiKey.isBlank() ? null : apiKey,
                    settings);
        }
        return LlmClientFactory.createFromEnvironment(null, settings);
    }

    private static boolean hasSecureApiKey() {
        IEclipsePreferences prefs= InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
        String provider= prefs.get(LlmPreferencePage.PREF_PROVIDER, ""); //$NON-NLS-1$
        return !provider.isBlank() && !LlmSecureCredentials.loadApiKey().isBlank();
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
        String value= System.getenv(key);
        return value != null && !value.isBlank();
    }
}
