/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.history;

import java.util.List;

/** Application responses independent of either persistence implementation. */
public interface SandboxHistorySearch {

	/** Searches messages only; offset and limit apply after filtering. */
	List<CommitHit> searchMessages(String repository, String query, int offset, int limit);

	/** Searches paths changed by a commit, including deleted paths. */
	List<PathHit> searchChangedPaths(String repository, String query, int offset, int limit);

	/** Counts all indexed commits, grouping by the complete author identity. */
	List<AuthorCount> authors(String repository);

	/** Checks the active search backend without loading a result page. */
	void checkSearch();

	record CommitHit(String objectId, String message, String author, String authorEmail) { }

	record PathHit(String objectId, String message, String changedPaths) { }

	record AuthorCount(String name, String email, long commits) { }
}
