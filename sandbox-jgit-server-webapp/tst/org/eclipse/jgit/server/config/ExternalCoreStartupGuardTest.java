/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.config;

import static org.junit.Assert.*;
import java.util.Properties;
import org.junit.Test;

public class ExternalCoreStartupGuardTest {
  @Test
  public void rejectsAnyHibernateSchemaMutationModeBeforeConnecting() {
    var properties = new Properties();
    properties.setProperty("hibernate.connection.url", "jdbc:invalid:not-opened");
    for (String mode : new String[] {"update", "create", "create-drop", "none", ""}) {
      properties.setProperty("hibernate.hbm2ddl.auto", mode);
      var failure = assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
      assertTrue(failure.getMessage().contains("hbm2ddl.auto=validate"));
    }
  }

  @Test
  public void rejectsJpaDatabaseActionsThatOverrideHibernateValidation() {
    var properties = ReleasedSchemaHistoryTest.properties();
    ReleasedSchemaHistoryTest.core(properties).migrate();
    ReleasedSchemaHistoryTest.search(properties).migrate();
    for (String key : new String[] {"jakarta.persistence.schema-generation.database.action",
        "javax.persistence.schema-generation.database.action"}) {
      for (String action : new String[] {"drop-and-create", "none"}) {
        properties.setProperty(key, action);
        var failure = assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
        assertTrue(failure.getMessage().contains(key));
      }
      properties.remove(key);
    }
  }

  @Test
  public void acceptsMetadataQuotedConfiguredSchema() {
    var properties = ReleasedSchemaHistoryTest.properties();
    properties.setProperty("hibernate.default_schema", "tenant-one");
    ReleasedSchemaHistoryTest.core(properties).migrate();
    ReleasedSchemaHistoryTest.search(properties).migrate();
    ExternalCoreStartupGuard.requireReady(properties);
  }

  @Test
  public void rejectsSchemaQuotesUnsupportedByTheReleasedFlywayDriverBeforeConnecting() {
    var properties = ReleasedSchemaHistoryTest.properties();
    properties.setProperty("hibernate.connection.url", "jdbc:invalid:not-opened");
    properties.setProperty("hibernate.default_schema", "tenant\"one");
    var failure = assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
    assertTrue(failure.getMessage().contains("embedded double quotes"));
  }

  @Test
  public void doesNotAcceptHistoryFromADifferentConfiguredSchema() {
    var properties = ReleasedSchemaHistoryTest.properties();
    ReleasedSchemaHistoryTest.core(properties).migrate();
    ReleasedSchemaHistoryTest.search(properties).migrate();
    properties.setProperty("hibernate.default_schema", "unmigrated");
    assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
  }

  @Test
  public void rejectsUnsupportedDatabaseFamilyBeforeConnecting() {
    var properties = ReleasedSchemaHistoryTest.properties();
    properties.setProperty("hibernate.connection.url", "jdbc:mysql://unused");
    assertThrows(IllegalStateException.class, () -> ExternalCoreStartupGuard.requireReady(properties));
  }
}
