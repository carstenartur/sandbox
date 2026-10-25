/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.history;

import static org.junit.Assert.*;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.github.carstenartur.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import io.github.carstenartur.jgit.storage.hibernate.search.SearchEntities;
import io.github.carstenartur.jgit.storage.hibernate.search.entity.GitCommitIndex;
import io.github.carstenartur.jgit.storage.hibernate.search.service.CommitIndexer;

public class ReleasedHistoryMaintenanceH2Test {
  private HibernateSessionFactoryProvider provider;
  private InMemoryRepository repository;
  private ReleasedSandboxHistoryMaintenance maintenance;

  @Before public void start() {
    var properties = new Properties();
    properties.setProperty("hibernate.connection.url", "jdbc:h2:mem:maintenance-" + UUID.randomUUID());
    properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
    properties.setProperty("hibernate.generate_statistics", "true");
    properties.setProperty("hibernate.search.backend.directory.type", "local-heap");
    properties.setProperty("hibernate.search.indexing.plan.synchronization.strategy", "sync");
    properties.setProperty("jgit.storage.hibernate.search.index_batch_size", "3");
    provider = new HibernateSessionFactoryProvider(properties, SearchEntities.annotatedClasses());
    repository = new InMemoryRepository(new DfsRepositoryDescription("repo"));
    maintenance = new ReleasedSandboxHistoryMaintenance(provider.getSessionFactory());
  }

  @After public void close() {
    if (repository != null) repository.close();
    if (provider != null) provider.close();
  }

  @Test public void certifiesAllParentsBeforeBoundingLaterPushesAndNeverTrustsATipRow() throws Exception {
    ObjectId root = commit("root");
    ObjectId tip = root;
    for (int i = 1; i <= 60; i++) tip = commit("history " + i, tip);
    new CommitIndexer(provider.getSessionFactory(), "repo").indexCommit(repository, tip);
    ObjectId side = commit("side", root);
    publish(tip, side);
    assertEquals(62, count());
    ObjectId merge = commit("merge", tip, side);
    provider.getSessionFactory().getStatistics().clear();
    publish(merge);
    long queries = provider.getSessionFactory().getStatistics().getQueryExecutionCount();
    assertTrue("A one-commit delta must not query every old batch: " + queries, queries <= 2);
    assertEquals(63, count());
    maintenance.checkHealthy();
  }

  @Test public void rebuildRepairsRowsAndClearsFailureWhileRefreshDoesNeither() throws Exception {
    ObjectId root = commit("root");
    ObjectId tip = commit("tip", root);
    ref("refs/heads/main", tip);
    publish(tip);
    remove(root);
    publish(ObjectId.fromString("1".repeat(40)));
    assertThrows(IllegalStateException.class, maintenance::checkHealthy);
    maintenance.refreshIndexes(provider.getSessionFactory());
    assertEquals(1, count());
    assertThrows(IllegalStateException.class, maintenance::checkHealthy);
    var result = maintenance.rebuild("repo", repository);
    assertEquals(1, result.removedProjections());
    assertEquals(2, result.indexedCommits());
    assertEquals(2, count());
    assertEquals(tip, repository.resolve("refs/heads/main"));
    maintenance.checkHealthy();
    // Rebuild invalidated every old certificate; the next push repairs even a removed root row.
    remove(root);
    publish(commit("next", tip));
    assertEquals(3, count());
  }

  @Test public void failedRebuildIsVisibleAndRetryIncludesDetachedHead() throws Exception {
    ObjectId detached = commit("detached");
    var update = repository.updateRef(Constants.HEAD, true);
    update.setNewObjectId(detached); update.setForceUpdate(true); update.update();
    var fail = new AtomicBoolean(true);
    SessionFactory actual = provider.getSessionFactory();
    var proxy = (SessionFactory) java.lang.reflect.Proxy.newProxyInstance(SessionFactory.class.getClassLoader(),
        new Class<?>[] {SessionFactory.class}, (ignored, method, arguments) -> {
          if (method.getName().equals("openSession") && fail.get()) throw new IllegalStateException("Injected failure");
          try { return method.invoke(actual, arguments); }
          catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
        });
    maintenance = new ReleasedSandboxHistoryMaintenance(proxy);
    assertThrows(IllegalStateException.class, () -> maintenance.rebuild("repo", repository));
    assertThrows(IllegalStateException.class, maintenance::checkHealthy);
    fail.set(false);
    assertEquals(1, maintenance.rebuild("repo", repository).indexedCommits());
    assertEquals(1, count());
    maintenance.checkHealthy();
    try {
      Thread.currentThread().interrupt();
      assertThrows(java.io.InterruptedIOException.class, () -> maintenance.rebuild("repo", repository));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally { Thread.interrupted(); }
    assertEquals(1, count());
  }

  @Test public void deletionInvalidatesCertificatesAndFailureForTheReusedIdentity() throws Exception {
    ObjectId root = commit("root");
    publish(root);
    remove(root);
    maintenance.repositoryDeleted("repo");
    publish(root);
    assertEquals(1, count());
    publish(ObjectId.fromString("1".repeat(40)));
    assertThrows(IllegalStateException.class, maintenance::checkHealthy);
    maintenance.repositoryDeleted("repo");
    maintenance.checkHealthy();
  }

  @Test public void rebuildRefreshesAReaderOpenedBeforeAnotherHandlePublishedAndIndexed() throws Exception {
    var factory = new io.github.carstenartur.jgit.storage.hibernate.DefaultHibernateRepositoryFactory(provider.getSessionFactory());
    var name = new io.github.carstenartur.jgit.storage.hibernate.RepositoryName("repo");
    try (var writer = factory.open(name)) {
      ObjectId first = commit(writer.repository(), "first");
      var ref = writer.repository().updateRef("refs/heads/main"); ref.setNewObjectId(first); ref.update();
      try (var stale = factory.open(name)) {
        assertEquals(first, stale.repository().resolve("refs/heads/main"));
        ObjectId second = commit(writer.repository(), "second", first);
        ref = writer.repository().updateRef("refs/heads/main"); ref.setNewObjectId(second); ref.update();
        var command = new ReceiveCommand(first, second, "refs/heads/main"); command.setResult(ReceiveCommand.Result.OK);
        maintenance.published("repo", writer.repository(), java.util.List.of(command));
        assertEquals(2, count());
        assertEquals(first, stale.repository().resolve("refs/heads/main"));
        assertEquals(2, maintenance.rebuild("repo", stale.repository()).indexedCommits());
        assertEquals(2, count());
        assertEquals(second, stale.repository().resolve("refs/heads/main"));
        maintenance.checkHealthy();
      }
    }
  }

  @Test public void retriesWithoutACertificateWhoseOlderAncestryWasPruned() throws Exception {
    ObjectId root = commit("old root");
    ObjectId boundary = commit("old boundary", root);
    publish(boundary);
    // Model object maintenance that retained the now-unreferenced boundary but removed its parent.
    try (var pruned = new InMemoryRepository(new DfsRepositoryDescription("pruned"))) {
      ObjectId next = commit(pruned, "unrelated new root");
      try (var inserter = pruned.newObjectInserter()) {
        assertEquals(boundary, inserter.insert(Constants.OBJ_COMMIT, repository.open(boundary).getBytes()));
        inserter.flush();
      }
      var command = new ReceiveCommand(ObjectId.zeroId(), next, "refs/heads/new");
      command.setResult(ReceiveCommand.Result.OK);
      maintenance.published("repo", pruned, java.util.List.of(command));
      maintenance.checkHealthy();
      assertEquals(3, count());
    }
  }

  private void publish(ObjectId... tips) {
    var commands = java.util.Arrays.stream(tips).map(tip -> {
      var command = new ReceiveCommand(ObjectId.zeroId(), tip, "refs/heads/test");
      command.setResult(ReceiveCommand.Result.OK); return command;
    }).toList();
    maintenance.published("repo", repository, commands);
  }

  private ObjectId commit(String message, ObjectId... parents) throws Exception {
    return commit(repository, message, parents);
  }

  private static ObjectId commit(org.eclipse.jgit.lib.Repository repository, String message, ObjectId... parents) throws Exception {
    try (var inserter = repository.newObjectInserter()) {
      var commit = new CommitBuilder(); commit.setTreeId(inserter.insert(new TreeFormatter()));
      commit.setParentIds(parents); commit.setMessage(message);
      var actor = new PersonIdent("Author", "author@example.org"); commit.setAuthor(actor); commit.setCommitter(actor);
      var id = inserter.insert(commit); inserter.flush(); return id;
    }
  }

  private void ref(String name, ObjectId id) throws Exception {
    var update = repository.updateRef(name); update.setNewObjectId(id); update.update();
  }

  private long count() {
    try (var session = provider.getSessionFactory().openSession()) {
      return session.createQuery("SELECT COUNT(c) FROM GitCommitIndex c WHERE c.repositoryName='repo'", Long.class).getSingleResult();
    }
  }

  private void remove(ObjectId id) {
    try (var session = provider.getSessionFactory().openSession()) {
      session.beginTransaction();
      session.remove(session.createQuery("FROM GitCommitIndex c WHERE c.repositoryName='repo' AND c.objectId=:id", GitCommitIndex.class)
          .setParameter("id", id.name()).getSingleResult());
      session.getTransaction().commit();
    }
  }
}
