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

import java.io.IOException;
import java.util.function.BooleanSupplier;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.service.prefs.BackingStoreException;
import org.sandbox.jdt.triggerpattern.mining.llm.EclipseLlmService;

/** Preference page for the LLM settings actually consumed by rule inference. */
public class LlmPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	@FunctionalInterface
	interface CredentialPersistence {
		void persist() throws StorageException, IOException, BackingStoreException;
	}

	public static final String PLUGIN_ID = "sandbox_triggerpattern"; //$NON-NLS-1$
	private static final String PREFIX = "org.sandbox.jdt.triggerpattern.llm."; //$NON-NLS-1$

	public static final String PREF_PROVIDER = PREFIX + "provider"; //$NON-NLS-1$
	/** Legacy ordinary-preference key. New versions migrate it to Secure Storage. */
	@Deprecated
	public static final String PREF_API_KEY = PREFIX + "apiKey"; //$NON-NLS-1$
	public static final String PREF_MODEL_NAME = PREFIX + "modelName"; //$NON-NLS-1$

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

	private Text apiKeyText;
	private ComboFieldEditor providerField;
	private String selectedProvider;

	public LlmPreferencePage() {
		super(GRID);
		setPreferenceStore(new ScopedPreferenceStore(InstanceScope.INSTANCE, PLUGIN_ID));
		setDescription("Configure the provider used for AI-assisted rule inference.\n" //$NON-NLS-1$
				+ "API keys are encrypted in Eclipse Secure Storage and bound to the selected provider. " //$NON-NLS-1$
				+ "Leave the key empty to use the provider-specific environment variable; leave Model name empty to use the provider default."); //$NON-NLS-1$
	}

	@Override
	public void createFieldEditors() {
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getFieldEditorParent(),
				"sandbox_triggerpattern.preferences"); //$NON-NLS-1$

		selectedProvider = getPreferenceStore().getString(PREF_PROVIDER);
		providerField = new ComboFieldEditor(
				PREF_PROVIDER,
				"&Provider:", //$NON-NLS-1$
				PROVIDER_ENTRIES,
				getFieldEditorParent());
		addField(providerField);

		createSecureApiKeyField(getFieldEditorParent());

		addField(new StringFieldEditor(
				PREF_MODEL_NAME,
				"&Model name (optional):", //$NON-NLS-1$
				getFieldEditorParent()));

		addField(new BooleanFieldEditor(
				PREF_WIZARD_AUTO_AI,
				"Automatically generate rule with AI when opening wizard from selection", //$NON-NLS-1$
				getFieldEditorParent()));
	}

	private void createSecureApiKeyField(Composite parent) {
		Label label = new Label(parent, SWT.NONE);
		label.setText("&API Key (Secure Storage):"); //$NON-NLS-1$

		apiKeyText = new Text(parent, SWT.BORDER | SWT.SINGLE | SWT.PASSWORD);
		apiKeyText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		apiKeyText.setText(LlmSecureCredentials.loadApiKey(selectedProvider));

		Label explanation = new Label(parent, SWT.WRAP);
		explanation.setText("Stored encrypted by Eclipse Equinox Secure Storage for the selected provider; environment-variable fallback remains available."); //$NON-NLS-1$
		GridData explanationData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		explanationData.horizontalSpan = 2;
		explanation.setLayoutData(explanationData);
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getSource() == providerField && FieldEditor.VALUE.equals(event.getProperty())) {
			selectedProvider = String.valueOf(event.getNewValue());
			if (apiKeyText != null && !apiKeyText.isDisposed()) {
				apiKeyText.setText(LlmSecureCredentials.loadApiKey(selectedProvider));
			}
		}
		super.propertyChange(event);
	}

	@Override
	public boolean performOk() {
		try {
			boolean result = persistAfterPreferenceAcceptance(
					super::performOk,
					() -> LlmSecureCredentials.storeApiKey(selectedProvider,
							apiKeyText != null ? apiKeyText.getText() : "")); //$NON-NLS-1$
			if (result) {
				EclipseLlmService.reset();
			}
			return result;
		} catch (StorageException | IOException | BackingStoreException e) {
			setErrorMessage("Could not save the API key in Eclipse Secure Storage: " + e.getMessage()); //$NON-NLS-1$
			return false;
		}
	}

	static boolean persistAfterPreferenceAcceptance(BooleanSupplier persistPreferences,
			CredentialPersistence persistCredential)
			throws StorageException, IOException, BackingStoreException {
		if (!persistPreferences.getAsBoolean()) {
			return false;
		}
		persistCredential.persist();
		return true;
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
		if (apiKeyText != null && !apiKeyText.isDisposed()) {
			apiKeyText.setText(""); //$NON-NLS-1$
		}
	}

	@Override
	public void init(IWorkbench workbench) {
		// Defaults are set by LlmPreferenceInitializer.
	}
}
