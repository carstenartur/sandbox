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
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.core.runtime.Status;
import org.osgi.service.prefs.BackingStoreException;

/** Secure-storage access and one-time migration for the LLM API key. */
public final class LlmSecureCredentials {

	private static final String SCREENSHOT_MODE = "sandbox.help.screenshot.mode"; //$NON-NLS-1$
	private static final String NODE_PATH = "sandbox_triggerpattern/llm"; //$NON-NLS-1$
	private static final String API_KEY = "apiKey"; //$NON-NLS-1$
	private static final ILog LOG = Platform.getLog(LlmSecureCredentials.class);

	private LlmSecureCredentials() {
	}

	/** Returns the API key from encrypted Equinox Secure Storage, or an empty string. */
	public static String loadApiKey() {
		if (Boolean.getBoolean(SCREENSHOT_MODE)) {
			return ""; //$NON-NLS-1$
		}
		try {
			migrateLegacyPreference();
			ISecurePreferences root = SecurePreferencesFactory.getDefault();
			if (root == null) {
				return ""; //$NON-NLS-1$
			}
			return root.node(NODE_PATH).get(API_KEY, ""); //$NON-NLS-1$
		} catch (StorageException | IOException | BackingStoreException e) {
			LOG.log(Status.warning("Could not read the LLM API key from Eclipse Secure Storage", e)); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
	}

	/** Stores the API key encrypted; an empty value removes the stored credential. */
	public static void storeApiKey(String apiKey) throws StorageException, IOException, BackingStoreException {
		ISecurePreferences root = SecurePreferencesFactory.getDefault();
		if (root == null) {
			throw new IOException("Eclipse Secure Storage is unavailable"); //$NON-NLS-1$
		}
		ISecurePreferences node = root.node(NODE_PATH);
		String value = apiKey == null ? "" : apiKey.trim(); //$NON-NLS-1$
		if (value.isEmpty()) {
			node.remove(API_KEY);
		} else {
			node.put(API_KEY, value, true);
		}
		node.flush();
		removeLegacyPreference();
	}

	/** Moves an API key saved by older Sandbox versions out of ordinary preferences. */
	static void migrateLegacyPreference() throws StorageException, IOException, BackingStoreException {
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
			node.flush();
		}
		removeLegacyPreference();
	}

	private static void removeLegacyPreference() throws BackingStoreException {
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		preferences.remove(LlmPreferencePage.PREF_API_KEY);
		preferences.flush();
	}
}
