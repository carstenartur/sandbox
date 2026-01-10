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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import java.util.Map;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.preferences.cleanup.AbstractCleanUpTabPage;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.StringSimplificationCleanUp;

/**
 * Preference tab page for string simplification cleanup options.
 * 
 * @since 1.2.2
 */
public class SandboxCodeTabPage extends AbstractCleanUpTabPage {
	
	/**
	 * Constant array for boolean selection
	 */
	static final String[] FALSE_TRUE = {
			CleanUpOptions.FALSE,
			CleanUpOptions.TRUE
	};
	
	public static final String ID = "org.sandbox.jdt.ui.cleanup.tabpage.string_simplification"; //$NON-NLS-1$
	
	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] {
				new StringSimplificationCleanUp(values)
		};
	}
	
	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		Group stringGroup = createGroup(numColumns, composite, CleanUpMessages.StringSimplificationTabPage_GroupName);
		createCheckboxPref(stringGroup, numColumns, CleanUpMessages.StringSimplificationTabPage_CheckboxName_StringSimplification, 
				MYCleanUpConstants.STRING_SIMPLIFICATION_CLEANUP, FALSE_TRUE);
	}
}
