/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.server.history.GitHistorySnapshot;
import org.eclipse.jgit.server.history.GitHistorySnapshot.CommitView;
import org.eclipse.jgit.server.history.SandboxFileHistory.FileVersion;
import org.eclipse.jgit.treewalk.TreeWalk;
import io.github.carstenartur.jgit.storage.hibernate.javaanalysis.*;

/** All historical commit/path pairs, streamed into the public path index without Java parsing. */
final class HistoricalPathIndex implements Closeable {
  private final JavaFileSearchIndex index;
  private final String repositoryName;
  private final Map<String, CommitView> commits;

  private HistoricalPathIndex(String repositoryName, JavaFileSearchIndex index, Map<String, CommitView> commits) {
    this.repositoryName = repositoryName;
    this.index = index; this.commits = Map.copyOf(commits);
  }

  static HistoricalPathIndex build(String canonicalName, Repository git, List<CommitView> snapshot) throws IOException {
    GitHistorySnapshot.checkInterrupted();
    var commits = new HashMap<String, CommitView>(); snapshot.forEach(c -> commits.put(c.objectId(), c));
    try (var documents = new PathDocuments(canonicalName, git.newObjectReader(), snapshot)) {
      return new HistoricalPathIndex(canonicalName, JavaFileSearchIndex.build(documents), commits);
    } catch (UncheckedIOException failure) { throw failure.getCause(); }
  }

  List<FileVersion> search(String canonicalName, String text, int offset, int limit) throws IOException {
    return index.search(new JavaFileSearchQuery(canonicalName, null, null, text,
        Map.of(JavaFileSearchField.PATH, 1f), offset, limit)).stream().map(hit ->
            new FileVersion(hit.commitId(), hit.path(), hit.blobId(), hit.fileType(),
                commits.get(hit.commitId()).authorTime())).toList();
  }

  @Override public void close() throws IOException { index.close(); }
  String repositoryName() { return repositoryName; }

  private static String documentId(String commitId, String path) {
    try {
      // Git paths are unbounded; only the identity is indexed as an exact keyword.
      // Keep the complete path in the stored result and analyzed PATH field.
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8));
      return commitId + ":" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("The JVM must support SHA-256", unavailable);
    }
  }

  private static final class PathDocuments implements Iterable<JavaFileSearchDocument>, Closeable {
    private final String name;
    private final ObjectReader reader;
    private final Iterator<CommitView> commits;
    private TreeWalk tree;
    private CommitView commit;
    private boolean iterated;

    PathDocuments(String name, ObjectReader reader, List<CommitView> commits) {
      this.name = name; this.reader = reader; this.commits = commits.iterator();
    }

    @Override public Iterator<JavaFileSearchDocument> iterator() {
      if (iterated) throw new IllegalStateException("Historical path stream is single-use");
      iterated = true;
      return new Iterator<>() {
        private JavaFileSearchDocument next;
        @Override public boolean hasNext() {
          if (next != null) return true;
          try {
            for (;;) {
              GitHistorySnapshot.checkInterrupted();
              if (tree != null && tree.next()) {
                String path = tree.getPathString();
                int dot = path.lastIndexOf('.');
                String type = dot < 0 ? "unknown" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
                next = new JavaFileSearchDocument(name, documentId(commit.objectId(), path), commit.objectId(),
                    tree.getObjectId(0).name(), path, "", type, Map.of(JavaFileSearchField.PATH, path));
                return true;
              }
              if (tree != null) { tree.close(); tree = null; }
              if (!commits.hasNext()) return false;
              commit = commits.next(); tree = new TreeWalk(reader);
              tree.addTree(ObjectId.fromString(commit.treeId())); tree.setRecursive(true);
            }
          } catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        @Override public JavaFileSearchDocument next() {
          if (!hasNext()) throw new NoSuchElementException();
          var result = next; next = null; return result;
        }
      };
    }

    @Override public void close() {
      try { if (tree != null) tree.close(); }
      finally { reader.close(); }
    }
  }
}
