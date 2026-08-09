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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.JfaceCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JFaceCleanUpCore;

/** Verifies the user-facing descriptions used by the Cleanup wizard and preview. */
class JFaceCleanUpDescriptionTest {

	@Test
	void exposesOneDescriptionPerEnabledJFaceMigration() {
		Map<String, String> options= new HashMap<>();
		options.put(MYCleanUpConstants.JFACE_CLEANUP, CleanUpOptions.TRUE);
		options.put(MYCleanUpConstants.JFACE_CLEANUP_MONITOR, CleanUpOptions.TRUE);
		options.put(MYCleanUpConstants.JFACE_CLEANUP_VIEWER_SORTER, CleanUpOptions.TRUE);
		options.put(MYCleanUpConstants.JFACE_CLEANUP_IMAGE_DPI, CleanUpOptions.TRUE);

		assertArrayEquals(new String[] {
				"Replace SubProgressMonitor with SubMonitor", //$NON-NLS-1$
				"Replace ViewerSorter with ViewerComparator", //$NON-NLS-1$
				"Modernize Image creation for DPI/zoom (ImageDataProvider)" //$NON-NLS-1$
		}, new JFaceCleanUpCore(options).getStepDescriptions());
	}

	@Test
	void previewDescriptionsAreSpecificAndUnique() {
		assertEquals(3, EnumSet.allOf(JfaceCleanUpFixCore.class).stream()
				.map(JfaceCleanUpFixCore::getDescription)
				.distinct()
				.count());
	}
}
