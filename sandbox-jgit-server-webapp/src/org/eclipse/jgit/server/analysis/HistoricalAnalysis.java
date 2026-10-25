/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.history.GitHistorySnapshot;
import org.eclipse.jgit.server.history.GitHistorySnapshot.CommitView;
import org.eclipse.jgit.server.repository.SandboxRepositoryNames;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.hibernate.SessionFactory;

/** Keeps an existing-only repository lease and a generation pin through complete result mapping. */
final class HistoricalAnalysis {
  @FunctionalInterface interface Snapshots {
    List<CommitView> read(String canonicalName, Repository repository) throws IOException;
  }
  private final SandboxRepositoryService repositories;
  private final AnalysisGenerations generations;
  private final JavaHistoryCorpus corpus;
  private final Snapshots snapshots;

  HistoricalAnalysis(SessionFactory factory, SandboxRepositoryService repositories, AnalysisGenerations generations,
      JavaHistoryCorpus corpus) {
    this(repositories, generations, corpus, (name, git) -> snapshot(factory, name, git));
  }

  HistoricalAnalysis(SandboxRepositoryService repositories, AnalysisGenerations generations, JavaHistoryCorpus corpus,
      Snapshots snapshots) {
    this.repositories = Objects.requireNonNull(repositories); this.generations = Objects.requireNonNull(generations);
    this.corpus = Objects.requireNonNull(corpus); this.snapshots = Objects.requireNonNull(snapshots);
  }

  <T> T sources(String requestName, AnalysisGenerationCache.Reader<HistoricalSourceIndex, T> read) throws IOException {
    String canonical = SandboxRepositoryNames.normalize(requestName);
    return repositories.withExistingRepository(requestName, git -> generations.read(AnalysisGenerations.Kind.SOURCE, canonical,
        () -> new AnalysisGenerations.Snapshot(snapshots.read(canonical, git), null),
        snapshot -> HistoricalSourceIndex.build(canonical, corpus.readSnapshot(canonical, git, snapshot.commits()).files()),
        HistoricalSourceIndex.class, read));
  }

  <T> T paths(String requestName, AnalysisGenerationCache.Reader<HistoricalPathIndex, T> read) throws IOException {
    String canonical = SandboxRepositoryNames.normalize(requestName);
    return repositories.withExistingRepository(requestName, git -> generations.read(AnalysisGenerations.Kind.PATH, canonical,
        () -> new AnalysisGenerations.Snapshot(snapshots.read(canonical, git), null),
        snapshot -> HistoricalPathIndex.build(canonical, git, snapshot.commits()), HistoricalPathIndex.class, read));
  }

  <T> T api(String requestName, String before, String after, boolean publicOnly,
      AnalysisGenerationCache.Reader<ApplicationApiComparison.Diff, T> read) throws IOException {
    String canonical = SandboxRepositoryNames.normalize(requestName);
    var request = new AnalysisGenerations.ApiRequest(before, after, publicOnly);
    return repositories.withExistingRepository(requestName, git -> generations.read(AnalysisGenerations.Kind.API, canonical,
        () -> { git.scanForRepoChanges(); return new AnalysisGenerations.Snapshot(List.of(), request); },
        snapshot -> new ApiGeneration(new ApplicationApiComparison(corpus).compare(canonical, git,
            ObjectId.fromString(snapshot.api().before()), ObjectId.fromString(snapshot.api().after()), snapshot.api().publicOnly())),
        ApiGeneration.class, generation -> read.read(generation.diff())));
  }

  /** Called after the per-identity lock, after waiting for capacity, and on every invalidation retry. */
  static List<CommitView> snapshot(SessionFactory factory, String canonicalName, Repository git) throws IOException {
    git.scanForRepoChanges();
    List<String> retained;
    try (var session = factory.openSession()) {
      retained = session.createQuery("SELECT c.objectId FROM GitCommitIndex c WHERE c.repositoryName = :repo", String.class)
          .setParameter("repo", canonicalName).getResultList();
    }
    return GitHistorySnapshot.read(git, retained);
  }

  private record ApiGeneration(ApplicationApiComparison.Diff diff) implements Closeable {
    @Override public void close() { /* Immutable application values own no external resources. */ }
  }
}
