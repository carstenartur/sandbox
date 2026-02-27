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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.sandbox.mining.core.report.GithubPagesGenerator;
import org.sandbox.mining.core.report.StatisticsCollector;

/**
 * Tests for {@link GithubPagesGenerator}.
 */
class GithubPagesGeneratorTest {

	@TempDir
	Path tempDir;

	@Test
	void testGenerateCreatesIndexHtml() throws IOException {
		GithubPagesGenerator generator = new GithubPagesGenerator();
		StatisticsCollector stats = new StatisticsCollector();

		CommitEvaluation eval = new CommitEvaluation(
				"abc123", "test commit", "https://github.com/test/repo",
				Instant.now(), null, true, null, false, null,
				4, 3, 2, TrafficLight.GREEN,
				"Collections", false, "reason",
				true, "rule", "file.sandbox-hint",
				null, null, "Test summary", null);
		stats.record(eval);

		generator.generate(List.of(eval), stats, tempDir);

		Path indexHtml = tempDir.resolve("index.html");
		assertTrue(Files.exists(indexHtml));

		String content = Files.readString(indexHtml, StandardCharsets.UTF_8);
		assertNotNull(content);
		assertTrue(content.contains("1")); // totalProcessed
		assertTrue(content.contains("html"));

		// Verify evaluations.json and statistics.json are generated
		Path evaluationsJson = tempDir.resolve("evaluations.json");
		assertTrue(Files.exists(evaluationsJson));
		String evalContent = Files.readString(evaluationsJson, StandardCharsets.UTF_8);
		assertTrue(evalContent.contains("abc123"));

		Path statisticsJson = tempDir.resolve("statistics.json");
		assertTrue(Files.exists(statisticsJson));
		String statsContent = Files.readString(statisticsJson, StandardCharsets.UTF_8);
		assertTrue(statsContent.contains("totalProcessed"));
	}

	@Test
	void testGenerateWithEmptyData() throws IOException {
		GithubPagesGenerator generator = new GithubPagesGenerator();
		StatisticsCollector stats = new StatisticsCollector();

		generator.generate(List.of(), stats, tempDir);

		Path indexHtml = tempDir.resolve("index.html");
		assertTrue(Files.exists(indexHtml));
	}

	@Test
	void testGenerateMergesExistingEvaluations() throws IOException {
		GithubPagesGenerator generator = new GithubPagesGenerator();

		CommitEvaluation first = new CommitEvaluation(
				"existing123", "existing commit", "https://github.com/test/repo",
				Instant.parse("2026-01-01T10:00:00Z"), null, true, null, false, null,
				4, 3, 2, TrafficLight.GREEN,
				"Collections", false, "reason",
				true, "rule", "file.sandbox-hint",
				null, null, "Existing summary", null);
		StatisticsCollector stats1 = new StatisticsCollector();
		stats1.record(first);
		generator.generate(List.of(first), stats1, tempDir);

		CommitEvaluation second = new CommitEvaluation(
				"new456", "new commit", "https://github.com/test/repo",
				Instant.parse("2026-01-02T10:00:00Z"), null, true, null, false, null,
				5, 5, 2, TrafficLight.GREEN,
				"String-API", false, "reason",
				true, "rule2", "file2.sandbox-hint",
				null, null, "New summary", null);
		StatisticsCollector stats2 = new StatisticsCollector();
		stats2.record(second);
		generator.generate(List.of(second), stats2, tempDir);

		String evalContent = Files.readString(tempDir.resolve("evaluations.json"), StandardCharsets.UTF_8);
		assertTrue(evalContent.contains("existing123"));
		assertTrue(evalContent.contains("new456"));

		String statsContent = Files.readString(tempDir.resolve("statistics.json"), StandardCharsets.UTF_8);
		assertTrue(statsContent.contains("totalProcessed"));
	}

	@Test
	void testGenerateFiltersDemoHashes() throws IOException {
		GithubPagesGenerator generator = new GithubPagesGenerator();

		// Pre-populate with a demo hash entry
		String demoJson = """
				[
				  {
				    "commitHash": "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
				    "commitMessage": "demo",
				    "repoUrl": "https://github.com/test/repo",
				    "evaluatedAt": "2026-01-01T10:00:00Z",
				    "relevant": true,
				    "isDuplicate": false,
				    "reusability": 1,
				    "codeImprovement": 1,
				    "implementationEffort": 1,
				    "trafficLight": "GREEN",
				    "isNewCategory": false,
				    "canImplementInCurrentDsl": false,
				    "summary": "demo"
				  }
				]""";
		Files.writeString(tempDir.resolve("evaluations.json"), demoJson, StandardCharsets.UTF_8);

		CommitEvaluation real = new CommitEvaluation(
				"real123", "real commit", "https://github.com/test/repo",
				Instant.parse("2026-01-02T10:00:00Z"), null, true, null, false, null,
				5, 5, 2, TrafficLight.GREEN,
				"String-API", false, "reason",
				true, "rule", "file.sandbox-hint",
				null, null, "Real summary", null);
		StatisticsCollector stats = new StatisticsCollector();
		stats.record(real);
		generator.generate(List.of(real), stats, tempDir);

		String evalContent = Files.readString(tempDir.resolve("evaluations.json"), StandardCharsets.UTF_8);
		assertFalse(evalContent.contains("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"));
		assertTrue(evalContent.contains("real123"));

		// Verify that statistics are rebuilt without counting the filtered demo entry
		Path statisticsJson = tempDir.resolve("statistics.json");
		assertTrue(Files.exists(statisticsJson));
		String statsContent = Files.readString(statisticsJson, StandardCharsets.UTF_8);
		assertTrue(statsContent.contains("\"totalProcessed\": 1"));
	}

	@Test
	void testGenerateDeduplicatesSameCommitHash() throws IOException {
		GithubPagesGenerator generator = new GithubPagesGenerator();

		CommitEvaluation eval = new CommitEvaluation(
				"samehash", "commit", "https://github.com/test/repo",
				Instant.parse("2026-01-01T10:00:00Z"), null, true, null, false, null,
				4, 3, 2, TrafficLight.GREEN,
				"Collections", false, "reason",
				true, "rule", "file.sandbox-hint",
				null, null, "Summary", null);
		StatisticsCollector stats = new StatisticsCollector();
		stats.record(eval);
		// First run
		generator.generate(List.of(eval), stats, tempDir);
		// Second run with the same hash
		generator.generate(List.of(eval), stats, tempDir);

		String evalContent = Files.readString(tempDir.resolve("evaluations.json"), StandardCharsets.UTF_8);
		// "samehash" should appear exactly once
		assertEquals(1, evalContent.split("\"samehash\"", -1).length - 1);
	}

	@Test
	void testLoadTemplate() throws IOException {
		GithubPagesGenerator generator = new GithubPagesGenerator();
		String template = generator.loadTemplate();

		assertNotNull(template);
		assertTrue(template.contains("{{totalProcessed}}"));
		assertTrue(template.contains("{{green}}"));
	}
}
