/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.history;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.server.repository.SandboxRepositoryNames;
import org.eclipse.jgit.server.repository.SandboxRepositoryService;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.hibernate.SessionFactory;

/** File-version view from public JGit reads and released Search's known commit identities. */
public final class ReleasedSandboxFileHistory implements SandboxFileHistory {
  private final SessionFactory factory;
  private final SandboxRepositoryService repositories;
  private final boolean enabled;

  public ReleasedSandboxFileHistory(SessionFactory factory, SandboxRepositoryService repositories) {
    this(factory, repositories, true);
  }

  /** Disabling the derived history view never removes authoritative Git objects. */
  public ReleasedSandboxFileHistory(SessionFactory factory, SandboxRepositoryService repositories, boolean enabled) {
    this.factory = Objects.requireNonNull(factory);
    this.repositories = Objects.requireNonNull(repositories);
    this.enabled = enabled;
  }

  @Override
  public List<FileVersion> versions(String repository, String path, int offset, int limit) throws IOException {
    if (offset < 0 || limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid history page");
    if (!enabled) return List.of();
    if (repository == null || repository.isBlank() || path == null || path.isEmpty()) return List.of();
    // TreeWalk normalizes a trailing slash; this endpoint promises an exact file path.
    if (path.endsWith("/")) return List.of();
    String canonical = SandboxRepositoryNames.normalize(repository);
    try {
      return repositories.withExistingRepository(repository, git -> {
        List<String> indexed;
        try (var session = factory.openSession()) {
          indexed = session.createQuery("SELECT c.objectId FROM GitCommitIndex c WHERE c.repositoryName = :repo", String.class)
              .setParameter("repo", canonical).getResultList();
        }
        var commits = GitHistorySnapshot.read(git, indexed);
        var result = new ArrayList<FileVersion>();
        long matched = 0;
        try (var reader = git.newObjectReader()) {
          for (var commit : commits) {
            GitHistorySnapshot.checkInterrupted();
            try (var tree = TreeWalk.forPath(reader, path, ObjectId.fromString(commit.treeId()))) {
              if (tree == null || tree.getFileMode(0).getObjectType() == Constants.OBJ_TREE) continue;
              if (matched++ < offset) continue;
              result.add(new FileVersion(commit.objectId(), path, tree.getObjectId(0).name(), fileType(path), commit.authorTime()));
              if (result.size() == limit) break;
            }
          }
        }
        return List.copyOf(result);
      });
    } catch (FileNotFoundException missing) {
      return List.of();
    }
  }

  private static String fileType(String path) {
    int dot = path.lastIndexOf('.');
    return dot < 0 ? "unknown" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
