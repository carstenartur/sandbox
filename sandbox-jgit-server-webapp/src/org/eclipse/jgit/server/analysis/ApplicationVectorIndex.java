/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Function;

/** Application-owned optional cosine ranking over immutable, repository-scoped document vectors. */
public final class ApplicationVectorIndex {
  public record Hit(String id, double score) {}
  private static final Comparator<Hit> BEST_FIRST = Comparator.comparingDouble(Hit::score).reversed().thenComparing(Hit::id);
  private final Map<String, float[]> vectors;
  private final int rejected;

  private ApplicationVectorIndex(Map<String, float[]> vectors, int rejected) {
    this.vectors = Map.copyOf(vectors); this.rejected = rejected;
  }

  /** Copies and normalizes inputs. Invalid dimensions, zero vectors and nonfinite values are omitted. */
  public static ApplicationVectorIndex build(Map<String, float[]> inputs) {
    var vectors = new LinkedHashMap<String, float[]>();
    int rejected = 0;
    for (var entry : inputs.entrySet()) {
      Objects.requireNonNull(entry.getKey(), "document identity");
      var normalized = normalize(entry.getValue());
      if (normalized == null) rejected++;
      else vectors.put(entry.getKey(), normalized);
    }
    return new ApplicationVectorIndex(vectors, rejected);
  }

  public int size() { return vectors.size(); }
  public int rejectedVectors() { return rejected; }

  public List<Hit> nearest(float[] query, int limit) { return nearestNormalized(normalize(query), limit, null); }

  /** Similarity remains available from existing vectors when the encoder is disabled or unavailable. */
  public List<Hit> similar(String documentId, int limit) {
    return nearestNormalized(vectors.get(documentId), limit, documentId);
  }

  private List<Hit> nearestNormalized(float[] query, int limit, String excluded) {
    validateLimit(limit);
    if (query == null) return List.of();
    var best = new PriorityQueue<Hit>(Math.min(limit, Math.max(1, vectors.size())), BEST_FIRST.reversed());
    for (var entry : vectors.entrySet()) {
      if (entry.getKey().equals(excluded)) continue;
      double dot = 0;
      for (int i = 0; i < query.length; i++) dot += (double) query[i] * entry.getValue()[i];
      var hit = new Hit(entry.getKey(), Math.max(-1, Math.min(1, dot)));
      if (best.size() < limit) best.add(hit);
      else if (BEST_FIRST.compare(hit, best.peek()) < 0) { best.poll(); best.add(hit); }
    }
    return best.stream().sorted(BEST_FIRST).toList();
  }

  private static float[] normalize(float[] vector) {
    if (vector == null || vector.length != EmbeddingEncoder.DIMENSIONS) return null;
    double norm = 0;
    for (float value : vector) {
      if (!Float.isFinite(value)) return null;
      norm += (double) value * value;
    }
    if (norm == 0) return null;
    double length = Math.sqrt(norm);
    var copy = new float[vector.length];
    for (int i = 0; i < copy.length; i++) copy[i] = (float) (vector[i] / length);
    return copy;
  }

  /** k=60 RRF; stable insertion order preserves semantic-first ties without database IDs. */
  public static <T> List<T> fuse(List<T> semantic, List<T> fulltext, int limit, Function<T, String> identity) {
    validateLimit(limit);
    var entries = new LinkedHashMap<String, T>();
    var scores = new LinkedHashMap<String, Double>();
    for (var source : List.of(semantic, fulltext)) {
      for (int rank = 0; rank < source.size(); rank++) {
        T entry = source.get(rank);
        String id = identity.apply(entry);
        if (id == null) continue;
        entries.putIfAbsent(id, entry);
        scores.merge(id, 1.0 / (60 + rank + 1), Double::sum);
      }
    }
    var ids = new ArrayList<>(entries.keySet());
    ids.sort(Comparator.comparingDouble((String id) -> scores.get(id)).reversed());
    return ids.stream().limit(limit).map(entries::get).toList();
  }

  private static void validateLimit(int limit) {
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Vector result limit must be between 1 and 1000");
  }
}
