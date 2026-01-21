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

import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Display;
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
		setDescription("Configure CSS formatting and validation tools."); //$NON-NLS-1$
	}

	@Override
	public void createFieldEditors() {
		// Show initial status message
		setDescription(getDescription() + "\n\nStatus: Checking tool availability..."); //$NON-NLS-1$

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

		// Check tool availability asynchronously to avoid blocking UI
		Job.create("Checking CSS tool availability", monitor -> { //$NON-NLS-1$
			String statusInfo = "\n\nStatus:\n" + //$NON-NLS-1$
					"  Node.js: " + (NodeExecutor.isNodeAvailable() ? "Available" : "Not found") + "\n" + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					"  npx: " + (NodeExecutor.isNpxAvailable() ? "Available" : "Not found") + "\n" + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					"  Prettier: " + (PrettierRunner.isPrettierAvailable() ? "Available" : "Not installed") + "\n" + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					"  Stylelint: " + (StylelintRunner.isStylelintAvailable() ? "Available" : "Not installed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			// Update description on UI thread
			Display.getDefault().asyncExec(() -> {
				if (!getControl().isDisposed()) {
					setDescription("Configure CSS formatting and validation tools." + statusInfo); //$NON-NLS-1$
				}
			});
			return org.eclipse.core.runtime.Status.OK_STATUS;
		}).schedule();
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
