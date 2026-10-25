/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.EnumMap;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.*;

/** Application rows and reports sharing one immutable released source-search generation. */
final class HistoricalSourceIndex implements Closeable {
  private static final System.Logger LOG = System.getLogger(HistoricalSourceIndex.class.getName());
  private final JavaFileSearchIndex index;
  private final String repositoryName;
  private final List<AnalyzedFile> files;
  private final Map<String, AnalyzedFile> byBlob;
  private final ApplicationSourceReports reports;
  private ApplicationVectorIndex vectors;

  private HistoricalSourceIndex(String repositoryName, JavaFileSearchIndex index, LinkedHashMap<String, AnalyzedFile> files) {
    this.repositoryName = repositoryName;
    this.index = index; this.files = List.copyOf(files.values()); this.byBlob = Map.copyOf(files);
    reports = new ApplicationSourceReports(this.files);
  }

  static HistoricalSourceIndex build(String canonicalName, List<AnalyzedFile> corpus) throws IOException {
    var files = new LinkedHashMap<String, AnalyzedFile>();
    for (var file : corpus) {
      if (!canonicalName.equals(file.repositoryName())) throw new IllegalArgumentException("Mixed repository source generation");
      files.putIfAbsent(file.blobObjectId(), file);
    }
    var documents = new ArrayList<JavaFileSearchDocument>();
    for (var file : files.values()) {
      org.eclipse.jgit.server.history.GitHistorySnapshot.checkInterrupted();
      var document = searchableDocument(file);
      if (document != null) documents.add(document);
    }
    JavaFileSearchIndex index;
    try {
      index = JavaFileSearchIndex.build(documents);
    } catch (IllegalArgumentException invalidToken) {
      // The released analyzer can discover an invalid term only while indexing. Probe
      // documents through that same public API on this exceptional path; do not copy
      // its analyzers, truncate identifiers, or lose the complete report corpus.
      var accepted = new ArrayList<JavaFileSearchDocument>();
      for (var document : documents) {
        org.eclipse.jgit.server.history.GitHistorySnapshot.checkInterrupted();
        try (var probe = JavaFileSearchIndex.build(List.of(document))) {
          accepted.add(document);
        } catch (IllegalArgumentException invalidDocument) {
          LOG.log(System.Logger.Level.WARNING, "Unsearchable Git blob {0}; retained in reports", document.blobId());
        }
      }
      index = JavaFileSearchIndex.build(accepted);
    }
    return new HistoricalSourceIndex(canonicalName, index, files);
  }

  private static JavaFileSearchDocument searchableDocument(AnalyzedFile file) {
    try {
      return file.searchDocument();
    } catch (IllegalArgumentException invalidField) {
      try {
        document(file, Map.of());
      } catch (IllegalArgumentException invalidMetadata) {
        LOG.log(System.Logger.Level.WARNING, "Unsearchable metadata in Git blob {0}; retained in reports", file.blobObjectId());
        return null;
      }
      var fields = new EnumMap<JavaFileSearchField, String>(JavaFileSearchField.class);
      for (var entry : file.facets().entrySet()) {
        try {
          document(file, Map.of(entry.getKey(), entry.getValue()));
          fields.put(entry.getKey(), entry.getValue());
        } catch (IllegalArgumentException invalidFacet) {
          LOG.log(System.Logger.Level.WARNING, "Unsearchable field {0} in Git blob {1}; other fields retained",
              entry.getKey(), file.blobObjectId());
        }
      }
      return document(file, fields);
    }
  }

  private static JavaFileSearchDocument document(AnalyzedFile file, Map<JavaFileSearchField, String> fields) {
    return new JavaFileSearchDocument(file.repositoryName(), file.blobObjectId(), file.commitObjectId(),
        file.blobObjectId(), file.filePath(), file.projectName(), file.fileType(), fields);
  }

  List<AnalyzedFile> search(String canonicalName, String text, String module, String fileType,
      Map<JavaFileSearchField, Float> boosts, int offset, int limit) throws IOException {
    boolean exactModule = module != null && !module.isEmpty() && module.isBlank();
    boolean exactType = fileType != null && !fileType.isEmpty() && fileType.isBlank();
    if (exactModule || exactType) {
      // The public index treats blank filters as absent; Sandbox historically distinguishes
      // an empty parameter from a nonempty whitespace value. Retain that exact application
      // contract through bounded public pages, applying pagination after metadata matching.
      var result = new java.util.ArrayList<AnalyzedFile>();
      int position = 0; long matched = 0;
      for (;;) {
        org.eclipse.jgit.server.history.GitHistorySnapshot.checkInterrupted();
        var page = index.search(new JavaFileSearchQuery(canonicalName, exactModule ? null : module,
            exactType ? null : fileType, text, boosts, position, 128));
        for (var hit : page) {
          var file = byBlob.get(hit.documentId());
          if (exactModule && !module.equals(file.projectName()) || exactType && !fileType.equals(file.fileType())) continue;
          if (matched++ < offset) continue;
          result.add(file);
          if (result.size() == limit) return List.copyOf(result);
        }
        if (page.size() < 128) return List.copyOf(result);
        position = Math.addExact(position, page.size());
      }
    }
    return index.search(new JavaFileSearchQuery(canonicalName, module, fileType, text, boosts, offset, limit))
        .stream().map(hit -> byBlob.get(hit.documentId())).toList();
  }

  AnalyzedFile byBlob(String id) { return byBlob.get(id); }
  /** The generation owns vectors; the context owns the encoder and waits for this reader. */
  synchronized ApplicationVectorIndex vectors(EmbeddingEncoder encoder) throws IOException {
    if (vectors != null) return vectors;
    var values = new LinkedHashMap<String, float[]>();
    for (var file : files) {
      org.eclipse.jgit.server.history.GitHistorySnapshot.checkInterrupted();
      if (encoder.status() == EmbeddingEncoder.Status.DISABLED || encoder.status() == EmbeddingEncoder.Status.UNAVAILABLE
          || encoder.status() == EmbeddingEncoder.Status.CLOSED) break;
      if (!"java".equals(file.fileType())) continue;
      String text = LocalEmbeddingEncoder.inputText(file.simpleClassName(), file.primaryDocumentation(),
          file.facet(JavaFileSearchField.METHOD_SIGNATURES), file.packageName());
      if (text.isBlank()) continue;
      float[] vector = encoder.embed(text);
      org.eclipse.jgit.server.history.GitHistorySnapshot.checkInterrupted();
      if (vector != null) values.put(file.blobObjectId(), vector);
    }
    vectors = ApplicationVectorIndex.build(values);
    return vectors;
  }
  String repositoryName() { return repositoryName; }
  List<AnalyzedFile> files() { return files; }
  ApplicationSourceReports reports() { return reports; }
  @Override public void close() throws IOException { index.close(); }
}
