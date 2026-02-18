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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkdownReporterTest {

	@Test
	void testWithErrors() {
		var report = new MiningReport();
		report.addFileCount("goodRepo", 10);
		report.addMatch("goodRepo", "collections", "use-isEmpty", "Foo.java", 42, "x.size() == 0", "x.isEmpty()");
		report.addFileCount("failedRepo", 0);
		report.addError("failedRepo", "Connection timed out");

		var reporter = new MarkdownReporter();
		String markdown = reporter.generate(report);

		// Failed repo should appear in summary with warning marker
		assertTrue(markdown.contains("failedRepo ⚠️"));
		// Good repo should not have warning marker
		assertTrue(markdown.contains("| goodRepo |"));
		assertFalse(markdown.contains("goodRepo ⚠️"));
		// Errors section should be present
		assertTrue(markdown.contains("## Errors"));
		assertTrue(markdown.contains("**failedRepo**"));
		assertTrue(markdown.contains("Connection timed out"));
	}

	@Test
	void testWithoutErrors() {
		var report = new MiningReport();
		report.addFileCount("repo1", 10);

		var reporter = new MarkdownReporter();
		String markdown = reporter.generate(report);

		assertFalse(markdown.contains("## Errors"));
		assertFalse(markdown.contains("⚠️"));
	}

	@Test
	void testErrorMessageEscaping() {
		var report = new MiningReport();
		report.addFileCount("failedRepo", 0);
		report.addError("failedRepo", "Error with **bold** and `backticks`\nand newlines");

		var reporter = new MarkdownReporter();
		String markdown = reporter.generate(report);

		// Error message should be rendered as inline code with newlines normalized
		assertTrue(markdown.contains("`Error with **bold**"));
		assertFalse(markdown.contains("\nand newlines`"));
	}
}
