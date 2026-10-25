/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.rest;

import static org.junit.Assert.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jgit.server.history.SandboxFileHistory;
import org.eclipse.jgit.server.history.SandboxHistorySearch;
import org.eclipse.jgit.server.search.SandboxSourceSearch;
import org.eclipse.jgit.server.search.SandboxSourceSearch.*;
import org.junit.Test;
import com.google.gson.JsonParser;

public class SourceSearchBoundaryRestTest {
  private static final SourceFile FILE = new SourceFile("blob", "commit", "src/p/Example.java", "p", "Example",
      "p.Example", "run", "value", "Base", "Runnable", "public");
  private record Call(String method, List<Object> arguments) {}

  @Test public void routesBothApiAliasesThroughApplicationResultsWithExistingPagingAndJson() throws Exception {
    var last = new AtomicReference<Call>();
    try (var endpoint = new Endpoint(last)) {
      var routes = Map.ofEntries(Map.entry("types", "searchByType"), Map.entry("symbols", "searchBySymbol"),
          Map.entry("hierarchy", "searchByHierarchy"), Map.entry("source", "searchSourceContent"),
          Map.entry("annotations", "searchByAnnotation"), Map.entry("docs", "searchByDocumentation"),
          Map.entry("references", "searchByReferencedType"), Map.entry("strings", "searchByStringLiteral"),
          Map.entry("migration", "getMigrationImpact"));
      for (String prefix : List.of("/search/", "/v1/search/")) {
        for (var route : routes.entrySet()) {
          var response = endpoint.get(prefix + route.getKey() + "?repo=example.git.git&q=Thing&offset=-5&limit=999");
          assertEquals(200, response.statusCode());
          assertEquals(new Call(route.getValue(), List.of("example.git.git", "Thing", 0, 100)), last.get());
          var body = JsonParser.parseString(response.body()).getAsJsonObject();
          assertEquals(1, body.get("totalResults").getAsInt()); assertEquals(100, body.get("limit").getAsInt());
          assertEquals("example.git.git", body.get("repository").getAsString());
          var file = body.getAsJsonArray("results").get(0).getAsJsonObject();
          assertEquals("blob", file.get("blobObjectId").getAsString());
          assertEquals("p.Example", file.get("fullyQualifiedNames").getAsString());
          assertEquals("Runnable", file.get("implementsTypes").getAsString());
          assertEquals(10, file.size());
        }
      }
      endpoint.get("/search/types?repo=repo&q=Thing&module=module");
      assertEquals(new Call("searchByTypeWithModule", List.of("repo", "Thing", "module", 0, 20)), last.get());
      endpoint.get("/search/fqn?repo=repo&q=Thing&fileType=pom");
      assertEquals(new Call("searchFqnAcrossTypes", List.of("repo", "Thing", "pom", 0, 20)), last.get());
    }
  }

  @Test public void vectorRoutesRetainTopKAndExistingValidationQuirks() throws Exception {
    var last = new AtomicReference<Call>();
    try (var endpoint = new Endpoint(last)) {
      for (String route : List.of("semantic", "hybrid")) {
        var result = endpoint.get("/search/" + route + "?repo=repo&q=Thing&offset=17&limit=3");
        assertEquals(new Call(route + "Search", List.of("repo", "Thing", 3)), last.get());
        assertEquals(0, JsonParser.parseString(result.body()).getAsJsonObject().get("offset").getAsInt());
      }
      assertEquals(400, endpoint.get("/search/similar?repo=repo&blobId=blob").statusCode());
      endpoint.get("/search/similar?repo=repo&q=required&blobId=blob&limit=3");
      assertEquals(new Call("findSimilarCode", List.of("repo", "blob", 3)), last.get());
      assertEquals(400, endpoint.get("/search/analytics/complexity?repo=repo").statusCode());
      assertEquals(400, endpoint.get("/search/api-diff?repo=repo&commitA=a").statusCode());
    }
  }

  @Test public void reportDiffAndPathResponsesKeepTheirPublicShapes() throws Exception {
    var last = new AtomicReference<Call>();
    try (var endpoint = new Endpoint(last)) {
      var authors = JsonParser.parseString(endpoint.get("/search/analytics/authors?repo=repo").body()).getAsJsonObject();
      assertEquals(7, authors.getAsJsonArray("results").get(0).getAsJsonObject().get("count").getAsLong());
      var complexity = JsonParser.parseString(endpoint.get("/search/analytics/complexity?repo=repo&q=p").body()).getAsJsonObject();
      assertEquals(12.5, complexity.getAsJsonArray("results").get(0).getAsJsonObject().get("avgLineCount").getAsDouble(), 0);
      var ratio = JsonParser.parseString(endpoint.get("/search/analytics/test-ratio?repo=repo").body()).getAsJsonObject();
      assertEquals(5, ratio.getAsJsonArray("results").get(0).getAsJsonObject().get("totalTypeCount").getAsLong());
      var summary = JsonParser.parseString(endpoint.get("/search/migration/summary?repo=repo&q=old").body()).getAsJsonObject();
      assertEquals(11, summary.get("totalFiles").getAsInt());
      for (String route : List.of("api-diff", "api-diff/public")) {
        var diff = JsonParser.parseString(endpoint.get("/search/" + route + "?repo=repo&commitA=a&commitB=b").body()).getAsJsonObject();
        assertEquals(new Call(route.endsWith("/public") ? "getPublicApiChanges" : "getApiDiff", List.of("repo", "a", "b")), last.get());
        assertEquals(3, diff.size());
        assertEquals("methods changed", diff.getAsJsonArray("changedFiles").get(0).getAsJsonObject().get("changeDescription").getAsString());
        assertEquals(8, diff.getAsJsonArray("addedFiles").get(0).getAsJsonObject().size());
      }
      var paths = JsonParser.parseString(endpoint.get("/search/filepaths?repo=repo&q=Example").body()).getAsJsonObject();
      assertEquals("1970-01-01T00:00:00Z", paths.getAsJsonArray("results").get(0).getAsJsonObject().get("commitTime").getAsString());
      endpoint.get("/search/analytics/monster-classes?repo=repo&threshold=123");
      assertEquals(new Call("getMonsterClasses", List.of("repo", 123, 0, 20)), last.get());
      endpoint.get("/search/analytics/dead-code?repo=repo");
      assertEquals(new Call("getDeadCodeCandidates", List.of("repo", 0, 20)), last.get());
      endpoint.get("/search/migration/deprecated?repo=repo");
      assertEquals(new Call("getDeprecatedApiUsage", List.of("repo", 0, 20)), last.get());
      endpoint.get("/search/deprecation-timeline?repo=repo");
      assertEquals(new Call("getDeprecationTimeline", List.of("repo", 0, 20)), last.get());
    }
  }

  @Test public void independentlyOwnedBoundariesAreNotClosedByServletShutdown() throws Exception {
    var endpoint = new Endpoint(new AtomicReference<>());
    endpoint.get("/search/commits?repo=repo&q=message");
    endpoint.close(); assertEquals(0, endpoint.sourceCloses.get());
  }

  private static final class Endpoint implements AutoCloseable {
    final AtomicInteger sourceCloses = new AtomicInteger();
    final Server server = new Server(0);
    final HttpClient client = HttpClient.newHttpClient();
    final int port;
    Endpoint(AtomicReference<Call> last) throws Exception {
      var sources = (SandboxSourceSearch) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {SandboxSourceSearch.class},
          (proxy, method, args) -> {
            if (method.getName().equals("close")) { sourceCloses.incrementAndGet(); return null; }
            last.set(new Call(method.getName(), java.util.Arrays.asList(args)));
            return switch (method.getName()) {
              case "getAuthorTypeStatistics" -> List.of(new AuthorTypes("Author", "class", 7));
              case "getCodeComplexityTrend" -> List.of(new Complexity(Instant.EPOCH.toString(), 12.5));
              case "getTestCoverageProxy" -> List.of(new TestRatio("p", 2, 5));
              case "getMigrationImpactSummary" -> Map.of("totalFiles", 11, "distinctAuthors", 2);
              case "getApiDiff", "getPublicApiChanges" -> new SourceDiff(List.of(FILE), List.of(FILE), List.of(new Change(FILE, FILE, "methods changed")));
              case "searchFilePath" -> List.of(new SandboxFileHistory.FileVersion("commit", FILE.filePath(), "blob", "java", Instant.EPOCH));
              default -> List.of(FILE);
            };
          });
      var history = (SandboxHistorySearch) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {SandboxHistorySearch.class},
          (proxy, method, args) -> List.of());
      SandboxFileHistory files = (repo, path, offset, limit) -> List.of();
      var context = new ServletContextHandler(); context.setContextPath("/api"); server.setHandler(context);
      context.addServlet(new ServletHolder(new SearchResource(history, files, sources)), "/search/*");
      context.addServlet(new ServletHolder(new SearchResource(history, files, sources)), "/v1/search/*");
      server.start(); port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }
    HttpResponse<String> get(String path) throws Exception {
      return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @Override public void close() throws Exception { try { server.stop(); } finally { client.close(); } }
  }
}
