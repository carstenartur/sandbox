/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ApplicationApiComparisonTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void comparesCompleteTreesAndKeepsActualCommitProvenance() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("api"))) {
      String shared = "package p; public class Shared {}";
      var a = commit(git, 1, Map.of("p/Shared.java", shared, "p/A.java", "package p; public class A { public int m() {return 1;} }",
          "Old.java", "public class Old {}", "Hidden.java", "class Hidden {}"));
      var b = commit(git, 2, Map.of("p/Shared.java", shared, "p/A.java", "package p; class A { public String n() {return null;} }",
          "New.java", "public class New {}", "Hidden.java", "class Hidden { int x; }"), a);
      var diff = comparator().compare("repo", git, a, b, false);
      assertEquals(List.of("New.java"), diff.added().stream().map(AnalyzedFile::filePath).toList());
      assertEquals(List.of("Old.java"), diff.removed().stream().map(AnalyzedFile::filePath).toList());
      assertEquals(List.of("Hidden.java", "p/A.java"), diff.changed().stream().map(c -> c.after().filePath()).toList());
      var changed = diff.changed().getLast();
      assertEquals(a.name(), changed.before().commitObjectId()); assertEquals(b.name(), changed.after().commitObjectId());
      assertEquals("methods changed, signatures changed, visibility changed", changed.description());
      assertEquals(List.of("p/A.java"), comparator().compare("repo", git, a, b, true).changed().stream()
          .map(c -> c.after().filePath()).toList());
      assertThrows(UnsupportedOperationException.class, () -> diff.changed().clear());
    }
  }

  @Test public void ignoresBodyAndBindingQualityOnlyChangesButFindsFieldAndAnnotationChanges() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("semantic"))) {
      String caller = "package p; public class Caller { String call(Base b) { return b.name(); } }";
      var a = commit(git, 1, Map.of("p/Caller.java", caller,
          "p/Base.java", "package p; public class Base { public String name(){return null;} }",
          "p/Body.java", "package p; class Body { int m(){ int x=1; return x; } }"));
      var b = commit(git, 2, Map.of("p/Caller.java", caller,
          "p/Base.java", "package p; @Deprecated public class Base { public int x; }",
          "p/Body.java", "package p; class Body { int m(){ int changed=2; return changed; } }"), a);
      var diff = comparator().compare("repo", git, a, b, false);
      assertEquals(List.of("p/Base.java"), diff.changed().stream().map(c -> c.after().filePath()).toList());
      assertTrue(diff.added().isEmpty()); assertTrue(diff.removed().isEmpty());
      var moved = commit(git, 3, Map.of("moved/Caller.java", caller), b);
      assertEquals(List.of("moved/Caller.java"), comparator().compare("repo", git, b, moved, false)
          .added().stream().map(AnalyzedFile::filePath).toList());
    }
  }

  @Test public void unknownCommitsFailAndInterruptedComparisonPreservesTheFlag() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("missing"))) {
      var a = commit(git, 1, Map.of("A.java", "class A {}"));
      assertThrows(java.io.IOException.class, () -> comparator().compare("repo", git, a, ObjectId.zeroId(), false));
      try {
        Thread.currentThread().interrupt();
        assertThrows(java.io.InterruptedIOException.class, () -> comparator().compare("repo", git, a, a, false));
        assertTrue(Thread.currentThread().isInterrupted());
      } finally { Thread.interrupted(); }
    }
  }

  @Test public void removingAnotherFileCannotCreateAnApiChangeInTheRemainingFile() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("removed-sibling"))) {
      for (String body : List.of("return 1;", "return 2;")) {
        var a = commit(git, 1, Map.of("A.java", "class A {}", "B.java", "class B { int m(){return 1;} }"));
        var b = commit(git, 2, Map.of("B.java", "class B { int m(){" + body + "} }"), a);
        var diff = comparator().compare("repo", git, a, b, false);
        assertEquals(List.of("A.java"), diff.removed().stream().map(AnalyzedFile::filePath).toList());
        assertTrue(diff.changed().toString(), diff.changed().isEmpty()); assertTrue(diff.added().isEmpty());
      }
    }
  }

  @Test public void aDeclarationTransferredBetweenRetainedFilesIsARemovalAndAddition() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("transfer"))) {
      var a = commit(git, 1, Map.of("A.java", "class A { int field; }", "B.java", "class B {}"));
      var b = commit(git, 2, Map.of("A.java", "class A {}", "B.java", "class B { int field; }"), a);
      var diff = comparator().compare("repo", git, a, b, false);
      assertTrue(diff.added().isEmpty()); assertTrue(diff.removed().isEmpty());
      assertEquals(List.of("A.java", "B.java"), diff.changed().stream().map(c -> c.after().filePath()).toList());
      assertEquals(List.of("declarations changed", "declarations changed"), diff.changed().stream().map(c -> c.description()).toList());
    }
  }

  @Test public void heuristicMatchesDoNotClaimAnExactRenameWhenOneOfTwoTypesIsRemoved() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("declarations"))) {
      var a = commit(git, 1, Map.of("Types.java", "class Removed {} class Kept {}"));
      var b = commit(git, 2, Map.of("Types.java", "class Kept {}"), a);
      var diff = comparator().compare("repo", git, a, b, false);
      assertEquals(1, diff.changed().size()); assertEquals("declarations changed", diff.changed().getFirst().description());
    }
  }

  private ApplicationApiComparison comparator() {
    return new ApplicationApiComparison(new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024));
  }

  static ObjectId commit(Repository git, int seconds, Map<String, String> files, ObjectId... parents) throws Exception {
    try (var inserter = git.newObjectInserter()) {
      var cache = DirCache.newInCore(); var builder = cache.builder();
      for (var file : new TreeMap<>(files).entrySet()) {
        var entry = new DirCacheEntry(file.getKey()); entry.setFileMode(FileMode.REGULAR_FILE);
        entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, file.getValue().getBytes(StandardCharsets.UTF_8))); builder.add(entry);
      }
      builder.finish(); var commit = new CommitBuilder(); commit.setTreeId(cache.writeTree(inserter)); commit.setParentIds(parents);
      var who = new PersonIdent("Author", "a@example.org", Instant.ofEpochSecond(seconds), ZoneOffset.UTC);
      commit.setAuthor(who); commit.setCommitter(who); commit.setMessage("commit " + seconds);
      var id = inserter.insert(commit); inserter.flush(); return id;
    }
  }
}
