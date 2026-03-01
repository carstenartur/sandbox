/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.mining.core.comparison.GapCategory;
import org.sandbox.mining.core.comparison.GapEntry;
import org.sandbox.mining.core.comparison.HintFileUpdater;

/**
 * Tests for {@link HintFileUpdater}.
 */
class HintFileUpdaterTest {

	@TempDir
	Path tempDir;

	@Test
	void testSanitizeFileName() {
		assertEquals("abc1234", HintFileUpdater.sanitizeFileName("abc1234567890"));
		assertEquals("unknown", HintFileUpdater.sanitizeFileName(null));
		assertEquals("unknown", HintFileUpdater.sanitizeFileName(""));
	}

	@Test
	void testApplyGapsIgnoresNonDslGaps() throws IOException {
		HintFileUpdater updater = new HintFileUpdater(new org.sandbox.jdt.triggerpattern.internal.DslValidator());
		GapEntry gap = new GapEntry("hash1", GapCategory.WRONG_TRAFFIC_LIGHT, "YELLOW", "GREEN", "mismatch");
		List<Path> created = updater.applyGaps(List.of(gap), tempDir);
		assertTrue(created.isEmpty(), "Should not create files for non-DSL gaps");
	}

	@Test
	void testApplyGapsSkipsNullRule() throws IOException {
		HintFileUpdater updater = new HintFileUpdater(new org.sandbox.jdt.triggerpattern.internal.DslValidator());
		GapEntry gap = new GapEntry("hash1", GapCategory.MISSING_DSL_RULE, null, null, "missing");
		List<Path> created = updater.applyGaps(List.of(gap), tempDir);
		assertTrue(created.isEmpty(), "Should not create files for null rules");
	}
}
