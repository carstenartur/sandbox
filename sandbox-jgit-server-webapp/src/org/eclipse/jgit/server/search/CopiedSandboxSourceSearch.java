/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.search;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jgit.server.history.SandboxFileHistory.FileVersion;
import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;
import org.eclipse.jgit.storage.hibernate.search.EmbeddingService;
import org.eclipse.jgit.storage.hibernate.service.ApiDiffResult;
import org.eclipse.jgit.storage.hibernate.service.GitDatabaseQueryService;
import org.hibernate.SessionFactory;

/** Transitional delegation only; owns its optional encoder but never the native session factory. */
public final class CopiedSandboxSourceSearch implements SandboxSourceSearch {
  private final GitDatabaseQueryService queries;
  private final EmbeddingService encoder = new EmbeddingService();

  public CopiedSandboxSourceSearch(SessionFactory factory) {
    queries = new GitDatabaseQueryService(Objects.requireNonNull(factory)); queries.setEmbeddingService(encoder);
  }

  @Override public List<SourceFile> searchByType(String repo, String query, int offset, int limit) {
    return files(queries.searchByType(repo, query, offset, limit));
  }
  @Override public List<SourceFile> searchByTypeWithModule(String repo, String query, String module, int offset, int limit) {
    return files(queries.searchByTypeWithModule(repo, query, module, offset, limit));
  }
  @Override public List<SourceFile> searchBySymbol(String repo, String query, int offset, int limit) {
    return files(queries.searchBySymbol(repo, query, offset, limit));
  }
  @Override public List<SourceFile> searchByHierarchy(String repo, String query, int offset, int limit) {
    return files(queries.searchByHierarchy(repo, query, offset, limit));
  }
  @Override public List<SourceFile> searchByAnnotation(String repo, String query, int offset, int limit) {
    return files(queries.searchByAnnotation(repo, query, offset, limit));
  }
  @Override public List<SourceFile> searchSourceContent(String repo, String query, int offset, int limit) {
    return files(queries.searchSourceContent(repo, query, offset, limit));
  }
  @Override public List<SourceFile> searchByDocumentation(String repo, String query, int offset, int limit) {
    return files(queries.searchByDocumentation(repo, query, offset, limit));
  }
  @Override public List<SourceFile> searchByReferencedType(String repo, String query, int offset, int limit) {
    return files(queries.searchByReferencedType(repo, query, offset, limit));
  }
  @Override public List<SourceFile> searchByStringLiteral(String repo, String query, int offset, int limit) {
    return files(queries.searchByStringLiteral(repo, query, offset, limit));
  }
  @Override public List<SourceFile> searchFqnAcrossTypes(String repo, String query, String fileType, int offset, int limit) {
    return files(queries.searchFqnAcrossTypes(repo, query, fileType, offset, limit));
  }
  @Override public List<FileVersion> searchFilePath(String repo, String query, int offset, int limit) {
    return queries.searchFilePath(repo, query, offset, limit).stream().map(file -> new FileVersion(file.getCommitObjectId(),
        file.getFilePath(), file.getBlobObjectId(), file.getFileType(), file.getCommitTime())).toList();
  }
  @Override public List<SourceFile> semanticSearch(String repo, String query, int limit) {
    return files(queries.semanticSearch(repo, query, limit));
  }
  @Override public List<SourceFile> hybridSearch(String repo, String query, int limit) {
    return files(queries.hybridSearch(repo, query, limit));
  }
  @Override public List<SourceFile> findSimilarCode(String repo, String blobId, int limit) {
    return files(queries.findSimilarCode(repo, blobId, limit));
  }
  @Override public List<SourceFile> getMigrationImpact(String repo, String prefix, int offset, int limit) {
    return files(queries.getMigrationImpact(repo, prefix, offset, limit));
  }
  @Override public Map<String, Object> getMigrationImpactSummary(String repo, String prefix) {
    return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(queries.getMigrationImpactSummary(repo, prefix)));
  }
  @Override public List<SourceFile> getDeprecatedApiUsage(String repo, int offset, int limit) {
    return files(queries.getDeprecatedApiUsage(repo, offset, limit));
  }
  @Override public List<SourceFile> getDeprecationTimeline(String repo, int offset, int limit) {
    return files(queries.getDeprecationTimeline(repo, offset, limit));
  }
  @Override public SourceDiff getApiDiff(String repo, String before, String after) {
    return diff(queries.getApiDiff(repo, before, after));
  }
  @Override public SourceDiff getPublicApiChanges(String repo, String before, String after) {
    return diff(queries.getPublicApiChanges(repo, before, after));
  }
  @Override public List<AuthorTypes> getAuthorTypeStatistics(String repo) {
    return queries.getAuthorTypeStatistics(repo).stream().map(row -> new AuthorTypes(text(row[0]), text(row[1]), count(row[2]))).toList();
  }
  @Override public List<Complexity> getCodeComplexityTrend(String repo, String prefix) {
    return queries.getCodeComplexityTrend(repo, prefix).stream().map(row -> new Complexity(text(row[0]),
        row[1] == null ? 0 : ((Number) row[1]).doubleValue())).toList();
  }
  @Override public List<SourceFile> getMonsterClasses(String repo, int threshold, int offset, int limit) {
    return files(queries.getMonsterClasses(repo, threshold, offset, limit));
  }
  @Override public List<SourceFile> getDeadCodeCandidates(String repo, int offset, int limit) {
    return files(queries.getDeadCodeCandidates(repo, offset, limit));
  }
  @Override public List<TestRatio> getTestCoverageProxy(String repo) {
    return queries.getTestCoverageProxy(repo).stream().map(row -> new TestRatio(text(row[0]), count(row[1]), count(row[2]))).toList();
  }
  @Override public void close() { encoder.close(); }

  private static String text(Object value) { return value == null ? null : value.toString(); }
  private static long count(Object value) { return value == null ? 0 : ((Number) value).longValue(); }
  private static List<SourceFile> files(List<JavaBlobIndex> files) { return files.stream().map(CopiedSandboxSourceSearch::file).toList(); }
  private static SourceFile file(JavaBlobIndex value) {
    return value == null ? null : new SourceFile(value.getBlobObjectId(), value.getCommitObjectId(), value.getFilePath(),
        value.getPackageName(), value.getDeclaredTypes(), value.getFullyQualifiedNames(), value.getDeclaredMethods(),
        value.getDeclaredFields(), value.getExtendsTypes(), value.getImplementsTypes(), value.getVisibility());
  }
  private static SourceDiff diff(ApiDiffResult value) {
    return new SourceDiff(files(value.getAddedFiles()), files(value.getRemovedFiles()), value.getChangedFiles().stream()
        .map(change -> new Change(file(change.getBefore()), file(change.getAfter()), change.getChangeDescription())).toList());
  }
}
