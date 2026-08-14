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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JFaceCleanUpCore;

class JFaceCleanUpDescriptionsTest {

	@Test
	void descriptionsExposeEachEnabledMigration() {
		JFaceCleanUpCore cleanUp= new JFaceCleanUpCore(options(true, true, true, true));
		String[] descriptions= cleanUp.getStepDescriptions();
		assertEquals(3, descriptions.length);
		assertTrue(Arrays.stream(descriptions).anyMatch(description -> description.contains("SubProgressMonitor")));
		assertTrue(Arrays.stream(descriptions).anyMatch(description -> description.contains("ViewerSorter")));
		assertTrue(Arrays.stream(descriptions).anyMatch(description -> description.contains("ImageDataProvider")));
	}

	@Test
	void masterToggleControlsExecutionGate() {
		JFaceCleanUpCore cleanUp= new JFaceCleanUpCore(options(false, true, true, true));
		assertFalse(cleanUp.requireAST());
		assertEquals(0, cleanUp.getStepDescriptions().length);
	}

	private static Map<String, String> options(boolean enableMaster, boolean enableMonitor, boolean enableSorter,
			boolean enableImageDataProvider) {
		Map<String, String> options= new HashMap<>();
		options.put(MYCleanUpConstants.JFACE_CLEANUP, booleanOption(enableMaster));
		options.put(MYCleanUpConstants.JFACE_CLEANUP_MONITOR, booleanOption(enableMonitor));
		options.put(MYCleanUpConstants.JFACE_CLEANUP_VIEWER_SORTER, booleanOption(enableSorter));
		options.put(MYCleanUpConstants.JFACE_CLEANUP_IMAGE_DPI, booleanOption(enableImageDataProvider));
		return options;
	}

	private static String booleanOption(boolean enabled) {
		return enabled ? CleanUpOptions.TRUE : CleanUpOptions.FALSE;
	}
}
