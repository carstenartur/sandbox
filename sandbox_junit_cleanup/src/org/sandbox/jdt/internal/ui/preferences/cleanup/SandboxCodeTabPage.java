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
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import org.sandbox.jdt.internal.corext.fix.JUnitMigrationOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUp;

public class SandboxCodeTabPage extends AbstractCleanUpTabPage {

	/** Constant array for boolean selection. */
	static final String[] FALSE_TRUE= { CleanUpOptions.FALSE, CleanUpOptions.TRUE };

	public static final String ID= "org.eclipse.jdt.ui.cleanup.tabpage.sandbox.junit"; //$NON-NLS-1$

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
		final CheckboxPreference junitBestEffort= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEST_EFFORT,
				JUnitMigrationOptions.BEST_EFFORT, FALSE_TRUE);

		intent(junitGroup);
		Label quickSelectLabel= new Label(junitGroup, SWT.NONE);
		quickSelectLabel.setText(CleanUpMessages.JavaFeatureTabPage_QuickSelect_Label);

		intent(junitGroup);
		final Combo quickSelectCombo= new Combo(junitGroup, SWT.READ_ONLY | SWT.DROP_DOWN);
		quickSelectCombo.setItems(new String[] {
				CleanUpMessages.JavaFeatureTabPage_QuickSelect_Empty,
				CleanUpMessages.JavaFeatureTabPage_QuickSelect_FullMigration,
				CleanUpMessages.JavaFeatureTabPage_QuickSelect_AnnotationsOnly,
				CleanUpMessages.JavaFeatureTabPage_QuickSelect_LifecycleOnly,
				CleanUpMessages.JavaFeatureTabPage_QuickSelect_AssertionsOnly,
				CleanUpMessages.JavaFeatureTabPage_QuickSelect_RulesOnly
		});
		quickSelectCombo.select(0);
		GridData comboGridData= new GridData(SWT.FILL, SWT.CENTER, true, false);
		comboGridData.horizontalSpan= numColumns - 2;
		quickSelectCombo.setLayoutData(comboGridData);

		intent(junitGroup);
		final CheckboxPreference junit_assert= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, FALSE_TRUE);
		intent(junitGroup);
		intent(junitGroup);
		final CheckboxPreference junit_assert_optimization= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT_OPTIMIZATION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_assume= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME, FALSE_TRUE);
		intent(junitGroup);
		intent(junitGroup);
		final CheckboxPreference junit_assume_optimization= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME_OPTIMIZATION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_ignore= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_IGNORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_test= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, FALSE_TRUE);

		intent(junitGroup);
		intent(junitGroup);
		final CheckboxPreference junit_test_timeout= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST_TIMEOUT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT, FALSE_TRUE);

		intent(junitGroup);
		intent(junitGroup);
		final CheckboxPreference junit_test_expected= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST_EXPECTED,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED, FALSE_TRUE);

		intent(junitGroup);
		final CheckboxPreference junit_before= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_after= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_beforeclass= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORECLASS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_afterclass= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTERCLASS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_ruletempfolder= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETEMPORARYFOLDER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_ruletestname= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETESTNAME,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_externalresource= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_EXTERNALRESOURCE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_ruleexternalresource= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULEEXTERNALRESOURCE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, FALSE_TRUE);
		intent(junitGroup);
		final CheckboxPreference junit_runwith= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RUNWITH,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, FALSE_TRUE);

		intent(junitGroup);
		intent(junitGroup);
		final CheckboxPreference junit_suite= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_SUITE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE, FALSE_TRUE);

		intent(junitGroup);
		intent(junitGroup);
		final CheckboxPreference junit_parameterized= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_PARAMETERIZED,
				MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED, FALSE_TRUE);

		intent(junitGroup);
		final CheckboxPreference junit_category= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_CATEGORY,
				MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY, FALSE_TRUE);

		intent(junitGroup);
		final CheckboxPreference junit_fixmethodorder= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_FIX_METHOD_ORDER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_FIX_METHOD_ORDER, FALSE_TRUE);

		intent(junitGroup);
		final CheckboxPreference junit_ruletimeout= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETIMEOUT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT, FALSE_TRUE);

		intent(junitGroup);
		final CheckboxPreference junit_ruleexpectedexception= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULEEXPECTEDEXCEPTION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION, FALSE_TRUE);

		intent(junitGroup);
		final CheckboxPreference junit_ruleerrorcollector= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULEERRORCOLLECTOR,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEERRORCOLLECTOR, FALSE_TRUE);

		intent(junitGroup);
		final CheckboxPreference junit_lost_tests= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_LOST_TESTS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS, FALSE_TRUE);

		intent(junitGroup);
		final CheckboxPreference junit_throwingrunnable= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_THROWINGRUNNABLE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE, FALSE_TRUE);

		registerSlavePreference(junitcb, new CheckboxPreference[] {
				junitBestEffort,
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
				junit_ruletimeout,
				junit_ruleexpectedexception,
				junit_ruleerrorcollector,
				junit_runwith,
				junit_category,
				junit_fixmethodorder,
				junit_lost_tests,
				junit_throwingrunnable });

		registerSlavePreference(junit_test, new CheckboxPreference[] {
				junit_test_timeout,
				junit_test_expected });

		registerSlavePreference(junit_assert, new CheckboxPreference[] { junit_assert_optimization });
		registerSlavePreference(junit_assume, new CheckboxPreference[] { junit_assume_optimization });

		registerSlavePreference(junit_runwith, new CheckboxPreference[] {
				junit_suite,
				junit_parameterized });

		quickSelectCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int index= quickSelectCombo.getSelectionIndex();
				applyQuickSelection(index, junitcb, junit_assert, junit_assume, junit_ignore, junit_test,
						junit_test_timeout, junit_test_expected, junit_before, junit_after,
						junit_beforeclass, junit_afterclass, junit_ruletempfolder, junit_ruletestname,
						junit_ruleexternalresource, junit_externalresource, junit_runwith,
						junit_suite, junit_category, junit_ruletimeout,
						junit_ruleexpectedexception, junit_ruleerrorcollector);
			}
		});

		intent(junitGroup);
		registerPreference(junitcb);

		Group junit3Group= createGroup(numColumns, composite, CleanUpMessages.JavaFeatureTabPage_GroupName_JUnit3);
		final CheckboxPreference junit3cb= createCheckboxPref(junit3Group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT3_CLEANUP, MYCleanUpConstants.JUNIT3_CLEANUP,
				FALSE_TRUE);
		intent(junit3Group);

		final CheckboxPreference junit3_test= createCheckboxPref(junit3Group, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT3_CLEANUP_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_3_TEST, FALSE_TRUE);

		intent(junit3Group);
		registerSlavePreference(junit3cb, new CheckboxPreference[] { junit3_test });
		intent(junit3Group);
		registerPreference(junit3cb);
	}

	private void applyQuickSelection(int selectionIndex, CheckboxPreference junitcb,
			CheckboxPreference junit_assert, CheckboxPreference junit_assume,
			CheckboxPreference junit_ignore, CheckboxPreference junit_test,
			CheckboxPreference junit_test_timeout, CheckboxPreference junit_test_expected,
			CheckboxPreference junit_before, CheckboxPreference junit_after,
			CheckboxPreference junit_beforeclass, CheckboxPreference junit_afterclass,
			CheckboxPreference junit_ruletempfolder, CheckboxPreference junit_ruletestname,
			CheckboxPreference junit_ruleexternalresource, CheckboxPreference junit_externalresource,
			CheckboxPreference junit_runwith, CheckboxPreference junit_suite,
			CheckboxPreference junit_category, CheckboxPreference junit_ruletimeout,
			CheckboxPreference junit_ruleexpectedexception, CheckboxPreference junit_ruleerrorcollector) {

		if (selectionIndex > 0) {
			junitcb.setChecked(true);
			junit_assert.setChecked(false);
			junit_assume.setChecked(false);
			junit_ignore.setChecked(false);
			junit_test.setChecked(false);
			junit_test_timeout.setChecked(false);
			junit_test_expected.setChecked(false);
			junit_before.setChecked(false);
			junit_after.setChecked(false);
			junit_beforeclass.setChecked(false);
			junit_afterclass.setChecked(false);
			junit_ruletempfolder.setChecked(false);
			junit_ruletestname.setChecked(false);
			junit_ruleexternalresource.setChecked(false);
			junit_externalresource.setChecked(false);
			junit_runwith.setChecked(false);
			junit_suite.setChecked(false);
			junit_category.setChecked(false);
			junit_ruletimeout.setChecked(false);
			junit_ruleexpectedexception.setChecked(false);
			junit_ruleerrorcollector.setChecked(false);
		}

		switch (selectionIndex) {
		case 0:
			break;
		case 1:
			junit_assert.setChecked(true);
			junit_assume.setChecked(true);
			junit_ignore.setChecked(true);
			junit_test.setChecked(true);
			junit_test_timeout.setChecked(true);
			junit_test_expected.setChecked(true);
			junit_before.setChecked(true);
			junit_after.setChecked(true);
			junit_beforeclass.setChecked(true);
			junit_afterclass.setChecked(true);
			junit_ruletempfolder.setChecked(true);
			junit_ruletestname.setChecked(true);
			junit_ruleexternalresource.setChecked(true);
			junit_externalresource.setChecked(true);
			junit_runwith.setChecked(true);
			junit_suite.setChecked(true);
			junit_category.setChecked(true);
			junit_ruletimeout.setChecked(true);
			junit_ruleexpectedexception.setChecked(true);
			junit_ruleerrorcollector.setChecked(true);
			break;
		case 2:
			junit_test.setChecked(true);
			junit_before.setChecked(true);
			junit_after.setChecked(true);
			junit_beforeclass.setChecked(true);
			junit_afterclass.setChecked(true);
			junit_ignore.setChecked(true);
			break;
		case 3:
			junit_before.setChecked(true);
			junit_after.setChecked(true);
			junit_beforeclass.setChecked(true);
			junit_afterclass.setChecked(true);
			break;
		case 4:
			junit_assert.setChecked(true);
			break;
		case 5:
			junit_ruletempfolder.setChecked(true);
			junit_ruletestname.setChecked(true);
			junit_ruleexternalresource.setChecked(true);
			junit_externalresource.setChecked(true);
			junit_ruletimeout.setChecked(true);
			junit_ruleexpectedexception.setChecked(true);
			junit_ruleerrorcollector.setChecked(true);
			break;
		default:
			break;
		}
	}
}
