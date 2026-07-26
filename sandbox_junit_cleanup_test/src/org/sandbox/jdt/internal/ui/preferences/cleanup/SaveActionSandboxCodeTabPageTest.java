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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

class SaveActionSandboxCodeTabPageTest {

	@Test
	void coordinatedAndInteractiveOptionsAreForcedOffForSaveActions() {
		Map<String, String> values= new HashMap<>();
		values.put(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, CleanUpOptions.TRUE);
		values.put(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, CleanUpOptions.TRUE);
		values.put(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, CleanUpOptions.TRUE);
		values.put(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE, CleanUpOptions.TRUE);
		values.put(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED, CleanUpOptions.TRUE);
		values.put(MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY, CleanUpOptions.TRUE);
		values.put(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS, CleanUpOptions.TRUE);
		values.put(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, CleanUpOptions.TRUE);

		SaveActionSandboxCodeTabPage page= new SaveActionSandboxCodeTabPage();
		page.setOptionsKind(CleanUpConstants.DEFAULT_SAVE_ACTION_OPTIONS);
		page.setWorkingValues(values);

		assertEquals(CleanUpOptions.FALSE, values.get(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE));
		assertEquals(CleanUpOptions.FALSE, values.get(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE));
		assertEquals(CleanUpOptions.FALSE, values.get(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH));
		assertEquals(CleanUpOptions.FALSE, values.get(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE));
		assertEquals(CleanUpOptions.FALSE, values.get(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED));
		assertEquals(CleanUpOptions.FALSE, values.get(MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY));
		assertEquals(CleanUpOptions.FALSE, values.get(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS));
		assertEquals(CleanUpOptions.TRUE, values.get(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT));
	}
}
