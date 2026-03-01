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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation;
import org.sandbox.jdt.triggerpattern.llm.CommitEvaluation.TrafficLight;
import org.sandbox.mining.core.report.NetBeansReporter;

/**
 * Tests for {@link NetBeansReporter}.
 */
class NetBeansReporterTest {

	@TempDir
	Path tempDir;

	@Test
	void testFormatGreenEvaluation() {
		NetBeansReporter reporter = new NetBeansReporter();
		CommitEvaluation eval = createEval("abc1234567890", "Fix encoding", TrafficLight.GREEN);
		String result = reporter.format(List.of(eval));
		assertTrue(result.contains("warning"), "GREEN should map to warning severity");
		assertTrue(result.contains("abc1234"), "Should contain short hash");
		assertTrue(result.contains("GREEN"), "Should contain traffic light");
	}

	@Test
	void testFormatNotApplicable() {
		NetBeansReporter reporter = new NetBeansReporter();
		CommitEvaluation eval = createEval("def5678901234", "Update docs", TrafficLight.NOT_APPLICABLE);
		String result = reporter.format(List.of(eval));
		assertTrue(result.contains("info"), "NOT_APPLICABLE should map to info severity");
	}

	@Test
	void testWriteToFile() throws IOException {
		NetBeansReporter reporter = new NetBeansReporter();
		CommitEvaluation eval = createEval("abc1234567890", "Fix bug", TrafficLight.GREEN);
		reporter.write(List.of(eval), tempDir);
		Path file = tempDir.resolve("evaluations.txt");
		assertTrue(Files.exists(file), "evaluations.txt should be created");
		String content = Files.readString(file);
		assertTrue(content.contains("abc1234"), "File should contain evaluation");
	}

	@Test
	void testMultipleEvaluations() {
		NetBeansReporter reporter = new NetBeansReporter();
		List<CommitEvaluation> evals = List.of(
				createEval("hash1111111", "First", TrafficLight.GREEN),
				createEval("hash2222222", "Second", TrafficLight.YELLOW),
				createEval("hash3333333", "Third", TrafficLight.RED));
		String result = reporter.format(evals);
		// Should have one line per evaluation (3 lines + trailing newline)
		String[] lines = result.strip().split("\n");
		assertEquals(3, lines.length, "Should have one line per evaluation");
	}

	@Test
	void testPrintToStream() {
		NetBeansReporter reporter = new NetBeansReporter();
		CommitEvaluation eval = createEval("abc1234567890", "Fix bug", TrafficLight.GREEN);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		reporter.printToStream(List.of(eval), new PrintStream(baos));
		String output = baos.toString();
		assertTrue(output.contains("abc1234"), "Stream output should contain evaluation");
	}

	@Test
	void testSeverityMapping() {
		assertEquals("warning", NetBeansReporter.severityFromTrafficLight(TrafficLight.GREEN));
		assertEquals("info", NetBeansReporter.severityFromTrafficLight(TrafficLight.YELLOW));
		assertEquals("info", NetBeansReporter.severityFromTrafficLight(TrafficLight.RED));
		assertEquals("info", NetBeansReporter.severityFromTrafficLight(TrafficLight.NOT_APPLICABLE));
		assertEquals("info", NetBeansReporter.severityFromTrafficLight(null));
	}

	@Test
	void testRepoShortName() {
		assertEquals("repo", NetBeansReporter.repoShortName("https://github.com/user/repo.git"));
		assertEquals("repo", NetBeansReporter.repoShortName("https://github.com/user/repo"));
	}

	@Test
	void testNullTrafficLightRendersUnknown() {
		NetBeansReporter reporter = new NetBeansReporter();
		CommitEvaluation eval = new CommitEvaluation(
				"abc1234567890", "Null light", "https://github.com/test/repo",
				Instant.now(), null, true, null, false, null,
				4, 3, 2, null,
				"Category", false, "reason",
				true, "rule", "file.sandbox-hint",
				null, null, "summary", null);
		String line = reporter.format(List.of(eval));
		assertTrue(line.contains("UNKNOWN"), "Null trafficLight should render as UNKNOWN, got: " + line);
		assertFalse(line.contains("/null/"), "Should not contain literal 'null' in brackets, got: " + line);
	}

	private CommitEvaluation createEval(String hash, String message, TrafficLight light) {
		return new CommitEvaluation(
				hash, message, "https://github.com/test/repo",
				Instant.now(), null, true, null, false, null,
				4, 3, 2, light,
				"Collections", false, "reason",
				true, "rule", "file.sandbox-hint",
				null, null, "summary", null);
	}
}
