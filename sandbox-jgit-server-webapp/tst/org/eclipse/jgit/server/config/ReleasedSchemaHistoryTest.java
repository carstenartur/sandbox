/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import static org.junit.Assert.*;

import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.Test;
import io.github.carstenartur.jgit.storage.hibernate.schema.CoreSchemaMigrations;
import io.github.carstenartur.jgit.storage.hibernate.search.schema.SearchSchemaMigrations;

public class ReleasedSchemaHistoryTest {
  @Test
  public void requiresCompleteCoreAndSearchHistoriesWithoutCreatingEither() throws Exception {
    var properties = properties();
    assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
    assertEquals(0, histories(properties));
    core(properties).migrate();
    assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
    assertEquals(1, histories(properties));
    search(properties).migrate();
    ExternalCoreStartupGuard.requireReady(properties);
    assertEquals(2, histories(properties));
  }

  @Test
  public void rejectsPendingMigrationsEvenWithAnOlderSuccessfulVersion() {
    var properties = properties();
    core(properties).migrate();
    searchConfiguration(properties).target("0.1.4").load().migrate();
    assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
  }

  @Test
  public void rejectsAlteredChecksumsAndFailedRowsAndFutureVersions() throws Exception {
    for (String change : new String[] {"\"checksum\" = 12345", "\"success\" = false", "\"version\" = '999'"}) {
      var properties = properties();
      core(properties).migrate();
      search(properties).migrate();
      try (var connection = DriverManager.getConnection(properties.getProperty("hibernate.connection.url"), "sa", "");
          var statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE \"" + SearchSchemaMigrations.SCHEMA_HISTORY_TABLE + "\" SET " + change
            + " WHERE \"installed_rank\" = (SELECT MAX(\"installed_rank\") FROM \""
            + SearchSchemaMigrations.SCHEMA_HISTORY_TABLE + "\")");
      }
      assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
    }
  }

  static Properties properties() {
    var properties = new Properties();
    properties.setProperty("hibernate.connection.url", "jdbc:h2:mem:history-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    properties.setProperty("hibernate.connection.username", "sa");
    properties.setProperty("hibernate.connection.password", "");
    properties.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    properties.setProperty("hibernate.hbm2ddl.auto", "validate");
    return properties;
  }

  static Flyway core(Properties properties) {
    return configuration(properties).locations(CoreSchemaMigrations.H2_LOCATION)
        .table(CoreSchemaMigrations.SCHEMA_HISTORY_TABLE).load();
  }

  static Flyway search(Properties properties) { return searchConfiguration(properties).load(); }

  static org.flywaydb.core.api.configuration.FluentConfiguration searchConfiguration(Properties properties) {
    return configuration(properties).locations(SearchSchemaMigrations.H2_LOCATION)
        .table(SearchSchemaMigrations.SCHEMA_HISTORY_TABLE).baselineOnMigrate(true).baselineVersion("0");
  }

  private static org.flywaydb.core.api.configuration.FluentConfiguration configuration(Properties properties) {
    var configuration = Flyway.configure().dataSource(properties.getProperty("hibernate.connection.url"), "sa", "");
    String schema = properties.getProperty("hibernate.default_schema");
    if (schema != null) configuration.defaultSchema(schema).schemas(schema);
    return configuration;
  }

  private static int histories(Properties properties) throws Exception {
    try (var connection = DriverManager.getConnection(properties.getProperty("hibernate.connection.url"), "sa", "");
        var rows = connection.getMetaData().getTables(null, "PUBLIC", "%", new String[] {"TABLE"})) {
      int count = 0;
      while (rows.next()) if (rows.getString("TABLE_NAME").endsWith("schema_history")) count++;
      return count;
    }
  }
}
