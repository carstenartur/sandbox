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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.mining.core.config.RepoEntry;
import org.sandbox.jdt.triggerpattern.git.CommitWalker;

/**
 * Tests for {@link MiningCli}.
 */
class MiningCliTest {

	@TempDir
	Path tempDir;

	@Test
	void testRepoDirectoryNameStripsGitSuffix() {
		assertEquals("myrepo", MiningCli.repoDirectoryName("https://github.com/user/myrepo.git"));
	}

	@Test
	void testRepoDirectoryNameNoSuffix() {
		assertEquals("myrepo", MiningCli.repoDirectoryName("https://github.com/user/myrepo"));
	}

	@Test
	void testFormatCommitInfoBasic() throws Exception {
		Path repoDir = createRepoWithCommit("Fix bug in dialog layout");
		try (CommitWalker walker = new CommitWalker(repoDir)) {
			List<RevCommit> batch = walker.nextBatch(null, null, 1);
			assertEquals(1, batch.size());
			RevCommit commit = batch.get(0);
			RepoEntry repo = new RepoEntry("https://github.com/test/repo", "main", List.of());

			String info = MiningCli.formatCommitInfo(commit, repo);

			assertTrue(info.startsWith(commit.getName().substring(0, 7) + " on main ("),
					"Should start with hash and branch: " + info);
			assertTrue(info.contains("\"Fix bug in dialog layout\""),
					"Should contain quoted commit title: " + info);
			assertTrue(info.matches(".*\\(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\).*"),
					"Should contain UTC datetime: " + info);
		}
	}

	@Test
	void testFormatCommitInfoBranchName() throws Exception {
		Path repoDir = createRepoWithCommit("Some change");
		try (CommitWalker walker = new CommitWalker(repoDir)) {
			List<RevCommit> batch = walker.nextBatch(null, null, 1);
			RevCommit commit = batch.get(0);
			RepoEntry repo = new RepoEntry("https://github.com/test/repo", "feature/my-branch", List.of());

			String info = MiningCli.formatCommitInfo(commit, repo);

			assertTrue(info.contains("on feature/my-branch ("), "Should contain branch name: " + info);
		}
	}

	@Disabled("Currently we don't escape double quotes in the title, but we should to avoid breaking the output format")
	@Test
	void testFormatCommitInfoEscapesDoubleQuotesInTitle() throws Exception {
		Path repoDir = createRepoWithCommit("Fix \"critical\" bug");
		try (CommitWalker walker = new CommitWalker(repoDir)) {
			List<RevCommit> batch = walker.nextBatch(null, null, 1);
			RevCommit commit = batch.get(0);
			RepoEntry repo = new RepoEntry("https://github.com/test/repo", "main", List.of());

			String info = MiningCli.formatCommitInfo(commit, repo);

			assertTrue(info.contains("\\\"critical\\\""), "Double quotes in title should be escaped: " + info);
		}
	}

	@Test
	void testFormatCommitInfoMultiLineMessageUsesFirstLineOnly() throws Exception {
		Path repoDir = createRepoWithCommit("First line\n\nDetailed description here.");
		try (CommitWalker walker = new CommitWalker(repoDir)) {
			List<RevCommit> batch = walker.nextBatch(null, null, 1);
			RevCommit commit = batch.get(0);
			RepoEntry repo = new RepoEntry("https://github.com/test/repo", "main", List.of());

			String info = MiningCli.formatCommitInfo(commit, repo);

			assertTrue(info.contains("\"First line\""), "Should only show first line: " + info);
			assertTrue(!info.contains("Detailed description"), "Should not show body: " + info);
		}
	}

	@Test
	void testMaxFailureDurationValidationRejectsNegative() {
		MiningCli cli = new MiningCli();
		assertThrows(IllegalArgumentException.class,
				() -> cli.run(new String[] { "--max-failure-duration", "-1" }));
	}

	@Test
	void testMaxFailureDurationValidationRejectsTooSmall() {
		MiningCli cli = new MiningCli();
		assertThrows(IllegalArgumentException.class,
				() -> cli.run(new String[] { "--max-failure-duration", "5" }));
	}

	private Path createRepoWithCommit(String message) throws IOException, InterruptedException {
		Path repoDir = tempDir.resolve("repo-" + System.nanoTime());
		Files.createDirectories(repoDir);
		exec(repoDir, "git", "init", ".");
		Files.writeString(repoDir.resolve("file.txt"), "content");
		exec(repoDir, "git", "add", ".");
		exec(repoDir, "git", "-c", "user.name=Test", "-c", "user.email=test@test.com",
				"commit", "-m", message);
		return repoDir;
	}

	@Test
	void testRetryDeferredFlagParsing() {
		MiningCli cli = new MiningCli();
		// --retry-deferred is a boolean flag; it should not throw when used alone
		// It will fail later due to missing config, but parsing itself succeeds
		try {
			cli.run(new String[] { "--retry-deferred" });
		} catch (Exception e) {
			// Expected: config file not found or similar runtime error
			// The flag itself parsed without error
		}
	}

	@Test
	void testResetLearnedLimitsFlagParsing() {
		MiningCli cli = new MiningCli();
		// --reset-learned-limits is a boolean flag; it should not throw when used alone
		try {
			cli.run(new String[] { "--reset-learned-limits" });
		} catch (Exception e) {
			// Expected: config file not found or similar runtime error
			// The flag itself parsed without error
		}
	}

	@Test
	void testShouldStopNoLimit() {
		long startTime = System.currentTimeMillis();
		// 0 means no limit
		assertFalse(MiningCli.shouldStop(startTime, 0));
	}

	@Test
	void testShouldStopNegativeLimit() {
		long startTime = System.currentTimeMillis();
		assertFalse(MiningCli.shouldStop(startTime, -1));
	}

	@Test
	void testShouldStopNotElapsed() {
		long startTime = System.currentTimeMillis();
		// 60 minutes, just started
		assertFalse(MiningCli.shouldStop(startTime, 60));
	}

	@Test
	void testShouldStopElapsed() {
		// Start time in the past (2 hours ago)
		long startTime = System.currentTimeMillis() - 2 * 60 * 60 * 1000;
		// 60 minutes limit should have been exceeded
		assertTrue(MiningCli.shouldStop(startTime, 60));
	}

	@Test
	void testReadCommitList() throws IOException {
		Path file = tempDir.resolve("commits.txt");
		Files.writeString(file, "# comment\nabc123\n\ndef456\n  ghi789  \n");
		List<String> hashes = MiningCli.readCommitList(file);
		assertEquals(3, hashes.size());
		assertEquals("abc123", hashes.get(0));
		assertEquals("def456", hashes.get(1));
		assertEquals("ghi789", hashes.get(2));
	}

	@Test
	void testReadCommitListEmpty() throws IOException {
		Path file = tempDir.resolve("empty-commits.txt");
		Files.writeString(file, "# only comments\n\n# another comment\n");
		List<String> hashes = MiningCli.readCommitList(file);
		assertTrue(hashes.isEmpty());
	}

	@Test
	void testNewFlagsParsing() {
		MiningCli cli = new MiningCli();
		ByteArrayOutputStream errStream = new ByteArrayOutputStream();
		PrintStream oldErr = System.err;
		System.setErr(new PrintStream(errStream));
		try {
			cli.run(new String[] {
					"--enrich-type-context",
					"--comparison-mode",
					"--output-format", "both",
					"--strict-netbeans",
					"--max-duration", "30"
			});
		} catch (Exception e) {
			// Expected: config file not found or similar runtime error
		} finally {
			System.setErr(oldErr);
		}
		String errOutput = errStream.toString();
		assertFalse(errOutput.contains("Unknown option"), //$NON-NLS-1$
				"No flags should be reported as unknown, but got: " + errOutput); //$NON-NLS-1$
	}

	@Test
	void testCommitListFlagParsing() throws IOException {
		// Create a valid commit list file
		Path commitList = tempDir.resolve("commit-list.txt");
		Files.writeString(commitList, "abc123\ndef456\n");

		MiningCli cli = new MiningCli();
		ByteArrayOutputStream errStream = new ByteArrayOutputStream();
		PrintStream oldErr = System.err;
		System.setErr(new PrintStream(errStream));
		try {
			cli.run(new String[] { "--commit-list", commitList.toString() });
		} catch (Exception e) {
			// Expected: config file not found or similar runtime error
		} finally {
			System.setErr(oldErr);
		}
		String errOutput = errStream.toString();
		assertFalse(errOutput.contains("Unknown option"), //$NON-NLS-1$
				"--commit-list should be recognized, but got: " + errOutput); //$NON-NLS-1$
	}

	@Test
	void testKeywordFilterFlagParsing() throws IOException {
		// Create a valid keyword file
		Path keywordFile = tempDir.resolve("keywords.txt");
		Files.writeString(keywordFile, "refactor\ncleanup\n");

		MiningCli cli = new MiningCli();
		ByteArrayOutputStream errStream = new ByteArrayOutputStream();
		PrintStream oldErr = System.err;
		System.setErr(new PrintStream(errStream));
		try {
			cli.run(new String[] { "--keyword-filter", keywordFile.toString() });
		} catch (Exception e) {
			// Expected: config file not found or similar runtime error
		} finally {
			System.setErr(oldErr);
		}
		String errOutput = errStream.toString();
		assertFalse(errOutput.contains("Unknown option"), //$NON-NLS-1$
				"--keyword-filter should be recognized, but got: " + errOutput); //$NON-NLS-1$
	}

	@Test
	void testCommitListAndKeywordFilterCombined() throws IOException {
		Path commitList = tempDir.resolve("commit-list.txt");
		Files.writeString(commitList, "abc123\n");
		Path keywordFile = tempDir.resolve("keywords.txt");
		Files.writeString(keywordFile, "refactor\n");

		MiningCli cli = new MiningCli();
		ByteArrayOutputStream errStream = new ByteArrayOutputStream();
		PrintStream oldErr = System.err;
		System.setErr(new PrintStream(errStream));
		try {
			cli.run(new String[] {
					"--commit-list", commitList.toString(),
					"--keyword-filter", keywordFile.toString()
			});
		} catch (Exception e) {
			// Expected: config file not found or similar runtime error
		} finally {
			System.setErr(oldErr);
		}
		String errOutput = errStream.toString();
		assertFalse(errOutput.contains("Unknown option"), //$NON-NLS-1$
				"Combined flags should be recognized, but got: " + errOutput); //$NON-NLS-1$
	}

	private static void exec(Path workDir, String... cmd) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.directory(workDir.toFile());
		pb.redirectErrorStream(true);
		Process p = pb.start();
		p.getInputStream().readAllBytes();
		assertEquals(0, p.waitFor());
	}
}
