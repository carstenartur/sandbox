/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.sandbox.jdt.internal.corext.fix.helper.LoopConversionInspection;
import org.sandbox.jdt.internal.corext.fix.helper.LoopTargetFormat;

/** Inspection policy is independent of explicit editor assists and cleanup profiles. */
public final class LoopConversionPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	public LoopConversionPreferencePage() {
		super(GRID);
		var store = new ScopedPreferenceStore(InstanceScope.INSTANCE, LoopConversionInspection.PLUGIN_ID);
		store.setDefault(LoopConversionInspection.SEVERITY, "off"); //$NON-NLS-1$
		store.setDefault(LoopConversionInspection.TARGET, LoopTargetFormat.STREAM.getId());
		setPreferenceStore(store);
		setDescription(CleanUpMessages.LoopConversion_Inspection_Description);
	}

	@Override
	public void init(IWorkbench workbench) {
		// The workbench supplies the preference dialog lifecycle.
	}

	@Override
	protected void createFieldEditors() {
		addField(new ComboFieldEditor(LoopConversionInspection.SEVERITY, CleanUpMessages.LoopConversion_Inspection_Severity,
				new String[][] {
					{ CleanUpMessages.LoopConversion_Inspection_Off, "off" }, //$NON-NLS-1$
					{ CleanUpMessages.LoopConversion_Inspection_Info, "info" }, //$NON-NLS-1$
					{ CleanUpMessages.LoopConversion_Inspection_Warning, "warning" } }, getFieldEditorParent())); //$NON-NLS-1$
		addField(new ComboFieldEditor(LoopConversionInspection.TARGET, CleanUpMessages.LoopConversion_TargetFormat,
				new String[][] {
					{ CleanUpMessages.LoopConversion_TargetFormat_Stream, LoopTargetFormat.STREAM.getId() },
					{ CleanUpMessages.LoopConversion_TargetFormat_EnhancedFor, LoopTargetFormat.FOR_LOOP.getId() },
					{ CleanUpMessages.LoopConversion_TargetFormat_IteratorWhile, LoopTargetFormat.WHILE_LOOP.getId() } }, getFieldEditorParent()));
	}
}
