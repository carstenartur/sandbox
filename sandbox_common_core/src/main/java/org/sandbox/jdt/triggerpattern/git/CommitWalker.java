/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.git;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;

/**
 * Iterates over commits in a Git repository using JGit's RevWalk.
 *
 * <p>Supports date-based filtering and batch iteration, allowing
 * the mining process to resume from the last processed commit.</p>
 */
public class CommitWalker implements Closeable {

	private final Git git;
	private final Repository repository;

	/**
	 * Creates a CommitWalker for the given repository directory.
	 *
	 * @param repoDir the local repository directory
	 * @throws IOException if the repository cannot be opened
	 */
	public CommitWalker(Path repoDir) throws IOException {
		this.git = Git.open(repoDir.toFile());
		this.repository = git.getRepository();
	}

	/**
	 * Returns the next batch of commits after the given commit hash.
	 *
	 * @param afterCommitHash the commit hash to start after (null for beginning)
	 * @param startDate       only include commits after this date ({@code yyyy-MM-dd} format, may be null)
	 * @param batchSize       maximum number of commits to return
	 * @return list of commits in chronological order
	 * @throws IOException if a Git operation fails
	 */
	public List<RevCommit> nextBatch(String afterCommitHash, String startDate, int batchSize)
			throws IOException {
		List<RevCommit> batch = new ArrayList<>();

		try (RevWalk walk = new RevWalk(repository)) {
			ObjectId head = repository.resolve("HEAD");
			if (head == null) {
				return batch;
			}
			walk.markStart(walk.parseCommit(head));
			walk.sort(RevSort.REVERSE); // chronological order

			// Apply date filter
			if (startDate != null && !startDate.isBlank()) {
				try {
					Date since = new SimpleDateFormat("yyyy-MM-dd").parse(startDate);
					walk.setRevFilter(CommitTimeRevFilter.after(since));
				} catch (ParseException e) {
					System.err.println("Invalid start-date format: " + startDate);
				}
			}

			boolean pastAnchor = (afterCommitHash == null || afterCommitHash.isBlank());

			for (RevCommit commit : walk) {
				if (!pastAnchor) {
					if (commit.getName().equals(afterCommitHash)) {
						pastAnchor = true;
					}
					continue;
				}

				batch.add(commit);
				if (batch.size() >= batchSize) {
					break;
				}
			}
		}

		return batch;
	}

	@Override
	public void close() {
		repository.close();
		git.close();
	}
}
