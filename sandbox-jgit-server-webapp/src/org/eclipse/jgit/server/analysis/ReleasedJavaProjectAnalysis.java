/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.*;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.entity.JavaSymbolIndex;

/** Adapts released analysis of a whole commit to Sandbox's historical file facets. */
final class ReleasedJavaProjectAnalysis {
  // Keep the mutable released analysis entities inside this package for semantic diff operations.
  record Result(Map<String, AnalyzedFile> files, JavaProjectAnalysisResult semantic) {
    Result { files = Map.copyOf(files); }
  }

  Result analyze(JavaProjectSnapshot project, JavaAnalysisConfiguration configuration, String author,
      Instant date, List<String> unresolvedDependencies) {
    return analyze(project, configuration, author, date, unresolvedDependencies, List.of());
  }

  Result analyze(JavaProjectSnapshot project, JavaAnalysisConfiguration configuration, String author,
      Instant date, List<String> unresolvedDependencies, List<String> configurationDiagnostics) {
    var excluded = PortableSourcePaths.excluded(project.sources().keySet());
    var semantic = semantic(project, configuration, excluded);
    var files = new LinkedHashMap<String, AnalyzedFile>();
    for (var source : project.sources().values().stream().sorted(Comparator.comparing(JavaSourceSnapshot::path)).toList()) {
      var analysis = semantic.files().get(source.path());
      var structure = new JavaSourceStructureExtractor().extract(source, configuration);
      var text = new JavaSourceTextExtractor().extract(source, configuration);
      var diagnostics = new ArrayList<>(configurationDiagnostics);
      if (!excluded.isEmpty()) diagnostics.add("Project binding context omits " + excluded.size()
          + " source paths that cannot share a portable temporary tree");
      if (excluded.contains(source.path())) diagnostics.add("Source path analyzed with individual recovery: " + source.path());
      files.put(source.path(), file(source, analysis, structure, text, author, date, unresolvedDependencies, diagnostics));
    }
    return new Result(files, semantic);
  }

  private static JavaProjectAnalysisResult semantic(JavaProjectSnapshot project, JavaAnalysisConfiguration configuration,
      java.util.Set<String> excluded) {
    if (excluded.isEmpty()) return new JavaProjectAnalyzer().analyze(project, configuration);
    var ordinary = new java.util.TreeMap<String, JavaSourceSnapshot>(project.sources());
    excluded.forEach(ordinary::remove);
    var shared = new JavaProjectAnalyzer().analyze(
        new JavaProjectSnapshot(project.repositoryName(), project.commitId(), ordinary), configuration);
    var results = new java.util.TreeMap<String, JavaAnalysisResult>(shared.files());
    var recovery = new JavaAnalysisConfiguration(configuration.sourceLevel(), BindingMode.RECOVERY,
        configuration.classpathEntries(), List.of(), List.of(), configuration.modulepathEntries(),
        configuration.includeRunningVmBootClasspath(), configuration.analyzerVersion());
    for (String path : new java.util.TreeSet<>(excluded)) {
      results.put(path, new JavaJdtAnalyzer().analyze(project.sources().get(path), recovery));
    }
    return new JavaProjectAnalysisResult(project, results,
        results.values().stream().flatMap(r -> r.symbols().stream()).toList(),
        results.values().stream().flatMap(r -> r.references().stream()).toList(),
        results.values().stream().mapToInt(r -> r.analysisRun().getProblemCount()).sum(),
        results.values().stream().mapToInt(r -> r.analysisRun().getErrorCount()).sum());
  }

  private static AnalyzedFile file(JavaSourceSnapshot source, JavaAnalysisResult analysis,
      JavaSourceStructure structure, JavaSourceText text, String author, Instant date, List<String> unresolved,
      List<String> configurationDiagnostics) {
    var imports = new HashMap<String, String>();
    for (var declaration : structure.imports()) {
      if (!declaration.onDemand()) {
        String name = declaration.name();
        imports.put(name.substring(name.lastIndexOf('.') + 1), name);
      }
    }
    // Preserve declaration-group visitation (all field fragments precede their initializer bodies).
    var symbols = analysis.symbols();
    var methods = symbols.stream().filter(s -> s.getSymbolKind() == JavaSymbolKind.METHOD
        || s.getSymbolKind() == JavaSymbolKind.CONSTRUCTOR).toList();
    var fields = new EnumMap<JavaFileSearchField, String>(JavaFileSearchField.class);
    fields.put(DECLARED_TYPES, lines(structure.types().stream().map(JavaSourceStructure.TypeDeclaration::name)));
    // Retain Sandbox's historical flat nested-type names; semantic results retain true nested identities.
    fields.put(QUALIFIED_NAMES, lines(structure.types().stream().map(t -> qualify(structure.packageName(), t.name()))));
    fields.put(METHODS, lines(methods.stream().map(JavaSymbolIndex::getSimpleName)));
    fields.put(FIELDS, lines(structure.fields().stream().map(JavaSourceStructure.FieldDeclaration::name)));
    var extended = new ArrayList<String>();
    var implemented = new ArrayList<String>();
    for (var type : structure.types()) {
      // Legacy file facets placed interface-extends in implementsTypes; semantic structure stays correct upstream.
      var target = type.kind() == JavaSourceStructure.TypeKind.INTERFACE ? implemented : extended;
      type.extendedTypes().forEach(t -> target.add(resolve(t.rawTypeName(), structure.packageName(), imports)));
      type.implementedTypes().forEach(t -> implemented.add(resolve(t.rawTypeName(), structure.packageName(), imports)));
    }
    fields.put(EXTENDED_TYPES, String.join("\n", extended));
    fields.put(IMPLEMENTED_TYPES, String.join("\n", implemented));
    fields.put(IMPORTS, lines(structure.imports().stream().map(i -> i.name() + (i.onDemand() ? ".*" : ""))));
    fields.put(ANNOTATIONS, lines(analysis.references().stream()
        .filter(r -> r.getReferenceKind() == JavaReferenceKind.ANNOTATION_USE)
        .sorted(Comparator.comparingInt(r -> r.getStartPosition()))
        .map(r -> imports.getOrDefault(r.getReferenceName(), r.getReferenceName()))));
    fields.put(REFERENCED_TYPES, lines(structure.typeUses().stream()
        .map(u -> resolve(u.rawTypeName(), structure.packageName(), imports))));
    var parameters = structure.typeUses().stream()
        .filter(u -> u.role() == JavaSourceStructure.TypeUseRole.METHOD_PARAMETER)
        .collect(Collectors.groupingBy(JavaSourceStructure.TypeUse::ownerStartPosition));
    fields.put(METHOD_SIGNATURES, lines(methods.stream().map(m -> m.getSimpleName() + "("
        + parameters.getOrDefault(m.getStartPosition(), List.of()).stream()
            .map(u -> resolve(u.rawTypeName(), structure.packageName(), imports)).collect(Collectors.joining(",")) + ")")));
    fields.put(DOCUMENTATION, lines(text.documentation().stream().map(f -> cleanDocumentation(f.text()))));
    fields.put(STRING_LITERALS, lines(text.stringLiterals().stream().map(JavaSourceText.Fragment::text)
        .filter(value -> value.length() > 3)));
    fields.put(SOURCE, truncate(source.source(), 65_535));
    fields.put(PATH, source.path());
    String filename = source.path().substring(source.path().lastIndexOf('/') + 1);
    String simpleName = filename.endsWith(".java") ? filename.substring(0, filename.length() - 5) : filename;
    fields.put(SIMPLE_CLASS_NAME, simpleName);
    var primary = structure.types().isEmpty() ? null : structure.types().getFirst();
    var primarySymbol = primary == null ? null : symbols.stream()
        .filter(s -> s.getStartPosition() == primary.startPosition()).findFirst().orElse(null);
    String kind = primary == null ? "" : primary.kind() == JavaSourceStructure.TypeKind.ANNOTATION_TYPE
        ? "annotation" : primary.kind().name().toLowerCase(Locale.ROOT);
    String documentation = structure.types().stream()
        .filter(t -> t.kind() == JavaSourceStructure.TypeKind.CLASS || t.kind() == JavaSourceStructure.TypeKind.INTERFACE)
        .flatMap(t -> text.documentation().stream().filter(f -> f.startPosition() == t.startPosition()))
        .map(f -> truncate(cleanDocumentation(f.text()).replaceAll("\\s+", " "), 2_000))
        .filter(value -> !value.isEmpty()).findFirst().orElse("");
    boolean main = methods.stream().anyMatch(m -> "main".equals(m.getSimpleName())
        && modifiers(m).contains("public") && modifiers(m).contains("static")
        && parameters.getOrDefault(m.getStartPosition(), List.of()).size() == 1);
    var bindingCounts = new EnumMap<BindingStatus, Long>(BindingStatus.class);
    Stream.concat(symbols.stream().map(JavaSymbolIndex::getBindingStatus),
        analysis.references().stream().map(r -> r.getBindingStatus()))
        .filter(java.util.Objects::nonNull).forEach(status -> bindingCounts.merge(status, 1L, Long::sum));
    var run = analysis.analysisRun();
    var quality = new AnalyzedFile.Quality(run.getStatus(), run.getProblemCount(), run.getErrorCount(),
        run.getFailureMessage(), bindingCounts, unresolved, configurationDiagnostics);
    return new AnalyzedFile(source.repositoryName(), source.commitId(), source.blobId(), source.path(), "java",
        projectName(source.path()), structure.packageName(), simpleName, kind, visibility(primarySymbol),
        source.source().isEmpty() ? 0 : 1 + (int) source.source().chars().filter(c -> c == '\n').count(), main,
        documentation, author, date, fields, quality);
  }

  private static String resolve(String rawName, String packageName, Map<String, String> imports) {
    return imports.getOrDefault(rawName, qualify(packageName, rawName));
  }

  private static String qualify(String packageName, String name) {
    return packageName.isEmpty() ? name : packageName + "." + name;
  }

  private static String lines(Stream<String> values) { return values.collect(Collectors.joining("\n")); }

  private static List<String> modifiers(JavaSymbolIndex symbol) {
    return symbol == null || symbol.getModifiers() == null ? List.of() : List.of(symbol.getModifiers().split("\\s+"));
  }

  private static String visibility(JavaSymbolIndex symbol) {
    if (symbol == null) return "";
    var modifiers = modifiers(symbol);
    String result = Stream.of("public", "protected", "private").filter(modifiers::contains).findFirst().orElse("package");
    if (modifiers.contains("abstract")) result += " abstract";
    if (modifiers.contains("final")) result += " final";
    return result;
  }

  private static String projectName(String path) {
    int source = path.indexOf("/src/");
    if (source < 0) source = path.indexOf("/tst/");
    if (source <= 0) return "";
    String before = path.substring(0, source);
    return before.substring(before.lastIndexOf('/') + 1);
  }

  private static String cleanDocumentation(String text) { return text.replaceAll("/\\*\\*|\\*/|\\*", "").trim(); }

  private static String truncate(String text, int limit) {
    if (text.length() <= limit) return text;
    int end = Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit;
    return text.substring(0, end);
  }
}
