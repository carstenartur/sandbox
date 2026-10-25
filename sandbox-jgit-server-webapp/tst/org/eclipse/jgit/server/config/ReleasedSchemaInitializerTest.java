/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import static org.junit.Assert.*;
import java.sql.DriverManager;
import org.junit.Test;

public class ReleasedSchemaInitializerTest {
  @Test public void rejectsCatalogOverridesBeforeCreatingAnyTable() throws Exception {
    var properties = ReleasedSchemaHistoryTest.properties();
    properties.setProperty("hibernate.default_catalog", "other_database");
    var failure = assertThrows(IllegalStateException.class, () -> ReleasedSchemaInitializer.initializeEmpty(properties));
    assertTrue(failure.getMessage().contains("hibernate.default_catalog"));
    assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
    try (var connection = DriverManager.getConnection(properties.getProperty("hibernate.connection.url"), "sa", "");
        var tables = connection.getMetaData().getTables(null, "PUBLIC", "%", new String[] {"TABLE"})) {
      assertFalse(tables.next());
    }
  }

  @Test public void targetsTheExactSchemaEvenWhenItsNameContainsMetadataWildcards() throws Exception {
    var properties = ReleasedSchemaHistoryTest.properties();
    properties.setProperty("hibernate.default_schema", "tenant_1");
    try (var connection = DriverManager.getConnection(properties.getProperty("hibernate.connection.url"), "sa", "");
        var statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA \"tenant_1\"");
      statement.execute("CREATE SCHEMA \"tenantA1\"");
      statement.execute("CREATE TABLE \"tenantA1\".operator_data (stored_number INTEGER)");
      statement.execute("INSERT INTO \"tenantA1\".operator_data VALUES (7)");
      ReleasedSchemaInitializer.initializeEmpty(properties);
      ExternalCoreStartupGuard.requireReady(properties);
      try (var rows = statement.executeQuery("SELECT stored_number FROM \"tenantA1\".operator_data")) {
        assertTrue(rows.next()); assertEquals(7, rows.getInt(1));
      }
      try (var rows = connection.getMetaData().getTables(null, "PUBLIC", "%", new String[] {"TABLE"})) { assertFalse(rows.next()); }
    }
  }

  @Test public void explicitlyInitializesEmptyH2AndHsqlSchemasThenNormalValidationIsReadOnly() throws Exception {
    for (String kind : java.util.List.of("h2", "hsqldb")) {
      var properties = ReleasedSchemaHistoryTest.properties();
      properties.setProperty("hibernate.connection.url", "jdbc:" + kind + ":mem:init-" + java.util.UUID.randomUUID());
      properties.setProperty("hibernate.connection.driver_class", kind.equals("h2") ? "org.h2.Driver" : "org.hsqldb.jdbc.JDBCDriver");
      if (kind.equals("h2")) properties.setProperty("hibernate.connection.url", properties.getProperty("hibernate.connection.url") + ";DB_CLOSE_DELAY=-1");
      ReleasedSchemaInitializer.initializeEmpty(properties);
      ExternalCoreStartupGuard.requireReady(properties);
      assertThrows(IllegalStateException.class, () -> ReleasedSchemaInitializer.initializeEmpty(properties));
      ExternalCoreStartupGuard.requireReady(properties);
    }
  }

  @Test public void refusesAnyExistingTableWithoutAdoptingOrAlteringIt() throws Exception {
    var properties = ReleasedSchemaHistoryTest.properties();
    try (var connection = DriverManager.getConnection(properties.getProperty("hibernate.connection.url"), "sa", "");
        var statement = connection.createStatement()) {
      statement.execute("CREATE TABLE operator_data (stored_number INTEGER)");
      statement.execute("INSERT INTO operator_data VALUES (7)");
      assertThrows(IllegalStateException.class, () -> ReleasedSchemaInitializer.initializeEmpty(properties));
      try (var rows = statement.executeQuery("SELECT stored_number FROM operator_data")) { assertTrue(rows.next()); assertEquals(7, rows.getInt(1)); }
      try (var rows = connection.getMetaData().getTables(null, "PUBLIC", "%", new String[] {"TABLE"})) {
        int count = 0; while (rows.next()) count++; assertEquals(1, count);
      }
    }
  }

  @Test public void rejectsDdlOverridesBeforeWritingMigrationHistories() {
    var properties = ReleasedSchemaHistoryTest.properties();
    properties.setProperty("jakarta.persistence.schema-generation.database.action", "create");
    assertThrows(IllegalStateException.class, () -> ReleasedSchemaInitializer.initializeEmpty(properties));
    properties.remove("jakarta.persistence.schema-generation.database.action");
    assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
  }
}
