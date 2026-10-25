/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ApplicationSourceReportsTest {
  @Test public void groupsHistoricalRowsIncludingNonJavaNullsAndExactInstants() {
    var reports = new ApplicationSourceReports(List.of(file("1", "p", "A", "class", 10, 1, Map.of()),
        file("2", "p", "A", "class", 30, 1, Map.of(ANNOTATIONS, "org.junit.jupiter.api.Test")),
        file("3", "q", "B", "interface", 90, 2, Map.of(ANNOTATIONS, "Test")),
        file("4", null, null, null, 0, 2, Map.of())));
    assertEquals(3, reports.authors().size());
    assertEquals(2, reports.authors().stream().filter(a -> "A".equals(a.author())).findFirst().orElseThrow().count());
    assertEquals(20, reports.complexity("p").getFirst().average(), 0.0);
    assertEquals(45, reports.complexity(null).getLast().average(), 0.0);
    assertEquals("1970-01-01T00:00:02Z", reports.complexity(null).getLast().date());
    var p = reports.tests().stream().filter(t -> "p".equals(t.packageName())).findFirst().orElseThrow();
    assertEquals(1, p.testCount()); assertEquals(2, p.totalCount());
    assertEquals(0, reports.tests().stream().filter(t -> "q".equals(t.packageName())).findFirst().orElseThrow().testCount());
    assertEquals(List.of("3", "2"), reports.monsters(30, 0, 20).stream().map(AnalyzedFile::blobObjectId).toList());
    assertEquals(List.of("2"), reports.monsters(30, 1, 1).stream().map(AnalyzedFile::blobObjectId).toList());
  }

  @Test public void importMigrationPreservesSqlWildcardsAndLineBoundariesAndSummaryNulls() {
    var reports = new ApplicationSourceReports(List.of(file("1", "p", "A", "class", 1, 1,
        Map.of(IMPORTS, "java.util.List\njavax.servlet.Request")), file("2", "p", "A", "class", 1, 3,
        Map.of(IMPORTS, "javaxXservlet.Other")), file("3", "q", null, "class", 1, 2,
        Map.of(IMPORTS, "prefix.javax.servlet.Other"))));
    assertEquals(2, reports.migration("javax_servlet", 0, 20).size());
    assertEquals(1, reports.migration("javax.servlet", 0, 20).size());
    var summary = reports.migrationSummary("javax%servlet");
    assertEquals(2, summary.get("totalFiles")); assertEquals(1, summary.get("distinctPackages"));
    assertEquals(1, summary.get("distinctAuthors")); assertEquals("1970-01-01T00:00:03Z", summary.get("latestDate"));
    assertNull(reports.migrationSummary("absent").get("earliestDate"));
    assertEquals(0, reports.migrationSummary("").get("totalFiles"));
    assertThrows(UnsupportedOperationException.class, () -> summary.put("extra", 1));
  }

  @Test public void deadCodeIsAnImportHeuristicAndPagesOnlyAfterFiltering() {
    var reports = new ApplicationSourceReports(List.of(file("imports", null, null, null, 0, 1,
        Map.of(IMPORTS, "a.One, b.*\na.Self")), file("one", "a", "A", "class", 1, 1,
        Map.of(QUALIFIED_NAMES, "a.One\na.Nested")), file("wild", "b", "A", "class", 1, 1,
        Map.of(QUALIFIED_NAMES, "b.Two")), file("deep", "b.deep", "A", "class", 1, 1,
        Map.of(QUALIFIED_NAMES, "b.deep.Three")), file("dead", "c", "A", "class", 1, 1,
        Map.of(QUALIFIED_NAMES, "c.Four")), file("self", "a", "A", "class", 1, 1,
        Map.of(QUALIFIED_NAMES, "a.Self", IMPORTS, "a.Self"))));
    assertEquals(List.of("deep", "dead"), reports.dead(0, 20).stream().map(AnalyzedFile::blobObjectId).toList());
    assertEquals("dead", reports.dead(1, 1).getFirst().blobObjectId());
    assertTrue(reports.dead(Integer.MAX_VALUE, 100).isEmpty());
  }

  @Test public void deprecatedUsesExactShortAnnotationTokensAndTimelineIsChronological() {
    var reports = new ApplicationSourceReports(List.of(file("late", "p", "A", "class", 1, 3,
        Map.of(ANNOTATIONS, "Other\nDeprecated")), file("qualified", "p", "A", "class", 1, 1,
        Map.of(ANNOTATIONS, "java.lang.Deprecated")), file("early", "p", "A", "class", 1, 2,
        Map.of(ANNOTATIONS, "Deprecated\nOther")), file("substring", "p", "A", "class", 1, 1,
        Map.of(ANNOTATIONS, "NotDeprecated"))));
    assertEquals(List.of("late", "early"), reports.deprecated(false, 0, 20).stream().map(AnalyzedFile::blobObjectId).toList());
    assertEquals(List.of("early", "late"), reports.deprecated(true, 0, 20).stream().map(AnalyzedFile::blobObjectId).toList());
  }

  private static AnalyzedFile file(String id, String pkg, String author, String kind, int lines, int date,
      Map<io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField, String> facets) {
    return new AnalyzedFile("repo", "commit", id, id + ".java", kind == null ? "xml" : "java", "module",
        pkg, id, kind, null, lines, false, null, author, Instant.ofEpochSecond(date), facets, null);
  }
}
