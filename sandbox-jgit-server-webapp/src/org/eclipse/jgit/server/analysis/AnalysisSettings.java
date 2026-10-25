/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable application policy; generic analysis receives explicit inputs only. */
public record AnalysisSettings(Path localRepository, int maxBlobBytes, boolean allFileTypes,
    boolean filePathHistory, Set<String> skippedExtensions, int retainedGenerations, int concurrentBuilds) {
  private static final Set<String> DEFAULT_SKIPPED = Set.of(".class", ".jar", ".png", ".jpg", ".jpeg", ".gif",
      ".zip", ".tar", ".gz", ".bz2", ".pdf", ".so", ".dll", ".exe", ".ico", ".war", ".ear");

  public AnalysisSettings {
    localRepository = Objects.requireNonNull(localRepository).toAbsolutePath().normalize();
    skippedExtensions = Set.copyOf(skippedExtensions);
    if (maxBlobBytes < 1 || retainedGenerations < 1 || concurrentBuilds < 1) throw new IllegalArgumentException("Analysis limits must be positive");
  }

  public static AnalysisSettings fromEnvironment() {
    String configured = System.getProperty("maven.repo.local");
    Path local = configured == null || configured.isBlank() ? Path.of(System.getProperty("user.home"), ".m2", "repository") : Path.of(configured);
    return from(System.getenv(), local);
  }

  static AnalysisSettings from(Map<String, String> environment, Path defaultLocalRepository) {
    String extensions = environment.get("JGIT_INDEX_SKIP_EXTENSIONS");
    var skipped = extensions == null || extensions.isEmpty() ? DEFAULT_SKIPPED : Arrays.stream(extensions.split(","))
        .map(String::strip).map(e -> e.toLowerCase(Locale.ROOT)).filter(e -> !e.isEmpty()).collect(Collectors.toUnmodifiableSet());
    String local = environment.get("JGIT_ANALYSIS_LOCAL_REPOSITORY");
    return new AnalysisSettings(local == null || local.isBlank() ? defaultLocalRepository : Path.of(local),
        positive(environment, "JGIT_INDEX_MAX_BLOB_SIZE", 1024 * 1024),
        !"false".equalsIgnoreCase(environment.get("JGIT_INDEX_ALL_FILE_TYPES")),
        !"false".equalsIgnoreCase(environment.get("JGIT_INDEX_FILE_PATH_HISTORY")), skipped,
        positive(environment, "JGIT_ANALYSIS_CACHE_GENERATIONS", 4), positive(environment, "JGIT_ANALYSIS_CONCURRENT_BUILDS", 2));
  }

  private static int positive(Map<String, String> values, String name, int fallback) {
    String value = values.get(name);
    if (value == null || value.isEmpty()) return fallback;
    try { int parsed = Integer.parseInt(value); if (parsed > 0) return parsed; }
    catch (NumberFormatException invalid) { /* Report the setting, not an incidental parser failure. */ }
    throw new IllegalArgumentException(name + " must be a positive integer");
  }

  JavaHistoryCorpus corpus() { return new JavaHistoryCorpus(localRepository, maxBlobBytes, allFileTypes, skippedExtensions); }
}
