/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.jdt.triggerpattern.mining.git;

import java.nio.file.Path;
import java.util.List;

import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;

/**
 * Abstraction for reading Git history and extracting file diffs.
 *
 * <p>Implementations may use JGit, EGit, or command-line {@code git} to access
 * the repository. Each implementation should be able to:</p>
 * <ul>
 *   <li>List recent commits for a project</li>
 *   <li>Extract file diffs (before/after content) for a given commit</li>
 *   <li>Retrieve file content at a specific revision</li>
 * </ul>
 *
 * @since 1.2.6
 */
public interface GitHistoryProvider {

	/**
	 * Returns the commit history for the repository at the given path.
	 *
	 * @param repositoryPath path to the working tree (or {@code .git} directory)
	 * @param maxCommits     maximum number of commits to return
	 * @return list of commit metadata, newest first
	 * @throws GitProviderException if the repository cannot be accessed
	 */
	List<CommitInfo> getHistory(Path repositoryPath, int maxCommits);

	/**
	 * Returns the file diffs for a specific commit.
	 *
	 * <p>Each {@link FileDiff} contains the full before/after content of the file
	 * and the individual diff hunks. Only Java source files are included.</p>
	 *
	 * @param repositoryPath path to the working tree
	 * @param commitId       the full or abbreviated commit hash
	 * @return list of file diffs for Java files changed in the commit
	 * @throws GitProviderException if the commit cannot be found or read
	 */
	List<FileDiff> getDiffs(Path repositoryPath, String commitId);

	/**
	 * Returns the content of a file at a specific commit.
	 *
	 * @param repositoryPath path to the working tree
	 * @param commitId       the full or abbreviated commit hash
	 * @param filePath       the file path relative to the repository root
	 * @return the file content as a string, or {@code null} if the file does not exist
	 * @throws GitProviderException if the repository cannot be accessed
	 */
	String getFileContent(Path repositoryPath, String commitId, String filePath);
}
