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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;
import org.sandbox.mining.core.report.JsonReporter;

/**
 * Tests for {@link JsonReporter}.
 */
class JsonReporterTest {

	@TempDir
	Path tempDir;

	@Test
	void testWriteEvaluationsCreatesFile() throws IOException {
		JsonReporter reporter = new JsonReporter();
		CommitEvaluation eval = createEval("abc123", "Test commit", TrafficLight.GREEN);

		reporter.writeEvaluations(List.of(eval), tempDir);

		Path file = tempDir.resolve("evaluations.json");
		assertTrue(Files.exists(file));
		String content = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(content.contains("abc123"));
	}

	@Test
	void testWriteEvaluationsAccumulatesAcrossRuns() throws IOException {
		JsonReporter reporter = new JsonReporter();

		// First run
		CommitEvaluation eval1 = createEval("hash1", "First commit", TrafficLight.GREEN);
		reporter.writeEvaluations(List.of(eval1), tempDir);

		// Second run - new evaluation
		CommitEvaluation eval2 = createEval("hash2", "Second commit", TrafficLight.YELLOW);
		reporter.writeEvaluations(List.of(eval2), tempDir);

		// Should contain both evaluations
		Path file = tempDir.resolve("evaluations.json");
		String content = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(content.contains("hash1"));
		assertTrue(content.contains("hash2"));
	}

	@Test
	void testWriteEvaluationsDeduplicatesByCommitHash() throws IOException {
		JsonReporter reporter = new JsonReporter();

		// First run
		CommitEvaluation eval1 = createEval("hash1", "First version", TrafficLight.YELLOW);
		reporter.writeEvaluations(List.of(eval1), tempDir);

		// Second run - same hash, updated evaluation
		CommitEvaluation eval1Updated = createEval("hash1", "Updated version", TrafficLight.GREEN);
		reporter.writeEvaluations(List.of(eval1Updated), tempDir);

		// Should contain only one evaluation (deduplicated)
		String content = Files.readString(tempDir.resolve("evaluations.json"), StandardCharsets.UTF_8);
		// Count occurrences of "hash1" in commitHash fields
		int count = content.split("\"hash1\"").length - 1;
		assertEquals(1, count, "Should have exactly one evaluation for hash1");
		assertTrue(content.contains("Updated version"), "Should contain updated evaluation");
	}

	@Test
	void testWriteEvaluationsPreservesOlderOnNewRun() throws IOException {
		JsonReporter reporter = new JsonReporter();

		// First run with 2 evaluations
		CommitEvaluation eval1 = createEval("hash1", "First commit", TrafficLight.GREEN);
		CommitEvaluation eval2 = createEval("hash2", "Second commit", TrafficLight.YELLOW);
		reporter.writeEvaluations(List.of(eval1, eval2), tempDir);

		// Second run with only 1 new evaluation
		CommitEvaluation eval3 = createEval("hash3", "Third commit", TrafficLight.RED);
		reporter.writeEvaluations(List.of(eval3), tempDir);

		// All 3 should be present
		String content = Files.readString(tempDir.resolve("evaluations.json"), StandardCharsets.UTF_8);
		assertTrue(content.contains("hash1"));
		assertTrue(content.contains("hash2"));
		assertTrue(content.contains("hash3"));
	}

	@Test
	void testWriteEvaluationsHandlesEmptyExistingFile() throws IOException {
		JsonReporter reporter = new JsonReporter();

		// Write empty array first
		Files.writeString(tempDir.resolve("evaluations.json"), "[]", StandardCharsets.UTF_8);

		// Write new evaluations
		CommitEvaluation eval = createEval("hash1", "New commit", TrafficLight.GREEN);
		reporter.writeEvaluations(List.of(eval), tempDir);

		String content = Files.readString(tempDir.resolve("evaluations.json"), StandardCharsets.UTF_8);
		assertTrue(content.contains("hash1"));
	}

	@Test
	void testWriteEvaluationsHandlesInvalidExistingFile() throws IOException {
		JsonReporter reporter = new JsonReporter();

		// Write invalid JSON first
		Files.writeString(tempDir.resolve("evaluations.json"), "not valid json", StandardCharsets.UTF_8);

		// Write new evaluations - should not fail
		CommitEvaluation eval = createEval("hash1", "New commit", TrafficLight.GREEN);
		reporter.writeEvaluations(List.of(eval), tempDir);

		String content = Files.readString(tempDir.resolve("evaluations.json"), StandardCharsets.UTF_8);
		assertTrue(content.contains("hash1"));
	}

	private CommitEvaluation createEval(String hash, String message, TrafficLight light) {
		return new CommitEvaluation(
				hash, message, "https://github.com/test/repo",
				Instant.now(), true, null, false, null,
				4, 3, 2, light,
				"Collections", false, "reason",
				true, "rule", "file.sandbox-hint",
				null, null, "summary", null);
	}
}
