/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.BindingStatus;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaAnalysisStatus;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchDocument;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.JavaFileSearchField;

/** Application file view; contains no mutable ORM entities or parser nodes. */
public record AnalyzedFile(String repositoryName, String commitObjectId, String blobObjectId, String filePath,
    String fileType, String projectName, String packageName, String simpleClassName, String typeKind,
    String visibility, int lineCount, boolean hasMainMethod, String primaryDocumentation,
    String commitAuthor, Instant commitDate, Map<JavaFileSearchField, String> facets, Quality quality) {
  public AnalyzedFile { facets = Map.copyOf(facets); }

  public String facet(JavaFileSearchField field) { return facets.getOrDefault(field, ""); }

  /** The historical search cardinality is one first-provenance document per repository/blob. */
  public JavaFileSearchDocument searchDocument() {
    return new JavaFileSearchDocument(repositoryName, blobObjectId, commitObjectId, blobObjectId,
        filePath, projectName, fileType, facets);
  }

  /** Binding counts include symbols and references; diagnostics never imply a complete classpath. */
  public record Quality(JavaAnalysisStatus status, int problemCount, int errorCount, String failureMessage,
      Map<BindingStatus, Long> bindings, List<String> unresolvedDependencies, List<String> configurationDiagnostics) {
    public Quality {
      bindings = Map.copyOf(bindings);
      unresolvedDependencies = List.copyOf(unresolvedDependencies);
      configurationDiagnostics = List.copyOf(configurationDiagnostics);
    }
  }
}
