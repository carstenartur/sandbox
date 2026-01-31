/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import java.util.Map;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.preferences.cleanup.AbstractCleanUpTabPage;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.XMLCleanUp;

public class SandboxCodeTabPage extends AbstractCleanUpTabPage {

	/**
	 * Constant array for boolean selection
	 */
	static final String[] FALSE_TRUE= { CleanUpOptions.FALSE, CleanUpOptions.TRUE };

	public static final String ID= "org.eclipse.jdt.ui.cleanup.tabpage.sandbox"; //$NON-NLS-1$

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] { new XMLCleanUp(values) };
	}

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		Group java1d8Group= createGroup(numColumns, composite, CleanUpMessages.JavaFeatureTabPage_GroupName_Java1d8);
		
		// Main XML cleanup checkbox
		final CheckboxPreference xmlsimplify= createCheckboxPref(java1d8Group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_XML_CLEANUP,
				MYCleanUpConstants.XML_CLEANUP, FALSE_TRUE);
		
		// Indent sub-option (depends on main checkbox)
		intent(java1d8Group);
		final CheckboxPreference xmlIndent= createCheckboxPref(java1d8Group, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_XML_INDENT,
				MYCleanUpConstants.XML_CLEANUP_INDENT, FALSE_TRUE);
		
		// Register dependency
		registerSlavePreference(xmlsimplify, new CheckboxPreference[] { xmlIndent });
		
		intent(java1d8Group);
		registerPreference(xmlsimplify);
	}
}