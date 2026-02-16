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
package org.sandbox.mining.gemini;

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
import org.sandbox.mining.gemini.gemini.CommitEvaluation;
import org.sandbox.mining.gemini.gemini.CommitEvaluation.TrafficLight;
import org.sandbox.mining.gemini.report.GithubPagesGenerator;
import org.sandbox.mining.gemini.report.StatisticsCollector;

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
				Instant.now(), true, null, false, null,
				4, 3, 2, TrafficLight.GREEN,
				"Collections", false, "reason",
				true, "rule", "file.sandbox-hint",
				null, null, "Test summary");
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
	void testLoadTemplate() throws IOException {
		GithubPagesGenerator generator = new GithubPagesGenerator();
		String template = generator.loadTemplate();

		assertNotNull(template);
		assertTrue(template.contains("{{totalProcessed}}"));
		assertTrue(template.contains("{{green}}"));
	}
}
