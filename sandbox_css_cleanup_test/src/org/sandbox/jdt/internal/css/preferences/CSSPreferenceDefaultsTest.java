/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.css.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.css.CSSCleanupPlugin;

/** Exercises the same preference store as the action without opening its page. */
class CSSPreferenceDefaultsTest {

	private static final List<String> KEYS= List.of(CSSPreferenceConstants.ENABLE_PRETTIER,
			CSSPreferenceConstants.ENABLE_STYLELINT, CSSPreferenceConstants.PRETTIER_OPTIONS,
			CSSPreferenceConstants.STYLELINT_CONFIG);

	private final Map<String, String> savedValues= new HashMap<>();
	private IEclipsePreferences preferences;
	private IPreferenceStore store;

	@BeforeEach
	void usePreferencesWithoutUserOverrides() {
		preferences= InstanceScope.INSTANCE.getNode(CSSCleanupPlugin.PLUGIN_ID);
		for (String key : KEYS) {
			savedValues.put(key, preferences.get(key, null));
			preferences.remove(key);
		}
		store= CSSCleanupPlugin.getDefault().getPreferenceStore();
	}

	@AfterEach
	void restoreUserOverrides() {
		savedValues.forEach((key, value) -> {
			if (value == null) {
				preferences.remove(key);
			} else {
				preferences.put(key, value);
			}
		});
	}

	@Test
	void formattingAndValidationAreEnabledBeforeOpeningPreferences() {
		assertTrue(store.getBoolean(CSSPreferenceConstants.ENABLE_PRETTIER));
		assertTrue(store.getBoolean(CSSPreferenceConstants.ENABLE_STYLELINT));
		assertEquals("{}", store.getString(CSSPreferenceConstants.PRETTIER_OPTIONS)); //$NON-NLS-1$
		assertEquals("", store.getString(CSSPreferenceConstants.STYLELINT_CONFIG)); //$NON-NLS-1$
	}

	@Test
	void explicitUserChoicesOverrideDefaults() {
		store.setValue(CSSPreferenceConstants.ENABLE_PRETTIER, false);
		store.setValue(CSSPreferenceConstants.ENABLE_STYLELINT, false);
		store.setValue(CSSPreferenceConstants.PRETTIER_OPTIONS, "{\"tabWidth\":4}"); //$NON-NLS-1$
		store.setValue(CSSPreferenceConstants.STYLELINT_CONFIG, "project/stylelint.json"); //$NON-NLS-1$

		assertFalse(store.getBoolean(CSSPreferenceConstants.ENABLE_PRETTIER));
		assertFalse(store.getBoolean(CSSPreferenceConstants.ENABLE_STYLELINT));
		assertEquals("{\"tabWidth\":4}", store.getString(CSSPreferenceConstants.PRETTIER_OPTIONS)); //$NON-NLS-1$
		assertEquals("project/stylelint.json", store.getString(CSSPreferenceConstants.STYLELINT_CONFIG)); //$NON-NLS-1$
		assertTrue(store.getDefaultBoolean(CSSPreferenceConstants.ENABLE_PRETTIER));
		assertTrue(store.getDefaultBoolean(CSSPreferenceConstants.ENABLE_STYLELINT));
		assertEquals("{}", store.getDefaultString(CSSPreferenceConstants.PRETTIER_OPTIONS)); //$NON-NLS-1$
		assertEquals("", store.getDefaultString(CSSPreferenceConstants.STYLELINT_CONFIG)); //$NON-NLS-1$
	}
}
