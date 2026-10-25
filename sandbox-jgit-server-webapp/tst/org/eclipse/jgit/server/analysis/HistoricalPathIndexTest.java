/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.server.history.GitHistorySnapshot;
import org.junit.Test;

public class HistoricalPathIndexTest {
  @Test public void includesEveryCommitPathIncludingUnchangedBinaryGitlinkAndRetainedHistory() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("paths"))) {
      var a = ApplicationApiComparisonTest.commit(git, 1, Map.of("p/kept.txt", "same", "p/old.bin", "\0binary"));
      var b = ApplicationApiComparisonTest.commit(git, 2, Map.of("p/kept.txt", "same", "p/new.bin", "\0binary"), a);
      ObjectId linked;
      try (var writer = git.newObjectInserter()) {
        var entry = new DirCacheEntry("vendor/submodule"); entry.setFileMode(FileMode.GITLINK); entry.setObjectId(a);
        var cache = DirCache.newInCore(); var builder = cache.builder(); builder.add(entry); builder.finish();
        var commit = new CommitBuilder(); commit.setTreeId(cache.writeTree(writer));
        commit.setAuthor(new PersonIdent("Author", "a@example.org")); commit.setCommitter(commit.getAuthor()); commit.setMessage("gitlink");
        linked = writer.insert(commit); writer.flush();
      }
      var commits = GitHistorySnapshot.read(git, List.of(a.name(), b.name(), linked.name()));
      var index = HistoricalPathIndex.build("repo", git, commits);
      try (index) {
        var kept = index.search("repo", "kept", 0, 20);
        assertEquals(2, kept.size()); assertNotEquals(kept.get(0).commitObjectId(), kept.get(1).commitObjectId());
        assertEquals(kept.get(0).blobObjectId(), kept.get(1).blobObjectId());
        assertEquals("txt", kept.getFirst().fileType()); assertNotNull(kept.getFirst().commitTime());
        assertEquals("p/old.bin", index.search("repo", "old", 0, 20).getFirst().filePath());
        assertEquals(a.name(), index.search("repo", "submodule", 0, 20).getFirst().blobObjectId());
        assertTrue(index.search("other", "kept", 0, 20).isEmpty());
        assertEquals(kept.subList(1, 2), index.search("repo", "kept", 1, 1));
        assertTrue(index.search("repo", "kept", Integer.MAX_VALUE, 20).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> kept.clear());
      }
      assertThrows(IllegalStateException.class, () -> index.search("repo", "kept", 0, 20));
      // Closing the generation must not close the caller's repository.
      try (var reader = git.newObjectReader()) { assertTrue(reader.has(a)); }
    }
  }

  @Test public void longHistoricalPathsRemainSearchableAfterDeletionWithoutPoisoningHealthyFiles() throws Exception {
    String path = ("directory" + "x".repeat(190) + "/").repeat(170) + "retained.txt";
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("long-paths"))) {
      var first = ApplicationApiComparisonTest.commit(git, 1, Map.of(path, "old", "healthy.txt", "healthy"));
      var second = ApplicationApiComparisonTest.commit(git, 2, Map.of("healthy.txt", "healthy"), first);
      var commits = GitHistorySnapshot.read(git, List.of(second.name()));
      try (var index = HistoricalPathIndex.build("repo", git, commits)) {
        assertEquals(2, index.search("repo", "healthy", 0, 10).size());
        var retained = index.search("repo", "retained", 0, 10);
        assertEquals(1, retained.size());
        assertEquals(path, retained.getFirst().filePath());
        assertEquals(first.name(), retained.getFirst().commitObjectId());
        assertEquals("txt", retained.getFirst().fileType());
      }
    }
  }

  @Test public void longLegacyFileTypeMetadataDoesNotPreventHistoricalPathSearch() throws Exception {
    String directories = ("directory" + "x".repeat(190) + "/").repeat(170);
    String path = "old.dir/" + directories + "LICENSE";
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("long-file-type"))) {
      var first = ApplicationApiComparisonTest.commit(git, 1, Map.of(path, "license", "healthy.txt", "healthy"));
      var second = ApplicationApiComparisonTest.commit(git, 2, Map.of("healthy.txt", "healthy"), first);
      var commits = GitHistorySnapshot.read(git, List.of(second.name()));
      try (var index = HistoricalPathIndex.build("repo", git, commits)) {
        assertEquals(2, index.search("repo", "healthy", 0, 10).size());
        var retained = index.search("repo", "LICENSE", 0, 10);
        assertEquals(1, retained.size());
        assertEquals(path, retained.getFirst().filePath());
        assertEquals(first.name(), retained.getFirst().commitObjectId());
        // Preserve the historical response convention without indexing it as a keyword.
        assertEquals("dir/" + directories + "license", retained.getFirst().fileType());
      }
    }
  }

  @Test public void interruptedAndMissingTreeBuildsFailWithoutLeakingTheLease() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("failure"))) {
      var missing = new GitHistorySnapshot.CommitView(ObjectId.zeroId().name(), ObjectId.zeroId().name(), null, null, null);
      assertThrows(java.io.IOException.class, () -> HistoricalPathIndex.build("repo", git, List.of(missing)));
      try {
        Thread.currentThread().interrupt();
        assertThrows(java.io.InterruptedIOException.class, () -> HistoricalPathIndex.build("repo", git, List.of(missing)));
        assertTrue(Thread.currentThread().isInterrupted());
      } finally { Thread.interrupted(); }
      var empty = HistoricalPathIndex.build("repo", git, List.of()); empty.close(); empty.close();
    }
  }
}
