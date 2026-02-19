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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonReporterTest {

	@Test
	void testWithErrors() {
		var report = new MiningReport();
		report.addFileCount("goodRepo", 10);
		report.addFileCount("failedRepo", 0);
		report.addError("failedRepo", "Connection timed out");

		var reporter = new JsonReporter();
		String json = reporter.generate(report);

		assertTrue(json.contains("\"errors\""));
		assertTrue(json.contains("\"failedRepo\""));
		assertTrue(json.contains("Connection timed out"));
	}

	@Test
	void testWithoutErrors() {
		var report = new MiningReport();
		report.addFileCount("repo1", 10);

		var reporter = new JsonReporter();
		String json = reporter.generate(report);

		// Errors section should still exist but be empty
		assertTrue(json.contains("\"errors\": {"));
	}
}
