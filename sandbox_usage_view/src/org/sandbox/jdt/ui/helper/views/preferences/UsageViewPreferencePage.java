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
package org.sandbox.jdt.ui.helper.views.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.sandbox.jdt.ui.helper.views.UsageViewPlugin;

/**
 * Preference page for the JavaHelper View (Usage View).
 */
public class UsageViewPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public UsageViewPreferencePage() {
		super(GRID);
		setPreferenceStore(UsageViewPlugin.getDefault().getPreferenceStore());
		setDescription("Settings for the JavaHelper View");
	}

	@Override
	public void createFieldEditors() {
		addField(new BooleanFieldEditor(
				UsageViewPreferenceConstants.SHOW_VIEW_AT_STARTUP,
				"Show JavaHelper View automatically at startup",
				getFieldEditorParent()));
	}

	@Override
	public void init(IWorkbench workbench) {
		// no initialization needed
	}
}
