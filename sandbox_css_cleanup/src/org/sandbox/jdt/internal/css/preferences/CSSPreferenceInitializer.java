/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.css.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.sandbox.jdt.internal.css.CSSCleanupPlugin;

/** Makes action defaults available before the preferences page is opened. */
public class CSSPreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		var defaults= DefaultScope.INSTANCE.getNode(CSSCleanupPlugin.PLUGIN_ID);
		defaults.putBoolean(CSSPreferenceConstants.ENABLE_PRETTIER, true);
		defaults.putBoolean(CSSPreferenceConstants.ENABLE_STYLELINT, true);
		defaults.put(CSSPreferenceConstants.PRETTIER_OPTIONS, "{}"); //$NON-NLS-1$
		defaults.put(CSSPreferenceConstants.STYLELINT_CONFIG, ""); //$NON-NLS-1$
	}
}
