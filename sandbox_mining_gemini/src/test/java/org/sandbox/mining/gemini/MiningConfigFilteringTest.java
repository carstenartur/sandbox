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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.mining.gemini.config.MiningConfig;
import org.sandbox.mining.gemini.git.DiffExtractor;

/**
 * Tests for the adaptive commit filtering feature.
 *
 * <p>Verifies that {@link MiningConfig} correctly parses the new
 * {@code min-diff-lines-per-commit} and {@code max-files-per-commit} fields,
 * and that {@link DiffExtractor} skips commits that touch too many files.</p>
 */
class MiningConfigFilteringTest {

	@TempDir
	Path tempDir;

	// -------------------------------------------------------------------------
	// MiningConfig field defaults
	// -------------------------------------------------------------------------

	@Test
	void testDefaultMinDiffLinesPerCommit() {
		MiningConfig config = new MiningConfig();
		assertEquals(10, config.getMinDiffLinesPerCommit());
	}

	@Test
	void testDefaultMaxFilesPerCommit() {
		MiningConfig config = new MiningConfig();
		assertEquals(20, config.getMaxFilesPerCommit());
	}

	@Test
	void testSettersRoundTrip() {
		MiningConfig config = new MiningConfig();
		config.setMinDiffLinesPerCommit(5);
		config.setMaxFilesPerCommit(15);
		assertEquals(5, config.getMinDiffLinesPerCommit());
		assertEquals(15, config.getMaxFilesPerCommit());
	}

	// -------------------------------------------------------------------------
	// MiningConfig YAML parsing
	// -------------------------------------------------------------------------

	@Test
	void testParseMinDiffLinesPerCommit() {
		String yaml = "mining:\n  settings:\n    min-diff-lines-per-commit: 25\n"; //$NON-NLS-1$
		MiningConfig config = MiningConfig.parse(
				new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
		assertEquals(25, config.getMinDiffLinesPerCommit());
	}

	@Test
	void testParseMaxFilesPerCommit() {
		String yaml = "mining:\n  settings:\n    max-files-per-commit: 5\n"; //$NON-NLS-1$
		MiningConfig config = MiningConfig.parse(
				new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
		assertEquals(5, config.getMaxFilesPerCommit());
	}

	@Test
	void testParseAllFilteringFields() {
		String yaml = "mining:\n  settings:\n    min-diff-lines-per-commit: 15\n    max-files-per-commit: 10\n"; //$NON-NLS-1$
		MiningConfig config = MiningConfig.parse(
				new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
		assertEquals(15, config.getMinDiffLinesPerCommit());
		assertEquals(10, config.getMaxFilesPerCommit());
	}

	@Test
	void testMissingFieldsUseDefaults() {
		String yaml = "mining:\n  settings:\n    batch-size: 50\n"; //$NON-NLS-1$
		MiningConfig config = MiningConfig.parse(
				new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
		assertEquals(10, config.getMinDiffLinesPerCommit());
		assertEquals(20, config.getMaxFilesPerCommit());
	}

	// -------------------------------------------------------------------------
	// DiffExtractor: skip commits that touch too many files
	// -------------------------------------------------------------------------

	@Test
	void testDiffExtractorSkipsCommitWithTooManyFiles() throws Exception {
		Path repoDir = tempDir.resolve("many-files-repo");
		Files.createDirectories(repoDir);
		exec(repoDir, "git", "init", ".");

		// Create an initial commit
		Files.writeString(repoDir.resolve("base.txt"), "base");
		exec(repoDir, "git", "add", ".");
		exec(repoDir, "git", "-c", "user.name=Test", "-c", "user.email=test@test.com",
				"commit", "-m", "Initial");

		// Create a second commit that touches 3 files
		for (int i = 0; i < 3; i++) {
			Files.writeString(repoDir.resolve("file" + i + ".txt"), "content " + i);
		}
		exec(repoDir, "git", "add", ".");
		exec(repoDir, "git", "-c", "user.name=Test", "-c", "user.email=test@test.com",
				"commit", "-m", "Add three files");

		// With maxFilesPerCommit=2, the second commit should be skipped (returns "")
		try (DiffExtractor extractor = new DiffExtractor(repoDir, 500, List.of(), 2)) {
			org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(repoDir.toFile());
			List<org.eclipse.jgit.revwalk.RevCommit> commits = new java.util.ArrayList<>();
			git.log().call().forEach(commits::add);
			git.close();

			// commits are newest-first; commits.get(0) is "Add three files"
			String diff = extractor.extractDiff(commits.get(0));
			assertTrue(diff.isEmpty(),
					"Expected empty diff for commit touching more files than maxFilesPerCommit");
		}
	}

	@Test
	void testDiffExtractorAllowsCommitWithinFileLimit() throws Exception {
		Path repoDir = tempDir.resolve("few-files-repo");
		Files.createDirectories(repoDir);
		exec(repoDir, "git", "init", ".");

		// Initial commit
		Files.writeString(repoDir.resolve("base.txt"), "base");
		exec(repoDir, "git", "add", ".");
		exec(repoDir, "git", "-c", "user.name=Test", "-c", "user.email=test@test.com",
				"commit", "-m", "Initial");

		// Second commit touches 2 files
		Files.writeString(repoDir.resolve("a.txt"), "aaa");
		Files.writeString(repoDir.resolve("b.txt"), "bbb");
		exec(repoDir, "git", "add", ".");
		exec(repoDir, "git", "-c", "user.name=Test", "-c", "user.email=test@test.com",
				"commit", "-m", "Add two files");

		// With maxFilesPerCommit=3, the commit should pass through
		try (DiffExtractor extractor = new DiffExtractor(repoDir, 500, List.of(), 3)) {
			org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(repoDir.toFile());
			List<org.eclipse.jgit.revwalk.RevCommit> commits = new java.util.ArrayList<>();
			git.log().call().forEach(commits::add);
			git.close();

			String diff = extractor.extractDiff(commits.get(0));
			assertTrue(!diff.isBlank(),
					"Expected non-empty diff for commit within maxFilesPerCommit");
		}
	}

	private static void exec(Path workDir, String... cmd) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.directory(workDir.toFile());
		pb.redirectErrorStream(true);
		Process p = pb.start();
		p.getInputStream().readAllBytes();
		assertEquals(0, p.waitFor());
	}
}
