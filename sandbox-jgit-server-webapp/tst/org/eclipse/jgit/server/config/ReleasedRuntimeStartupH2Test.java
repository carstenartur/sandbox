/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import static org.junit.Assert.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.server.JGitServerApplication;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.google.gson.JsonParser;

/** Production entry points against the publicly released modules and an explicitly initialized database. */
public class ReleasedRuntimeStartupH2Test {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void defaultFactoryRequiresMigrationsAndRegistersOnlyReleasedEntities() throws Exception {
    var properties = properties();
    assertThrows(IllegalStateException.class, () -> HibernateConfig.createPersistenceContext(properties));
    ReleasedSchemaInitializer.initializeEmpty(properties);
    try (var context = HibernateConfig.createPersistenceContext(properties)) {
      var entities = context.sessionFactory().getMetamodel().getEntities();
      assertEquals(6, entities.size());
      assertTrue(entities.stream().allMatch(e -> e.getJavaType().getName().startsWith("io.github.carstenartur.jgit.storage.hibernate.")));
      assertTrue(context.repositoryService() instanceof org.eclipse.jgit.server.repository.ExternalHibernateRepositoryService);
    }
  }

  @Test public void normalServerPushSearchBothAliasesAndRestartUseReleasedStorage() throws Exception {
    var properties = properties(); ReleasedSchemaInitializer.initializeEmpty(properties);
    var app = new JGitServerApplication();
    String first;
    try (var client = HttpClient.newHttpClient();
         var git = Git.init().setDirectory(temporary.newFolder("client")).setInitialBranch("main").call()) {
      app.start(properties, 0, 0);
      Files.writeString(git.getRepository().getWorkTree().toPath().resolve("Hello.java"),
          "package p; /** searchable documentation */ public class Hello { public void run() {} }");
      git.add().addFilepattern(".").call();
      first = git.commit().setMessage("Needle source").setAuthor("Author", "a@example.test").setCommitter("Author", "a@example.test").call().name();
      for (var result : git.push().setRemote("http://localhost:" + app.getGitPort() + "/git/example.git.git")
          .setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call())
        for (var update : result.getRemoteUpdates()) assertEquals(RemoteRefUpdate.Status.OK, update.getStatus());
      for (String alias : List.of("/api", "/api/v1")) {
        var types = get(client, app, alias + "/search/types?repo=example.git.git&q=Hello");
        assertEquals(1, types.get("totalResults").getAsInt());
        assertEquals(first, types.getAsJsonArray("results").get(0).getAsJsonObject().get("commitObjectId").getAsString());
        assertEquals(1, get(client, app, alias + "/search/commits?repo=example.git.git&q=Needle").get("totalResults").getAsInt());
        assertEquals(1, get(client, app, alias + "/search/filepaths?repo=example.git.git&q=Hello").get("totalResults").getAsInt());
        assertEquals(1, get(client, app, alias + "/search/analytics/authors?repo=example.git.git").get("totalResults").getAsInt());
      }
      var factory = app.getSessionFactory(); app.stop(); assertTrue(factory.isClosed());
      app.start(properties, 0, 0);
      assertEquals(1, get(client, app, "/api/v1/search/types?repo=example.git.git&q=Hello").get("totalResults").getAsInt());
      assertEquals(1, get(client, app, "/api/search/commits?repo=example.git.git&q=Needle").get("totalResults").getAsInt());
      try (var clone = Git.cloneRepository().setURI("http://localhost:" + app.getGitPort() + "/git/example.git.git")
          .setBranch("main").setDirectory(temporary.newFolder("clone")).call()) {
        assertEquals(first, clone.getRepository().resolve("HEAD").name());
      }
      assertEquals(0, get(client, app, "/api/search/types?repo=missing&q=Hello").get("totalResults").getAsInt());
      assertFalse(app.getRepositoryResolver().getRepositoryService().isOpen("missing"));
    } finally { app.stop(); }
  }

  @Test public void stopWaitsForInFlightPersistenceBootstrapAndClosesThePublishedContext() throws Exception {
    var p = properties(); ReleasedSchemaInitializer.initializeEmpty(p);
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    p.put("hibernate.search.backend.analysis.configurer",
        (org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer) context -> {
          new org.eclipse.jgit.server.history.SandboxHistoryAnalysisConfigurer().configure(context);
          entered.countDown();
          try { if (!release.await(20, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Bootstrap not released"); }
          catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
        });
    p.setProperty(io.github.carstenartur.jgit.storage.hibernate.search.analysis.GitTextAnalysis.PROFILE_ID_PROPERTY,
        "sandbox-commit-message-v1");
    var app = new JGitServerApplication();
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var starting = executor.submit(() -> { app.start(p, 0, 0); return null; });
      try {
        assertTrue("Hibernate bootstrap did not reach analysis configuration", entered.await(15, java.util.concurrent.TimeUnit.SECONDS));
        var stopping = executor.submit(() -> { app.stop(); return null; });
        assertThrows(java.util.concurrent.TimeoutException.class,
            () -> stopping.get(150, java.util.concurrent.TimeUnit.MILLISECONDS));
        release.countDown();
        starting.get(20, java.util.concurrent.TimeUnit.SECONDS);
        stopping.get(20, java.util.concurrent.TimeUnit.SECONDS);
        assertThrows(IllegalStateException.class, app::getSessionFactory);
        assertEquals(-1, app.getRestPort()); assertEquals(-1, app.getGitPort());
      } finally {
        release.countDown();
        try { starting.get(20, java.util.concurrent.TimeUnit.SECONDS); }
        finally { app.stop(); }
      }
    }
  }

  @Test public void drainsPinnedSemanticReadersBeforeClosingModelRepositoriesAndDatabase() throws Exception {
    var p = properties(); ReleasedSchemaInitializer.initializeEmpty(p);
    var encoder = new BlockingEncoder();
    var owner = new io.github.carstenartur.jgit.storage.hibernate.config.HibernateSessionFactoryProvider(p,
        io.github.carstenartur.jgit.storage.hibernate.search.SearchEntities.annotatedClasses());
    var context = new ExternalServerPersistenceContext(owner, org.eclipse.jgit.server.analysis.AnalysisSettings.fromEnvironment(), encoder);
    var factory = context.sessionFactory();
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      try {
        var repository = context.repositoryService().openOrCreate("lifecycle");
        org.eclipse.jgit.lib.ObjectId commit;
        try (var inserter = repository.newObjectInserter()) {
          var tree = new org.eclipse.jgit.lib.TreeFormatter();
          tree.append("Hello.java", org.eclipse.jgit.lib.FileMode.REGULAR_FILE,
              inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, "public class Hello {}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
          var builder = new org.eclipse.jgit.lib.CommitBuilder(); builder.setTreeId(inserter.insert(tree));
          builder.setAuthor(new org.eclipse.jgit.lib.PersonIdent("Author", "a@example.test")); builder.setCommitter(builder.getAuthor());
          builder.setMessage("source"); commit = inserter.insert(builder); inserter.flush();
        }
        var ref = repository.updateRef("refs/heads/main"); ref.setNewObjectId(commit); ref.update();
        var query = executor.submit(() -> context.sourceSearch().semanticSearch("lifecycle", "question", 10));
        assertTrue(encoder.entered.await(10, java.util.concurrent.TimeUnit.SECONDS));
        var closing = executor.submit(() -> { context.close(); return null; });
        assertThrows(java.util.concurrent.TimeoutException.class, () -> closing.get(150, java.util.concurrent.TimeUnit.MILLISECONDS));
        assertFalse(factory.isClosed()); assertEquals(0, encoder.closes.get());
        encoder.release.countDown();
        assertEquals(1, query.get(10, java.util.concurrent.TimeUnit.SECONDS).size());
        closing.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(factory.isClosed()); assertEquals(1, encoder.closes.get());
        context.close(); assertEquals(1, encoder.closes.get());
      } finally { encoder.release.countDown(); context.close(); }
    }
  }

  private static final class BlockingEncoder implements org.eclipse.jgit.server.analysis.EmbeddingEncoder {
    final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
    final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    final java.util.concurrent.atomic.AtomicInteger closes = new java.util.concurrent.atomic.AtomicInteger();
    @Override public Status status() { return closes.get() == 0 ? Status.AVAILABLE : Status.CLOSED; }
    @Override public float[] embed(String text) {
      entered.countDown();
      try { if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Reader was not released"); }
      catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
      var vector = new float[DIMENSIONS]; vector[0] = 1; return vector;
    }
    @Override public void close() { closes.incrementAndGet(); }
  }

  private static com.google.gson.JsonObject get(HttpClient client, JGitServerApplication app, String path) throws Exception {
    var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + app.getRestPort() + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
    assertEquals(response.body(), 200, response.statusCode());
    return JsonParser.parseString(response.body()).getAsJsonObject();
  }

  private Properties properties() throws Exception {
    var p = new Properties();
    p.setProperty("hibernate.connection.url", "jdbc:h2:mem:runtime-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    p.setProperty("hibernate.connection.username", "sa"); p.setProperty("hibernate.connection.password", "");
    p.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    p.setProperty("hibernate.hbm2ddl.auto", "validate");
    p.setProperty("hibernate.search.backend.type", "lucene");
    p.setProperty("hibernate.search.backend.directory.root", temporary.newFolder().getAbsolutePath());
    p.setProperty("hibernate.cache.use_second_level_cache", "false");
    return p;
  }
}
