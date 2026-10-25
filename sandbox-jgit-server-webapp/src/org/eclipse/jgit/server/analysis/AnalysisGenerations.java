/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.server.history.GitHistorySnapshot.CommitView;

/** Shared build capacity and shutdown ownership for source, path and API generations. */
public final class AnalysisGenerations implements Closeable {
  enum Kind { SOURCE, PATH, API }
  record ApiRequest(String before, String after, boolean publicOnly) {}
  record Snapshot(List<CommitView> commits, ApiRequest api) {
    Snapshot { commits = List.copyOf(commits); }
  }
  private final AnalysisGenerationCache<Snapshot, Closeable> cache;

  public AnalysisGenerations(int retainedGenerations, int concurrentBuilds) {
    cache = new AnalysisGenerationCache<>(retainedGenerations, concurrentBuilds);
  }

  <G extends Closeable, T> T read(Kind kind, String canonicalName,
      AnalysisGenerationCache.Snapshot<Snapshot> snapshots,
      AnalysisGenerationCache.Builder<Snapshot, G> builder, Class<G> type,
      AnalysisGenerationCache.Reader<G, T> reader) throws IOException {
    return cache.read(key(kind, canonicalName), snapshots, snapshot -> builder.build(snapshot),
        generation -> reader.read(type.cast(generation)));
  }

  /** Canonical identities arrive from accepted transport/deletion/rebuild operations. */
  public void invalidateCanonical(String canonicalName) {
    for (var kind : Kind.values()) cache.invalidateCanonical(key(kind, canonicalName));
  }

  /** Explicitly refreshes local dependency/classpath inputs as well as Git-derived generations. */
  public void refresh() { cache.invalidateAll(); }

  private static String key(Kind kind, String canonicalName) {
    return kind.name() + ":" + Objects.requireNonNull(canonicalName);
  }

  @Override public void close() throws IOException { cache.close(); }
}
