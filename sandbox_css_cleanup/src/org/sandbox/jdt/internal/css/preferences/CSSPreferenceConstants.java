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
package org.sandbox.jdt.internal.css.preferences;

/**
 * Preference constants for CSS Cleanup.
 */
public class CSSPreferenceConstants {

	/** Preference key for enabling Prettier formatting */
	public static final String ENABLE_PRETTIER = "cssCleanup.enablePrettier"; //$NON-NLS-1$

	/** Preference key for enabling Stylelint validation */
	public static final String ENABLE_STYLELINT = "cssCleanup.enableStylelint"; //$NON-NLS-1$

	/** Preference key for Prettier options */
	public static final String PRETTIER_OPTIONS = "cssCleanup.prettierOptions"; //$NON-NLS-1$

	/** Preference key for Stylelint config path */
	public static final String STYLELINT_CONFIG = "cssCleanup.stylelintConfig"; //$NON-NLS-1$

	private CSSPreferenceConstants() {
		// Constants class
	}
}
