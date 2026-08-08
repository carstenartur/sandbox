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
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/** Preference page for the LLM settings actually consumed by rule inference. */
public class LlmPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public static final String PLUGIN_ID = "sandbox_triggerpattern"; //$NON-NLS-1$
	private static final String PREFIX = "org.sandbox.jdt.triggerpattern.llm."; //$NON-NLS-1$

	public static final String PREF_PROVIDER = PREFIX + "provider"; //$NON-NLS-1$
	public static final String PREF_API_KEY = PREFIX + "apiKey"; //$NON-NLS-1$
	public static final String PREF_MODEL_NAME = PREFIX + "modelName"; //$NON-NLS-1$

	/** Legacy keys retained so existing preference nodes can be migrated/ignored safely. */
	@Deprecated
	public static final String PREF_MAX_TOKENS = PREFIX + "maxTokens"; //$NON-NLS-1$
	@Deprecated
	public static final String PREF_TEMPERATURE = PREFIX + "temperature"; //$NON-NLS-1$

	private static final String WIZARD_PREFIX = "org.sandbox.jdt.triggerpattern.wizard."; //$NON-NLS-1$
	public static final String PREF_WIZARD_AUTO_AI = WIZARD_PREFIX + "autoInferOnSelection"; //$NON-NLS-1$
	@Deprecated
	public static final String PREF_WIZARD_DEFAULT_FOLDER = WIZARD_PREFIX + "defaultHintFolder"; //$NON-NLS-1$

	private static final String[][] PROVIDER_ENTRIES = {
			{ "Gemini", "GEMINI" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "OpenAI", "OPENAI" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "DeepSeek", "DEEPSEEK" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "Qwen", "QWEN" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "Llama", "LLAMA" }, //$NON-NLS-1$ //$NON-NLS-2$
			{ "Mistral", "MISTRAL" } //$NON-NLS-1$ //$NON-NLS-2$
	};

	public LlmPreferencePage() {
		super(GRID);
		setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, PLUGIN_ID));
		setDescription("Configure the provider used for AI-assisted rule inference.\n" //$NON-NLS-1$
				+ "Leave API Key empty to use the provider-specific environment variable; " //$NON-NLS-1$
				+ "leave Model name empty to use the provider/environment default."); //$NON-NLS-1$
	}

	@Override
	public void createFieldEditors() {
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getFieldEditorParent(),
				"sandbox_triggerpattern.preferences"); //$NON-NLS-1$

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
				"&Model name (optional):", //$NON-NLS-1$
				getFieldEditorParent()));

		addField(new BooleanFieldEditor(
				PREF_WIZARD_AUTO_AI,
				"Automatically generate rule with AI when opening wizard from selection", //$NON-NLS-1$
				getFieldEditorParent()));
	}

	@Override
	public void init(IWorkbench workbench) {
		// Defaults are set by LlmPreferenceInitializer.
	}
}
