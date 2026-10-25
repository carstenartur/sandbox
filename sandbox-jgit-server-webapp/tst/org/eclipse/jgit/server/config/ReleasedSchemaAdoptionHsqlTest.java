/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.revwalk.RevWalk;
import org.flywaydb.core.Flyway;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import io.github.carstenartur.jgit.storage.hibernate.RepositoryName;
import io.github.carstenartur.jgit.storage.hibernate.schema.CoreSchemaMigrations;
import io.github.carstenartur.jgit.storage.hibernate.search.schema.SearchSchemaMigrations;
import io.github.carstenartur.jgit.storage.hibernate.search.service.CommitProjectionRebuilder;

/** Rehearses adoption of a frozen SQL/BLOB database captured from the copied backend. */
public class ReleasedSchemaAdoptionHsqlTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private static final String NAME = "copied-example";
  private static final PersonIdent AUTHOR = new PersonIdent("Müller", "author@example.org",
      Instant.parse("2025-01-02T03:04:05Z"), ZoneOffset.UTC);

  @Test
  public void adoptsCopiedStorageAndRebuildsSearchWithoutChangingGitOrArchivedRows() throws Exception {
    String url = "jdbc:hsqldb:file:" + temporary.getRoot().toPath().resolve("legacy").toString().replace('\\', '/');
    ObjectId original = ObjectId.fromString("7b7b9e9852d8d97bd49feebe76c28a15a4a7cac7");
    try {
      restoreLegacyFixture(url);
      var packs = packChecksums(url);
      var reflogs = rows(url, "SELECT id, version, repository_name, ref_name, old_id, new_id, who_name, who_email, who_when, message FROM git_reflog ORDER BY id");
      var projection = rows(url, "SELECT object_id, commit_message, author_name, author_email, commit_time FROM git_commit_index ORDER BY id");
      assertFalse(packs.isEmpty());
      assertEquals(1, reflogs.size());
      assertEquals(1, projection.size());
      shutdown(url);

      var settings = properties(url + ";ifexists=true", "validate");
      assertTrue(LegacyCoreSchemaPreflight.inspect(settings).requiresAdoption());
      assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(settings));
      Flyway.configure().dataSource(url, "SA", "").locations(CoreSchemaMigrations.HSQLDB_LEGACY_ADOPTION_LOCATION)
          .table(CoreSchemaMigrations.LEGACY_ADOPTION_SCHEMA_HISTORY_TABLE).baselineOnMigrate(true)
          .baselineVersion(CoreSchemaMigrations.PRE_MIGRATION_BASELINE_VERSION).load().migrate();
      Flyway.configure().dataSource(url, "SA", "").locations(CoreSchemaMigrations.HSQLDB_LOCATION)
          .table(CoreSchemaMigrations.SCHEMA_HISTORY_TABLE).baselineOnMigrate(true)
          .baselineVersion(CoreSchemaMigrations.CURRENT_SCHEMA_VERSION).load().migrate();
      assertFalse(LegacyCoreSchemaPreflight.inspect(settings).requiresAdoption());
      assertEquals(packs, packChecksums(url));
      assertEquals(reflogs, rows(url, "SELECT id, version, repository_name, ref_name, old_id, new_id, who_name, who_email, who_when, message FROM git_reflog ORDER BY id"));

      // Explicitly archive the incompatible derived table and its conflicting index names.
      // The startup guard does none of these writes.
      try (var connection = DriverManager.getConnection(url, "SA", ""); var statement = connection.createStatement()) {
        statement.execute("ALTER TABLE git_commit_index RENAME TO sandbox_legacy_commit_index");
        statement.execute("ALTER INDEX idx_commit_repo RENAME TO sandbox_legacy_commit_repo");
        statement.execute("ALTER INDEX idx_commit_repo_time RENAME TO sandbox_legacy_commit_repo_time");
      }
      var search = Flyway.configure().dataSource(url, "SA", "")
          .locations(SearchSchemaMigrations.HSQLDB_LOCATION)
          .table(SearchSchemaMigrations.SCHEMA_HISTORY_TABLE).baselineOnMigrate(true)
          .baselineVersion(SearchSchemaMigrations.PRE_MIGRATION_BASELINE_VERSION).failOnMissingLocations(true).load();
      search.migrate();
      assertEquals(0, search.migrate().migrationsExecuted);
      ExternalCoreStartupGuard.requireReady(settings);
      assertEquals(projection, rows(url, "SELECT object_id, commit_message, author_name, author_email, commit_time FROM sandbox_legacy_commit_index ORDER BY id"));

      try (var released = HibernateConfig.createExternalPersistenceContext(settings)) {
        // The released normal-open API establishes per-repository lock metadata after adoption.
        // Existing-only security reads deliberately never initialize missing metadata themselves.
        try (var initialized = new io.github.carstenartur.jgit.storage.hibernate.DefaultHibernateRepositoryFactory(
            released.sessionFactory()).open(new RepositoryName(NAME))) {
          assertEquals(original, initialized.repository().resolve(Constants.R_HEADS + "main"));
        }
        released.repositoryService().withExistingRepository(NAME, repository -> {
          assertEquals(original, repository.resolve(Constants.R_HEADS + "main"));
          try (var walk = new RevWalk(repository)) {
            assertEquals("historical needle", walk.parseCommit(original).getFullMessage());
          }
          assertEquals("legacy reflog preserved", repository.getReflogReader(Constants.R_HEADS + "main").getLastEntry().getComment());
          var rebuilt = new CommitProjectionRebuilder(released.sessionFactory()).rebuild(repository, new RepositoryName(NAME));
          assertEquals(1, rebuilt.indexedCommits());
          return null;
        });
        assertEquals(1, released.historySearch().searchMessages(NAME, "needle", 0, 10).size());
      }
      assertEquals(packs, packChecksums(url));
      assertEquals(reflogs, rows(url, "SELECT id, version, repository_name, ref_name, old_id, new_id, who_name, who_email, who_when, message FROM git_reflog ORDER BY id"));
      shutdown(url);
      try (var released = HibernateConfig.createExternalPersistenceContext(settings)) {
        released.historyMaintenance().refreshIndexes(released.sessionFactory());
        assertEquals(1, released.historySearch().searchMessages(NAME, "needle", 0, 10).size());
        var repository = released.repositoryService().openOrCreate(NAME);
        ObjectId next = commit(repository, "released write", original);
        assertEquals(next, repository.resolve(Constants.R_HEADS + "main"));
        assertTrue(repository.getReflogReader(Constants.R_HEADS + "main").getReverseEntries().stream()
            .anyMatch(entry -> next.equals(entry.getNewId())));
      }
    } finally {
      shutdown(url);
    }
  }

  private static void restoreLegacyFixture(String url) throws Exception {
    byte[] bytes;
    try (var input = ReleasedSchemaAdoptionHsqlTest.class.getResourceAsStream("/fixtures/copied-hsql-prelibrary.sql")) {
      assertNotNull("Pinned legacy fixture is packaged", input); bytes = input.readAllBytes();
    }
    assertEquals("b5a91d7a535fe08d2852562b5cf66a92521dda94a5468aa5c8e29d7ed0551780",
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    try (var connection = DriverManager.getConnection(url, "SA", ""); var statement = connection.createStatement()) {
      for (String line : new String(bytes, StandardCharsets.UTF_8).lines().toList()) {
        if (!line.isBlank() && !line.startsWith("--")) statement.execute(line);
      }
    }
  }

  private static ObjectId commit(Repository repository, String message, ObjectId parent) throws Exception {
    ObjectId id;
    try (var inserter = repository.newObjectInserter()) {
      var tree = new TreeFormatter();
      tree.append("readme.txt", FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, message.getBytes(StandardCharsets.UTF_8)));
      var builder = new CommitBuilder();
      builder.setTreeId(inserter.insert(tree));
      if (parent != null) builder.setParentId(parent);
      builder.setAuthor(AUTHOR); builder.setCommitter(AUTHOR); builder.setMessage(message);
      id = inserter.insert(builder); inserter.flush();
    }
    var update = repository.updateRef(Constants.R_HEADS + "main");
    update.setNewObjectId(id); update.setRefLogIdent(AUTHOR); update.setRefLogMessage(message, false);
    assertEquals(parent == null ? RefUpdate.Result.NEW : RefUpdate.Result.FAST_FORWARD, update.update());
    return id;
  }

  private static Properties properties(String url, String ddl) {
    var properties = new Properties();
    properties.setProperty("hibernate.connection.url", url);
    properties.setProperty("hibernate.connection.username", "SA");
    properties.setProperty("hibernate.connection.password", "");
    properties.setProperty("hibernate.connection.driver_class", "org.hsqldb.jdbc.JDBCDriver");
    properties.setProperty("hibernate.hbm2ddl.auto", ddl);
    properties.setProperty("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl");
    properties.setProperty("hibernate.cache.use_second_level_cache", "false");
    properties.setProperty("hibernate.search.backend.directory.type", "local-heap");
    properties.setProperty("hibernate.search.indexing.plan.synchronization.strategy", "sync");
    return properties;
  }

  private static List<String> packChecksums(String url) throws Exception {
    var checksums = new ArrayList<String>();
    var digest = MessageDigest.getInstance("SHA-256");
    try (var connection = DriverManager.getConnection(url, "SA", ""); var statement = connection.createStatement();
        var rows = statement.executeQuery("SELECT id, repository_name, pack_name, pack_extension, data FROM git_packs ORDER BY id")) {
      while (rows.next()) checksums.add(rows.getLong(1) + "/" + rows.getString(2) + "/" + rows.getString(3) + "."
          + rows.getString(4) + "=" + HexFormat.of().formatHex(digest.digest(rows.getBytes(5))));
    }
    return List.copyOf(checksums);
  }

  private static List<List<Object>> rows(String url, String sql) throws Exception {
    var result = new ArrayList<List<Object>>();
    try (var connection = DriverManager.getConnection(url, "SA", ""); var statement = connection.createStatement();
        var rows = statement.executeQuery(sql)) {
      while (rows.next()) {
        var row = new ArrayList<Object>();
        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.add(rows.getObject(i));
        result.add(row);
      }
    }
    return result;
  }

  private static void shutdown(String url) throws Exception {
    try (var connection = DriverManager.getConnection(url, "SA", ""); var statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
  }
}
