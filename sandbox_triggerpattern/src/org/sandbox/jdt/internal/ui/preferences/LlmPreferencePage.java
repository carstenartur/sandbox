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
package org.sandbox.jdt.internal.ui.preferences;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/**
 * Preference page for LLM settings used by AI-powered rule inference.
 * <p>
 * Users can configure the LLM provider, API key, model name, max tokens,
 * and temperature. When preferences are empty, the system falls back to
 * environment variables via {@code LlmClientFactory.createFromEnvironment()}.
 * </p>
 *
 * @since 1.2.6
 */
public class LlmPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	/** Plugin identifier used as preference scope node. */
	public static final String PLUGIN_ID = "sandbox_triggerpattern"; //$NON-NLS-1$

	private static final String PREFIX = "org.sandbox.jdt.triggerpattern.llm."; //$NON-NLS-1$

	/** Preference key for the selected LLM provider. */
	public static final String PREF_PROVIDER = PREFIX + "provider"; //$NON-NLS-1$

	/** Preference key for the API key. */
	public static final String PREF_API_KEY = PREFIX + "apiKey"; //$NON-NLS-1$

	/** Preference key for the model name. */
	public static final String PREF_MODEL_NAME = PREFIX + "modelName"; //$NON-NLS-1$

	/** Preference key for the maximum number of tokens. */
	public static final String PREF_MAX_TOKENS = PREFIX + "maxTokens"; //$NON-NLS-1$

	/** Preference key for the temperature value. */
	public static final String PREF_TEMPERATURE = PREFIX + "temperature"; //$NON-NLS-1$

	private static final String[][] PROVIDER_ENTRIES = {
			{ "Gemini", "GEMINI" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "OpenAI", "OPENAI" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "DeepSeek", "DEEPSEEK" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "Qwen", "QWEN" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "Llama", "LLAMA" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "Mistral", "MISTRAL" } //$NON-NLS-1$ //$NON-NLS-2$
	};

	/**
	 * Creates a new LLM preference page with GRID layout.
	 */
	public LlmPreferencePage() {
		super(GRID);
		setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, PLUGIN_ID));
		setDescription("Configure LLM settings for AI-powered rule inference.\n" //$NON-NLS-1$
				+ "Leave API Key empty to fall back to environment variables."); //$NON-NLS-1$
	}

	@Override
	public void createFieldEditors() {
		addField(new ComboFieldEditor(
				PREF_PROVIDER,
				"&Provider:", //$NON-NLS-1$
				PROVIDER_ENTRIES,
				getFieldEditorParent()));

		addField(new StringFieldEditor(
				PREF_API_KEY,
				"&API Key:", //$NON-NLS-1$
				getFieldEditorParent()));

		addField(new StringFieldEditor(
				PREF_MODEL_NAME,
				"&Model name:", //$NON-NLS-1$
				getFieldEditorParent()));

		IntegerFieldEditor maxTokensField = new IntegerFieldEditor(
				PREF_MAX_TOKENS,
				"Max &tokens:", //$NON-NLS-1$
				getFieldEditorParent());
		maxTokensField.setValidRange(1, 128000);
		addField(maxTokensField);

		StringFieldEditor temperatureField = new StringFieldEditor(
				PREF_TEMPERATURE,
				"T&emperature (0.0\u20131.0):", //$NON-NLS-1$
				getFieldEditorParent()) {
			@Override
			protected boolean doCheckState() {
				try {
					double value = Double.parseDouble(getStringValue().trim());
					if (value < 0.0 || value > 1.0) {
						setErrorMessage("Temperature must be between 0.0 and 1.0"); //$NON-NLS-1$
						return false;
					}
				} catch (NumberFormatException e) {
					setErrorMessage("Temperature must be a valid decimal number"); //$NON-NLS-1$
					return false;
				}
				return true;
			}
		};
		temperatureField.setEmptyStringAllowed(false);
		addField(temperatureField);
	}

	@Override
	public void init(IWorkbench workbench) {
		// Defaults are set by LlmPreferenceInitializer
	}
}
