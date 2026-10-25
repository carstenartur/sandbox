/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jgit.server.history.SandboxFileHistory.FileVersion;
import org.eclipse.jgit.server.search.SandboxSourceSearch;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField;

/** Released analysis mapped to the established application contract while each generation is pinned. */
public final class ReleasedSandboxSourceSearch implements SandboxSourceSearch {
  interface SemanticQueries {
    List<AnalyzedFile> semantic(HistoricalSourceIndex index, String query, int limit) throws IOException;
    List<AnalyzedFile> hybrid(HistoricalSourceIndex index, String query, int limit) throws IOException;
    List<AnalyzedFile> similar(HistoricalSourceIndex index, String blob, int limit) throws IOException;
  }
  private static final SourceDiff EMPTY_DIFF = new SourceDiff(List.of(), List.of(), List.of());
  private final HistoricalAnalysis analysis;
  private final boolean pathHistory;
  private final SemanticQueries semantics;

  ReleasedSandboxSourceSearch(HistoricalAnalysis analysis, boolean pathHistory, SemanticQueries semantics) {
    this.analysis = Objects.requireNonNull(analysis); this.pathHistory = pathHistory;
    this.semantics = Objects.requireNonNull(semantics);
  }

  @Override public List<SourceFile> searchByType(String repo, String query, int offset, int limit) throws IOException {
    return text(repo, query, null, null, Map.of(QUALIFIED_NAMES, 2f, DECLARED_TYPES, 1.5f), offset, limit);
  }
  @Override public List<SourceFile> searchByTypeWithModule(String repo, String query, String module, int offset, int limit) throws IOException {
    return text(repo, query, module, null, Map.of(SIMPLE_CLASS_NAME, 1f, DECLARED_TYPES, 1f, QUALIFIED_NAMES, 1f), offset, limit);
  }
  @Override public List<SourceFile> searchBySymbol(String repo, String query, int offset, int limit) throws IOException {
    return text(repo, query, null, null, Map.of(METHODS, 1.5f, FIELDS, 1f), offset, limit);
  }
  @Override public List<SourceFile> searchByHierarchy(String repo, String query, int offset, int limit) throws IOException {
    return text(repo, query, null, null, Map.of(EXTENDED_TYPES, 1f, IMPLEMENTED_TYPES, 1f), offset, limit);
  }
  @Override public List<SourceFile> searchByAnnotation(String repo, String query, int offset, int limit) throws IOException {
    return text(repo, query, null, null, Map.of(ANNOTATIONS, 1f), offset, limit);
  }
  @Override public List<SourceFile> searchSourceContent(String repo, String query, int offset, int limit) throws IOException {
    return text(repo, query, null, null, Map.of(SOURCE, 1f), offset, limit);
  }
  @Override public List<SourceFile> searchByDocumentation(String repo, String query, int offset, int limit) throws IOException {
    return text(repo, query, null, null, Map.of(DOCUMENTATION, 1f), offset, limit);
  }
  @Override public List<SourceFile> searchByReferencedType(String repo, String query, int offset, int limit) throws IOException {
    return text(repo, query, null, null, Map.of(REFERENCED_TYPES, 1f), offset, limit);
  }
  @Override public List<SourceFile> searchByStringLiteral(String repo, String query, int offset, int limit) throws IOException {
    return text(repo, query, null, null, Map.of(STRING_LITERALS, 1f), offset, limit);
  }
  @Override public List<SourceFile> searchFqnAcrossTypes(String repo, String query, String fileType, int offset, int limit) throws IOException {
    return text(repo, query, null, fileType, Map.of(QUALIFIED_NAMES, 1f), offset, limit);
  }
  private List<SourceFile> text(String repo, String query, String module, String fileType,
      Map<JavaFileSearchField, Float> boosts, int offset, int limit) throws IOException {
    page(offset, limit);
    if (blank(query)) return List.of();
    return sources(repo, List.of(), index -> files(index.search(index.repositoryName(), query, module, fileType, boosts, offset, limit)));
  }

  @Override public List<FileVersion> searchFilePath(String repo, String query, int offset, int limit) throws IOException {
    page(offset, limit);
    if (!pathHistory || blank(repo) || blank(query)) return List.of();
    try { return analysis.paths(repo, index -> index.search(index.repositoryName(), query, offset, limit)); }
    catch (FileNotFoundException missing) { return List.of(); }
  }
  @Override public List<SourceFile> semanticSearch(String repo, String query, int limit) throws IOException {
    page(0, limit);
    if (blank(query)) return List.of();
    return sources(repo, List.of(), index -> files(semantics.semantic(index, query, limit)));
  }
  @Override public List<SourceFile> hybridSearch(String repo, String query, int limit) throws IOException {
    page(0, limit);
    if (blank(query)) return List.of();
    return sources(repo, List.of(), index -> files(semantics.hybrid(index, query, limit)));
  }
  @Override public List<SourceFile> findSimilarCode(String repo, String blob, int limit) throws IOException {
    page(0, limit);
    if (blank(blob)) return List.of();
    return sources(repo, List.of(), index -> files(semantics.similar(index, blob, limit)));
  }
  @Override public List<SourceFile> getMigrationImpact(String repo, String prefix, int offset, int limit) throws IOException {
    page(offset, limit); return sources(repo, List.of(), index -> files(index.reports().migration(prefix, offset, limit)));
  }
  @Override public Map<String, Object> getMigrationImpactSummary(String repo, String prefix) throws IOException {
    return sources(repo, new ApplicationSourceReports(List.of()).migrationSummary(prefix), index -> index.reports().migrationSummary(prefix));
  }
  @Override public List<SourceFile> getDeprecatedApiUsage(String repo, int offset, int limit) throws IOException {
    page(offset, limit); return sources(repo, List.of(), index -> files(index.reports().deprecated(false, offset, limit)));
  }
  @Override public List<SourceFile> getDeprecationTimeline(String repo, int offset, int limit) throws IOException {
    page(offset, limit); return sources(repo, List.of(), index -> files(index.reports().deprecated(true, offset, limit)));
  }
  @Override public SourceDiff getApiDiff(String repo, String before, String after) throws IOException { return api(repo, before, after, false); }
  @Override public SourceDiff getPublicApiChanges(String repo, String before, String after) throws IOException { return api(repo, before, after, true); }
  private SourceDiff api(String repo, String before, String after, boolean publicOnly) throws IOException {
    if (blank(repo)) return EMPTY_DIFF;
    try { return analysis.api(repo, before, after, publicOnly, diff -> new SourceDiff(files(diff.added()), files(diff.removed()),
        diff.changed().stream().map(change -> new Change(file(change.before()), file(change.after()), change.description())).toList())); }
    catch (FileNotFoundException missing) { return EMPTY_DIFF; }
  }
  @Override public List<AuthorTypes> getAuthorTypeStatistics(String repo) throws IOException {
    return sources(repo, List.of(), index -> index.reports().authors().stream().map(row -> new AuthorTypes(row.author(), row.kind(), row.count())).toList());
  }
  @Override public List<Complexity> getCodeComplexityTrend(String repo, String prefix) throws IOException {
    return sources(repo, List.of(), index -> index.reports().complexity(prefix).stream().map(row -> new Complexity(row.date(), row.average())).toList());
  }
  @Override public List<SourceFile> getMonsterClasses(String repo, int threshold, int offset, int limit) throws IOException {
    page(offset, limit); return sources(repo, List.of(), index -> files(index.reports().monsters(threshold, offset, limit)));
  }
  @Override public List<SourceFile> getDeadCodeCandidates(String repo, int offset, int limit) throws IOException {
    page(offset, limit); return sources(repo, List.of(), index -> files(index.reports().dead(offset, limit)));
  }
  @Override public List<TestRatio> getTestCoverageProxy(String repo) throws IOException {
    return sources(repo, List.of(), index -> index.reports().tests().stream().map(row -> new TestRatio(row.packageName(), row.testCount(), row.totalCount())).toList());
  }

  private <T> T sources(String repo, T empty, AnalysisGenerationCache.Reader<HistoricalSourceIndex, T> read) throws IOException {
    if (blank(repo)) return empty;
    try { return analysis.sources(repo, read); } catch (FileNotFoundException missing) { return empty; }
  }
  static List<SourceFile> files(List<AnalyzedFile> values) { return values.stream().map(ReleasedSandboxSourceSearch::file).toList(); }
  private static SourceFile file(AnalyzedFile value) {
    return new SourceFile(value.blobObjectId(), value.commitObjectId(), value.filePath(), value.packageName(),
        value.facets().get(DECLARED_TYPES), value.facets().get(QUALIFIED_NAMES), value.facets().get(METHODS),
        value.facets().get(FIELDS), value.facets().get(EXTENDED_TYPES), value.facets().get(IMPLEMENTED_TYPES), value.visibility());
  }
  private static boolean blank(String value) { return value == null || value.isBlank(); }
  private static void page(int offset, int limit) {
    if (offset < 0 || limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid source-search page");
  }
  /** The persistence context closes generations and the optional encoder before repositories. */
  @Override public void close() { /* Borrowed context resources. */ }
}
