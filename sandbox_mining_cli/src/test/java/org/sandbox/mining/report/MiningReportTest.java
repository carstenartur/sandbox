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
package org.sandbox.mining.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MiningReportTest {

	@Test
	void testErrorTracking() {
		var report = new MiningReport();
		assertFalse(report.hasErrors());
		assertTrue(report.getErrors().isEmpty());

		report.addError("failedRepo", "Connection timed out");
		assertTrue(report.hasErrors());
		assertEquals(1, report.getErrors().size());
		assertEquals("Connection timed out", report.getErrors().get("failedRepo"));

		// Error repo with zero file count should appear in fileCounts
		report.addFileCount("failedRepo", 0);
		assertEquals(0, report.getFileCounts().get("failedRepo"));
	}

	@Test
	void testMergeWithErrors() {
		var report1 = new MiningReport();
		report1.addFileCount("repo1", 10);
		report1.addError("repo2", "Clone failed");

		var report2 = new MiningReport();
		report2.addFileCount("repo3", 20);
		report2.addError("repo4", "Timeout");

		report1.merge(report2);
		assertEquals(2, report1.getErrors().size());
		assertEquals("Clone failed", report1.getErrors().get("repo2"));
		assertEquals("Timeout", report1.getErrors().get("repo4"));
	}
}
