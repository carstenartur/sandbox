/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;

public class AnalysisSettingsTest {
  @Test public void preservesDefaultSelectionAndUsesExplicitLocalDependencyRepository() {
    var settings = AnalysisSettings.from(Map.of(), Path.of("local-artifacts"));
    assertEquals(1024 * 1024, settings.maxBlobBytes());
    assertTrue(settings.allFileTypes()); assertTrue(settings.filePathHistory());
    assertTrue(settings.skippedExtensions().containsAll(java.util.Set.of(".png", ".class", ".jar", ".ear")));
    assertEquals(Path.of("local-artifacts").toAbsolutePath().normalize(), settings.localRepository());
    assertEquals(4, settings.retainedGenerations()); assertEquals(2, settings.concurrentBuilds());
  }

  @Test public void honorsLegacyOverridesAndRejectsInvalidResourceLimitsBeforeStartup() {
    var settings = AnalysisSettings.from(Map.of("JGIT_INDEX_MAX_BLOB_SIZE", "4096",
        "JGIT_INDEX_ALL_FILE_TYPES", "false", "JGIT_INDEX_FILE_PATH_HISTORY", "FALSE",
        "JGIT_INDEX_SKIP_EXTENSIONS", ".PNG, .pdf", "JGIT_ANALYSIS_CACHE_GENERATIONS", "3",
        "JGIT_ANALYSIS_CONCURRENT_BUILDS", "1", "JGIT_ANALYSIS_LOCAL_REPOSITORY", "other-artifacts"), Path.of("unused"));
    assertEquals(4096, settings.maxBlobBytes()); assertFalse(settings.allFileTypes()); assertFalse(settings.filePathHistory());
    assertEquals(java.util.Set.of(".png", ".pdf"), settings.skippedExtensions());
    assertEquals(Path.of("other-artifacts").toAbsolutePath().normalize(), settings.localRepository());
    assertEquals(3, settings.retainedGenerations()); assertEquals(1, settings.concurrentBuilds());
    for (String key : java.util.List.of("JGIT_INDEX_MAX_BLOB_SIZE", "JGIT_ANALYSIS_CACHE_GENERATIONS", "JGIT_ANALYSIS_CONCURRENT_BUILDS")) {
      for (String value : java.util.List.of("0", "-1", "no", "2147483648")) {
        var failure = assertThrows(IllegalArgumentException.class, () -> AnalysisSettings.from(Map.of(key, value), Path.of("unused")));
        assertTrue(failure.getMessage().contains(key));
      }
    }
  }
}
