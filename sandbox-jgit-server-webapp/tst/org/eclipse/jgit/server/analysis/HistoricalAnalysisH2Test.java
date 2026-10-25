/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.server.history.SandboxHistoryMaintenance;
import org.eclipse.jgit.server.history.ReleasedSandboxHistoryMaintenance;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.server.repository.ExternalHibernateRepositoryService;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import io.github.carstenartur.jgit.storage.hibernate.DefaultHibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import io.github.carstenartur.jgit.storage.hibernate.search.SearchEntities;

public class HistoricalAnalysisH2Test {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private HibernateSessionFactoryProvider provider;
  private ExternalHibernateRepositoryService repositories;
  private AnalysisGenerations generations;
  private JavaHistoryCorpus corpus;
  private HistoricalAnalysis analysis;
  private Repository writer;
  private ObjectId first;

  @Before public void start() throws Exception {
    var properties = new Properties();
    properties.setProperty("hibernate.connection.url", "jdbc:h2:mem:analysis-" + UUID.randomUUID());
    properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
    properties.setProperty("hibernate.search.backend.directory.type", "local-heap");
    provider = new HibernateSessionFactoryProvider(properties, SearchEntities.annotatedClasses());
    repositories = new ExternalHibernateRepositoryService(new DefaultHibernateRepositoryFactory(provider.getSessionFactory()),
        SandboxHistoryMaintenance.NONE, provider.getSessionFactory());
    generations = new AnalysisGenerations(4, 1);
    corpus = new JavaHistoryCorpus(temporary.getRoot().toPath(), 1024 * 1024);
    analysis = new HistoricalAnalysis(provider.getSessionFactory(), repositories, generations, corpus);
    writer = repositories.openOrCreate("example.git.git");
    first = ApplicationApiComparisonTest.commit(writer, 1, Map.of("A.java", "class A {}"));
    ref(first);
  }

  @After public void close() throws Exception {
    if (generations != null) generations.close();
    if (repositories != null) repositories.close();
    if (provider != null) provider.close();
  }

  @Test public void usesExistingOnlyLeasesAndInvalidatesAllKindsWithoutNormalizingCanonicalNamesAgain() throws Exception {
    int source = analysis.sources("example.git.git", System::identityHashCode);
    int paths = analysis.paths("example.git.git", System::identityHashCode);
    int api = analysis.api("example.git.git", first.name(), first.name(), false, System::identityHashCode);
    generations.invalidateCanonical("example");
    assertEquals(source, (int) analysis.sources("example.git.git", System::identityHashCode));
    assertEquals(paths, (int) analysis.paths("example.git.git", System::identityHashCode));
    assertEquals(api, (int) analysis.api("example.git.git", first.name(), first.name(), false, System::identityHashCode));
    generations.invalidateCanonical("example.git");
    assertNotEquals(source, (int) analysis.sources("example.git.git", System::identityHashCode));
    assertNotEquals(paths, (int) analysis.paths("example.git.git", System::identityHashCode));
    assertNotEquals(api, (int) analysis.api("example.git.git", first.name(), first.name(), false, System::identityHashCode));
    int refreshed = analysis.sources("example.git.git", System::identityHashCode);
    generations.refresh();
    assertNotEquals(refreshed, (int) analysis.sources("example.git.git", System::identityHashCode));
    assertThrows(java.io.FileNotFoundException.class, () -> analysis.sources("example.git", ignored -> null));
    assertThrows(java.io.FileNotFoundException.class, () -> analysis.paths("missing", ignored -> null));
    assertThrows(java.io.FileNotFoundException.class, () -> repositories.withExistingRepository("missing", ignored -> null));
  }

  @Test public void reloadsActualRepositoryRefsAfterWaitingForTheSharedBuildPermit() throws Exception {
    var building = new CountDownLatch(1); var resume = new CountDownLatch(1); var captured = new CountDownLatch(1);
    var reads = new AtomicInteger();
    var observed = new HistoricalAnalysis(repositories, generations, corpus, (name, git) -> {
      var snapshot = HistoricalAnalysis.snapshot(provider.getSessionFactory(), name, git);
      if (reads.incrementAndGet() == 1) captured.countDown();
      return snapshot;
    });
    try (var executor = Executors.newFixedThreadPool(2)) {
      try {
        var blocker = executor.submit(() -> generations.read(AnalysisGenerations.Kind.PATH, "blocker",
            () -> new AnalysisGenerations.Snapshot(List.of(), null), snapshot -> {
              building.countDown(); await(resume); return HistoricalPathIndex.build("blocker", writer, List.of());
            }, HistoricalPathIndex.class, ignored -> null));
        assertTrue(building.await(10, TimeUnit.SECONDS));
        var waiting = executor.submit(() -> observed.sources("example.git.git", index -> index.files().stream().map(AnalyzedFile::filePath).toList()));
        assertTrue(captured.await(10, TimeUnit.SECONDS));
        var second = ApplicationApiComparisonTest.commit(writer, 2, Map.of("B.java", "class B {}"), first); ref(second);
        resume.countDown(); blocker.get(10, TimeUnit.SECONDS);
        assertEquals(List.of("A.java", "B.java"), waiting.get(10, TimeUnit.SECONDS));
        assertTrue(reads.get() >= 2);
      } finally { resume.countDown(); }
    }
  }

  @Test public void contextShutdownKeepsTheFactoryAndLeaseAliveUntilResultMappingFinishes() throws Exception {
    var entered = new CountDownLatch(1); var resume = new CountDownLatch(1); var closing = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      try {
        var reading = executor.submit(() -> analysis.sources("example.git.git", index -> {
          entered.countDown(); await(resume); assertFalse(provider.getSessionFactory().isClosed());
          return index.files().getFirst().filePath();
        }));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        var close = executor.submit(() -> { closing.countDown(); generations.close(); repositories.close(); provider.close(); return null; });
        assertTrue(closing.await(10, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> close.get(100, TimeUnit.MILLISECONDS));
        resume.countDown(); assertEquals("A.java", reading.get(10, TimeUnit.SECONDS)); close.get(10, TimeUnit.SECONDS);
        assertTrue(provider.getSessionFactory().isClosed());
      } finally { resume.countDown(); }
    }
  }

  @Test public void acceptedDeletionOnlyAndFailedProjectionPushesInvalidateButRejectedCommandsDoNot() throws Exception {
    var maintenance = new ReleasedSandboxHistoryMaintenance(provider.getSessionFactory(), generations);
    int original = analysis.sources("example.git.git", System::identityHashCode);
    var deletion = new ReceiveCommand(first, ObjectId.zeroId(), "refs/heads/main");
    deletion.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON);
    maintenance.published("example.git", writer, List.of(deletion));
    assertEquals(original, (int) analysis.sources("example.git.git", System::identityHashCode));
    deletion.setResult(ReceiveCommand.Result.OK);
    maintenance.published("example.git", writer, List.of(deletion));
    int deleted = analysis.sources("example.git.git", System::identityHashCode); assertNotEquals(original, deleted);
    var missing = new ReceiveCommand(first, ObjectId.fromString("1".repeat(40)), "refs/heads/main");
    missing.setResult(ReceiveCommand.Result.OK);
    maintenance.published("example.git", writer, List.of(missing));
    assertThrows(IllegalStateException.class, maintenance::checkHealthy);
    assertNotEquals(deleted, (int) analysis.sources("example.git.git", System::identityHashCode));
  }

  @Test public void rebuildFailureInvalidatesAGenerationCreatedDuringTheAttemptAndRefreshAndDeleteReachTheSameOwner() throws Exception {
    var actual = provider.getSessionFactory(); var intermediate = new AtomicInteger();
    var proxy = (org.hibernate.SessionFactory) java.lang.reflect.Proxy.newProxyInstance(
        org.hibernate.SessionFactory.class.getClassLoader(), new Class<?>[] {org.hibernate.SessionFactory.class},
        (ignored, method, arguments) -> {
          if (method.getName().equals("openSession")) {
            intermediate.set(analysis.sources("example.git.git", System::identityHashCode));
            throw new IllegalStateException("Injected rebuild failure");
          }
          try { return method.invoke(actual, arguments); }
          catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
        });
    int original = analysis.sources("example.git.git", System::identityHashCode);
    var failing = new ReleasedSandboxHistoryMaintenance(proxy, generations);
    assertThrows(IllegalStateException.class, () -> failing.rebuild("example.git", writer));
    assertNotEquals(original, intermediate.get());
    assertNotEquals(intermediate.get(), (int) analysis.sources("example.git.git", System::identityHashCode));
    var maintenance = new ReleasedSandboxHistoryMaintenance(actual, generations);
    int paths = analysis.paths("example.git.git", System::identityHashCode);
    maintenance.refreshIndexes(actual);
    assertNotEquals(paths, (int) analysis.paths("example.git.git", System::identityHashCode));
    int api = analysis.api("example.git.git", first.name(), first.name(), false, System::identityHashCode);
    maintenance.repositoryDeleted("example.git");
    assertNotEquals(api, (int) analysis.api("example.git.git", first.name(), first.name(), false, System::identityHashCode));
  }

  private void ref(ObjectId id) throws Exception {
    var update = writer.updateRef("refs/heads/main"); update.setNewObjectId(id); update.setForceUpdate(true); update.update();
  }
  private static void await(CountDownLatch latch) throws java.io.IOException {
    try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException(); }
  }
}
