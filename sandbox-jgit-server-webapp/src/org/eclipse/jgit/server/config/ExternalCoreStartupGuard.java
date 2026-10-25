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
import io.github.carstenartur.jgit.storage.hibernate.schema.CoreSchemaMigrations;
import io.github.carstenartur.jgit.storage.hibernate.search.schema.SearchSchemaMigrations;

/** Read-only validation of both released migration streams before Hibernate bootstrap. */
final class ExternalCoreStartupGuard {
  private ExternalCoreStartupGuard() {}

  static void requireReady(Properties properties) {
    Objects.requireNonNull(properties, "properties");
    if (!"validate".equalsIgnoreCase(properties.getProperty("hibernate.hbm2ddl.auto", "").strip())) {
      throw new IllegalStateException("Released storage requires hibernate.hbm2ddl.auto=validate; migrate Core and Search first.");
    }
    for (String key : new String[] {"jakarta.persistence.schema-generation.database.action",
        "javax.persistence.schema-generation.database.action"}) {
      if (properties.get(key) != null) {
        throw new IllegalStateException("Remove " + key + "; it overrides hibernate.hbm2ddl.auto=validate.");
      }
    }
    String url = Objects.requireNonNull(properties.getProperty("hibernate.connection.url"), "JDBC URL");
    String schema = properties.getProperty("hibernate.default_schema");
    if (schema != null && schema.contains("\"")) {
      throw new IllegalStateException("Flyway validation does not support embedded double quotes in the schema name.");
    }
    String core;
    String search;
    if (url.startsWith("jdbc:h2:")) {
      core = CoreSchemaMigrations.H2_LOCATION; search = SearchSchemaMigrations.H2_LOCATION;
    } else if (url.startsWith("jdbc:hsqldb:")) {
      core = CoreSchemaMigrations.HSQLDB_LOCATION;
      search = SearchSchemaMigrations.HSQLDB_LOCATION;
    } else if (url.startsWith("jdbc:postgresql:")) {
      core = CoreSchemaMigrations.POSTGRESQL_LOCATION; search = SearchSchemaMigrations.POSTGRESQL_LOCATION;
    } else if (url.startsWith("jdbc:sqlserver:")) {
      core = CoreSchemaMigrations.SQL_SERVER_LOCATION; search = SearchSchemaMigrations.SQL_SERVER_LOCATION;
    } else {
      throw new IllegalStateException("No released Core/Search migration pair for this database family.");
    }
    try {
      String driver = properties.getProperty("hibernate.connection.driver_class");
      if (driver != null && !driver.isBlank()) Class.forName(driver);
      var source = new ReadOnlySource(url, properties.getProperty("hibernate.connection.username", ""),
          properties.getProperty("hibernate.connection.password", ""));
      validate(source, schema, CoreSchemaMigrations.SCHEMA_HISTORY_TABLE, core);
      validate(source, schema, SearchSchemaMigrations.SCHEMA_HISTORY_TABLE, search);
    } catch (ClassNotFoundException | RuntimeException failure) {
      throw new IllegalStateException("Released Core/Search migration validation failed before Hibernate startup.", failure);
    }
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
