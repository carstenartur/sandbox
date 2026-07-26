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

import org.sandbox.jdt.internal.corext.fix.IntToEnumCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;

class SandboxCodeTabPageSaveActionTest {

	@Test
	void projectWideModeIsForcedOffForSaveActions() {
		Map<String, String> values= new HashMap<>();
		values.put(MYCleanUpConstants.INT_TO_ENUM_CLEANUP, CleanUpOptions.TRUE);
		values.put(IntToEnumCleanUpOptions.PROJECT_WIDE, CleanUpOptions.TRUE);

		SandboxCodeTabPage page= new SandboxCodeTabPage();
		page.setOptionsKind(CleanUpConstants.DEFAULT_SAVE_ACTION_OPTIONS);
		page.setWorkingValues(values);

		assertEquals(CleanUpOptions.TRUE, values.get(MYCleanUpConstants.INT_TO_ENUM_CLEANUP));
		assertEquals(CleanUpOptions.FALSE, values.get(IntToEnumCleanUpOptions.PROJECT_WIDE));
	}
}
