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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.git.CommitWalker;

/**
 * Tests for {@link CommitWalker}.
 *
 * <p>Tests the batch iteration logic without a real Git repository.</p>
 */
class CommitWalkerTest {

	@TempDir
	Path tempDir;

	@Test
	void testNextBatchOnEmptyRepo() throws Exception {
		// Initialize a bare git repo with at least one commit
		Path repoDir = tempDir.resolve("test-repo");
		Files.createDirectories(repoDir);

		ProcessBuilder pb = new ProcessBuilder("git", "init", repoDir.toString());
		pb.redirectErrorStream(true);
		Process initProcess = pb.start();
		initProcess.getInputStream().readAllBytes();
		assertEquals(0, initProcess.waitFor());

		// Create an initial commit
		Path testFile = repoDir.resolve("README.md");
		Files.writeString(testFile, "# Test");
		exec(repoDir, "git", "add", ".");
		exec(repoDir, "git", "-c", "user.name=Test", "-c", "user.email=test@test.com",
				"commit", "-m", "Initial commit");

		try (CommitWalker walker = new CommitWalker(repoDir)) {
			List<?> batch = walker.nextBatch(null, null, 10);
			assertNotNull(batch);
			assertTrue(batch.size() >= 1);
		}
	}

	@Test
	void testNextBatchWithDateFilter() throws Exception {
		Path repoDir = tempDir.resolve("date-repo");
		Files.createDirectories(repoDir);

		exec(repoDir, "git", "init", ".");
		Files.writeString(repoDir.resolve("file.txt"), "content");
		exec(repoDir, "git", "add", ".");
		// Create a commit with a fixed date in the past using GIT_COMMITTER_DATE
		ProcessBuilder pb = new ProcessBuilder("git", "-c", "user.name=Test",
				"-c", "user.email=test@test.com",
				"commit", "-m", "First commit",
				"--date", "2020-01-01T00:00:00");
		pb.directory(repoDir.toFile());
		pb.environment().put("GIT_COMMITTER_DATE", "2020-01-01T00:00:00");
		pb.redirectErrorStream(true);
		Process p = pb.start();
		p.getInputStream().readAllBytes();
		assertEquals(0, p.waitFor());

		try (CommitWalker walker = new CommitWalker(repoDir)) {
			// Date after the commit should return nothing
			List<?> batch = walker.nextBatch(null, "2021-01-01", 10);
			assertNotNull(batch);
			assertEquals(0, batch.size());
		}
	}

	@Test
	void testBatchSizeLimiting() throws Exception {
		Path repoDir = tempDir.resolve("batch-repo");
		Files.createDirectories(repoDir);

		exec(repoDir, "git", "init", ".");

		// Create multiple commits
		for (int i = 0; i < 5; i++) {
			Files.writeString(repoDir.resolve("file" + i + ".txt"), "content " + i);
			exec(repoDir, "git", "add", ".");
			exec(repoDir, "git", "-c", "user.name=Test", "-c", "user.email=test@test.com",
					"commit", "-m", "Commit " + i);
		}

		try (CommitWalker walker = new CommitWalker(repoDir)) {
			List<?> batch = walker.nextBatch(null, null, 3);
			assertNotNull(batch);
			assertEquals(3, batch.size());
		}
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