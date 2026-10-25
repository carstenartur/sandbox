/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.*;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Application report formulas over one repository's immutable, one-row-per-blob historical corpus. */
final class ApplicationSourceReports {
  private static final Comparator<String> TEXT = Comparator.nullsFirst(Comparator.naturalOrder());
  private static final Comparator<Instant> DATE = Comparator.nullsFirst(Comparator.naturalOrder());
  private final List<AnalyzedFile> files;
  ApplicationSourceReports(List<AnalyzedFile> files) { this.files = List.copyOf(files); }

  record AuthorTypes(String author, String kind, long count) {}
  record Complexity(String date, double average) {}
  record TestRatio(String packageName, long testCount, long totalCount) {}

  List<AuthorTypes> authors() {
    record Key(String author, String kind) {}
    var counts = new HashMap<Key, Long>();
    files.forEach(f -> counts.merge(new Key(f.commitAuthor(), f.typeKind()), 1L, Long::sum));
    return counts.entrySet().stream().sorted(Map.Entry.comparingByKey(
        Comparator.comparing(Key::author, TEXT).thenComparing(Key::kind, TEXT)))
        .map(e -> new AuthorTypes(e.getKey().author(), e.getKey().kind(), e.getValue())).toList();
  }

  List<Complexity> complexity(String prefix) {
    var dates = new java.util.TreeMap<Instant, java.util.IntSummaryStatistics>(DATE);
    files.stream().filter(f -> prefix == null || prefix.isEmpty() || like(f.packageName(), prefix + "%"))
        .forEach(f -> dates.computeIfAbsent(f.commitDate(), ignored -> new java.util.IntSummaryStatistics()).accept(f.lineCount()));
    return dates.entrySet().stream().map(e -> new Complexity(e.getKey() == null ? null : e.getKey().toString(),
        e.getValue().getAverage())).toList();
  }

  List<TestRatio> tests() {
    var totals = new java.util.TreeMap<String, long[]>(TEXT);
    for (var file : files) {
      var count = totals.computeIfAbsent(file.packageName(), ignored -> new long[2]); count[1]++;
      String annotations = file.facet(ANNOTATIONS);
      if (annotations.contains("org.junit.Test") || annotations.contains("org.junit.jupiter.api.Test")) count[0]++;
    }
    return totals.entrySet().stream().map(e -> new TestRatio(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
  }

  List<AnalyzedFile> monsters(int threshold, int offset, int limit) {
    return page(files.stream().filter(f -> f.lineCount() >= threshold)
        .sorted(Comparator.comparingInt(AnalyzedFile::lineCount).reversed()), offset, limit);
  }

  List<AnalyzedFile> deprecated(boolean chronological, int offset, int limit) {
    var matches = files.stream().filter(f -> List.of(f.facet(ANNOTATIONS).split("\n", -1)).contains("Deprecated"));
    if (chronological) matches = matches.sorted(Comparator.comparing(AnalyzedFile::commitDate, DATE));
    return page(matches, offset, limit);
  }

  List<AnalyzedFile> dead(int offset, int limit) {
    var imports = new HashSet<String>(); var wildcardPackages = new HashSet<String>();
    for (var file : files) {
      for (String token : file.facet(IMPORTS).split("[,\\s]+")) {
        if (token.isBlank()) continue;
        if (token.endsWith(".*") && token.length() > 2) wildcardPackages.add(token.substring(0, token.length() - 2));
        else imports.add(token);
      }
    }
    return page(files.stream().filter(f -> !f.facet(QUALIFIED_NAMES).isBlank()).filter(f -> {
      for (String name : f.facet(QUALIFIED_NAMES).split("[,\\s]+")) {
        int dot = name.lastIndexOf('.');
        if (imports.contains(name) || dot > 0 && wildcardPackages.contains(name.substring(0, dot))) return false;
      }
      return true;
    }), offset, limit);
  }

  List<AnalyzedFile> migration(String prefix, int offset, int limit) {
    return page(files.stream().filter(migrationPredicate(prefix)), offset, limit);
  }

  Map<String, Object> migrationSummary(String prefix) {
    var packages = new HashSet<String>(); var authors = new HashSet<String>();
    Instant earliest = null; Instant latest = null; long count = 0;
    var predicate = migrationPredicate(prefix);
    for (var file : files) {
      if (!predicate.test(file)) continue;
      count++;
      if (file.packageName() != null) packages.add(file.packageName());
      if (file.commitAuthor() != null) authors.add(file.commitAuthor());
      var date = file.commitDate();
      if (date != null) {
        if (earliest == null || date.isBefore(earliest)) earliest = date;
        if (latest == null || date.isAfter(latest)) latest = date;
      }
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("totalFiles", (int) Math.min(Integer.MAX_VALUE, count));
    result.put("distinctPackages", packages.size()); result.put("distinctAuthors", authors.size());
    result.put("earliestDate", earliest == null ? null : earliest.toString());
    result.put("latestDate", latest == null ? null : latest.toString());
    return Collections.unmodifiableMap(result);
  }

  private static Predicate<AnalyzedFile> migrationPredicate(String prefix) {
    if (prefix == null || prefix.isEmpty()) return ignored -> false;
    return file -> like(file.facet(IMPORTS), prefix + "%") || like(file.facet(IMPORTS), "%\n" + prefix + "%");
  }

  static List<AnalyzedFile> page(Stream<AnalyzedFile> matches, int offset, int limit) {
    return matches.skip(Math.max(0, offset)).limit(Math.max(0, limit)).toList();
  }

  /** The existing report queries accept SQL LIKE '%' and '_' wildcards; no regex is evaluated. */
  private static boolean like(String value, String pattern) {
    if (value == null) return false;
    int[] text = value.codePoints().toArray(), query = pattern.codePoints().toArray();
    int t = 0, q = 0, star = -1, retry = 0;
    while (t < text.length) {
      if (q < query.length && query[q] != '%' && (query[q] == '_' || query[q] == text[t])) { t++; q++; }
      else if (q < query.length && query[q] == '%') { star = q++; retry = t; }
      else if (star >= 0) { q = star + 1; t = ++retry; }
      else return false;
    }
    while (q < query.length && query[q] == '%') q++;
    return q == query.length;
  }
}
