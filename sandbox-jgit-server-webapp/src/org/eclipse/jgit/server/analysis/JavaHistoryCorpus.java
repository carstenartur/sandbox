/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.IOException;
import java.nio.charset.Charset;
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
  static final String PROVENANCE_POLICY = "oldest-author-oid-path-v1";
  private final Path localRepository;
  private final int maxBlobBytes;

  JavaHistoryCorpus(Path localRepository, int maxBlobBytes) {
    this.localRepository = localRepository.toAbsolutePath().normalize();
    if (maxBlobBytes < 1) throw new IllegalArgumentException("Maximum source blob size must be positive");
    this.maxBlobBytes = maxBlobBytes;
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
      if (project.snapshot().sources().values().stream().noneMatch(s -> !files.containsKey(s.blobId()))) continue;
      // Analyze the whole selected commit once, even when only one of its blobs is new to the corpus.
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
    return result;
  }

  private record Project(JavaProjectSnapshot snapshot, Map<String, String> builds, List<String> diagnostics) {}
  private record Configuration(JavaAnalysisConfiguration configuration, List<String> unresolved, List<String> diagnostics) {}

  private Project project(String name, Repository git, CommitView commit) throws IOException {
    var sources = new TreeMap<String, JavaSourceSnapshot>();
    var builds = new TreeMap<String, String>();
    var diagnostics = new ArrayList<String>();
    try (var reader = git.newObjectReader(); var tree = new TreeWalk(reader)) {
      tree.addTree(ObjectId.fromString(commit.treeId())); tree.setRecursive(true);
      while (tree.next()) {
        GitHistorySnapshot.checkInterrupted();
        String path = tree.getPathString();
        if (tree.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) continue;
        boolean java = path.endsWith(".java");
        if (!java && !buildFile(path)) continue;
        var blob = reader.open(tree.getObjectId(0), Constants.OBJ_BLOB);
        if (blob.getSize() > maxBlobBytes) {
          diagnostics.add("Source/build file exceeds the configured byte limit: " + path); continue;
        }
        byte[] bytes = blob.getBytes(Charset.defaultCharset());
        boolean binary = false;
        for (int i = 0; i < Math.min(bytes.length, 8192); i++) if (bytes[i] == 0) { binary = true; break; }
        if (binary) { diagnostics.add("Binary source/build file omitted: " + path); continue; }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (java) sources.put(path, new JavaSourceSnapshot(name, commit.objectId(), tree.getObjectId(0).name(), path, text));
        else builds.put(path, text);
      }
    }
    return new Project(new JavaProjectSnapshot(name, commit.objectId(), sources), builds, diagnostics);
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
