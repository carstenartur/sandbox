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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;
import org.sandbox.mining.core.comparison.DeltaReport;
import org.sandbox.mining.core.comparison.GapCategory;
import org.sandbox.mining.core.comparison.MiningComparator;

/**
 * Tests for {@link MiningComparator}.
 */
class MiningComparatorTest {

	@Test
	void testNoGapsWhenResultsMatch() {
		CommitEvaluation mining = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "Cat1");
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(mining), List.of(ref));
		assertEquals(0, report.getTotalGaps());
	}

	@Test
	void testMissedRelevantCommit() {
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(), List.of(ref));
		assertEquals(1, report.getTotalGaps());
		assertEquals(GapCategory.MISSED_RELEVANT, report.getGaps().get(0).category());
	}

	@Test
	void testWrongTrafficLight() {
		CommitEvaluation mining = createEval("hash1", true, TrafficLight.YELLOW, "rule1", "VALID", "Cat1");
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(mining), List.of(ref));
		assertTrue(report.getGaps().stream()
				.anyMatch(g -> g.category() == GapCategory.WRONG_TRAFFIC_LIGHT));
	}

	@Test
	void testMissingDslRule() {
		CommitEvaluation mining = createEval("hash1", true, TrafficLight.GREEN, null, null, "Cat1");
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(mining), List.of(ref));
		assertTrue(report.getGaps().stream()
				.anyMatch(g -> g.category() == GapCategory.MISSING_DSL_RULE));
	}

	@Test
	void testCategoryMismatch() {
		CommitEvaluation mining = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "CatA");
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "CatB");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(mining), List.of(ref));
		assertTrue(report.getGaps().stream()
				.anyMatch(g -> g.category() == GapCategory.CATEGORY_MISMATCH));
	}

	@Test
	void testMiningMarkedIrrelevantButRefRelevant() {
		CommitEvaluation mining = createEvalIrrelevant("hash1");
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(mining), List.of(ref));
		assertTrue(report.getGaps().stream()
				.anyMatch(g -> g.category() == GapCategory.MISSED_RELEVANT));
	}

	@Test
	void testDeltaReportFormat() {
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(), List.of(ref));
		String formatted = report.format();
		assertTrue(formatted.contains("Delta Report"));
		assertTrue(formatted.contains("MISSED_RELEVANT"));
	}

	@Test
	void testDslErrorXmlDetectedAsDslSyntax() {
		// Verify XML syntax errors are categorized via the comparison flow
		CommitEvaluation mining = createEval("hash1", true, TrafficLight.GREEN,
				"<trigger>rule</trigger>", "Found <trigger> XML tag", "Cat1");
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN,
				"rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(mining), List.of(ref));
		assertTrue(report.getGaps().stream()
				.anyMatch(g -> g.category() == GapCategory.DSL_SYNTAX));
	}

	@Test
	void testDslErrorGuardDetectedAsGuardWissen() {
		CommitEvaluation mining = createEval("hash1", true, TrafficLight.GREEN,
				"rule with isType()", "isType() is not supported", "Cat1");
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN,
				"rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(mining), List.of(ref));
		assertTrue(report.getGaps().stream()
				.anyMatch(g -> g.category() == GapCategory.GUARD_WISSEN));
	}

	@Test
	void testDslErrorUnknownAsInvalidDslRule() {
		CommitEvaluation mining = createEval("hash1", true, TrafficLight.GREEN,
				"bad rule", "some other error", "Cat1");
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN,
				"rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(mining), List.of(ref));
		assertTrue(report.getGaps().stream()
				.anyMatch(g -> g.category() == GapCategory.INVALID_DSL_RULE));
	}

	@Test
	void testGapCategorySuggestedAction() {
		assertFalse(GapCategory.MISSED_RELEVANT.suggestedAction().isBlank());
		assertFalse(GapCategory.TYP_KONTEXT.suggestedAction().isBlank());
		assertFalse(GapCategory.DSL_SYNTAX.suggestedAction().isBlank());
		assertFalse(GapCategory.GUARD_WISSEN.suggestedAction().isBlank());
		assertFalse(GapCategory.GENERALISIERUNG.suggestedAction().isBlank());
		assertFalse(GapCategory.DUPLIKAT_ERKENNUNG.suggestedAction().isBlank());
		assertFalse(GapCategory.KONTEXT_NUTZUNG.suggestedAction().isBlank());
		assertFalse(GapCategory.API_VERSION.suggestedAction().isBlank());
	}

	@Test
	void testDeltaReportMarkdownFormat() {
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(), List.of(ref));
		String markdown = report.formatMarkdown();
		assertTrue(markdown.contains("# Delta Report"));
		assertTrue(markdown.contains("Gap Distribution"));
		assertTrue(markdown.contains("MISSED_RELEVANT"));
		assertTrue(markdown.contains("Suggested Action"));
	}

	@Test
	void testDeltaReportEmptyMarkdown() {
		DeltaReport report = new DeltaReport();
		String markdown = report.formatMarkdown();
		assertTrue(markdown.contains("No gaps found"));
	}

	@Test
	void testDeltaReportJson() {
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(), List.of(ref));
		String json = report.toJson();
		assertTrue(json.contains("totalGaps"));
		assertTrue(json.contains("MISSED_RELEVANT"));
		assertTrue(json.contains("hash1"));
	}

	@Test
	void testDeltaReportWriteToFiles(@TempDir Path tempDir) throws IOException {
		CommitEvaluation ref = createEval("hash1", true, TrafficLight.GREEN, "rule1", "VALID", "Cat1");
		MiningComparator comparator = new MiningComparator();
		DeltaReport report = comparator.compare(List.of(), List.of(ref));
		report.writeToFiles(tempDir);
		assertTrue(Files.exists(tempDir.resolve("delta-report.json")));
		assertTrue(Files.exists(tempDir.resolve("delta-report.md")));
		String jsonContent = Files.readString(tempDir.resolve("delta-report.json"));
		assertTrue(jsonContent.contains("totalGaps"));
		String mdContent = Files.readString(tempDir.resolve("delta-report.md"));
		assertTrue(mdContent.contains("# Delta Report"));
	}

	private CommitEvaluation createEval(String hash, boolean relevant, TrafficLight light,
			String dslRule, String validationResult, String category) {
		return new CommitEvaluation(
				hash, "commit message", "https://github.com/test/repo",
				Instant.now(), null, relevant, null, false, null,
				4, 3, 2, light,
				category, false, "reason",
				true, dslRule, "file.sandbox-hint",
				null, null, "summary", validationResult);
	}

	private CommitEvaluation createEvalIrrelevant(String hash) {
		return new CommitEvaluation(
				hash, "commit message", "https://github.com/test/repo",
				Instant.now(), null, false, "not relevant", false, null,
				0, 0, 0, TrafficLight.NOT_APPLICABLE,
				null, false, null,
				false, null, null,
				null, null, "summary", null);
	}
}
