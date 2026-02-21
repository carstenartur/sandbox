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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.mining.core.config.RepoEntry;
import org.sandbox.mining.core.git.CommitWalker;

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

	private static void exec(Path workDir, String... cmd) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.directory(workDir.toFile());
		pb.redirectErrorStream(true);
		Process p = pb.start();
		p.getInputStream().readAllBytes();
		assertEquals(0, p.waitFor());
	}
}
