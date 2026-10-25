/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.history;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Application snapshot of all ref ancestry plus still-available indexed historical roots. */
public final class GitHistorySnapshot {
  private GitHistorySnapshot() {}

  public static List<CommitView> read(Repository repository, Collection<String> indexedCommits) throws IOException {
    var result = new ArrayList<CommitView>();
    try (var walk = new RevWalk(repository)) {
      walk.setRetainBody(false);
      var refs = new ArrayList<>(repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS));
      Ref head = repository.exactRef(Constants.HEAD);
      if (head != null) refs.add(head);
      refs.sort(Comparator.comparing(Ref::getName));
      for (var ref : refs) {
        checkInterrupted();
        if (ref.getObjectId() != null) {
          var target = walk.peel(walk.parseAny(ref.getObjectId()));
          if (target instanceof RevCommit commit) walk.markStart(commit);
        }
      }
      for (String indexed : indexedCommits) {
        checkInterrupted();
        try {
          walk.markStart(walk.parseCommit(ObjectId.fromString(indexed)));
        } catch (MissingObjectException pruned) {
          // A retained derived row cannot resurrect an object already removed by Git maintenance.
          // Missing authoritative ref targets above remain errors.
        }
      }
      for (var commit : walk) {
        checkInterrupted();
        walk.parseBody(commit);
        var author = commit.getAuthorIdent();
        result.add(new CommitView(commit.name(), commit.getTree().name(),
            author == null ? null : author.getWhenAsInstant(), author == null ? null : author.getName(),
            author == null ? null : author.getEmailAddress()));
        commit.disposeBody();
      }
    }
    result.sort(Comparator.comparing(CommitView::authorTime, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(CommitView::objectId));
    return List.copyOf(result);
  }

  public static void checkInterrupted() throws InterruptedIOException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("History read interrupted");
  }

  public record CommitView(String objectId, String treeId, Instant authorTime, String authorName, String authorEmail) {}
}
