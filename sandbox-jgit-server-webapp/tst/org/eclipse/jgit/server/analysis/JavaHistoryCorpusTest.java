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

public class JavaHistoryCorpusTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private int sequence;
  private static final String CALLER = "package p; public class Caller { String call(Base value) { return value.name(); } }";
  private static final String BASE = "package p; public class Base { String name() { return \"value\"; } }";

  @Test public void buildsAllParentsRefsAndRetainedRootsWithOneDeterministicOriginPerBlob() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("history"))) {
      var initial = Map.of("module/src/p/Caller.java", CALLER, "module/src/p/Base.java", BASE,
          "copy/src/p/Caller.java", CALLER, "Binary.java", "\0binary");
      ObjectId root = commit(git, 10, initial, true);
      ObjectId main = commit(git, 20, Map.of("module/src/p/Caller.java", CALLER,
          "module/src/p/Base.java", "package p; public class Base {}"), true, root);
      ObjectId side = commit(git, 15, Map.of("side/src/p/Other.java", "package p; class Other {}"), false, root);
      ObjectId merge = commit(git, 30, initial, true, main, side);
      ObjectId retained = commit(git, 5, Map.of("gone/src/p/Retained.java", "package p; class Retained {}"), false);
      ref(git, "refs/heads/main", merge);
      ref(git, "refs/tags/side", side);
      var builder = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024);
      var corpus = builder.read("repo", git, List.of(retained.name()));
      assertEquals(5, corpus.commits().size());
      assertEquals(5, corpus.files().size()); // Caller, two Base blobs, Other and retained source.
      var caller = corpus.files().stream().filter(f -> f.simpleClassName().equals("Caller")).findFirst().orElseThrow();
      assertEquals(root.name(), caller.commitObjectId());
      assertEquals("copy/src/p/Caller.java", caller.filePath());
      assertEquals("copy", caller.projectName());
      assertTrue(corpus.files().stream().anyMatch(f -> f.simpleClassName().equals("Retained")));
      assertTrue(corpus.files().stream().noneMatch(f -> f.filePath().equals("Binary.java") || f.filePath().equals("vendor.java")));
      var changed = builder.atCommit("repo", git, main).files().get("module/src/p/Caller.java");
      assertEquals(caller.blobObjectId(), changed.blobObjectId());
      assertTrue(changed.quality().errorCount() > 0);
      assertEquals(main.name(), changed.commitObjectId());
      assertThrows(UnsupportedOperationException.class, () -> corpus.files().clear());
    }
  }

  @Test public void canonicalOriginChangesExplicitlyWhenAnOlderRootBecomesVisibleAndSurvivesRestart() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("origins"))) {
      ObjectId first = commit(git, 20, Map.of("new/src/p/Caller.java", CALLER), false);
      ref(git, "refs/heads/main", first);
      var builder = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024);
      assertEquals("new", builder.read("repo", git, List.of()).files().getFirst().projectName());
      ObjectId older = commit(git, 10, Map.of("old/src/p/Caller.java", CALLER), false);
      ref(git, "refs/heads/older", older);
      var origin = builder.read("repo", git, List.of()).files().getFirst();
      assertEquals(older.name(), origin.commitObjectId());
      assertEquals("old", origin.projectName());
      assertEquals(origin, new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024)
          .read("repo", git, List.of()).files().getFirst());
    }
  }

  @Test public void keepsHistoricalSourcesSearchableWhenBuildMetadataIsInvalidOrEscapesTheRepository() throws Exception {
    for (String pom : List.of("<project>",
        "<project><build><sourceDirectory>../../outside</sourceDirectory></build></project>",
        "<project><build><sourceDirectory>/outside</sourceDirectory></build></project>",
        "<project><build><sourceDirectory>C:/outside</sourceDirectory></build></project>",
        "<project><build><sourceDirectory>src/p/Base.java</sourceDirectory></build></project>",
        "<project><build><sourceDirectory>src/p/Base.java/nested</sourceDirectory></build></project>")) {
      try (var git = new InMemoryRepository(new DfsRepositoryDescription("configuration"))) {
        var id = commit(git, 1, Map.of("pom.xml", pom, "src/p/Base.java", BASE), false);
        ref(git, "refs/heads/main", id);
        var corpus = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024).read("repo", git, List.of());
        assertEquals(1, corpus.files().size());
        assertEquals("Base", corpus.files().getFirst().simpleClassName());
        assertFalse(corpus.files().getFirst().quality().configurationDiagnostics().isEmpty());
      }
    }
  }

  @Test public void resolvesRootLayoutSiblingsAlongsideConventionalModuleRoots() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("mixed-layout"))) {
      var id = commit(git, 1, Map.of("p/Caller.java", CALLER, "p/Base.java", BASE,
          "module/src/q/Other.java", "package q; class Other {}"), false);
      ref(git, "refs/heads/main", id);
      var corpus = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024).read("repo", git, List.of());
      var caller = corpus.files().stream().filter(f -> f.simpleClassName().equals("Caller")).findFirst().orElseThrow();
      assertEquals(0, caller.quality().errorCount());
    }
  }

  @Test public void reportsMissingDependenciesAndSkippedSourcesAndPreservesInterruption() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("limits"))) {
      String pom = "<project><dependencies><dependency><groupId>missing</groupId><artifactId>library</artifactId>"
          + "<version>1</version></dependency></dependencies></project>";
      var id = commit(git, 1, Map.of("pom.xml", pom, "src/p/Base.java", BASE,
          "src/p/Large.java", "class Large { }" + " ".repeat(1000)), false);
      ref(git, "refs/heads/main", id);
      var builder = new JavaHistoryCorpus(temporary.getRoot().toPath(), 512);
      var corpus = builder.read("repo", git, List.of());
      assertEquals(1, corpus.files().size());
      assertEquals(List.of("missing:library:1"), corpus.files().getFirst().quality().unresolvedDependencies());
      assertTrue(corpus.files().getFirst().quality().configurationDiagnostics().stream().anyMatch(d -> d.contains("Large.java")));
      try {
        Thread.currentThread().interrupt();
        assertThrows(java.io.InterruptedIOException.class, () -> builder.read("repo", git, List.of()));
        assertTrue(Thread.currentThread().isInterrupted());
      } finally { Thread.interrupted(); }
    }
  }

  @Test public void findsARealSourceDirectoryAfterAnEarlierSubstringMatch() throws Exception {
    for (String root : List.of("mysrc/module/src", "mytst/module/tst", "mysrc/main/java/module/src/main/java")) {
      try (var git = new InMemoryRepository(new DfsRepositoryDescription("source-boundary"))) {
        var id = commit(git, 1, Map.of(root + "/p/Caller.java", CALLER, root + "/p/Base.java", BASE), false);
        ref(git, "refs/heads/main", id);
        var corpus = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024).read("repo", git, List.of());
        var caller = corpus.files().stream().filter(f -> f.simpleClassName().equals("Caller")).findFirst().orElseThrow();
        assertEquals(root, 0, caller.quality().errorCount());
      }
    }
  }

  private ObjectId commit(Repository git, int seconds, Map<String, String> files, boolean gitlink, ObjectId... parents) throws Exception {
    try (var inserter = git.newObjectInserter()) {
      var entries = new TreeMap<String, DirCacheEntry>();
      for (var file : files.entrySet()) {
        var entry = new DirCacheEntry(file.getKey()); entry.setFileMode(FileMode.REGULAR_FILE);
        entry.setObjectId(inserter.insert(Constants.OBJ_BLOB, file.getValue().getBytes(StandardCharsets.UTF_8)));
        entries.put(file.getKey(), entry);
      }
      if (gitlink) {
        var entry = new DirCacheEntry("vendor.java"); entry.setFileMode(FileMode.GITLINK);
        entry.setObjectId(ObjectId.fromString("1111111111111111111111111111111111111111")); entries.put("vendor.java", entry);
      }
      var cache = DirCache.newInCore(); var builder = cache.builder(); entries.values().forEach(builder::add); builder.finish();
      var commit = new CommitBuilder(); commit.setTreeId(cache.writeTree(inserter)); commit.setParentIds(parents);
      var who = new PersonIdent("Author", "a@example.org", Instant.parse("2025-01-01T00:00:00Z").plusSeconds(seconds), ZoneOffset.UTC);
      commit.setAuthor(who); commit.setCommitter(who); commit.setMessage("commit " + ++sequence);
      var id = inserter.insert(commit); inserter.flush(); return id;
    }
  }

  private static void ref(Repository git, String name, ObjectId id) throws Exception {
    var update = git.updateRef(name); update.setNewObjectId(id); update.setForceUpdate(true);
    assertTrue(List.of(RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.FAST_FORWARD).contains(update.update()));
  }
}
