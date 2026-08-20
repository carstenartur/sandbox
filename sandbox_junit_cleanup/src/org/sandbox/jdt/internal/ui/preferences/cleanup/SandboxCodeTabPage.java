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

import org.sandbox.jdt.internal.corext.fix.JUnit4MigrationPresets;
import org.sandbox.jdt.internal.corext.fix.JUnit4MigrationPresets.Preset;
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
		CheckboxPreference junit= createCheckboxPref(junitGroup, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP, MYCleanUpConstants.JUNIT_CLEANUP,
				FALSE_TRUE);

		intent(junitGroup);
		CheckboxPreference bestEffort= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEST_EFFORT,
				JUnitMigrationOptions.BEST_EFFORT, FALSE_TRUE);

		intent(junitGroup);
		CheckboxPreference junit6Compatibility= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT6_COMPATIBILITY,
				JUnitMigrationOptions.JUNIT6_COMPATIBILITY, FALSE_TRUE);

		intent(junitGroup);
		Label quickSelectLabel= new Label(junitGroup, SWT.NONE);
		quickSelectLabel.setText(CleanUpMessages.JavaFeatureTabPage_QuickSelect_Label);

		intent(junitGroup);
		Combo quickSelectCombo= new Combo(junitGroup, SWT.READ_ONLY | SWT.DROP_DOWN);
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
		CheckboxPreference junitAssert= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, FALSE_TRUE);
		intent(junitGroup);
		intent(junitGroup);
		CheckboxPreference junitAssertOptimization= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT_OPTIMIZATION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitAssume= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME, FALSE_TRUE);
		intent(junitGroup);
		intent(junitGroup);
		CheckboxPreference junitAssumeOptimization= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME_OPTIMIZATION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitIgnore= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_IGNORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitTest= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, FALSE_TRUE);

		intent(junitGroup);
		intent(junitGroup);
		CheckboxPreference junitTestTimeout= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST_TIMEOUT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT, FALSE_TRUE);

		intent(junitGroup);
		intent(junitGroup);
		CheckboxPreference junitTestExpected= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST_EXPECTED,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED, FALSE_TRUE);

		intent(junitGroup);
		CheckboxPreference junitBefore= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitAfter= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitBeforeClass= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORECLASS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitAfterClass= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTERCLASS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitTemporaryFolder= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETEMPORARYFOLDER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitTestName= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETESTNAME,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitExternalResource= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_EXTERNALRESOURCE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitRuleExternalResource= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULEEXTERNALRESOURCE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, FALSE_TRUE);
		intent(junitGroup);
		CheckboxPreference junitRunWith= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RUNWITH,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, FALSE_TRUE);

		intent(junitGroup);
		intent(junitGroup);
		CheckboxPreference junitSuite= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_SUITE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE, FALSE_TRUE);

		intent(junitGroup);
		intent(junitGroup);
		CheckboxPreference junitParameterized= createCheckboxPref(junitGroup, numColumns - 2,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_PARAMETERIZED,
				MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED, FALSE_TRUE);

		intent(junitGroup);
		CheckboxPreference junitCategory= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_CATEGORY,
				MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY, FALSE_TRUE);

		intent(junitGroup);
		CheckboxPreference junitFixMethodOrder= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_FIX_METHOD_ORDER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_FIX_METHOD_ORDER, FALSE_TRUE);

		intent(junitGroup);
		CheckboxPreference junitRuleTimeout= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETIMEOUT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT, FALSE_TRUE);

		intent(junitGroup);
		CheckboxPreference junitExpectedException= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULEEXPECTEDEXCEPTION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION, FALSE_TRUE);

		intent(junitGroup);
		CheckboxPreference junitErrorCollector= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULEERRORCOLLECTOR,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEERRORCOLLECTOR, FALSE_TRUE);

		intent(junitGroup);
		CheckboxPreference junitLostTests= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_LOST_TESTS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS, FALSE_TRUE);

		intent(junitGroup);
		CheckboxPreference junitThrowingRunnable= createCheckboxPref(junitGroup, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_THROWINGRUNNABLE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE, FALSE_TRUE);

		registerSlavePreference(junit, new CheckboxPreference[] {
				bestEffort,
				junit6Compatibility,
				junitAssert,
				junitAssume,
				junitIgnore,
				junitTest,
				junitBefore,
				junitAfter,
				junitBeforeClass,
				junitAfterClass,
				junitTemporaryFolder,
				junitTestName,
				junitRuleExternalResource,
				junitExternalResource,
				junitRuleTimeout,
				junitExpectedException,
				junitErrorCollector,
				junitRunWith,
				junitCategory,
				junitFixMethodOrder,
				junitLostTests,
				junitThrowingRunnable });

		registerSlavePreference(junitTest, new CheckboxPreference[] {
				junitTestTimeout,
				junitTestExpected });
		registerSlavePreference(junitAssert, new CheckboxPreference[] { junitAssertOptimization });
		registerSlavePreference(junitAssume, new CheckboxPreference[] { junitAssumeOptimization });
		registerSlavePreference(junitRunWith, new CheckboxPreference[] { junitSuite, junitParameterized });

		Map<String, CheckboxPreference> junit4Preferences= Map.ofEntries(
				Map.entry(JUnitMigrationOptions.BEST_EFFORT, bestEffort),
				Map.entry(JUnitMigrationOptions.JUNIT6_COMPATIBILITY, junit6Compatibility),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, junitAssert),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION, junitAssertOptimization),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME, junitAssume),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION, junitAssumeOptimization),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE, junitIgnore),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, junitTest),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT, junitTestTimeout),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED, junitTestExpected),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, junitBefore),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER, junitAfter),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS, junitBeforeClass),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS, junitAfterClass),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER, junitTemporaryFolder),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME, junitTestName),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, junitExternalResource),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, junitRuleExternalResource),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, junitRunWith),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE, junitSuite),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED, junitParameterized),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY, junitCategory),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_FIX_METHOD_ORDER, junitFixMethodOrder),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT, junitRuleTimeout),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION, junitExpectedException),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEERRORCOLLECTOR, junitErrorCollector),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS, junitLostTests),
				Map.entry(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE, junitThrowingRunnable));

		quickSelectCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				applyQuickSelection(quickSelectCombo.getSelectionIndex(), junit, junit4Preferences);
			}
		});

		intent(junitGroup);
		registerPreference(junit);

		Group junit3Group= createGroup(numColumns, composite, CleanUpMessages.JavaFeatureTabPage_GroupName_JUnit3);
		CheckboxPreference junit3= createCheckboxPref(junit3Group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT3_CLEANUP, MYCleanUpConstants.JUNIT3_CLEANUP,
				FALSE_TRUE);
		intent(junit3Group);

		CheckboxPreference junit3Test= createCheckboxPref(junit3Group, numColumns - 1,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT3_CLEANUP_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_3_TEST, FALSE_TRUE);

		intent(junit3Group);
		registerSlavePreference(junit3, new CheckboxPreference[] { junit3Test });
		intent(junit3Group);
		registerPreference(junit3);
	}

	private void applyQuickSelection(int selectionIndex, CheckboxPreference junit,
			Map<String, CheckboxPreference> preferences) {
		Preset preset= JUnit4MigrationPresets.fromSelectionIndex(selectionIndex);
		if (preset == Preset.CUSTOM) {
			return;
		}

		Map<String, Boolean> selection= JUnit4MigrationPresets.selectionFor(preset);
		if (!preferences.keySet().equals(selection.keySet())) {
			throw new IllegalStateException("JUnit 4 preset bindings do not match the managed options"); //$NON-NLS-1$
		}

		junit.setChecked(true);
		selection.forEach((option, enabled) -> preferences.get(option).setChecked(enabled.booleanValue()));
	}
}
