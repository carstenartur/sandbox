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

	@Test
	void testWriteHintFilesCreatesFileForGreenValid() throws IOException {
		HintFileUpdater updater = new HintFileUpdater(new org.sandbox.jdt.triggerpattern.internal.DslValidator());
		String dslRule = "// test rule\njava.util.Collections.emptyList() :: sourceVersionGE(9)\n=> java.util.List.of()\n;;\n";
		CommitEvaluation eval = new CommitEvaluation(
				"abc1234def", "Replace emptyList", "https://github.com/test/repo",
				Instant.now(), Instant.now(), true, null, false, null,
				8, 7, 3, TrafficLight.GREEN, "Collections",
				false, null, true, dslRule, null, null, null,
				"Test summary", "VALID");
		List<Path> created = updater.writeHintFiles(List.of(eval), tempDir);
		assertEquals(1, created.size());
		assertTrue(Files.exists(created.get(0)));
		String content = Files.readString(created.get(0));
		assertTrue(content.contains("Collections.emptyList()"));
	}

	@Test
	void testWriteHintFilesSkipsNonGreen() throws IOException {
		HintFileUpdater updater = new HintFileUpdater(new org.sandbox.jdt.triggerpattern.internal.DslValidator());
		CommitEvaluation eval = new CommitEvaluation(
				"abc1234def", "Some commit", "https://github.com/test/repo",
				Instant.now(), Instant.now(), true, null, false, null,
				5, 5, 5, TrafficLight.YELLOW, "Category",
				false, null, false, "some rule", null, null, null,
				"Summary", "VALID");
		List<Path> created = updater.writeHintFiles(List.of(eval), tempDir);
		assertTrue(created.isEmpty(), "Should not create files for non-GREEN evaluations");
	}

	@Test
	void testWriteHintFilesSkipsInvalidDsl() throws IOException {
		HintFileUpdater updater = new HintFileUpdater(new org.sandbox.jdt.triggerpattern.internal.DslValidator());
		CommitEvaluation eval = new CommitEvaluation(
				"abc1234def", "Some commit", "https://github.com/test/repo",
				Instant.now(), Instant.now(), true, null, false, null,
				8, 7, 3, TrafficLight.GREEN, "Category",
				false, null, true, "some rule", null, null, null,
				"Summary", "INVALID: parse error");
		List<Path> created = updater.writeHintFiles(List.of(eval), tempDir);
		assertTrue(created.isEmpty(), "Should not create files for non-VALID evaluations");
	}

	@Test
	void testWriteHintFilesUsesTargetHintFile() throws IOException {
		HintFileUpdater updater = new HintFileUpdater(new org.sandbox.jdt.triggerpattern.internal.DslValidator());
		String dslRule = "// custom hint\njava.util.Collections.emptyList() :: sourceVersionGE(9)\n=> java.util.List.of()\n;;\n";
		CommitEvaluation eval = new CommitEvaluation(
				"abc1234def", "Replace emptyList", "https://github.com/test/repo",
				Instant.now(), Instant.now(), true, null, false, null,
				8, 7, 3, TrafficLight.GREEN, "Collections",
				false, null, true, dslRule, "custom-collections", null, null,
				"Summary", "VALID");
		List<Path> created = updater.writeHintFiles(List.of(eval), tempDir);
		assertEquals(1, created.size());
		assertEquals("custom-collections.sandbox-hint", created.get(0).getFileName().toString());
	}

	@Test
	void testWriteHintFilesSkipsNullDslRule() throws IOException {
		HintFileUpdater updater = new HintFileUpdater(new org.sandbox.jdt.triggerpattern.internal.DslValidator());
		CommitEvaluation eval = new CommitEvaluation(
				"abc1234def", "Some commit", "https://github.com/test/repo",
				Instant.now(), Instant.now(), true, null, false, null,
				8, 7, 3, TrafficLight.GREEN, "Category",
				false, null, true, null, null, null, null,
				"Summary", "VALID");
		List<Path> created = updater.writeHintFiles(List.of(eval), tempDir);
		assertTrue(created.isEmpty(), "Should not create files when dslRule is null");
	}

	@Test
	void testWriteHintFilesMultipleEvaluations() throws IOException {
		HintFileUpdater updater = new HintFileUpdater(new org.sandbox.jdt.triggerpattern.internal.DslValidator());
		String dslRule1 = "// rule 1\njava.util.Collections.emptyList() :: sourceVersionGE(9)\n=> java.util.List.of()\n;;\n";
		String dslRule2 = "// rule 2\njava.util.Collections.emptySet() :: sourceVersionGE(9)\n=> java.util.Set.of()\n;;\n";
		CommitEvaluation green1 = new CommitEvaluation(
				"aaa1111", "commit 1", "repo", Instant.now(), Instant.now(),
				true, null, false, null, 8, 7, 3, TrafficLight.GREEN,
				"Cat1", false, null, true, dslRule1, null, null, null,
				"Sum1", "VALID");
		CommitEvaluation yellow = new CommitEvaluation(
				"bbb2222", "commit 2", "repo", Instant.now(), Instant.now(),
				true, null, false, null, 5, 5, 5, TrafficLight.YELLOW,
				"Cat2", false, null, false, "some", null, null, null,
				"Sum2", "VALID");
		CommitEvaluation green2 = new CommitEvaluation(
				"ccc3333", "commit 3", "repo", Instant.now(), Instant.now(),
				true, null, false, null, 9, 8, 2, TrafficLight.GREEN,
				"Cat3", false, null, true, dslRule2, null, null, null,
				"Sum3", "VALID");
		List<Path> created = updater.writeHintFiles(List.of(green1, yellow, green2), tempDir);
		assertEquals(2, created.size(), "Should create 2 files (skipping YELLOW)");
		assertFalse(created.stream().anyMatch(p -> p.getFileName().toString().contains("bbb")),
				"Should not include the YELLOW commit");
	}

	@Test
	void testWriteHintFilesSanitizesPathTraversal() throws IOException {
		HintFileUpdater updater = new HintFileUpdater(new org.sandbox.jdt.triggerpattern.internal.DslValidator());
		String dslRule = "// test rule\njava.util.Collections.emptyList() :: sourceVersionGE(9)\n=> java.util.List.of()\n;;\n";
		// Provide a targetHintFile with path traversal attempt
		CommitEvaluation eval = new CommitEvaluation(
				"abc1234def", "Path traversal", "https://github.com/test/repo",
				Instant.now(), Instant.now(), true, null, false, null,
				8, 7, 3, TrafficLight.GREEN, "Collections",
				false, null, true, dslRule, "../../../etc/malicious", null, null,
				"Summary", "VALID");
		List<Path> created = updater.writeHintFiles(List.of(eval), tempDir);
		assertEquals(1, created.size());
		// The file must be inside the output directory, not outside it
		assertTrue(created.get(0).startsWith(tempDir),
				"Hint file must be inside output dir, but was: " + created.get(0));
		assertFalse(created.get(0).toString().contains(".."),
				"Hint file path must not contain '..': " + created.get(0));
	}
}
