/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.rest;

import static org.junit.Assert.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.server.history.ReleasedSandboxHistoryMaintenance;
import org.eclipse.jgit.server.history.ReleasedSandboxHistorySearch;
import org.eclipse.jgit.server.repository.ExternalHibernateRepositoryService;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.Test;
import com.google.gson.JsonParser;
import io.github.carstenartur.jgit.storage.hibernate.DefaultHibernateRepositoryFactory;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;
import io.github.carstenartur.jgit.storage.hibernate.config.HibernateSessionFactoryProvider;
import io.github.carstenartur.jgit.storage.hibernate.search.SearchEntities;

public class AdminRebuildH2Test {
  @Test public void authenticatesBothPrefixesRepairsHealthAndNeverCreatesAMissingRepository() throws Exception {
    var properties = new Properties();
    properties.setProperty("hibernate.connection.url", "jdbc:h2:mem:admin-" + UUID.randomUUID());
    properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
    properties.setProperty("hibernate.search.backend.directory.type", "local-heap");
    properties.setProperty("hibernate.search.backend.type", "lucene");
    try (var provider = new HibernateSessionFactoryProvider(properties, SearchEntities.annotatedClasses())) {
      var factory = new DefaultHibernateRepositoryFactory(provider.getSessionFactory());
      var maintenance = new ReleasedSandboxHistoryMaintenance(provider.getSessionFactory());
      ObjectId original;
      try (var storage = factory.open(new RepositoryName("example.git"))) {
        var repository = storage.repository();
        try (var inserter = repository.newObjectInserter()) {
          var builder = new CommitBuilder(); builder.setTreeId(inserter.insert(new TreeFormatter()));
          var actor = new PersonIdent("Author", "author@example.org"); builder.setAuthor(actor); builder.setCommitter(actor);
          builder.setMessage("Rebuild needle"); original = inserter.insert(builder); inserter.flush();
        }
        var ref = repository.updateRef("refs/heads/main"); ref.setNewObjectId(original); ref.update();
        var failed = new ReceiveCommand(ObjectId.zeroId(), ObjectId.fromString("1".repeat(40)), "refs/heads/missing");
        failed.setResult(ReceiveCommand.Result.OK);
        maintenance.published("example.git", repository, List.of(failed));
      }
      var history = new ReleasedSandboxHistorySearch(provider.getSessionFactory());
      try (var repositories = new ExternalHibernateRepositoryService(factory, maintenance, provider.getSessionFactory());
          var client = HttpClient.newHttpClient()) {
        var server = new Server(0);
        var contexts = new ContextHandlerCollection();
        for (String prefix : List.of("/api", "/api/v1")) {
          var context = new ServletContextHandler(); context.setContextPath(prefix);
          context.addServlet(new ServletHolder(new AdminResource(provider.getSessionFactory(), maintenance, repositories,
              () -> "local-test-token")), "/admin/*");
          context.addServlet(new ServletHolder(new HealthResource(provider.getSessionFactory(), history, maintenance)), "/health");
          contexts.addHandler(context);
        }
        for (String failure : List.of("configuration", "storage")) {
          var failingMaintenance = new org.eclipse.jgit.server.history.SandboxHistoryMaintenance() {
            @Override public RebuildResult rebuild(String name, org.eclipse.jgit.lib.Repository repository) throws java.io.IOException {
              if (failure.equals("configuration")) throw new IllegalArgumentException("Invalid purge batch size");
              throw new java.io.FileNotFoundException("Missing authoritative storage object");
            }
          };
          var context = new ServletContextHandler(); context.setContextPath("/failure-" + failure);
          context.addServlet(new ServletHolder(new AdminResource(provider.getSessionFactory(), failingMaintenance, repositories,
              () -> "local-test-token")), "/admin/*");
          contexts.addHandler(context);
        }
        server.setHandler(contexts);
        try {
          server.start();
          String base = "http://localhost:" + ((ServerConnector) server.getConnectors()[0]).getLocalPort();
          assertEquals(503, get(client, base + "/api/health").statusCode());
          for (String failure : List.of("configuration", "storage")) {
            var response = post(client, base + "/failure-" + failure + "/admin/rebuild?repo=example.git.git", "local-test-token");
            assertEquals(response.body(), 500, response.statusCode());
            assertFalse(repositories.isOpen("example.git.git"));
          }
          for (String prefix : List.of("/api", "/api/v1")) {
            String admin = base + prefix + "/admin/rebuild";
            assertEquals(401, post(client, admin + "?repo=example.git.git", null).statusCode());
            assertEquals(401, post(client, admin + "?repo=example.git.git", "wrong").statusCode());
            assertEquals(400, post(client, admin, "local-test-token").statusCode());
            assertEquals(400, post(client, admin + "?repo=.git", "local-test-token").statusCode());
            assertEquals(404, post(client, admin + "?repo=missing", "local-test-token").statusCode());
            var rebuilt = post(client, admin + "?repo=example.git.git", "local-test-token");
            assertEquals(rebuilt.body(), 200, rebuilt.statusCode());
            var body = JsonParser.parseString(rebuilt.body()).getAsJsonObject();
            assertEquals("example.git.git", body.get("repository").getAsString());
            assertEquals(1, body.get("indexedCommits").getAsLong());
            assertEquals(200, get(client, base + prefix + "/health").statusCode());
          }
          assertEquals(original.name(), history.searchMessages("example.git.git", "needle", 0, 10).getFirst().objectId());
          assertFalse(repositories.isOpen("example.git.git"));
          assertThrows(java.io.FileNotFoundException.class, () -> repositories.withExistingRepository("missing", ignored -> null));
        } finally { server.stop(); }
      }
    }
  }

  private static HttpResponse<String> post(HttpClient client, String url, String token) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody());
    if (token != null) request.header("Authorization", "Bearer " + token);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
  private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }
}
