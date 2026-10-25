/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MixedHistoryCorpusTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void selectsOneCanonicalBlobAcrossJavaAndOtherFormatsButKeepsBothPathsForApiDiff() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("shared-content"))) {
      String source = "package p; class Shared {}";
      var id = ApplicationApiComparisonTest.commit(git, 1, Map.of("a.txt", source, "z.java", source));
      var builder = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024, true, Set.of());
      var corpus = builder.read("repo", git, List.of(id.name()));
      assertEquals(1, corpus.files().size()); assertEquals("a.txt", corpus.files().getFirst().filePath());
      assertEquals("txt", corpus.files().getFirst().fileType());
      assertEquals(Set.of("a.txt", "z.java"), builder.atCommit("repo", git, id).files().keySet());
      var next = ApplicationApiComparisonTest.commit(git, 2, Map.of("z.java", source), id);
      var diff = new ApplicationApiComparison(builder).compare("repo", git, id, next, false);
      assertEquals(List.of("a.txt"), diff.removed().stream().map(AnalyzedFile::filePath).toList());
      assertTrue(diff.added().isEmpty()); assertTrue(diff.changed().isEmpty());
    }
  }

  @Test public void adaptsFormatFacetsAndNullMetadataAlongsideCaseInsensitiveJavaSelection() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("formats"))) {
      var id = ApplicationApiComparisonTest.commit(git, 1, Map.of(
          "plugin.xml", "<plugin><extension point=\"p.Point\"><handler class=\"p.Handler\"/></extension></plugin>",
          "pom.xml", "<project><groupId>p.group</groupId><artifactId>artifact</artifactId><modules><module>child</module></modules></project>",
          "settings.properties", "handler=p.Handler\n",
          "MANIFEST.MF", "Manifest-Version: 1.0\nBundle-SymbolicName: p.bundle\n\n",
          "Upper.JAVA", "class Upper {}", "lower.java", "class Lower {}",
          "photo.png", "p.Picture", "huge.txt", "x".repeat(2048), "binary.dat", "\0binary"));
      var builder = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024, true, Set.of(".png"));
      var all = builder.atCommit("repo", git, id).files();
      assertEquals(6, all.size());
      assertEquals("java", all.get("Upper.JAVA").fileType()); assertEquals("Upper", all.get("Upper.JAVA").facet(DECLARED_TYPES));
      assertEquals("plugin-xml", all.get("plugin.xml").fileType());
      assertEquals("p.Point", all.get("plugin.xml").facet(QUALIFIED_NAMES));
      assertEquals("p.Handler", all.get("plugin.xml").facet(DECLARED_TYPES));
      assertEquals("child", all.get("pom.xml").facet(FIELDS));
      assertEquals("p.Handler", all.get("settings.properties").facet(QUALIFIED_NAMES));
      assertEquals("p.bundle", all.get("MANIFEST.MF").facet(QUALIFIED_NAMES));
      var plugin = all.get("plugin.xml");
      assertNull(plugin.packageName()); assertNull(plugin.projectName()); assertNull(plugin.typeKind()); assertNull(plugin.visibility());
      assertEquals(0, plugin.lineCount()); assertNull(plugin.facets().get(IMPORTS));
      assertEquals(id.name(), plugin.commitObjectId()); assertEquals("Author", plugin.commitAuthor());
      assertNotNull(plugin.commitDate()); assertEquals("plugin.xml", plugin.facet(PATH));
    }
  }

  @Test public void retainsJavaOnlyAndExplicitSkipExtensionPolicies() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("selection"))) {
      var id = ApplicationApiComparisonTest.commit(git, 1, Map.of("lower.java", "class Lower {}",
          "Upper.JAVA", "class Upper {}", "text.txt", "p.Text"));
      var javaOnly = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024, false, Set.of());
      assertEquals(Set.of("lower.java"), javaOnly.atCommit("repo", git, id).files().keySet());
      var skipJava = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024, true, Set.of(".java"));
      assertEquals(Set.of("text.txt"), skipJava.atCommit("repo", git, id).files().keySet());
    }
  }

  @Test public void nonJavaOnlyHistoryChangesRetainTheOriginalJavaContextAndNewFormatRows() throws Exception {
    try (var git = new InMemoryRepository(new DfsRepositoryDescription("documentation-change"))) {
      String source = "package p; class A { String value; }";
      var first = ApplicationApiComparisonTest.commit(git, 1, Map.of("p/A.java", source));
      var next = ApplicationApiComparisonTest.commit(git, 2, Map.of("p/A.java", source, "README.txt", "p.Documentation"), first);
      var corpus = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024, true, Set.of()).read("repo", git, List.of(next.name()));
      assertEquals(List.of("p/A.java", "README.txt"), corpus.files().stream().map(AnalyzedFile::filePath).toList());
      assertEquals(first.name(), corpus.files().getFirst().commitObjectId());
      assertEquals("value", corpus.files().getFirst().facet(FIELDS));
      assertEquals("p.Documentation", corpus.files().getLast().facet(QUALIFIED_NAMES));
    }
  }
}
