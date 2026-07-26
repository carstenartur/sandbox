/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.preferences.cleanup;

import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.preferences.cleanup.AbstractCleanUpTabPage;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUp;

/**
 * Conservative save-action page that exposes only local JUnit migrations.
 * Coordinated, runner/suite and discovery-oriented transformations are forced
 * off because they require a project preview and must not run while saving one
 * compilation unit.
 */
public final class SaveActionSandboxCodeTabPage extends AbstractCleanUpTabPage {

	private static final String[] FALSE_TRUE= { CleanUpOptions.FALSE, CleanUpOptions.TRUE };

	private static final Set<String> PROJECT_OR_MANUAL_OPTIONS= Set.of(
			MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE,
			MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE,
			MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH,
			MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE,
			MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED,
			MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY,
			MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

	@Override
	public void setWorkingValues(Map<String, String> workingValues) {
		PROJECT_OR_MANUAL_OPTIONS.forEach(option -> workingValues.put(option, CleanUpOptions.FALSE));
		super.setWorkingValues(workingValues);
	}

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] { new JUnitCleanUp(values) };
	}

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		Group group= createGroup(numColumns, composite, CleanUpMessages.JavaFeatureTabPage_GroupName_JUnit);
		CheckboxPreference enabled= preference(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP,
				MYCleanUpConstants.JUNIT_CLEANUP);

		CheckboxPreference assertions= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		CheckboxPreference assertionOptimization= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSERT_OPTIMIZATION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);
		CheckboxPreference assumptions= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		CheckboxPreference assumptionOptimization= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_ASSUME_OPTIMIZATION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION);
		CheckboxPreference ignore= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_IGNORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		CheckboxPreference test= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		CheckboxPreference timeout= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST_TIMEOUT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT);
		CheckboxPreference expected= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_TEST_EXPECTED,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED);
		CheckboxPreference before= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		CheckboxPreference after= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		CheckboxPreference beforeClass= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_BEFORECLASS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS);
		CheckboxPreference afterClass= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_AFTERCLASS,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
		CheckboxPreference temporaryFolder= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETEMPORARYFOLDER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		CheckboxPreference testName= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETESTNAME,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);
		CheckboxPreference ruleTimeout= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULETIMEOUT,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT);
		CheckboxPreference expectedException= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_RULEEXPECTEDEXCEPTION,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXPECTEDEXCEPTION);
		CheckboxPreference throwingRunnable= child(group, numColumns,
				CleanUpMessages.JavaFeatureTabPage_CheckboxName_JUNIT_CLEANUP_THROWINGRUNNABLE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE);

		CheckboxPreference[] localOptions= { assertions, assumptions, ignore, test, before, after, beforeClass,
				afterClass, temporaryFolder, testName, ruleTimeout, expectedException, throwingRunnable };
		registerSlavePreference(enabled, localOptions);
		registerSlavePreference(assertions, new CheckboxPreference[] { assertionOptimization });
		registerSlavePreference(assumptions, new CheckboxPreference[] { assumptionOptimization });
		registerSlavePreference(test, new CheckboxPreference[] { timeout, expected });
	}

	private CheckboxPreference preference(Group group, int columns, String label, String option) {
		return createCheckboxPref(group, columns, label, option, FALSE_TRUE);
	}

	private CheckboxPreference child(Group group, int columns, String label, String option) {
		intent(group);
		return createCheckboxPref(group, columns - 1, label, option, FALSE_TRUE);
	}
}
