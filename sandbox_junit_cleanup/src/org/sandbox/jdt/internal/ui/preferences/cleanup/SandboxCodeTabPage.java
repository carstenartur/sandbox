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

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

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
		final CheckboxPreference junit_assert= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT, MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_assume= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME, MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_ignore= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_IGNORE, MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_test= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST, MYCleanUpConstants.JUNIT_CLEANUP_4_TEST,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_before= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORE, MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_after= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTER, MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_beforeclass= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORECLASS, MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_afterclass= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTERCLASS, MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_ruletempfolder= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETEMPORARYFOLDER, MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_ruletestname= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETESTNAME, MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_externalresource= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_EXTERNALRESOURCE, MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_ruleexternalresource= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULEEXTERNALRESOURCE, MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE,
				FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_runwith= createCheckboxPref(junitGroup, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RUNWITH, MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH,
				FALSE_TRUE);
		registerSlavePreference(junitcb, new CheckboxPreference[] {
				junit_assert,
				junit_assume,
				junit_ignore,
				junit_test,
				junit_before,
				junit_after,
				junit_beforeclass,
				junit_afterclass,
				junit_ruletempfolder,
				junit_ruletestname,
				junit_ruleexternalresource,
				junit_externalresource,
				junit_runwith});
		intent(junitGroup);
		registerPreference(junitcb);
		
		Group junit3Group= createGroup(numColumns, composite, CleanUpMessages.JavaFeatureTabPage_GroupName_JUnit3);
		final CheckboxPreference junit3cb= createCheckboxPref(junit3Group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT3_CLEANUP, MYCleanUpConstants.JUNIT3_CLEANUP,
				FALSE_TRUE);
		intent(junit3Group);
		
		final CheckboxPreference junit3_test= createCheckboxPref(junit3Group, numColumns-1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT3_CLEANUP_TEST, MYCleanUpConstants.JUNIT_CLEANUP_3_TEST,
				FALSE_TRUE);
		
		intent(junit3Group);
		
		
		registerSlavePreference(junit3cb, new CheckboxPreference[] {
				junit3_test
				});
		intent(junit3Group);
		registerPreference(junit3cb);
	}
}
