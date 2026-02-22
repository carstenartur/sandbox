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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.mining.core.config.MiningState;
import org.sandbox.mining.core.config.MiningState.DeferredCommit;
import org.sandbox.mining.core.config.MiningState.RepoState;

/**
 * Tests for {@link MiningState}.
 */
class MiningStateTest {

@TempDir
Path tempDir;

@Test
void testDeferredCommitCreationAndGetters() {
DeferredCommit dc = new DeferredCommit("abc123", "Fix bug", 50,
"too large", Instant.now().toString(), 0, 3);
assertEquals("abc123", dc.getHash());
assertEquals("Fix bug", dc.getMessage());
assertEquals(50, dc.getDiffLines());
assertEquals("too large", dc.getReason());
assertEquals(0, dc.getRetryCount());
assertEquals(3, dc.getMaxRetries());
assertNotNull(dc.getDeferredAt());
}

@Test
void testRepoStateAddDeferredCommitAndMoveToPermanentlySkipped() {
RepoState state = new RepoState();
DeferredCommit dc = new DeferredCommit("abc123", "Fix bug", 50,
"too large", Instant.now().toString(), 0, 3);
state.addDeferredCommit(dc);
assertEquals(1, state.getDeferredCommits().size());
assertEquals("abc123", state.getDeferredCommits().get(0).getHash());

state.moveToPermanentlySkipped("abc123");
assertTrue(state.getDeferredCommits().isEmpty());
assertTrue(state.getPermanentlySkipped().contains("abc123"));
}

@Test
void testRepoStateLearnedMaxDiffLinesDefault() {
RepoState state = new RepoState();
assertEquals(-1, state.getLearnedMaxDiffLines());
state.setLearnedMaxDiffLines(200);
assertEquals(200, state.getLearnedMaxDiffLines());
}

@Test
void testRepoStateLastModelUsedGetterSetter() {
RepoState state = new RepoState();
state.setLastModelUsed("gemini-2.5-flash");
assertEquals("gemini-2.5-flash", state.getLastModelUsed());
}

@Test
void testSaveLoadRoundtripWithDeferredCommits() throws IOException {
Path stateFile = tempDir.resolve("state.json");
MiningState state = new MiningState();
RepoState repoState = state.getRepoState("https://github.com/test/repo");
repoState.addDeferredCommit(new DeferredCommit("abc123", "Fix bug", 50,
"too large", Instant.now().toString(), 1, 3));
repoState.setLearnedMaxDiffLines(150);
repoState.setLastModelUsed("gemini-2.5-flash");

state.save(stateFile);
assertTrue(Files.exists(stateFile));

MiningState loaded = MiningState.load(stateFile);
RepoState loadedRepo = loaded.getRepoState("https://github.com/test/repo");
assertEquals(1, loadedRepo.getDeferredCommits().size());
assertEquals("abc123", loadedRepo.getDeferredCommits().get(0).getHash());
assertEquals(150, loadedRepo.getLearnedMaxDiffLines());
assertEquals("gemini-2.5-flash", loadedRepo.getLastModelUsed());
}

@Test
void testAtomicSaveCreatesTempFileAndRenames() throws IOException {
Path stateFile = tempDir.resolve("state.json");
MiningState state = new MiningState();
state.updateLastProcessedCommit("https://github.com/test/repo", "abc123");

state.save(stateFile);
assertTrue(Files.exists(stateFile));
assertFalse(Files.exists(stateFile.resolveSibling("state.json.tmp")));
}

@Test
void testBackupCreatesBackFile() throws IOException {
Path stateFile = tempDir.resolve("state.json");
MiningState state = new MiningState();
state.save(stateFile);

MiningState.backup(stateFile);
Path bakFile = stateFile.resolveSibling("state.json.bak");
assertTrue(Files.exists(bakFile));
}

@Test
void testGetRepoStateCreatesNewStateIfNotExists() {
MiningState state = new MiningState();
RepoState repoState = state.getRepoState("https://github.com/test/new-repo");
assertNotNull(repoState);
assertEquals(-1, repoState.getLearnedMaxDiffLines());
assertTrue(repoState.getDeferredCommits().isEmpty());
}

@Test
void testAddDeferredCommitDeduplicatesByHash() {
RepoState repoState = new RepoState();
DeferredCommit dc1 = new DeferredCommit("abc123", "msg1", 500, "DIFF_TOO_LARGE",
Instant.now().toString(), 0, 3);
DeferredCommit dc2 = new DeferredCommit("abc123", "msg1 updated", 600, "DIFF_TOO_LARGE",
Instant.now().toString(), 1, 3);
repoState.addDeferredCommit(dc1);
repoState.addDeferredCommit(dc2);
assertEquals(1, repoState.getDeferredCommits().size());
assertEquals("msg1", repoState.getDeferredCommits().get(0).getMessage());
}

@Test
void testAddDeferredCommitSkipsPermanentlySkipped() {
RepoState repoState = new RepoState();
DeferredCommit dc = new DeferredCommit("abc123", "msg", 500, "DIFF_TOO_LARGE",
Instant.now().toString(), 0, 3);
repoState.addDeferredCommit(dc);
repoState.moveToPermanentlySkipped("abc123");
assertTrue(repoState.getDeferredCommits().isEmpty());
DeferredCommit dc2 = new DeferredCommit("abc123", "msg", 500, "DIFF_TOO_LARGE",
Instant.now().toString(), 0, 3);
repoState.addDeferredCommit(dc2);
assertTrue(repoState.getDeferredCommits().isEmpty());
}

@Test
void testRemoveDeferredCommit() {
RepoState repoState = new RepoState();
DeferredCommit dc = new DeferredCommit("abc123", "msg", 500, "DIFF_TOO_LARGE",
Instant.now().toString(), 0, 3);
repoState.addDeferredCommit(dc);
assertEquals(1, repoState.getDeferredCommits().size());
repoState.removeDeferredCommit("abc123");
assertTrue(repoState.getDeferredCommits().isEmpty());
}
}
