/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.*;

public class ReleasedJavaProjectAnalysisTest {
  private static final Instant TIME = Instant.parse("2025-01-01T00:00:00Z");
  private static final JavaAnalysisConfiguration CONFIG = new JavaAnalysisConfiguration("25", BindingMode.RECOVERY,
      List.of(), List.of("src"), List.of("UTF-8"), List.of(), true, JavaAnalysisConfiguration.DEFAULT_ANALYZER_VERSION);

  @Test public void mapsOrderedFileFacetsWithoutDependingOnResolvedTypeNames() {
    String source = """
        package sample;
        import java.util.List;
        import java.io.IOException;
        import java.io.UncheckedIOException;
        import java.lang.Deprecated;
        /** Primary documentation. */
        @Deprecated public final class Example extends Base implements Runnable {
          List<String> first, second;
          /** Method documentation also searchable. */
          public void run() { String text = "literal value"; }
          public Example(List<String> value) { }
          public static void main(String[] args) { }
          void catchIt() { try { throw new IOException(); } catch (IOException | UncheckedIOException failure) { } }
          class Nested { int value; }
          enum Choice { YES, NO }
        }
        """;
    var result = analyze("commit-a", Map.of("src/sample/Example.java", source,
        "src/sample/Base.java", "package sample; class Base {}"));
    var file = result.files().get("src/sample/Example.java");
    assertEquals("Example\nNested\nChoice", file.facet(DECLARED_TYPES));
    assertEquals("sample.Example\nsample.Nested\nsample.Choice", file.facet(QUALIFIED_NAMES));
    assertEquals("run\nExample\nmain\ncatchIt", file.facet(METHODS));
    assertEquals("first\nsecond\nvalue\nYES\nNO", file.facet(FIELDS));
    assertEquals("sample.Base", file.facet(EXTENDED_TYPES));
    assertEquals("sample.Runnable", file.facet(IMPLEMENTED_TYPES));
    assertEquals("java.lang.Deprecated", file.facet(ANNOTATIONS));
    assertEquals("run()\nExample(java.util.List)\nmain(sample.String[])\ncatchIt()", file.facet(METHOD_SIGNATURES));
    assertEquals("java.util.List\nsample.void\njava.util.List\nsample.String[]\nsample.void\nsample.void\n"
        + "sample.IOException|UncheckedIOException\nsample.int", file.facet(REFERENCED_TYPES));
    assertTrue(file.facet(DOCUMENTATION).contains("Primary documentation."));
    assertTrue(file.facet(DOCUMENTATION).contains("Method documentation also searchable."));
    assertEquals("literal value", file.facet(STRING_LITERALS));
    assertEquals("class", file.typeKind());
    assertEquals("public final", file.visibility());
    assertTrue(file.hasMainMethod());
    assertEquals("sample", file.packageName());
    assertEquals("Author", file.commitAuthor());
    assertEquals(TIME, file.commitDate());
    assertEquals("java", file.fileType());
    assertEquals("Example", file.simpleClassName());
    assertEquals("src/sample/Example.java", file.facet(PATH));
    assertEquals(source, file.facet(SOURCE));
    assertThrows(UnsupportedOperationException.class, () -> file.facets().put(SOURCE, "changed"));
    assertEquals("blob-src/sample/Example.java", file.searchDocument().documentId());
    result.semantic().symbols().getFirst().setSimpleName("mutated upstream entity");
    result.semantic().files().get("src/sample/Example.java").analysisRun().setErrorCount(999);
    assertEquals("Example\nNested\nChoice", file.facet(DECLARED_TYPES));
    assertNotEquals(999, file.quality().errorCount());
  }

  @Test public void analyzesSiblingContextAgainForUnchangedBytesInAnotherCommit() {
    String caller = "package sample; public class Caller { public String call(Base value) { return value.name(); } }";
    var good = analyze("first", Map.of("src/sample/Caller.java", caller,
        "src/sample/Base.java", "package sample; public class Base { public String name() { return \"ok\"; } }"));
    var changed = analyze("second", Map.of("src/sample/Caller.java", caller,
        "src/sample/Base.java", "package sample; public class Base { }"));
    var before = good.files().get("src/sample/Caller.java");
    var after = changed.files().get("src/sample/Caller.java");
    assertEquals(before.blobObjectId(), after.blobObjectId());
    assertEquals(0, before.quality().errorCount());
    assertTrue(after.quality().errorCount() > 0);
    assertTrue(before.quality().bindings().getOrDefault(BindingStatus.FULL, 0L) > 0);
    assertEquals("second", after.commitObjectId());
    assertEquals("first", good.semantic().project().commitId());
  }

  @Test public void includesRecordsAllAnnotationUsesAndDecodedTextBlocksWithVisibleRecovery() {
    String source = "package sample; public record Data(@Deprecated String value) { "
        + "void broken() { @Deprecated Missing local = null; String text = \"\"\"\ntext block\n\"\"\"; } }";
    var file = analyze("record", Map.of("src/sample/Data.java", source)).files().get("src/sample/Data.java");
    assertEquals("record", file.typeKind());
    assertEquals("Data", file.facet(DECLARED_TYPES));
    assertEquals("Deprecated\nDeprecated", file.facet(ANNOTATIONS));
    assertEquals("text block\n", file.facet(STRING_LITERALS));
    assertTrue(file.quality().errorCount() > 0);
    assertEquals(List.of("missing:dependency:1"), file.quality().unresolvedDependencies());
  }

  @Test public void retainsUnsupportedGitPathsWithoutLosingOrdinarySiblingBindings() {
    var sources = new java.util.TreeMap<String, String>();
    sources.put("src/p/Base.java", "package p; public class Base { public String name() { return \"ok\"; } }");
    sources.put("src/p/Caller.java", "package p; class Caller { String call(Base b) { return b.name(); } }");
    for (String path : List.of("..\\Outside.java", "src/p\\Base.java", "src/CON.java", "src/trailing./Odd.java",
        "src/p/A.java", "src/p/a.java", "src/" + "a".repeat(251) + ".java",
        "src/" + "é".repeat(126) + ".java", "directory/".repeat(40) + "Odd.java")) {
      sources.put(path, "package p; class Odd { int field; }");
    }
    var result = analyze("unusual-paths", sources);
    assertEquals(sources.size(), result.files().size());
    assertEquals(0, result.files().get("src/p/Caller.java").quality().errorCount());
    assertEquals(sources.size(), result.semantic().project().sources().size());
    for (var entry : result.files().entrySet()) {
      assertEquals(entry.getKey(), entry.getValue().filePath());
      assertFalse(entry.getValue().quality().configurationDiagnostics().isEmpty());
      if (!entry.getKey().equals("src/p/Base.java") && !entry.getKey().equals("src/p/Caller.java")) {
        assertEquals("field", entry.getValue().facet(FIELDS));
        assertTrue(entry.getValue().quality().configurationDiagnostics().stream()
            .anyMatch(d -> d.contains("individual recovery")));
      }
    }
  }

  @Test public void preservesLegacyInterfaceImportsAndPrimaryDocumentationAndPackageOnlySources() {
    String source = "package sample; import java.util.*; import static java.util.Collections.emptyList; "
        + "interface Parent {} /** " + "documentation ".repeat(200) + " */ "
        + "public interface Child extends Parent { void accept(int a, int b); }";
    var file = analyze("interfaces", Map.of("module/src/sample/Child.java", source))
        .files().get("module/src/sample/Child.java");
    assertEquals("", file.facet(EXTENDED_TYPES));
    assertEquals("sample.Parent", file.facet(IMPLEMENTED_TYPES));
    assertEquals("java.util.*\njava.util.Collections.emptyList", file.facet(IMPORTS));
    assertEquals("sample.int\nsample.int\nsample.void", file.facet(REFERENCED_TYPES));
    assertEquals("accept(sample.int,sample.int)", file.facet(METHOD_SIGNATURES));
    assertEquals("documentation ".repeat(200).substring(0, 2_000), file.primaryDocumentation());
    assertEquals(2_000, file.primaryDocumentation().length());
    assertTrue(file.facet(DOCUMENTATION).length() > 2_000);
    assertEquals("module", file.projectName());
    var packageOnly = analyze("package", Map.of("src/sample/package-info.java", "/** Package docs. */ package sample;"))
        .files().get("src/sample/package-info.java");
    assertEquals("sample", packageOnly.packageName());
    assertEquals("", packageOnly.facet(DECLARED_TYPES));
    assertEquals("Package docs.", packageOnly.facet(DOCUMENTATION));
  }

  @Test public void retainsQualifiedGenericParentSpellingAndFieldDeclarationGroups() {
    String source = "package sample; class Outer<T> { class Inner {} } "
        + "class Example extends Outer<String>.Inner { "
        + "Object first = new Object() { int nested; }, second; }";
    var file = analyze("qualified", Map.of("src/sample/Example.java", source)).files().get("src/sample/Example.java");
    assertEquals("sample.Outer<String>.Inner", file.facet(EXTENDED_TYPES));
    assertEquals("first\nsecond\nnested", file.facet(FIELDS));
  }

  @Test public void normalizesPrimaryDocumentationWhitespaceBeforeItsLengthLimit() {
    String source = "/** first\n       * second\n       *   third */ public class Example {}";
    var file = analyze("documentation", Map.of("src/Example.java", source)).files().get("src/Example.java");
    assertEquals("first second third", file.primaryDocumentation());
  }

  @Test public void retainsEnumDeclarationGroupsBeforeTheirAnonymousBodies() {
    String source = "enum Choice { FIRST { int nested; }, SECOND; }";
    var file = analyze("enum", Map.of("src/Choice.java", source)).files().get("src/Choice.java");
    assertEquals("FIRST\nSECOND\nnested", file.facet(FIELDS));
  }

  @Test public void includesTypeUseAnnotationsAndRetainsRawAnnotatedParents() {
    String source = "package sample; import java.util.List; @interface Mark {} "
        + "class Base<T> {} class Example extends @Mark Base<String> { List<@Mark String> values; }";
    var file = analyze("annotated", Map.of("src/sample/Example.java", source)).files().get("src/sample/Example.java");
    assertEquals("sample.Base", file.facet(EXTENDED_TYPES));
    assertEquals("Mark\nMark", file.facet(ANNOTATIONS));
  }

  private static ReleasedJavaProjectAnalysis.Result analyze(String commit, Map<String, String> sources) {
    var snapshots = new java.util.TreeMap<String, JavaSourceSnapshot>();
    sources.forEach((path, source) -> snapshots.put(path,
        new JavaSourceSnapshot("repo", commit, "blob-" + path, path, source)));
    return new ReleasedJavaProjectAnalysis().analyze(new JavaProjectSnapshot("repo", commit, snapshots),
        CONFIG, "Author", TIME, List.of("missing:dependency:1"));
  }
}
