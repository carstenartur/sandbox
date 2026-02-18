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
package org.sandbox.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.config.MiningConfig;

class MiningConfigTest {

	@Test
	void testParseFullConfig() {
		String yaml = """
				mining:
				  hints:
				    - bundled:collections
				    - path:my/custom.sandbox-hint
				  repositories:
				    - url: https://github.com/example/repo1
				      branch: main
				      paths:
				        - src/main
				    - url: https://github.com/example/repo2
				      branch: develop
				  settings:
				    max-files-per-repo: 3000
				    timeout-per-repo-minutes: 5
				""";

		MiningConfig config = MiningConfig.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

		assertEquals(2, config.getHints().size());
		assertEquals("bundled:collections", config.getHints().get(0));
		assertEquals("path:my/custom.sandbox-hint", config.getHints().get(1));

		assertEquals(2, config.getRepositories().size());
		assertEquals("https://github.com/example/repo1", config.getRepositories().get(0).getUrl());
		assertEquals("main", config.getRepositories().get(0).getBranch());
		assertEquals(1, config.getRepositories().get(0).getPaths().size());
		assertEquals("src/main", config.getRepositories().get(0).getPaths().get(0));

		assertEquals("https://github.com/example/repo2", config.getRepositories().get(1).getUrl());
		assertEquals("develop", config.getRepositories().get(1).getBranch());
		assertTrue(config.getRepositories().get(1).getPaths().isEmpty());

		assertEquals(3000, config.getMaxFilesPerRepo());
		assertEquals(5, config.getTimeoutPerRepoMinutes());
	}

	@Test
	void testParseMinimalConfig() {
		String yaml = """
				mining:
				  repositories:
				    - url: https://github.com/example/repo1
				""";

		MiningConfig config = MiningConfig.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

		assertTrue(config.getHints().isEmpty());
		assertEquals(1, config.getRepositories().size());
		assertEquals("main", config.getRepositories().get(0).getBranch());
		assertEquals(5000, config.getMaxFilesPerRepo());
		assertEquals(10, config.getTimeoutPerRepoMinutes());
	}

	@Test
	void testParseEmptyConfig() {
		String yaml = "mining:\n";

		MiningConfig config = MiningConfig.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

		assertNotNull(config);
		assertTrue(config.getHints().isEmpty());
		assertTrue(config.getRepositories().isEmpty());
	}

	@Test
	void testParseNullRoot() {
		String yaml = "";

		MiningConfig config = MiningConfig.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

		assertNotNull(config);
		assertTrue(config.getRepositories().isEmpty());
	}

	@Test
	void testExtractRepoName() {
		assertEquals("eclipse.jdt.core", MiningCli.extractRepoName("https://github.com/eclipse-jdt/eclipse.jdt.core"));
		assertEquals("eclipse.jdt.core", MiningCli.extractRepoName("https://github.com/eclipse-jdt/eclipse.jdt.core.git"));
		assertEquals("unknown", MiningCli.extractRepoName(null));
	}

	@Test
	void testHelpReturnsZero() throws Exception {
		int result = MiningCli.run(new String[]{"--help"});
		assertEquals(0, result);
	}

	@Test
	void testMiningReportBasics() {
		var report = new org.sandbox.mining.report.MiningReport();
		assertFalse(report.hasMatches());

		report.addFileCount("repo1", 10);
		report.addMatch("repo1", "collections", "use-isEmpty", "Foo.java", 42, "x.size() == 0", "x.isEmpty()");

		assertTrue(report.hasMatches());
		assertEquals(1, report.getMatches().size());
		assertEquals(10, report.getFileCounts().get("repo1"));
		assertEquals(1, report.getDistinctRuleCount("repo1"));
	}

	@Test
	void testMiningReportErrors() {
		var report = new org.sandbox.mining.report.MiningReport();
		assertFalse(report.hasErrors());
		assertTrue(report.getErrors().isEmpty());

		report.addError("failedRepo", "Connection timed out");
		assertTrue(report.hasErrors());
		assertEquals(1, report.getErrors().size());
		assertEquals("Connection timed out", report.getErrors().get("failedRepo"));

		// Error repo with zero file count should appear in fileCounts
		report.addFileCount("failedRepo", 0);
		assertEquals(0, report.getFileCounts().get("failedRepo"));
	}

	@Test
	void testMiningReportMergeWithErrors() {
		var report1 = new org.sandbox.mining.report.MiningReport();
		report1.addFileCount("repo1", 10);
		report1.addError("repo2", "Clone failed");

		var report2 = new org.sandbox.mining.report.MiningReport();
		report2.addFileCount("repo3", 20);
		report2.addError("repo4", "Timeout");

		report1.merge(report2);
		assertEquals(2, report1.getErrors().size());
		assertEquals("Clone failed", report1.getErrors().get("repo2"));
		assertEquals("Timeout", report1.getErrors().get("repo4"));
	}

	@Test
	void testMarkdownReporterWithErrors() {
		var report = new org.sandbox.mining.report.MiningReport();
		report.addFileCount("goodRepo", 10);
		report.addMatch("goodRepo", "collections", "use-isEmpty", "Foo.java", 42, "x.size() == 0", "x.isEmpty()");
		report.addFileCount("failedRepo", 0);
		report.addError("failedRepo", "Connection timed out");

		var reporter = new org.sandbox.mining.report.MarkdownReporter();
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
	void testMarkdownReporterWithoutErrors() {
		var report = new org.sandbox.mining.report.MiningReport();
		report.addFileCount("repo1", 10);

		var reporter = new org.sandbox.mining.report.MarkdownReporter();
		String markdown = reporter.generate(report);

		assertFalse(markdown.contains("## Errors"));
		assertFalse(markdown.contains("⚠️"));
	}

	@Test
	void testJsonReporterWithErrors() {
		var report = new org.sandbox.mining.report.MiningReport();
		report.addFileCount("goodRepo", 10);
		report.addFileCount("failedRepo", 0);
		report.addError("failedRepo", "Connection timed out");

		var reporter = new org.sandbox.mining.report.JsonReporter();
		String json = reporter.generate(report);

		assertTrue(json.contains("\"errors\""));
		assertTrue(json.contains("\"failedRepo\""));
		assertTrue(json.contains("Connection timed out"));
	}

	@Test
	void testJsonReporterWithoutErrors() {
		var report = new org.sandbox.mining.report.MiningReport();
		report.addFileCount("repo1", 10);

		var reporter = new org.sandbox.mining.report.JsonReporter();
		String json = reporter.generate(report);

		// Errors section should still exist but be empty
		assertTrue(json.contains("\"errors\": {"));
	}
}
