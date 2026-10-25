/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ApplicationSemanticQueriesTest {
  @Test public void buildsVectorsLazilyOnceAndReusesThemForSimilarityWithoutEncoding() throws Exception {
    var encoder = new FakeEncoder();
    var queries = new ApplicationSemanticQueries(encoder);
    try (var index = HistoricalSourceIndex.build("repo", List.of(file("a", "Alpha"), file("b", "Beta")))) {
      assertEquals(1, index.search("repo", "Alpha", null, null, Map.of(SOURCE, 1f), 0, 10).size());
      assertTrue(encoder.inputs.isEmpty());
      assertEquals("a", queries.semantic(index, "question", 1).getFirst().blobObjectId());
      assertEquals(List.of("question", "Alpha: docs\nMethods: void go()\nPackage: p",
          "Beta: docs\nMethods: void go()\nPackage: p"), encoder.inputs);
      encoder.available = false;
      assertEquals(List.of("b"), queries.similar(index, "a", 10).stream().map(AnalyzedFile::blobObjectId).toList());
      assertEquals(3, encoder.inputs.size());
      assertTrue(queries.similar(index, "missing", 10).isEmpty());
      assertEquals("b", queries.hybrid(index, "Beta", 10).getFirst().blobObjectId());
      assertFalse(encoder.closed);
    }
  }

  @Test public void disabledSemanticQueriesRetainFulltextAndDoNotEncodeTheCorpus() throws Exception {
    var encoder = new FakeEncoder(); encoder.available = false;
    var queries = new ApplicationSemanticQueries(encoder);
    try (var index = HistoricalSourceIndex.build("repo", List.of(file("a", "Alpha")))) {
      assertTrue(queries.semantic(index, "Alpha", 10).isEmpty());
      assertEquals("a", queries.hybrid(index, "Alpha", 10).getFirst().blobObjectId());
      assertTrue(queries.similar(index, "a", 10).isEmpty());
      assertTrue(encoder.inputs.isEmpty());
    }
  }

  @Test public void vectorsBelongToTheirOwnGenerationAndInterruptedBuildsAreNotPublished() throws Exception {
    var encoder = new FakeEncoder(); var queries = new ApplicationSemanticQueries(encoder);
    try (var first = HistoricalSourceIndex.build("repo", List.of(file("a", "Alpha")));
         var second = HistoricalSourceIndex.build("other", List.of(new AnalyzedFile("other", "commit", "b", "B.java", "java",
             null, "p", "Beta", "class", "public", 1, false, "docs", "author", null, Map.of(SOURCE, "Beta"), null)))) {
      Thread.currentThread().interrupt();
      try { assertThrows(java.io.InterruptedIOException.class, () -> queries.similar(first, "a", 10)); }
      finally { Thread.interrupted(); }
      assertEquals("a", queries.semantic(first, "question", 10).getFirst().blobObjectId());
      assertEquals(List.of("b"), queries.semantic(second, "question", 10).stream().map(AnalyzedFile::blobObjectId).toList());
    }
  }

  private static AnalyzedFile file(String id, String name) {
    return new AnalyzedFile("repo", "commit", id, name + ".java", "java", null, "p", name, "class", "public", 1,
        false, "docs", "author", null, Map.of(SOURCE, name, METHOD_SIGNATURES, "void go()"), null);
  }
  private static final class FakeEncoder implements EmbeddingEncoder {
    final List<String> inputs = new ArrayList<>(); boolean available = true; boolean closed;
    @Override public Status status() { return available ? Status.AVAILABLE : Status.DISABLED; }
    @Override public float[] embed(String text) {
      if (!available) return null;
      inputs.add(text); var vector = new float[DIMENSIONS]; vector[0] = text.startsWith("Beta") ? -1 : 1; return vector;
    }
    @Override public void close() { closed = true; }
  }
}
