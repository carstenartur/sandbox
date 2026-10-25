/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.*;

/** Application rows and reports sharing one immutable released source-search generation. */
final class HistoricalSourceIndex implements Closeable {
  private final JavaFileSearchIndex index;
  private final List<AnalyzedFile> files;
  private final Map<String, AnalyzedFile> byBlob;
  private final ApplicationSourceReports reports;

  private HistoricalSourceIndex(JavaFileSearchIndex index, LinkedHashMap<String, AnalyzedFile> files) {
    this.index = index; this.files = List.copyOf(files.values()); this.byBlob = Map.copyOf(files);
    reports = new ApplicationSourceReports(this.files);
  }

  static HistoricalSourceIndex build(String canonicalName, List<AnalyzedFile> corpus) throws IOException {
    var files = new LinkedHashMap<String, AnalyzedFile>();
    for (var file : corpus) {
      if (!canonicalName.equals(file.repositoryName())) throw new IllegalArgumentException("Mixed repository source generation");
      files.putIfAbsent(file.blobObjectId(), file);
    }
    return new HistoricalSourceIndex(JavaFileSearchIndex.build(
        () -> files.values().stream().map(AnalyzedFile::searchDocument).iterator()), files);
  }

  List<AnalyzedFile> search(String canonicalName, String text, String module, String fileType,
      Map<JavaFileSearchField, Float> boosts, int offset, int limit) throws IOException {
    return index.search(new JavaFileSearchQuery(canonicalName, module, fileType, text, boosts, offset, limit))
        .stream().map(hit -> byBlob.get(hit.documentId())).toList();
  }

  AnalyzedFile byBlob(String id) { return byBlob.get(id); }
  List<AnalyzedFile> files() { return files; }
  ApplicationSourceReports reports() { return reports; }
  @Override public void close() throws IOException { index.close(); }
}
