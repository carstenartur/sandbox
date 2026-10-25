/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jgit.server.history.GitHistorySnapshot;
import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.SOURCE;

/** Optional application rankings, evaluated inside an existing source-generation pin. */
final class ApplicationSemanticQueries implements ReleasedSandboxSourceSearch.SemanticQueries {
  private final EmbeddingEncoder encoder;
  ApplicationSemanticQueries(EmbeddingEncoder encoder) { this.encoder = Objects.requireNonNull(encoder); }

  @Override public List<AnalyzedFile> semantic(HistoricalSourceIndex index, String query, int limit) throws IOException {
    GitHistorySnapshot.checkInterrupted();
    if (unavailable()) return List.of();
    float[] vector = encoder.embed(query);
    GitHistorySnapshot.checkInterrupted();
    if (vector == null) return List.of();
    return index.vectors(encoder).nearest(vector, limit).stream().map(hit -> index.byBlob(hit.id())).toList();
  }

  @Override public List<AnalyzedFile> hybrid(HistoricalSourceIndex index, String query, int limit) throws IOException {
    var text = index.search(index.repositoryName(), query, null, null, Map.of(SOURCE, 1f), 0, limit);
    var semantic = semantic(index, query, limit);
    return semantic.isEmpty() ? text : ApplicationVectorIndex.fuse(semantic, text, limit, AnalyzedFile::blobObjectId);
  }

  @Override public List<AnalyzedFile> similar(HistoricalSourceIndex index, String blob, int limit) throws IOException {
    GitHistorySnapshot.checkInterrupted();
    if (index.byBlob(blob) == null) return List.of();
    return index.vectors(encoder).similar(blob, limit).stream().map(hit -> index.byBlob(hit.id())).toList();
  }

  private boolean unavailable() {
    return switch (encoder.status()) {
      case DISABLED, UNAVAILABLE, CLOSED -> true;
      default -> false;
    };
  }
}
