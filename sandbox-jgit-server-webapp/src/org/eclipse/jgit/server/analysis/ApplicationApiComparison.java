/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaSemanticDiff;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.SemanticChangeKind;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.entity.JavaSymbolIndex;

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
    var semantic = new HashMap<String, EnumSet<SemanticChangeKind>>();
    for (var change : new JavaSemanticDiff().compare(before.semantic(), after.semantic())) {
      if (change.kind() == SemanticChangeKind.BINDING_QUALITY_CHANGED) continue;
      add(semantic, change.before(), change.kind()); add(semantic, change.after(), change.kind());
    }
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
        if (labels.isEmpty()) {
          for (var kind : semantic.getOrDefault(path, EnumSet.noneOf(SemanticChangeKind.class))) {
            labels.add(switch (kind) {
              case ADDED -> "declarations added";
              case REMOVED -> "declarations removed";
              case MOVED -> "declarations moved";
              case RENAMED -> "declarations renamed";
              case SIGNATURE_CHANGED -> "signatures changed";
              case MODIFIERS_CHANGED -> "modifiers changed";
              case ANNOTATIONS_CHANGED -> "annotations changed";
              case BINDING_QUALITY_CHANGED -> throw new IllegalStateException("Binding changes are not API changes");
            });
          }
        }
        if (!labels.isEmpty()) changed.add(new Change(a, b, String.join(", ", labels)));
      }
    }
    return new Diff(added, removed, changed);
  }

  private static void add(Map<String, EnumSet<SemanticChangeKind>> changes, JavaSymbolIndex symbol,
      SemanticChangeKind kind) {
    if (symbol != null) changes.computeIfAbsent(symbol.getPath(), ignored -> EnumSet.noneOf(SemanticChangeKind.class)).add(kind);
  }

  private static boolean isPublic(AnalyzedFile file) {
    return file != null && file.visibility() != null && file.visibility().contains("public");
  }
}
