/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import static org.junit.Assert.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Verifies the shipped executable after shading, including its real CLI/bootstrap classpath. */
public class ReleasedPackagingIT {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private static final Path JAR = Path.of(System.getProperty("basedir", "."), "target", "jgit-server.jar").toAbsolutePath();
  private static final Pattern REST_PORT = Pattern.compile("REST API: http://0\\.0\\.0\\.0:(\\d+)/api/");

  @Test public void executableContainsReleasedModulesAndServerProvidersWithoutCopiedClasses() throws Exception {
    try (var jar = new JarFile(JAR.toFile())) {
      assertEquals("true", jar.getManifest().getMainAttributes().getValue("Multi-Release"));
      assertFalse("Copied implementation must not remain on the shipped classpath",
          jar.stream().anyMatch(e -> e.getName().startsWith("org/eclipse/jgit/storage/hibernate/")));
      for (String type : List.of(
          "io/github/carstenartur/jgit/storage/hibernate/DefaultHibernateRepositoryFactory",
          "io/github/carstenartur/jgit/storage/hibernate/search/service/GitHistorySearchService",
          "io/github/carstenartur/jgit/storage/hibernate/javaanalysis/JavaProjectAnalyzer",
          "org/hibernate/hikaricp/internal/HikariCPConnectionProvider",
          "com/zaxxer/hikari/HikariDataSource", "org/hibernate/cache/jcache/internal/JCacheRegionFactory",
          "com/github/benmanes/caffeine/jcache/spi/CaffeineCachingProvider", "org/h2/Driver"))
        assertNotNull("Missing runtime class: " + type, jar.getEntry(type + ".class"));
    }
  }

  @Test public void packagedCliInitializesOnlyExplicitlyAndRestartsWithValidatedSchema() throws Exception {
    Path root = temporary.newFolder().toPath();
    Path helpLog = root.resolve("help.log");
    var help = process(root, helpLog, "--help");
    try {
      assertTrue(help.waitFor(30, TimeUnit.SECONDS));
      assertEquals(Files.readString(helpLog, StandardCharsets.UTF_8), 0, help.exitValue());
      assertTrue(Files.readString(helpLog, StandardCharsets.UTF_8).contains("--initialize-empty-schema"));
    } finally { stop(help); }
    Path rejectedLog = root.resolve("uninitialized.log");
    var rejected = process(root, rejectedLog);
    try {
      assertTrue(rejected.waitFor(30, TimeUnit.SECONDS));
      assertNotEquals("Uninitialized database must fail normal startup", 0, rejected.exitValue());
    } finally { stop(rejected); }
    for (boolean initialize : List.of(true, false)) {
      Path log = root.resolve(initialize ? "initialize.log" : "restart.log");
      var child = initialize ? process(root, log, "--initialize-empty-schema") : process(root, log);
      try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
        int port = port(child, log);
        var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/health"))
            .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(response.body(), 200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
      } finally { stop(child); }
      // Windows uses TerminateProcess; only platforms supporting normal termination
      // can exercise a JVM shutdown hook. Initialization/restart run on every platform.
      if (child.supportsNormalTermination()) assertTrue("SIGTERM must close the application-owned database pool: " + Files.readString(log, StandardCharsets.UTF_8),
          Files.readString(log, StandardCharsets.UTF_8).contains("Shutdown completed."));
    }
  }

  private static Process process(Path root, Path log, String... arguments) throws Exception {
    var command = new ArrayList<String>();
    String java = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    command.add(Path.of(System.getProperty("java.home"), "bin", java).toString());
    command.add("-jar"); command.add(JAR.toString()); command.addAll(List.of(arguments));
    var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
    var env = builder.environment();
    env.put("JGIT_DB_URL", "jdbc:h2:file:" + root.resolve("database").toString().replace('\\', '/'));
    env.put("JGIT_DB_USER", "sa"); env.put("JGIT_DB_PASSWORD", ""); env.remove("JGIT_DB_PASSWORD_FILE");
    env.put("JGIT_DB_DIALECT", "org.hibernate.dialect.H2Dialect"); env.put("JGIT_DB_DRIVER", "org.h2.Driver");
    env.put("JGIT_DB_DDL_AUTO", "validate"); env.put("JGIT_REST_PORT", "0"); env.put("JGIT_GIT_PORT", "0");
    env.put("JGIT_SEARCH_BACKEND", "lucene"); env.put("JGIT_SEARCH_INDEX_DIR", root.resolve("index").toString());
    env.put("JGIT_EMBEDDING_ENABLED", "false"); env.remove("JGIT_DEFAULT_REPOS");
    return builder.start();
  }

  private static int port(Process child, Path log) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
    while (System.nanoTime() < deadline) {
      String output = Files.readString(log, StandardCharsets.UTF_8);
      var match = REST_PORT.matcher(output);
      if (match.find()) return Integer.parseInt(match.group(1));
      assertTrue(output, child.isAlive());
      Thread.sleep(100);
    }
    throw new AssertionError("Packaged server did not become ready: " + Files.readString(log, StandardCharsets.UTF_8));
  }

  private static void stop(Process child) throws InterruptedException {
    child.destroy();
    if (!child.waitFor(10, TimeUnit.SECONDS)) { child.destroyForcibly(); child.waitFor(10, TimeUnit.SECONDS); }
  }
}
