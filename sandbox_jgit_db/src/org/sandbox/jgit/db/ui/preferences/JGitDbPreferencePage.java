/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jgit.db.ui.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.sandbox.jgit.db.internal.DbActivator;

/**
 * Preference page for the Git Database Index plugin.
 *
 * <p>
 * Settings:
 * </p>
 * <ul>
 * <li>Auto-index on commit/pull</li>
 * <li>Index Java blob metadata</li>
 * <li>Index non-Java files</li>
 * <li>Maximum blob size (KB)</li>
 * </ul>
 */
public class JGitDbPreferencePage extends FieldEditorPreferencePage
		implements IWorkbenchPreferencePage {

	/**
	 * Creates a new preference page.
	 */
	public JGitDbPreferencePage() {
		super(GRID);
		setPreferenceStore(DbActivator.getDefault().getPreferenceStore());
		setDescription("Git Database Index Settings"); //$NON-NLS-1$
	}

	@Override
	public void createFieldEditors() {
		addField(new BooleanFieldEditor(
				JGitDbPreferenceConstants.AUTO_INDEX_ON_COMMIT,
				"Auto-index on commit/pull", //$NON-NLS-1$
				getFieldEditorParent()));

		addField(new BooleanFieldEditor(
				JGitDbPreferenceConstants.INDEX_JAVA_METADATA,
				"Index Java blob metadata", //$NON-NLS-1$
				getFieldEditorParent()));

		addField(new BooleanFieldEditor(
				JGitDbPreferenceConstants.INDEX_NON_JAVA_FILES,
				"Index non-Java files", //$NON-NLS-1$
				getFieldEditorParent()));

		IntegerFieldEditor maxBlobField = new IntegerFieldEditor(
				JGitDbPreferenceConstants.MAX_BLOB_SIZE_KB,
				"Max blob size (KB):", //$NON-NLS-1$
				getFieldEditorParent());
		maxBlobField.setValidRange(1, 10240);
		addField(maxBlobField);
	}

	@Override
	public void init(IWorkbench workbench) {
		// nothing to initialize
	}
}
