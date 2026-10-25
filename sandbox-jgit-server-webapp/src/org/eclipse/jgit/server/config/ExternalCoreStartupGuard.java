/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Environment;
import io.github.carstenartur.jgit.storage.hibernate.schema.CoreSchemaMigrations;
import io.github.carstenartur.jgit.storage.hibernate.search.schema.SearchSchemaMigrations;

/** Read-only validation of both released migration streams before Hibernate bootstrap. */
final class ExternalCoreStartupGuard {
  private ExternalCoreStartupGuard() {}

  static Properties requireReady(Properties supplied) {
    var properties = validatedSettings(supplied);
    String url = properties.getProperty("hibernate.connection.url");
    String schema = properties.getProperty("hibernate.default_schema");
    var locations = locations(url);
    try {
      loadDriver(properties);
      var source = new ReadOnlySource(url, properties.getProperty("hibernate.connection.username", ""),
          properties.getProperty("hibernate.connection.password", ""));
      validate(source, schema, CoreSchemaMigrations.SCHEMA_HISTORY_TABLE, locations.core());
      validate(source, schema, SearchSchemaMigrations.SCHEMA_HISTORY_TABLE, locations.search());
    } catch (ClassNotFoundException | RuntimeException failure) {
      throw new IllegalStateException("Released Core/Search migration validation failed before Hibernate startup.", failure);
    }
    return properties;
  }

  static Properties validatedSettings(Properties supplied) {
    Objects.requireNonNull(supplied, "properties");
    // Configuration inherits this JVM/classpath snapshot before addProperties overlays caller entries.
    // Validate and return precisely that flat snapshot for the subsequent provider bootstrap.
    var properties = new Properties();
    properties.putAll(Environment.getProperties());
    properties.putAll(supplied);
    String catalog = properties.getProperty("hibernate.default_catalog");
    if (catalog != null && !catalog.isBlank()) {
      throw new IllegalStateException("Remove hibernate.default_catalog; select the migration and Hibernate database in the JDBC URL.");
    }
    if (!"validate".equalsIgnoreCase(properties.getProperty("hibernate.hbm2ddl.auto", "").strip())) {
      throw new IllegalStateException("Released storage requires hibernate.hbm2ddl.auto=validate; migrate Core and Search first.");
    }
    for (Object setting : properties.keySet()) {
      if (!(setting instanceof String key)) continue;
      for (String action : new String[] {"jakarta.persistence.schema-generation.database.action",
          "javax.persistence.schema-generation.database.action", "jakarta.persistence.schema-generation.scripts.action",
          "javax.persistence.schema-generation.scripts.action", "hibernate.hbm2ddl.auto"}) {
        if ((!action.equals("hibernate.hbm2ddl.auto") && key.equals(action)) || key.startsWith(action + ".")) {
          throw new IllegalStateException("Remove " + key + "; it overrides hibernate.hbm2ddl.auto=validate.");
        }
      }
    }
    Objects.requireNonNull(properties.getProperty("hibernate.connection.url"), "JDBC URL");
    String schema = properties.getProperty("hibernate.default_schema");
    if (schema != null && schema.contains("\"")) {
      throw new IllegalStateException("Flyway validation does not support embedded double quotes in the schema name.");
    }
    return properties;
  }

  record Locations(String core, String search) {}

  static Locations locations(String url) {
    if (url.startsWith("jdbc:h2:")) {
      return new Locations(CoreSchemaMigrations.H2_LOCATION, SearchSchemaMigrations.H2_LOCATION);
    } else if (url.startsWith("jdbc:hsqldb:")) {
      return new Locations(CoreSchemaMigrations.HSQLDB_LOCATION, SearchSchemaMigrations.HSQLDB_LOCATION);
    } else if (url.startsWith("jdbc:postgresql:")) {
      return new Locations(CoreSchemaMigrations.POSTGRESQL_LOCATION, SearchSchemaMigrations.POSTGRESQL_LOCATION);
    } else if (url.startsWith("jdbc:sqlserver:")) {
      return new Locations(CoreSchemaMigrations.SQL_SERVER_LOCATION, SearchSchemaMigrations.SQL_SERVER_LOCATION);
    } else {
      throw new IllegalStateException("No released Core/Search migration pair for this database family.");
    }
  }

  static void loadDriver(Properties properties) throws ClassNotFoundException {
    String driver = properties.getProperty("hibernate.connection.driver_class");
    if (driver != null && !driver.isBlank()) Class.forName(driver);
  }

  private static void validate(DataSource source, String schema, String table, String location) {
    var configuration = Flyway.configure().dataSource(source).locations(location).table(table)
        .createSchemas(false).cleanDisabled(true).failOnMissingLocations(true).ignoreMigrationPatterns(new String[0]);
    if (schema != null && !schema.isBlank()) configuration.defaultSchema(schema).schemas(schema);
    var flyway = configuration.load();
    var information = flyway.info();
    if (information.pending().length != 0
        || Arrays.stream(information.applied()).noneMatch(migration -> migration.getVersion() != null
            && "SQL".equals(migration.getType().name()) && migration.getState().isApplied())) {
      throw new IllegalStateException("Missing or incomplete released migration stream: " + table);
    }
    flyway.validate();
  }

  /** Flyway receives only read-only connections; no migrate/repair/baseline API is called. */
  private static final class ReadOnlySource implements DataSource {
    private final String url;
    private final String user;
    private final String password;
    ReadOnlySource(String url, String user, String password) {
      this.url = url; this.user = user; this.password = password;
    }
    @Override public Connection getConnection() throws SQLException { return getConnection(user, password); }
    @Override public Connection getConnection(String username, String suppliedPassword) throws SQLException {
      Connection connection = DriverManager.getConnection(url, username, suppliedPassword);
      try {
        connection.setReadOnly(true);
        return connection;
      } catch (SQLException failure) {
        try { connection.close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
        throw failure;
      }
    }
    @Override public PrintWriter getLogWriter() { return null; }
    @Override public void setLogWriter(PrintWriter writer) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public int getLoginTimeout() { return 0; }
    @Override public void setLoginTimeout(int seconds) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean isWrapperFor(Class<?> type) { return type.isInstance(this); }
    @Override public <T> T unwrap(Class<T> type) throws SQLException {
      if (type.isInstance(this)) return type.cast(this);
      throw new SQLException("Not a wrapper for " + type.getName());
    }
  }
}
