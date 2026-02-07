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
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import java.util.Map;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.preferences.cleanup.AbstractCleanUpTabPage;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.IntToEnumCleanUp;

/**
 * Cleanup tab page for int to enum conversion options.
 */
public class SandboxCodeTabPage extends AbstractCleanUpTabPage {

	/**
	 * Constant array for boolean selection
	 */
	static final String[] FALSE_TRUE = {
			CleanUpOptions.FALSE,
			CleanUpOptions.TRUE
	};

	public static final String ID = "org.eclipse.jdt.ui.cleanup.tabpage.int_to_enum"; //$NON-NLS-1$

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] {
				new IntToEnumCleanUp(values)
		};
	}

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		Group intToEnumGroup = createGroup(numColumns, composite, CleanUpMessages.IntToEnumTabPage_GroupName_IntToEnum);
		createCheckboxPref(intToEnumGroup, numColumns, 
				CleanUpMessages.IntToEnumTabPage_CheckboxName_IntToEnum, 
				MYCleanUpConstants.INT_TO_ENUM_CLEANUP, 
				FALSE_TRUE);
	}
}
