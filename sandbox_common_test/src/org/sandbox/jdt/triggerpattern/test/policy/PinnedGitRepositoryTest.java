/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests exact ref/commit checkout and cleanup without command-line Git.
 *
 * @since 1.3.4
 */
public class PinnedGitRepositoryTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	public void testPinnedRefAndCommitIdentity() throws Exception {
		Path source = temporaryDirectory.resolve("source"); //$NON-NLS-1$
		Path checkout = temporaryDirectory.resolve("checkout"); //$NON-NLS-1$
		try (Git sourceGit = Git.init().setDirectory(source.toFile()).call()) {
			RevCommit pinnedCommit = commit(sourceGit, source, "fixture.txt", "pinned\n", "Pinned fixture"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			sourceGit.branchCreate().setName("fixture").setStartPoint(pinnedCommit.name()).call(); //$NON-NLS-1$
			commit(sourceGit, source, "fixture.txt", "newer\n", "Advance default branch"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

			try (PinnedGitRepository fixture = PinnedGitRepository.cloneAt(checkout, source.toUri(),
					"fixture", pinnedCommit.name())) { //$NON-NLS-1$
				assertEquals(pinnedCommit.name(), fixture.headCommit());
				assertEquals("pinned\n", fixture.readString("fixture.txt")); //$NON-NLS-1$ //$NON-NLS-2$
				assertTrue(fixture.hasRef("refs/sandbox/pinned")); //$NON-NLS-1$
				assertFalse(fixture.hasRef(Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/fixture"), //$NON-NLS-1$
						"The fixture must not create tracking refs for unrelated remote history"); //$NON-NLS-1$
			}
		}
		assertFalse(Files.exists(checkout), "Closing the fixture must remove the checkout"); //$NON-NLS-1$
	}

	@Test
	public void testMismatchedCommitFailsAndCleansCheckout() throws Exception {
		Path source = temporaryDirectory.resolve("mismatch-source"); //$NON-NLS-1$
		Path checkout = temporaryDirectory.resolve("mismatch-checkout"); //$NON-NLS-1$
		try (Git sourceGit = Git.init().setDirectory(source.toFile()).call()) {
			RevCommit pinnedCommit = commit(sourceGit, source, "fixture.txt", "pinned\n", "Pinned fixture"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			sourceGit.branchCreate().setName("fixture").setStartPoint(pinnedCommit.name()).call(); //$NON-NLS-1$
			RevCommit newerCommit = commit(sourceGit, source, "fixture.txt", "newer\n", //$NON-NLS-1$ //$NON-NLS-2$
					"Advance default branch"); //$NON-NLS-1$

			assertThrows(IllegalArgumentException.class,
					() -> PinnedGitRepository.cloneAt(checkout, source.toUri(), "fixture", //$NON-NLS-1$
							newerCommit.name()));
		}
		assertFalse(Files.exists(checkout), "Failed identity verification must remove the checkout"); //$NON-NLS-1$
	}

	@Test
	public void testFetchMaintenanceCompletesBeforeFixtureCleanup() throws Exception {
		Path source = temporaryDirectory.resolve("maintenance-source"); //$NON-NLS-1$
		Path checkout = temporaryDirectory.resolve("maintenance-checkout"); //$NON-NLS-1$
		try (Git sourceGit = Git.init().setDirectory(source.toFile()).call()) {
			RevCommit pinnedCommit = commit(sourceGit, source, "fixture.txt", "pinned\n", "Pinned fixture"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			sourceGit.branchCreate().setName("fixture").setStartPoint(pinnedCommit.name()).call(); //$NON-NLS-1$
			try (PinnedGitRepository fixture = PinnedGitRepository.cloneAt(checkout, source.toUri(),
					"fixture", pinnedCommit.name()); Git inspected = Git.open(fixture.directory().toFile())) { //$NON-NLS-1$
				assertFalse(inspected.getRepository().getConfig().getBoolean("gc", "autoDetach", true), //$NON-NLS-1$ //$NON-NLS-2$
						"Fetch maintenance must not outlive the temporary checkout"); //$NON-NLS-1$
				assertEquals(pinnedCommit.name(), fixture.headCommit());
			}
		}
		assertFalse(Files.exists(checkout), "Closing the fixture must remove the checkout"); //$NON-NLS-1$
	}

	@Test
	public void testHistoricalCommitSurvivesAdvertisedRefAdvanceWithoutWeakeningTipContract() throws Exception {
		Path source = temporaryDirectory.resolve("historical-source"), checkout = temporaryDirectory.resolve("historical-checkout"); //$NON-NLS-1$ //$NON-NLS-2$
		try (Git sourceGit = Git.init().setDirectory(source.toFile()).call()) {
			RevCommit pinned = commit(sourceGit, source, "fixture.txt", "pinned\n", "Pinned fixture"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			sourceGit.checkout().setCreateBranch(true).setName("advancing").call(); //$NON-NLS-1$
			commit(sourceGit, source, "fixture.txt", "newer\n", "Advance source ref"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertThrows(IllegalArgumentException.class, () -> PinnedGitRepository.cloneAt(checkout, source.toUri(),
					"refs/heads/advancing", pinned.name())); //$NON-NLS-1$
			assertFalse(Files.exists(checkout));
			try (var fixture = PinnedGitRepository.cloneAtCommit(checkout, source.toUri(), "refs/heads/advancing", pinned.name()); //$NON-NLS-1$
					var inspected = Git.open(checkout.toFile())) {
				assertEquals(pinned.name(), fixture.headCommit());
				assertEquals("pinned\n", fixture.readString("fixture.txt")); //$NON-NLS-1$ //$NON-NLS-2$
				assertEquals(pinned.name(), inspected.getRepository().getFullBranch());
				assertEquals(source.toUri().toString(), inspected.getRepository().getConfig().getString("remote", "origin", "url")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
		assertFalse(Files.exists(checkout));
	}

	@Test
	public void testHistoricalCommitRejectsDivergentHistoryWrongCommitAndMissingSourceRef() throws Exception {
		Path source = temporaryDirectory.resolve("divergent-source"), checkout = temporaryDirectory.resolve("rejected-checkout"); //$NON-NLS-1$ //$NON-NLS-2$
		try (Git sourceGit = Git.init().setDirectory(source.toFile()).call()) {
			RevCommit root = commit(sourceGit, source, "fixture.txt", "root\n", "Root"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			sourceGit.checkout().setCreateBranch(true).setName("source").call(); //$NON-NLS-1$
			commit(sourceGit, source, "fixture.txt", "tip\n", "Source tip"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			sourceGit.checkout().setName(root.name()).call();
			RevCommit divergent = commit(sourceGit, source, "fixture.txt", "unrelated\n", "Divergent commit"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			sourceGit.branchCreate().setName("unrelated").call(); //$NON-NLS-1$
			assertThrows(IllegalArgumentException.class, () -> PinnedGitRepository.cloneAtCommit(checkout, source.toUri(),
					"refs/heads/source", divergent.name())); //$NON-NLS-1$
			assertFalse(Files.exists(checkout));
			assertThrows(IllegalArgumentException.class, () -> PinnedGitRepository.cloneAtCommit(checkout, source.toUri(),
					"refs/heads/source", "0000000000000000000000000000000000000000")); //$NON-NLS-1$ //$NON-NLS-2$
			assertFalse(Files.exists(checkout));
			assertThrows(IllegalArgumentException.class, () -> PinnedGitRepository.cloneAtCommit(checkout, source.toUri(),
					"refs/heads/missing", root.name())); //$NON-NLS-1$
			assertFalse(Files.exists(checkout));
		}
	}

	private static RevCommit commit(Git git, Path repository, String relativePath, String contents,
			String message) throws Exception {
		Path file = repository.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, contents, StandardCharsets.UTF_8);
		git.add().addFilepattern(relativePath).call();
		return git.commit()
				.setMessage(message)
				.setAuthor("Sandbox policy test", "sandbox@example.invalid") //$NON-NLS-1$ //$NON-NLS-2$
				.setCommitter("Sandbox policy test", "sandbox@example.invalid") //$NON-NLS-1$ //$NON-NLS-2$
				.call();
	}
}
