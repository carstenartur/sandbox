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
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUp;

public class SandboxCodeTabPage extends AbstractCleanUpTabPage {

	/**
	 * Constant array for boolean selection
	 */
	static final String[] FALSE_TRUE= { CleanUpOptions.FALSE, CleanUpOptions.TRUE };

	public static final String ID= "org.eclipse.jdt.ui.cleanup.tabpage.sandbox"; //$NON-NLS-1$

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] { new JUnitCleanUp(values) };
	}

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		Group junitGroup= createGroup(numColumns, composite, CleanUpMessages.JavaFeatureTabPage_GroupName_JUnit);
		final CheckboxPreference junitcb= createCheckboxPref(junitGroup, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP, MYCleanUpConstants.JUNIT_CLEANUP,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_assert= createCheckboxPref(junitGroup, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT, MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT,
				FALSE_TRUE);
		final CheckboxPreference junit_assume= createCheckboxPref(junitGroup, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME, MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME,
				FALSE_TRUE);
		final CheckboxPreference junit_ignore= createCheckboxPref(junitGroup, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_IGNORE, MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE,
				FALSE_TRUE);
		final CheckboxPreference junit_test= createCheckboxPref(junitGroup, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST, MYCleanUpConstants.JUNIT_CLEANUP_4_TEST,
				FALSE_TRUE);
		registerSlavePreference(junitcb, new CheckboxPreference[] {junit_assert, junit_assume,junit_ignore,junit_test});
		intent(junitGroup);
		
		registerPreference(junitcb);
	}
}