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

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

/**
 * Initializes default preference values for LLM configuration.
 *
 * @since 1.2.6
 */
public class LlmPreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		IEclipsePreferences defaults = DefaultScope.INSTANCE.getNode(LlmPreferencePage.PLUGIN_ID);
		defaults.put(LlmPreferencePage.PREF_PROVIDER, "GEMINI"); //$NON-NLS-1$
		defaults.put(LlmPreferencePage.PREF_API_KEY, ""); //$NON-NLS-1$
		defaults.put(LlmPreferencePage.PREF_MODEL_NAME, ""); //$NON-NLS-1$
		defaults.putInt(LlmPreferencePage.PREF_MAX_TOKENS, 4096);
		defaults.put(LlmPreferencePage.PREF_TEMPERATURE, "0.3"); //$NON-NLS-1$
	}
}
