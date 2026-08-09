/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences;

import java.io.IOException;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.osgi.service.prefs.BackingStoreException;
import org.sandbox.jdt.triggerpattern.llm.LlmProvider;

/** Secure-storage access and one-time migration for the LLM API key. */
public final class LlmSecureCredentials {

	private static final String SCREENSHOT_MODE = "sandbox.help.screenshot.mode"; //$NON-NLS-1$
	private static final String NODE_PATH = "sandbox_triggerpattern/llm"; //$NON-NLS-1$
	private static final String API_KEY = "apiKey"; //$NON-NLS-1$
	private static final String API_KEY_PROVIDER = "apiKeyProvider"; //$NON-NLS-1$
	private static final String LEGACY_DEFAULT_PROVIDER = "GEMINI"; //$NON-NLS-1$
	private static final ILog LOG = Platform.getLog(LlmSecureCredentials.class);

	private LlmSecureCredentials() {
	}

	/** Returns the API key owned by the currently configured provider, or an empty string. */
	public static String loadApiKey() {
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		return loadApiKey(preferences.get(LlmPreferencePage.PREF_PROVIDER, LEGACY_DEFAULT_PROVIDER));
	}

	/** Returns the API key only when the secure credential belongs to {@code providerName}. */
	public static String loadApiKey(String providerName) {
		if (Boolean.getBoolean(SCREENSHOT_MODE)) {
			return ""; //$NON-NLS-1$
		}
		String provider = canonicalProvider(providerName);
		if (provider == null) {
			return ""; //$NON-NLS-1$
		}
		try {
			migrateLegacyPreference(provider);
			ISecurePreferences root = SecurePreferencesFactory.getDefault();
			if (root == null) {
				return ""; //$NON-NLS-1$
			}
			ISecurePreferences node = root.node(NODE_PATH);
			String value = node.get(API_KEY, ""); //$NON-NLS-1$
			if (value.isBlank()) {
				return ""; //$NON-NLS-1$
			}
			String credentialProvider = node.get(API_KEY_PROVIDER, ""); //$NON-NLS-1$
			if (credentialProvider.isBlank()) {
				// Secure Storage from the first implementation had one unscoped key. Its
				// only defensible migration is to bind it to the provider selected when
				// the upgraded version first reads it.
				node.put(API_KEY_PROVIDER, provider, false);
				node.flush();
				credentialProvider = provider;
			}
			return credentialForProvider(provider, credentialProvider, value);
		} catch (StorageException | IOException | BackingStoreException e) {
			LOG.log(Status.warning("Could not read the LLM API key from Eclipse Secure Storage", e)); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	/** Stores the API key for the currently configured provider. */
	public static void storeApiKey(String apiKey) throws StorageException, IOException, BackingStoreException {
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		storeApiKey(preferences.get(LlmPreferencePage.PREF_PROVIDER, LEGACY_DEFAULT_PROVIDER), apiKey);
	}

	/**
	 * Stores the API key encrypted and associates it with {@code providerName}.
	 * An empty value removes a credential only when it belongs to that provider.
	 */
	public static void storeApiKey(String providerName, String apiKey)
			throws StorageException, IOException, BackingStoreException {
		String provider = canonicalProvider(providerName);
		if (provider == null) {
			throw new IOException("Unsupported LLM provider: " + providerName); //$NON-NLS-1$
		}
		ISecurePreferences root = SecurePreferencesFactory.getDefault();
		if (root == null) {
			throw new IOException("Eclipse Secure Storage is unavailable"); //$NON-NLS-1$
		}
		migrateLegacyPreference(provider);
		ISecurePreferences node = root.node(NODE_PATH);
		String value = apiKey == null ? "" : apiKey.trim(); //$NON-NLS-1$
		if (value.isEmpty()) {
			String credentialProvider = node.get(API_KEY_PROVIDER, ""); //$NON-NLS-1$
			if (credentialProvider.isBlank() || provider.equals(credentialProvider)) {
				node.remove(API_KEY);
				node.remove(API_KEY_PROVIDER);
				node.flush();
			}
		} else {
			node.put(API_KEY, value, true);
			node.put(API_KEY_PROVIDER, provider, false);
			node.flush();
		}
		removeLegacyPreference();
	}

	/** Moves an API key saved by older Sandbox versions out of ordinary preferences. */
	static void migrateLegacyPreference() throws StorageException, IOException, BackingStoreException {
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		migrateLegacyPreference(preferences.get(LlmPreferencePage.PREF_PROVIDER, LEGACY_DEFAULT_PROVIDER));
	}

	static void migrateLegacyPreference(String providerName)
			throws StorageException, IOException, BackingStoreException {
		String provider = canonicalProvider(providerName);
		if (provider == null) {
			return;
		}
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		String legacy = preferences.get(LlmPreferencePage.PREF_API_KEY, ""); //$NON-NLS-1$
		if (legacy == null || legacy.isBlank()) {
			return;
		}
		ISecurePreferences root = SecurePreferencesFactory.getDefault();
		if (root == null) {
			return;
		}
		ISecurePreferences node = root.node(NODE_PATH);
		if (node.get(API_KEY, "").isBlank()) { //$NON-NLS-1$
			node.put(API_KEY, legacy, true);
			node.put(API_KEY_PROVIDER, provider, false);
			node.flush();
		}
		removeLegacyPreference();
	}

	static String canonicalProvider(String providerName) {
		String value = providerName == null || providerName.isBlank()
				? LEGACY_DEFAULT_PROVIDER
				: providerName;
		try {
			return LlmProvider.fromString(value).name();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static String credentialForProvider(String requestedProvider, String credentialProvider, String value) {
		return requestedProvider != null && requestedProvider.equals(credentialProvider)
				&& value != null && !value.isBlank()
				? value
				: ""; //$NON-NLS-1$
	}

	private static void removeLegacyPreference() throws BackingStoreException {
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		preferences.remove(LlmPreferencePage.PREF_API_KEY);
		preferences.flush();
	}
}
