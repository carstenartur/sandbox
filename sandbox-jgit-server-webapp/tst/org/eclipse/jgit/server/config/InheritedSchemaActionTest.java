/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class InheritedSchemaActionTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private static final List<String> ACTIONS = List.of("jakarta.persistence.schema-generation.database.action",
      "javax.persistence.schema-generation.scripts.action", "hibernate.hbm2ddl.auto.orm");

  @Test public void rejectsJvmInheritedActionsBeforeAnyJdbcConnection() throws Exception { probe(false); }
  @Test public void rejectsClasspathInheritedActionsBeforeAnyJdbcConnection() throws Exception { probe(true); }

  private void probe(boolean classpathSettings) throws Exception {
    for (String key : ACTIONS) {
      Path directory = temporary.newFolder().toPath();
      String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
      var command = new java.util.ArrayList<String>();
      command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
      if (classpathSettings) {
        Files.writeString(directory.resolve("hibernate.properties"), key + "=create\n");
        classpath = directory + java.io.File.pathSeparator + classpath;
      } else command.add("-D" + key + "=create");
      command.addAll(List.of("-cp", classpath, Probe.class.getName(), key));
      Path output = directory.resolve("probe.log");
      var process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
      if (!process.waitFor(45, TimeUnit.SECONDS)) {
        process.destroyForcibly(); fail("Inherited settings probe timed out: " + key);
      }
      assertEquals(Files.readString(output), 0, process.exitValue());
    }
  }

  /** A new JVM is essential: Hibernate snapshots JVM/classpath defaults at Environment initialization. */
  public static class Probe {
    public static void main(String[] arguments) throws Exception {
      String key = arguments[0];
      if (!"create".equals(new org.hibernate.cfg.Configuration().getProperties().get(key))) {
        throw new AssertionError("Fixture did not supply the inherited action: " + key);
      }
      var driver = new CountingDriver(); DriverManager.registerDriver(driver);
      var properties = new Properties();
      properties.setProperty("hibernate.hbm2ddl.auto", "validate");
      properties.setProperty("hibernate.connection.url", "jdbc:h2:guard-probe");
      try {
        ExternalCoreStartupGuard.requireReady(properties);
        throw new AssertionError("Inherited action accepted: " + key);
      } catch (IllegalStateException rejected) {
        if (!rejected.getMessage().contains(key)) throw new AssertionError("Wrong rejection", rejected);
      }
      if (driver.connections != 0) throw new AssertionError("Guard attempted a JDBC connection");
    }
  }

  private static class CountingDriver implements Driver {
    int connections;
    @Override public Connection connect(String url, Properties properties) throws SQLException {
      if (!acceptsURL(url)) return null;
      connections++; throw new SQLException("The guard must reject inherited actions before JDBC");
    }
    @Override public boolean acceptsURL(String url) { return url.equals("jdbc:h2:guard-probe"); }
    @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties properties) { return new DriverPropertyInfo[0]; }
    @Override public int getMajorVersion() { return 1; }
    @Override public int getMinorVersion() { return 0; }
    @Override public boolean jdbcCompliant() { return false; }
    @Override public Logger getParentLogger() { return Logger.getGlobal(); }
  }
}
