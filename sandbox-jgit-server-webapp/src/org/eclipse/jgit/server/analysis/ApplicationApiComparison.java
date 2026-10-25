/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaSemanticDiff;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaProjectAnalysisResult;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.SemanticChangeKind;

/** Sandbox's file-level API report, computed from complete Git snapshots and the public semantic diff. */
final class ApplicationApiComparison {
  private final JavaHistoryCorpus corpus;
  ApplicationApiComparison(JavaHistoryCorpus corpus) { this.corpus = Objects.requireNonNull(corpus); }

  record Change(AnalyzedFile before, AnalyzedFile after, String description) {}
  record Diff(List<AnalyzedFile> added, List<AnalyzedFile> removed, List<Change> changed) {
    Diff { added = List.copyOf(added); removed = List.copyOf(removed); changed = List.copyOf(changed); }
  }

  Diff compare(String repositoryName, Repository git, ObjectId beforeId, ObjectId afterId, boolean publicOnly)
      throws IOException {
    var before = corpus.atCommit(repositoryName, git, beforeId);
    var after = corpus.atCommit(repositoryName, git, afterId);
    var paths = new TreeSet<>(before.files().keySet()); paths.addAll(after.files().keySet());
    var added = new ArrayList<AnalyzedFile>(); var removed = new ArrayList<AnalyzedFile>();
    var changed = new ArrayList<Change>();
    for (String path : paths) {
      var a = before.files().get(path); var b = after.files().get(path);
      if (publicOnly && !isPublic(a) && !isPublic(b)) continue;
      if (a == null) added.add(b);
      else if (b == null) removed.add(a);
      else {
        var labels = new ArrayList<String>();
        if (!Objects.equals(a.facet(METHODS), b.facet(METHODS))) labels.add("methods changed");
        if (!Objects.equals(a.facet(METHOD_SIGNATURES), b.facet(METHOD_SIGNATURES))) labels.add("signatures changed");
        if (!Objects.equals(a.visibility(), b.visibility())) labels.add("visibility changed");
        // A heuristic declaration match is evidence of a change, not an exact rename diagnosis.
        if (labels.isEmpty() && declarationsChanged(before.semantic(), after.semantic(), path)) labels.add("declarations changed");
        if (!labels.isEmpty()) changed.add(new Change(a, b, String.join(", ", labels)));
      }
    }
    return new Diff(added, removed, changed);
  }

  private static boolean declarationsChanged(JavaProjectAnalysisResult before,
      JavaProjectAnalysisResult after, String path) {
    // Match declarations only within this retained file. Cross-file heuristic matches can consume
    // an unrelated exact identity. Binding analysis still used the entire project on each side.
    if (!before.files().containsKey(path) || !after.files().containsKey(path)) return false;
    return new JavaSemanticDiff().compare(fileView(before, path), fileView(after, path)).stream()
        .anyMatch(change -> change.kind() != SemanticChangeKind.BINDING_QUALITY_CHANGED);
  }

  private static JavaProjectAnalysisResult fileView(JavaProjectAnalysisResult project, String path) {
    var file = project.files().get(path);
    return new JavaProjectAnalysisResult(project.project(), Map.of(path, file), file.symbols(), file.references(),
        file.analysisRun().getProblemCount(), file.analysisRun().getErrorCount());
  }

  private static boolean isPublic(AnalyzedFile file) {
    return file != null && file.visibility() != null && file.visibility().contains("public");
  }
}
