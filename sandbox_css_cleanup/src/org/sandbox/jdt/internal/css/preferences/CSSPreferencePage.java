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

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.sandbox.jdt.internal.css.CSSCleanupPlugin;
import org.sandbox.jdt.internal.css.core.NodeExecutor;
import org.sandbox.jdt.internal.css.core.PrettierRunner;
import org.sandbox.jdt.internal.css.core.StylelintRunner;

/**
 * Preference page for CSS Cleanup settings.
 */
public class CSSPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public CSSPreferencePage() {
		super(GRID);
		setPreferenceStore(CSSCleanupPlugin.getDefault().getPreferenceStore());
		setDescription("Configure CSS formatting and validation tools.\n\n" + //$NON-NLS-1$
				"Status:\n" + //$NON-NLS-1$
				"  Node.js: " + (NodeExecutor.isNodeAvailable() ? "Available" : "Not found") + "\n" + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"  npx: " + (NodeExecutor.isNpxAvailable() ? "Available" : "Not found") + "\n" + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"  Prettier: " + (PrettierRunner.isPrettierAvailable() ? "Available" : "Not installed") + "\n" + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"  Stylelint: " + (StylelintRunner.isStylelintAvailable() ? "Available" : "Not installed")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Override
	public void createFieldEditors() {
		addField(new BooleanFieldEditor(
				CSSPreferenceConstants.ENABLE_PRETTIER,
				"&Enable Prettier formatting", //$NON-NLS-1$
				getFieldEditorParent()));

		addField(new BooleanFieldEditor(
				CSSPreferenceConstants.ENABLE_STYLELINT,
				"Enable &Stylelint validation", //$NON-NLS-1$
				getFieldEditorParent()));

		addField(new StringFieldEditor(
				CSSPreferenceConstants.PRETTIER_OPTIONS,
				"Prettier &options (JSON):", //$NON-NLS-1$
				getFieldEditorParent()));

		addField(new FileFieldEditor(
				CSSPreferenceConstants.STYLELINT_CONFIG,
				"Stylelint &config file:", //$NON-NLS-1$
				getFieldEditorParent()));
	}

	@Override
	public void init(IWorkbench workbench) {
		// Initialize default values
		getPreferenceStore().setDefault(CSSPreferenceConstants.ENABLE_PRETTIER, true);
		getPreferenceStore().setDefault(CSSPreferenceConstants.ENABLE_STYLELINT, true);
		getPreferenceStore().setDefault(CSSPreferenceConstants.PRETTIER_OPTIONS, "{}"); //$NON-NLS-1$
		getPreferenceStore().setDefault(CSSPreferenceConstants.STYLELINT_CONFIG, ""); //$NON-NLS-1$
	}
}
