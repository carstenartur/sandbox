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

	@Test
	void testDeltaReportShowsNewAndKnownColumns() {
		var previousReport = new MiningReport();
		previousReport.addFileCount("repo1", 10);
		previousReport.addMatch("repo1", "collections", "use-isEmpty", "Foo.java", 10, "x.size() == 0", "x.isEmpty()");

		var currentReport = new MiningReport();
		currentReport.addFileCount("repo1", 15);
		currentReport.addMatch("repo1", "collections", "use-isEmpty", "Foo.java", 10, "x.size() == 0", "x.isEmpty()");
		currentReport.addMatch("repo1", "collections", "use-isEmpty", "Bar.java", 20, "y.size() == 0", "y.isEmpty()");

		var reporter = new MarkdownReporter();
		String markdown = reporter.generate(currentReport, previousReport);

		// Delta summary should include New and Known columns
		assertTrue(markdown.contains("| New | Known |"));
		// 1 new match (Bar.java:20), 1 known match (Foo.java:10)
		assertTrue(markdown.contains("| 1 | 1 |"));
		// Details should only show the new match
		assertTrue(markdown.contains("Bar.java:20"));
		assertFalse(markdown.contains("Foo.java:10"));
		assertTrue(markdown.contains("Only **new** matches"));
	}

	@Test
	void testDeltaReportWithNullPreviousReport() {
		var report = new MiningReport();
		report.addFileCount("repo1", 10);
		report.addMatch("repo1", "collections", "use-isEmpty", "Foo.java", 10, "x.size() == 0", "x.isEmpty()");

		var reporter = new MarkdownReporter();
		String markdown = reporter.generate(report, null);

		// Should behave like the original non-delta report
		assertTrue(markdown.contains("Foo.java:10"));
		assertFalse(markdown.contains("| New |"));
		assertFalse(markdown.contains("Only **new** matches"));
	}

	@Test
	void testDeltaReportAllKnown() {
		var previousReport = new MiningReport();
		previousReport.addFileCount("repo1", 10);
		previousReport.addMatch("repo1", "collections", "use-isEmpty", "Foo.java", 10, "x.size() == 0", "x.isEmpty()");

		var currentReport = new MiningReport();
		currentReport.addFileCount("repo1", 10);
		currentReport.addMatch("repo1", "collections", "use-isEmpty", "Foo.java", 10, "x.size() == 0", "x.isEmpty()");

		var reporter = new MarkdownReporter();
		String markdown = reporter.generate(currentReport, previousReport);

		// All matches are known, details section should be empty
		assertTrue(markdown.contains("| 0 | 1 |"));
	}
}
