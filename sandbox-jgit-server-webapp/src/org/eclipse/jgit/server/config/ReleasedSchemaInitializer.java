/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import io.github.carstenartur.jgit.storage.hibernate.schema.CoreSchemaMigrations;
import io.github.carstenartur.jgit.storage.hibernate.search.schema.SearchSchemaMigrations;

/** Explicit, exclusive operator action for an empty schema; never performs legacy adoption. */
public final class ReleasedSchemaInitializer {
  private ReleasedSchemaInitializer() {}

  public static void initializeEmpty(Properties supplied) {
    var properties = ExternalCoreStartupGuard.validatedSettings(supplied);
    String url = properties.getProperty("hibernate.connection.url");
    var locations = ExternalCoreStartupGuard.locations(url);
    String user = properties.getProperty("hibernate.connection.username", "");
    String password = properties.getProperty("hibernate.connection.password", "");
    String schema = properties.getProperty("hibernate.default_schema");
    try {
      ExternalCoreStartupGuard.loadDriver(properties);
      try (var connection = DriverManager.getConnection(url, user, password)) {
        if (schema == null || schema.isBlank()) schema = connection.getSchema();
        if (schema == null || schema.isBlank()) throw new IllegalStateException("Set hibernate.default_schema for initialization");
        try (var tables = connection.getMetaData().getTables(connection.getCatalog(), schema, "%", new String[] {"TABLE", "VIEW", "BASE TABLE"})) {
          // JDBC takes a pattern here: '_' and '%' in an exact schema name also match other schemas.
          while (tables.next()) {
            if (schema.equals(tables.getString("TABLE_SCHEM"))) {
              throw new IllegalStateException("Schema initialization requires an empty schema; use the documented adoption procedure for existing data");
            }
          }
        }
      }
      configuration(url, user, password, schema).locations(locations.core()).table(CoreSchemaMigrations.SCHEMA_HISTORY_TABLE)
          .baselineOnMigrate(false).load().migrate();
      // Only our just-created Core tables can be present after the explicit empty-schema precondition.
      configuration(url, user, password, schema).locations(locations.search()).table(SearchSchemaMigrations.SCHEMA_HISTORY_TABLE)
          .baselineOnMigrate(true).baselineVersion("0").load().migrate();
      ExternalCoreStartupGuard.requireReady(properties);
    } catch (SQLException | ClassNotFoundException failure) {
      throw new IllegalStateException("Could not initialize the empty Core/Search schema", failure);
    }
  }

  private static FluentConfiguration configuration(String url, String user, String password, String schema) {
    return Flyway.configure().dataSource(url, user, password).defaultSchema(schema).schemas(schema)
        .createSchemas(false).cleanDisabled(true).failOnMissingLocations(true);
  }
}
