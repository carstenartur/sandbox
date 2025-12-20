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
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUp;

public class SandboxCodeTabPage extends AbstractCleanUpTabPage {

	/**
	 * Constant array for boolean selection
	 */
	static final String[] FALSE_TRUE = {
			CleanUpOptions.FALSE,
			CleanUpOptions.TRUE
	};

	public static final String ID= "org.eclipse.jdt.ui.cleanup.tabpage.duplicate_code"; //$NON-NLS-1$

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] {
				new UseExplicitEncodingCleanUp(values)
		};
	}

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		Group java1d6Group= createGroup(numColumns, composite, CleanUpMessages.JavaFeatureTabPage_GroupName_Java1d6);
		final CheckboxPreference explicit_encoding= createCheckboxPref(java1d6Group, numColumns, CleanUpMessages.JavaFeatureTabPage_CheckboxName_ExplicitEncoding, MYCleanUpConstants.EXPLICITENCODING_CLEANUP, FALSE_TRUE);
		intent(java1d6Group);
		final RadioPreference useBlockAlwaysPref= createRadioPref(java1d6Group, numColumns - 1, CleanUpMessages.JavaFeatureTabPage_RadioName_Keep_Behavior, MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR, FALSE_TRUE);
		intent(java1d6Group);
		final RadioPreference useBlockJDTStylePref= createRadioPref(java1d6Group, numColumns - 1, CleanUpMessages.JavaFeatureTabPage_RadioName_Insert_UTF8, MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8, FALSE_TRUE);
		intent(java1d6Group);
		final RadioPreference useBlockNeverPref= createRadioPref(java1d6Group, numColumns - 1, CleanUpMessages.JavaFeatureTabPage_RadioName_Aggregate_to_UTF8, MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8, FALSE_TRUE);
		registerSlavePreference(explicit_encoding, new RadioPreference[] {useBlockAlwaysPref, useBlockJDTStylePref, useBlockNeverPref});


	}

}
