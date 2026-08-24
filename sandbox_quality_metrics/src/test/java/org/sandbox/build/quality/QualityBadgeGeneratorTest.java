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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.build.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualityBadgeGeneratorTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void collectsSurefireAndFailsafeTestcasesExactly() throws Exception {
		write("module-a/target/surefire-reports/TEST-A.xml", """
				<testsuite tests="3" failures="1" errors="0" skipped="1">
				  <testcase classname="A" name="passes"/>
				  <testcase classname="A" name="skips"><skipped/></testcase>
				  <testcase classname="A" name="fails"><failure/></testcase>
				</testsuite>
				""");
		write("module-b/target/failsafe-reports/TEST-B.xml", """
				<testsuites tests="2">
				  <testsuite tests="2" failures="0" errors="1" skipped="0">
				    <testcase classname="B" name="passes"/>
				    <testcase classname="B" name="errors"><error/></testcase>
				  </testsuite>
				</testsuites>
				""");

		QualityBadgeGenerator.TestTotals totals = QualityBadgeGenerator.collectTests(temporaryDirectory);

		assertEquals(5, totals.tests());
		assertEquals(1, totals.failures());
		assertEquals(1, totals.errors());
		assertEquals(1, totals.skipped());
		assertEquals(4, totals.executed());
		assertEquals(2, totals.passed());
		assertEquals(2, totals.reportFiles());
	}

	@Test
	void rejectsMissingOrContradictoryJUnitCounts() throws Exception {
		Path missing = write("missing/target/surefire-reports/TEST-Missing.xml", """
				<testsuite failures="0" errors="0" skipped="0">
				  <testcase classname="Missing" name="count"/>
				</testsuite>
				""");
		IOException missingError = assertThrows(IOException.class,
				() -> QualityBadgeGenerator.collectTests(temporaryDirectory));
		assertTrue(missingError.getMessage().contains("Missing required 'tests'"));

		Files.delete(missing);
		write("mismatch/target/surefire-reports/TEST-Mismatch.xml", """
				<testsuite tests="2" failures="0" errors="0" skipped="0">
				  <testcase classname="Mismatch" name="onlyOne"/>
				</testsuite>
				""");
		IOException mismatchError = assertThrows(IOException.class,
				() -> QualityBadgeGenerator.collectTests(temporaryDirectory));
		assertTrue(mismatchError.getMessage().contains("declares 2 tests but contains 1"));
	}

	@Test
	void countsSurefireNestedClassTestcasesEvenWhenSummaryDeduplicatesMethodNames() throws Exception {
		write("nested/target/surefire-reports/TEST-Nested.xml", """
				<testsuite tests="3" failures="0" errors="0" skipped="0">
				  <testcase classname="First nested class" name="sharedName"/>
				  <testcase classname="Second nested class" name="sharedName"/>
				  <testcase classname="First nested class" name="firstOnly"/>
				  <testcase classname="Second nested class" name="secondOnly"/>
				</testsuite>
				""");

		QualityBadgeGenerator.TestTotals totals = QualityBadgeGenerator.collectTests(temporaryDirectory);

		assertEquals(4, totals.tests());
		assertEquals(4, totals.passed());
		assertEquals(1, totals.reportFiles());
	}

	@Test
	void rejectsRepeatedIdenticalTestcaseIdentityAsAContradictorySummary() throws Exception {
		write("duplicate/target/surefire-reports/TEST-Duplicate.xml", """
				<testsuite tests="1" failures="0" errors="0" skipped="0">
				  <testcase classname="Same class" name="sameName"/>
				  <testcase classname="Same class" name="sameName"/>
				</testsuite>
				""");

		IOException error = assertThrows(IOException.class,
				() -> QualityBadgeGenerator.collectTests(temporaryDirectory));

		assertTrue(error.getMessage().contains("declares 1 tests but contains 2"));
	}

	@Test
	void usesOnlyOneCompleteAggregateJacocoCounter() throws Exception {
		Path report = write("sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml", """
				<report name="aggregate">
				  <package name="example">
				    <counter type="INSTRUCTION" missed="999" covered="1"/>
				  </package>
				  <counter type="BRANCH" missed="5" covered="5"/>
				  <counter type="INSTRUCTION" missed="18" covered="82"/>
				</report>
				""");

		QualityBadgeGenerator.CoverageTotals totals =
				QualityBadgeGenerator.collectCoverage(report, "instruction");

		assertEquals(82, totals.covered());
		assertEquals(18, totals.missed());
		assertEquals(82.0, totals.percent());
		assertEquals("INSTRUCTION", totals.metric());
	}

	@Test
	void rejectsIncompleteAggregateJacocoCounter() throws Exception {
		Path report = write("coverage.xml", """
				<report name="aggregate">
				  <counter type="INSTRUCTION" missed="18"/>
				</report>
				""");

		IOException error = assertThrows(IOException.class,
				() -> QualityBadgeGenerator.collectCoverage(report, "INSTRUCTION"));

		assertTrue(error.getMessage().contains("Missing required 'covered'"));
	}

	@Test
	void generatesBadgesSummaryAndLinksForEveryReportBearingModule() throws Exception {
		write("sandbox_method_reuse_test/target/surefire-reports/TEST-Reuse.xml", """
				<testsuite tests="2" failures="0" errors="0" skipped="1">
				  <testcase classname="Reuse" name="passes"/>
				  <testcase classname="Reuse" name="skips"><skipped/></testcase>
				</testsuite>
				""");
		write("sandbox-functional-converter-core/target/surefire-reports/TEST-Core.xml", """
				<testsuite tests="1" failures="0" errors="0" skipped="0">
				  <testcase classname="Core" name="passes"/>
				</testsuite>
				""");
		write("sandbox_method_reuse_test/target/site/surefire-report.html", "reuse");
		write("sandbox-functional-converter-core/target/site/surefire-report.html", "core");
		Path coverage = write("sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml", """
				<report name="aggregate">
				  <counter type="INSTRUCTION" missed="25" covered="75"/>
				</report>
				""");
		Path output = temporaryDirectory.resolve("quality-site");

		QualityBadgeGenerator.generate(temporaryDirectory, output, coverage, "abc123",
				"2026-08-23T12:00:00Z");

		String testsBadge = Files.readString(output.resolve("badges/tests.json"), StandardCharsets.UTF_8);
		String coverageBadge = Files.readString(output.resolve("badges/coverage.json"), StandardCharsets.UTF_8);
		String summary = Files.readString(output.resolve("quality-summary.json"), StandardCharsets.UTF_8);
		String index = Files.readString(output.resolve("tests/index.html"), StandardCharsets.UTF_8);
		assertTrue(testsBadge.contains("\"message\": \"3, 1 skipped\""));
		assertTrue(coverageBadge.contains("\"message\": \"75.0%\""));
		assertTrue(summary.contains("\"passed\": 2"));
		assertTrue(summary.contains("\"sourceCommit\": \"abc123\""));
		assertTrue(index.contains("sandbox_method_reuse_test/surefire-report.html"));
		assertTrue(index.contains("sandbox-functional-converter-core/surefire-report.html"));
	}

	@Test
	void rendersFailuresAndErrorsAsARedTestBadge() throws Exception {
		write("module/target/surefire-reports/TEST-Failing.xml", """
				<testsuite tests="4" failures="1" errors="1" skipped="1">
				  <testcase classname="Failing" name="passes"/>
				  <testcase classname="Failing" name="skips"><skipped/></testcase>
				  <testcase classname="Failing" name="fails"><failure/></testcase>
				  <testcase classname="Failing" name="errors"><error/></testcase>
				</testsuite>
				""");
		write("module/target/site/surefire-report.html", "report");
		Path coverage = write("sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml", """
				<report name="aggregate">
				  <counter type="INSTRUCTION" missed="5" covered="5"/>
				</report>
				""");
		Path output = temporaryDirectory.resolve("quality-site");

		QualityBadgeGenerator.generate(temporaryDirectory, output, coverage, "failed-build",
				"2026-08-23T12:00:00Z");

		String badge = Files.readString(output.resolve("badges/tests.json"), StandardCharsets.UTF_8);
		assertTrue(badge.contains("\"message\": \"4, 2 failing, 1 skipped\""));
		assertTrue(badge.contains("\"color\": \"red\""));
	}

	@Test
	void escapesProvenanceForJsonAndHtml() throws Exception {
		write("module/target/surefire-reports/TEST-Provenance.xml", """
				<testsuite tests="1" failures="0" errors="0" skipped="0">
				  <testcase classname="Provenance" name="passes"/>
				</testsuite>
				""");
		write("module/target/site/surefire-report.html", "report");
		Path coverage = write("sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml", """
				<report name="aggregate">
				  <counter type="INSTRUCTION" missed="1" covered="9"/>
				</report>
				""");
		Path output = temporaryDirectory.resolve("quality-site");

		QualityBadgeGenerator.generate(temporaryDirectory, output, coverage,
				"ref\"with\\<unsafe>&", "2026-08-23T12:00:00Z");

		String summary = Files.readString(output.resolve("quality-summary.json"), StandardCharsets.UTF_8);
		String index = Files.readString(output.resolve("tests/index.html"), StandardCharsets.UTF_8);
		assertTrue(summary.contains("\"sourceCommit\": \"ref\\\"with\\\\<unsafe>&\""));
		assertTrue(index.contains("<code>ref&quot;with\\&lt;unsafe&gt;&amp;</code>"));
	}

	@Test
	void rejectsMissingEvidenceAndConflictingTestcaseOutcomes() throws Exception {
		assertThrows(IOException.class, () -> QualityBadgeGenerator.collectTests(temporaryDirectory));
		assertThrows(IOException.class, () -> QualityBadgeGenerator.collectCoverage(
				temporaryDirectory.resolve("missing.xml"), "INSTRUCTION"));

		write("module/target/surefire-reports/TEST-Broken.xml", """
				<testsuite tests="1" failures="1" errors="1" skipped="0">
				  <testcase classname="Broken" name="both"><failure/><error/></testcase>
				</testsuite>
				""");
		IOException error = assertThrows(IOException.class,
				() -> QualityBadgeGenerator.collectTests(temporaryDirectory));
		assertTrue(error.getMessage().contains("conflicting outcomes"));
	}

	@Test
	void rejectsExternalEntityDeclarations() throws Exception {
		write("module/target/surefire-reports/TEST-Unsafe.xml", """
				<!DOCTYPE testsuite [<!ENTITY external SYSTEM "file:///etc/passwd">]>
				<testsuite tests="1" failures="0" errors="0" skipped="0">
				  <testcase classname="Unsafe" name="&external;"/>
				</testsuite>
				""");

		IOException error = assertThrows(IOException.class,
				() -> QualityBadgeGenerator.collectTests(temporaryDirectory));
		assertTrue(error.getMessage().contains("Cannot parse XML evidence"));
	}

	private Path write(String relative, String content) throws IOException {
		Path target = temporaryDirectory.resolve(relative);
		Files.createDirectories(target.getParent());
		Files.writeString(target, content, StandardCharsets.UTF_8);
		return target;
	}
}
