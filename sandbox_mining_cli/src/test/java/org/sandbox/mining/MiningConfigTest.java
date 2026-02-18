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
}
