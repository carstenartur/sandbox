/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.search;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.server.history.SandboxFileHistory.FileVersion;

/** Application search/report results, independent of ORM entities and Java parser implementation. */
public interface SandboxSourceSearch extends Closeable {
  List<SourceFile> searchByType(String repo, String query, int offset, int limit) throws IOException;
  List<SourceFile> searchByTypeWithModule(String repo, String query, String module, int offset, int limit) throws IOException;
  List<SourceFile> searchBySymbol(String repo, String query, int offset, int limit) throws IOException;
  List<SourceFile> searchByHierarchy(String repo, String query, int offset, int limit) throws IOException;
  List<SourceFile> searchByAnnotation(String repo, String query, int offset, int limit) throws IOException;
  List<SourceFile> searchSourceContent(String repo, String query, int offset, int limit) throws IOException;
  List<SourceFile> searchByDocumentation(String repo, String query, int offset, int limit) throws IOException;
  List<SourceFile> searchByReferencedType(String repo, String query, int offset, int limit) throws IOException;
  List<SourceFile> searchByStringLiteral(String repo, String query, int offset, int limit) throws IOException;
  List<SourceFile> searchFqnAcrossTypes(String repo, String query, String fileType, int offset, int limit) throws IOException;
  List<FileVersion> searchFilePath(String repo, String query, int offset, int limit) throws IOException;
  List<SourceFile> semanticSearch(String repo, String query, int limit) throws IOException;
  List<SourceFile> hybridSearch(String repo, String query, int limit) throws IOException;
  List<SourceFile> findSimilarCode(String repo, String blobId, int limit) throws IOException;
  List<SourceFile> getMigrationImpact(String repo, String prefix, int offset, int limit) throws IOException;
  Map<String, Object> getMigrationImpactSummary(String repo, String prefix) throws IOException;
  List<SourceFile> getDeprecatedApiUsage(String repo, int offset, int limit) throws IOException;
  List<SourceFile> getDeprecationTimeline(String repo, int offset, int limit) throws IOException;
  SourceDiff getApiDiff(String repo, String before, String after) throws IOException;
  SourceDiff getPublicApiChanges(String repo, String before, String after) throws IOException;
  List<AuthorTypes> getAuthorTypeStatistics(String repo) throws IOException;
  List<Complexity> getCodeComplexityTrend(String repo, String packagePrefix) throws IOException;
  List<SourceFile> getMonsterClasses(String repo, int threshold, int offset, int limit) throws IOException;
  List<SourceFile> getDeadCodeCandidates(String repo, int offset, int limit) throws IOException;
  List<TestRatio> getTestCoverageProxy(String repo) throws IOException;

  record SourceFile(String blobObjectId, String commitObjectId, String filePath, String packageName,
      String declaredTypes, String fullyQualifiedNames, String declaredMethods, String declaredFields,
      String extendsTypes, String implementsTypes, String visibility) {}
  record Change(SourceFile before, SourceFile after, String changeDescription) {}
  record SourceDiff(List<SourceFile> addedFiles, List<SourceFile> removedFiles, List<Change> changedFiles) {
    public SourceDiff {
      addedFiles = List.copyOf(addedFiles); removedFiles = List.copyOf(removedFiles); changedFiles = List.copyOf(changedFiles);
    }
  }
  record AuthorTypes(String author, String typeKind, long count) {}
  record Complexity(String commitDate, double avgLineCount) {}
  record TestRatio(String packageName, long testTypeCount, long totalTypeCount) {}
}
