/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.*;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.server.history.GitHistorySnapshot;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HistoricalSourceIndexTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void preservesCanonicalBlobRowsAndFiltersBeforePagingThroughTheReleasedIndex() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("sources"))) {
      String original = "package p; public class Cache { public void clearCache(){} }";
      var a = ApplicationApiComparisonTest.commit(git, 1, Map.of("alpha/src/p/Cache.java", original,
          "duplicate/src/p/Cache.java", original));
      var b = ApplicationApiComparisonTest.commit(git, 2, Map.of("alpha/src/p/Cache.java", original,
          "beta/src/p/Cache.java", "package p; public class Cache { int field; public void clearCache(){} }"), a);
      var corpus = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024).read("repo", git, List.of(b.name()));
      var index = HistoricalSourceIndex.build("repo", corpus.files());
      try (index) {
        var all = index.search("repo", "Cache", null, null, Map.of(DECLARED_TYPES, 1.5f, QUALIFIED_NAMES, 2f), 0, 20);
        assertEquals(2, all.size());
        var alpha = all.stream().filter(f -> "alpha".equals(f.projectName())).findFirst().orElseThrow();
        assertEquals(a.name(), alpha.commitObjectId()); assertEquals("alpha/src/p/Cache.java", alpha.filePath());
        assertSame(alpha, index.byBlob(alpha.blobObjectId()));
        assertNull(index.byBlob("missing"));
        assertEquals("beta", index.search("repo", "Cache", "beta", "java", Map.of(DECLARED_TYPES, 1f), 0, 1)
            .getFirst().projectName());
        assertTrue(index.search("repo", "Cache", "duplicate", null, Map.of(DECLARED_TYPES, 1f), 0, 1).isEmpty());
        assertTrue(index.search("other", "Cache", null, null, Map.of(DECLARED_TYPES, 1f), 0, 20).isEmpty());
        assertTrue(index.search("repo", "Cache", null, "xml", Map.of(DECLARED_TYPES, 1f), 0, 20).isEmpty());
        assertEquals(2, index.search("repo", "clearCache", null, null, Map.of(METHODS, 1.5f, FIELDS, 1f), 0, 20).size());
        assertEquals(2, index.reports().authors().getFirst().count());
        assertThrows(UnsupportedOperationException.class, () -> all.clear());
      }
      assertThrows(IllegalStateException.class,
          () -> index.search("repo", "Cache", null, null, Map.of(DECLARED_TYPES, 1f), 0, 20));
      assertThrows(IllegalArgumentException.class, () -> HistoricalSourceIndex.build("other", corpus.files()));
    }
  }

  @Test public void corpusBuildUsesTheCapturedGenerationSnapshotWhenRefsMove() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("snapshot"))) {
      var a = ApplicationApiComparisonTest.commit(git, 1, Map.of("A.java", "class A {}"));
      var captured = GitHistorySnapshot.read(git, List.of(a.name()));
      var b = ApplicationApiComparisonTest.commit(git, 2, Map.of("B.java", "class B {}"), a);
      var update = git.updateRef("refs/heads/main"); update.setNewObjectId(b); update.update();
      var builder = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024);
      assertEquals(List.of("A.java"), builder.readSnapshot("repo", git, captured).files().stream().map(AnalyzedFile::filePath).toList());
      assertEquals(2, builder.read("repo", git, List.of()).files().size());
    }
  }
}
