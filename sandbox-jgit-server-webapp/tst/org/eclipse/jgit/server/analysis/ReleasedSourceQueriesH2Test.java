/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.history.SandboxHistoryMaintenance;
import org.eclipse.jgit.server.repository.ExternalHibernateRepositoryService;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import io.github.carstenartur.jgit.storage.hibernate.DefaultHibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import io.github.carstenartur.jgit.storage.hibernate.search.SearchEntities;

public class ReleasedSourceQueriesH2Test {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private HibernateSessionFactoryProvider provider;
  private ExternalHibernateRepositoryService repositories;
  private AnalysisGenerations generations;
  private HistoricalAnalysis analysis;
  private ReleasedSandboxSourceSearch search;
  private Repository writer;
  private ObjectId first;

  @Before public void start() throws Exception {
    var properties = new Properties();
    properties.setProperty("hibernate.connection.url", "jdbc:h2:mem:source-queries-" + UUID.randomUUID());
    properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
    properties.setProperty("hibernate.search.backend.type", "lucene");
    properties.setProperty("hibernate.search.backend.directory.type", "local-heap");
    provider = new HibernateSessionFactoryProvider(properties, SearchEntities.annotatedClasses());
    repositories = new ExternalHibernateRepositoryService(new DefaultHibernateRepositoryFactory(provider.getSessionFactory()),
        SandboxHistoryMaintenance.NONE, provider.getSessionFactory());
    generations = new AnalysisGenerations(4, 1);
    analysis = new HistoricalAnalysis(provider.getSessionFactory(), repositories, generations,
        new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024, true, Set.of()));
    search = new ReleasedSandboxSourceSearch(analysis, true, new NoSemanticQueries());
    writer = repositories.openOrCreate("example.git.git");
    String source = "package p; import java.util.List; /** Helpful documentation. */ @Deprecated public class Cache implements Runnable { "
        + "public List<String> names; public void run(){ System.out.println(\"literalneedle\"); } }";
    first = ApplicationApiComparisonTest.commit(writer, 1, Map.of("alpha/src/p/Cache.java", source,
        "beta/src/p/Cache.java", source.replace("Helpful", "Other").replace("literalneedle", "different"),
        "plugin.xml", "<plugin><extension point=\"p.Cache\" class=\"p.Cache\"/></plugin>", "readme.txt", "ordinary text"));
    var ref = writer.updateRef("refs/heads/main"); ref.setNewObjectId(first); ref.update();
  }

  @After public void stop() throws Exception {
    if (generations != null) generations.close();
    if (repositories != null) repositories.close();
    if (provider != null) provider.close();
  }

  @Test public void mapsEveryTextFacetAndFiltersBeforePagingUsingTheRawRepositoryIdentity() throws Exception {
    assertEquals(2, search.searchByType("example.git.git", "Cache", 0, 10).size());
    assertEquals("beta/src/p/Cache.java", search.searchByTypeWithModule("example.git.git", "Cache", "beta", 0, 1).getFirst().filePath());
    assertEquals(2, search.searchBySymbol("example.git.git", "run", 0, 10).size());
    assertEquals(2, search.searchByHierarchy("example.git.git", "p.Runnable", 0, 10).size());
    assertTrue(search.searchByHierarchy("example.git.git", "Runnable", 0, 10).isEmpty());
    assertEquals(2, search.searchByAnnotation("example.git.git", "Deprecated", 0, 10).size());
    assertEquals("alpha/src/p/Cache.java", search.searchByDocumentation("example.git.git", "Helpful", 0, 10).getFirst().filePath());
    // The retained qualified-name analysis matches the complete legacy newline-delimited value.
    String references = analysis.sources("example.git.git", index -> index.files().stream()
        .filter(file -> file.filePath().equals("alpha/src/p/Cache.java")).findFirst().orElseThrow()
        .facet(io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.REFERENCED_TYPES));
    assertFalse(references.isBlank());
    assertEquals(2, search.searchByReferencedType("example.git.git", references, 0, 10).size());
    var literal = search.searchByStringLiteral("example.git.git", "literalneedle", 0, 10).getFirst();
    assertEquals(literal, search.searchSourceContent("example.git.git", "literalneedle", 0, 10).getFirst());
    assertEquals("plugin.xml", search.searchFqnAcrossTypes("example.git.git", "p.Cache", "plugin-xml", 0, 10).getFirst().filePath());
    assertEquals("readme.txt", search.searchFilePath("example.git.git", "readme", 0, 10).getFirst().filePath());
    assertEquals(first.name(), literal.commitObjectId()); assertEquals("p", literal.packageName());
    assertEquals("public", literal.visibility()); assertTrue(literal.declaredMethods().contains("run"));
    assertTrue(search.searchByType("example.git.git", "Cache", 2, 10).isEmpty());
    assertTrue(search.searchByType("example.git", "Cache", 0, 10).isEmpty());
    assertTrue(search.searchByType("missing", "Cache", 0, 10).isEmpty());
    assertThrows(java.io.FileNotFoundException.class, () -> repositories.withExistingRepository("missing", ignored -> null));
  }

  @Test public void mapsReportsAndWholeTreeApiResultsWithoutLeakingTheirGeneration() throws Exception {
    String repo = "example.git.git";
    assertEquals(2, search.getMigrationImpact(repo, "java.util", 0, 10).size());
    assertEquals(2, search.getMigrationImpactSummary(repo, "java.util").get("totalFiles"));
    assertEquals(2, search.getDeprecatedApiUsage(repo, 0, 10).size());
    assertEquals(2, search.getDeprecationTimeline(repo, 0, 10).size());
    assertEquals(4, search.getAuthorTypeStatistics(repo).stream().mapToLong(row -> row.count()).sum());
    assertFalse(search.getCodeComplexityTrend(repo, "p").isEmpty());
    assertEquals(2, search.getMonsterClasses(repo, 1, 0, 10).size());
    assertFalse(search.getDeadCodeCandidates(repo, 0, 10).isEmpty());
    assertEquals(4, search.getTestCoverageProxy(repo).stream().mapToLong(row -> row.totalTypeCount()).sum());
    var after = ApplicationApiComparisonTest.commit(writer, 2, Map.of("New.java", "public class New {}"), first);
    var diff = search.getApiDiff(repo, first.name(), after.name());
    assertEquals(4, diff.removedFiles().size()); assertEquals("New.java", diff.addedFiles().getFirst().filePath());
    assertEquals(2, search.getPublicApiChanges(repo, first.name(), after.name()).removedFiles().size());
    assertTrue(search.getApiDiff("missing", first.name(), after.name()).addedFiles().isEmpty());
    assertNull(search.getMigrationImpactSummary("missing", "java").get("earliestDate"));
    generations.refresh();
    assertEquals(first.name(), diff.removedFiles().getFirst().commitObjectId());
    search.close(); // The context owns generations, repositories and the model.
    assertFalse(provider.getSessionFactory().isClosed());
    assertEquals(2, search.searchByType(repo, "Cache", 0, 10).size());
    assertTrue(new ReleasedSandboxSourceSearch(analysis, false, new NoSemanticQueries()).searchFilePath(repo, "readme", 0, 10).isEmpty());
  }

  @Test public void publishedReleasePreservesBlankAndPaddedGitPathsAcrossBothGenerations() throws Exception {
    ObjectId next;
    try (var inserter = writer.newObjectInserter()) {
      var tree = new org.eclipse.jgit.lib.TreeFormatter();
      tree.append(" ", org.eclipse.jgit.lib.FileMode.REGULAR_FILE, inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB,
          "blankpathnamepayload".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      tree.append(" padded.txt ", org.eclipse.jgit.lib.FileMode.REGULAR_FILE, inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB,
          "paddedpathpayload".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      var commit = new org.eclipse.jgit.lib.CommitBuilder();
      commit.setTreeId(inserter.insert(tree)); commit.setParentId(first);
      commit.setAuthor(new org.eclipse.jgit.lib.PersonIdent("Author", "a@example.test")); commit.setCommitter(commit.getAuthor());
      commit.setMessage("Unusual paths"); next = inserter.insert(commit); inserter.flush();
    }
    var ref = writer.updateRef("refs/heads/main"); ref.setNewObjectId(next); ref.update();
    assertEquals(" ", search.searchSourceContent("example.git.git", "blankpathnamepayload", 0, 10).getFirst().filePath());
    assertEquals(" padded.txt ", search.searchFilePath("example.git.git", "padded", 0, 10).getFirst().filePath());
  }

  @Test public void malformedHistoricalPropertiesCannotHideHealthySourceAndReports() throws Exception {
    poisonHistory("bad.properties", "brokenpropertyliteral=\\uZZZZ", "brokenpropertyliteral");
  }

  @Test public void oversizedQualifiedFacetsAndTokensCannotHideHealthySourceAndReports() throws Exception {
    poisonHistory("README.txt", "p.A\n".repeat(9000) + "oversizedfacetmarker", "oversizedfacetmarker");
    poisonHistory("huge-token.txt", "q".repeat(40_000), null);
  }

  private void poisonHistory(String path, String content, String retainedText) throws Exception {
    String repo = "poison-" + UUID.randomUUID();
    var git = repositories.openOrCreate(repo);
    var healthy = ApplicationApiComparisonTest.commit(git, 1, Map.of("Healthy.java", "public class Healthy {}"));
    var ref = git.updateRef("refs/heads/main"); ref.setNewObjectId(healthy); ref.update();
    assertEquals(1, search.searchByType(repo, "Healthy", 0, 10).size());
    var bad = ApplicationApiComparisonTest.commit(git, 2, Map.of("Healthy.java", "public class Healthy {}", path, content), healthy);
    ref = git.updateRef("refs/heads/main"); ref.setNewObjectId(bad); ref.update();
    assertEquals(1, search.searchByType(repo, "Healthy", 0, 10).size());
    var removed = ApplicationApiComparisonTest.commit(git, 3, Map.of("Healthy.java", "public class Healthy {}"), bad);
    ref = git.updateRef("refs/heads/main"); ref.setNewObjectId(removed); ref.update();
    assertEquals(1, search.searchByType(repo, "Healthy", 0, 10).size());
    assertEquals(2, search.getAuthorTypeStatistics(repo).stream().mapToLong(row -> row.count()).sum());
    if (retainedText != null) assertEquals(path, search.searchSourceContent(repo, retainedText, 0, 10).getFirst().filePath());
  }

  private static final class NoSemanticQueries implements ReleasedSandboxSourceSearch.SemanticQueries {
    @Override public List<AnalyzedFile> semantic(HistoricalSourceIndex index, String query, int limit) throws IOException { throw new AssertionError("Text/report queries must not initialize semantic work"); }
    @Override public List<AnalyzedFile> hybrid(HistoricalSourceIndex index, String query, int limit) throws IOException { throw new AssertionError(); }
    @Override public List<AnalyzedFile> similar(HistoricalSourceIndex index, String blob, int limit) throws IOException { throw new AssertionError(); }
  }
}
