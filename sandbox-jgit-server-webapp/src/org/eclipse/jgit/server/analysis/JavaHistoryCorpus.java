/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.EnumMap;
import java.util.TreeMap;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.server.history.GitHistorySnapshot;
import org.eclipse.jgit.server.history.GitHistorySnapshot.CommitView;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.*;

/** Builds an application corpus from authoritative Git; callers own the repository lease. */
final class JavaHistoryCorpus {
  private static final System.Logger LOG = System.getLogger(JavaHistoryCorpus.class.getName());
  static final String PROVENANCE_POLICY = "oldest-author-oid-path-v1";
  private final Path localRepository;
  private final int maxBlobBytes;
  private final boolean allFileTypes;
  private final Set<String> skippedExtensions;

  JavaHistoryCorpus(Path localRepository, int maxBlobBytes) {
    this(localRepository, maxBlobBytes, false, Set.of());
  }

  JavaHistoryCorpus(Path localRepository, int maxBlobBytes, boolean allFileTypes, Set<String> skippedExtensions) {
    this.localRepository = localRepository.toAbsolutePath().normalize();
    if (maxBlobBytes < 1) throw new IllegalArgumentException("Maximum source blob size must be positive");
    this.maxBlobBytes = maxBlobBytes;
    this.allFileTypes = allFileTypes;
    this.skippedExtensions = skippedExtensions.stream().map(e -> e.toLowerCase(Locale.ROOT))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  record Corpus(List<AnalyzedFile> files, List<CommitView> commits) {
    Corpus { files = List.copyOf(files); commits = List.copyOf(commits); }
  }

  Corpus read(String canonicalName, Repository git, Collection<String> retainedRoots) throws IOException {
    return readSnapshot(canonicalName, git, GitHistorySnapshot.read(git, retainedRoots));
  }

  /** Build exactly the captured generation; do not discover refs again under its snapshot key. */
  Corpus readSnapshot(String canonicalName, Repository git, List<CommitView> snapshot) throws IOException {
    var commits = List.copyOf(snapshot);
    var files = new LinkedHashMap<String, AnalyzedFile>();
    var oldestFirst = commits.stream().sorted(Comparator.comparing(CommitView::authorTime,
        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(CommitView::objectId)).toList();
    for (var commit : oldestFirst) {
      GitHistorySnapshot.checkInterrupted();
      var project = project(canonicalName, git, commit);
      if (project.files().values().stream().noneMatch(s -> !files.containsKey(s.objectId()))) continue;
      // New Java blobs need the whole sibling context. Changes confined to other formats must not
      // reparse every unchanged Java file in every historical commit.
      if (project.snapshot().sources().values().stream().noneMatch(s -> !files.containsKey(s.blobId()))) {
        project = new Project(new JavaProjectSnapshot(canonicalName, commit.objectId(), Map.of()), Map.of(),
            project.diagnostics(), project.files());
      }
      var analyzed = analyze(project, commit);
      for (String path : new java.util.TreeSet<>(analyzed.files().keySet())) {
        var file = analyzed.files().get(path);
        files.putIfAbsent(file.blobObjectId(), file);
      }
    }
    return new Corpus(List.copyOf(files.values()), commits);
  }

  ReleasedJavaProjectAnalysis.Result atCommit(String name, Repository git, ObjectId id) throws IOException {
    GitHistorySnapshot.checkInterrupted();
    try (var walk = new RevWalk(git)) {
      var commit = walk.parseCommit(id);
      var author = commit.getAuthorIdent();
      var view = new CommitView(commit.name(), commit.getTree().name(), author == null ? null : author.getWhenAsInstant(),
          author == null ? null : author.getName(), author == null ? null : author.getEmailAddress());
      return analyze(project(name, git, view), view);
    }
  }

  private ReleasedJavaProjectAnalysis.Result analyze(Project project, CommitView commit) throws IOException {
    GitHistorySnapshot.checkInterrupted();
    var config = configuration(project);
    var result = new ReleasedJavaProjectAnalysis().analyze(project.snapshot(), config.configuration(),
        commit.authorName(), commit.authorTime(), config.unresolved(), config.diagnostics());
    GitHistorySnapshot.checkInterrupted();
    var files = new TreeMap<>(result.files());
    for (var entry : project.files().entrySet()) {
      var source = entry.getValue();
      if (source.java()) continue;
      ApplicationFileFormats.Facets extracted;
      try {
        extracted = ApplicationFileFormats.extract(entry.getKey(), source.text());
      } catch (IllegalArgumentException malformed) {
        // Properties.load rejects malformed Unicode escapes. One retained historical
        // file must not prevent healthy source generations from being queried.
        if (!entry.getKey().toLowerCase(Locale.ROOT).endsWith(".properties")) throw malformed;
        LOG.log(System.Logger.Level.WARNING, "Invalid properties metadata in Git blob {0}; retaining text only", source.objectId());
        extracted = ApplicationFileFormats.plainText("properties", source.text());
      }
      var fields = new EnumMap<JavaFileSearchField, String>(JavaFileSearchField.class);
      if (extracted.fullyQualifiedNames() != null) fields.put(JavaFileSearchField.QUALIFIED_NAMES, extracted.fullyQualifiedNames());
      if (extracted.declaredTypes() != null) fields.put(JavaFileSearchField.DECLARED_TYPES, extracted.declaredTypes());
      if (extracted.declaredFields() != null) fields.put(JavaFileSearchField.FIELDS, extracted.declaredFields());
      if (extracted.sourceSnippet() != null) fields.put(JavaFileSearchField.SOURCE, extracted.sourceSnippet());
      fields.put(JavaFileSearchField.PATH, entry.getKey());
      files.put(entry.getKey(), new AnalyzedFile(project.snapshot().repositoryName(), commit.objectId(), source.objectId(),
          entry.getKey(), extracted.fileType(), null, null, null, null, null, 0, false, null,
          commit.authorName(), commit.authorTime(), fields, null));
    }
    return new ReleasedJavaProjectAnalysis.Result(files, result.semantic());
  }

  private record FileSource(String objectId, String text, boolean java) {}
  private record Project(JavaProjectSnapshot snapshot, Map<String, String> builds, List<String> diagnostics,
      Map<String, FileSource> files) {}
  private record Configuration(JavaAnalysisConfiguration configuration, List<String> unresolved, List<String> diagnostics) {}

  private Project project(String name, Repository git, CommitView commit) throws IOException {
    var sources = new TreeMap<String, JavaSourceSnapshot>();
    var builds = new TreeMap<String, String>();
    var files = new TreeMap<String, FileSource>();
    var diagnostics = new ArrayList<String>();
    try (var reader = git.newObjectReader(); var tree = new TreeWalk(reader)) {
      tree.addTree(ObjectId.fromString(commit.treeId())); tree.setRecursive(true);
      while (tree.next()) {
        GitHistorySnapshot.checkInterrupted();
        String path = tree.getPathString();
        if (tree.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) continue;
        boolean java = path.toLowerCase(Locale.ROOT).endsWith(".java");
        int dot = path.lastIndexOf('.');
        boolean skipped = dot >= 0 && skippedExtensions.contains(path.substring(dot).toLowerCase(Locale.ROOT));
        boolean selected = !skipped && (allFileTypes || path.endsWith(".java"));
        if (!selected && !buildFile(path)) continue;
        var blob = reader.open(tree.getObjectId(0), Constants.OBJ_BLOB);
        if (blob.getSize() > maxBlobBytes) {
          diagnostics.add("Source/build file exceeds the configured byte limit: " + path); continue;
        }
        byte[] bytes = blob.getBytes();
        boolean binary = false;
        for (int i = 0; i < Math.min(bytes.length, 8192); i++) if (bytes[i] == 0) { binary = true; break; }
        if (binary) { diagnostics.add("Binary source/build file omitted: " + path); continue; }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (selected) {
          files.put(path, new FileSource(tree.getObjectId(0).name(), text, java));
          if (java) sources.put(path, new JavaSourceSnapshot(name, commit.objectId(), tree.getObjectId(0).name(), path, text));
        }
        if (buildFile(path)) builds.put(path, text);
      }
    }
    return new Project(new JavaProjectSnapshot(name, commit.objectId(), sources), builds, diagnostics, files);
  }

  private Configuration configuration(Project project) {
    var diagnostics = new ArrayList<>(project.diagnostics());
    var unresolved = new LinkedHashSet<String>();
    var configurations = new ArrayList<JavaAnalysisConfiguration>();
    if (project.builds().keySet().stream().anyMatch(p -> p.equals("pom.xml") || p.endsWith("/pom.xml"))) {
      try {
        var resolved = new MavenJavaAnalysisConfigurationResolver(localRepository).resolve(project.builds());
        configurations.add(resolved.configuration()); unresolved.addAll(resolved.unresolvedDependencies());
      } catch (IllegalArgumentException invalid) {
        diagnostics.add("Invalid historical Maven metadata; using available source roots without its dependency model");
      }
    }
    if (project.builds().keySet().stream().anyMatch(p -> p.endsWith("build.gradle") || p.endsWith("build.gradle.kts"))) {
      try {
        var resolved = new GradleJavaAnalysisConfigurationResolver(localRepository).resolve(project.builds());
        configurations.add(resolved.configuration()); unresolved.addAll(resolved.unresolvedDependencies());
      } catch (IllegalArgumentException invalid) {
        diagnostics.add("Invalid historical Gradle metadata; using available source roots without its dependency model");
      }
    }
    var roots = new LinkedHashSet<String>();
    var classpath = new LinkedHashSet<String>();
    int level = configurations.isEmpty() ? 25 : 0;
    for (var configuration : configurations) {
      String version = configuration.sourceLevel().replaceFirst("^1\\.", "");
      try { level = Math.max(level, Integer.parseInt(version)); }
      catch (NumberFormatException invalid) { diagnostics.add("Unsupported build source level: " + configuration.sourceLevel()); }
      for (String root : configuration.sourcepathEntries()) {
        addRoot(root, roots, project.snapshot().sources().keySet(), diagnostics);
      }
      for (String entry : configuration.classpathEntries()) {
        Path path = Path.of(entry).toAbsolutePath().normalize();
        if (path.startsWith(localRepository)) classpath.add(path.toString());
        else diagnostics.add("Build dependency outside the configured local artifact repository was omitted");
      }
    }
    for (String path : project.snapshot().sources().keySet()) {
      for (String layout : List.of("src/main/java/", "src/test/java/", "src/", "tst/")) {
        int position = path.indexOf(layout);
        while (position > 0 && path.charAt(position - 1) != '/') position = path.indexOf(layout, position + 1);
        if (position >= 0) {
          addRoot(path.substring(0, position + layout.length() - 1), roots,
              project.snapshot().sources().keySet(), diagnostics); break;
        }
      }
    }
    // Root-layout packages may coexist with module/src or explicitly configured roots.
    roots.add("");
    return new Configuration(new JavaAnalysisConfiguration(level == 0 ? "25" : Integer.toString(level), BindingMode.RECOVERY,
        List.copyOf(classpath), List.copyOf(roots), roots.stream().map(ignored -> "UTF-8").toList(), List.of(), true,
        JavaAnalysisConfiguration.DEFAULT_ANALYZER_VERSION), List.copyOf(unresolved), List.copyOf(diagnostics));
  }

  private static void addRoot(String root, Collection<String> roots, Collection<String> sources, List<String> diagnostics) {
    String normalized = root.replace('\\', '/');
    try {
      Path path = Path.of(normalized).normalize();
      String canonicalRoot = path.toString().replace('\\', '/');
      // A shared source root needs one collision scan, regardless of its number of files.
      if (roots.contains(canonicalRoot)) return;
      if (normalized.startsWith("/") || path.isAbsolute() || normalized.matches("^[A-Za-z]:.*") || path.startsWith("..")) {
        diagnostics.add("Source root outside the repository was omitted: " + root); return;
      }
      if (!PortableSourcePaths.relative(canonicalRoot)) {
        diagnostics.add("Source root cannot be represented portably and was omitted: " + root); return;
      }
      for (String source : sources) {
        if (!PortableSourcePaths.relative(source)) continue;
        if (path.startsWith(Path.of(source.replace('\\', '/')).normalize())) {
          diagnostics.add("Source root colliding with a source file was omitted: " + root); return;
        }
      }
      roots.add(canonicalRoot);
    } catch (InvalidPathException invalid) {
      diagnostics.add("Source root cannot be represented on this host and was omitted: " + root);
    }
  }

  private static boolean buildFile(String path) {
    String name = path.substring(path.lastIndexOf('/') + 1);
    return name.equals("pom.xml") || name.equals("build.gradle") || name.equals("build.gradle.kts");
  }
}
