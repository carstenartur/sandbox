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

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * Clones Git repositories using JGit.
 *
 * <p>Supports both shallow clones (for quick analysis) and full clones
 * (for commit history iteration).</p>
 */
public class RepoCloner {

	/**
	 * Clones a repository with full history.
	 *
	 * @param repoUrl   the URL of the Git repository
	 * @param branch    the branch to clone
	 * @param targetDir the directory to clone into
	 * @return the path to the cloned repository
	 * @throws IOException     if an I/O error occurs
	 * @throws GitAPIException if a Git operation fails
	 */
	public Path cloneRepo(String repoUrl, String branch, Path targetDir)
			throws IOException, GitAPIException {
		CloneCommand cloneCommand = Git.cloneRepository()
				.setURI(repoUrl)
				.setDirectory(targetDir.toFile())
				.setBranch(branch)
				.setNoCheckout(false);

		try (Git git = cloneCommand.call()) {
			return targetDir;
		}
	}

	/**
	 * Performs a shallow clone (depth 1) of the given repository.
	 *
	 * @param repoUrl   the URL of the Git repository
	 * @param branch    the branch to clone
	 * @param targetDir the directory to clone into
	 * @return the path to the cloned repository
	 * @throws IOException     if an I/O error occurs
	 * @throws GitAPIException if a Git operation fails
	 */
	public Path shallowClone(String repoUrl, String branch, Path targetDir)
			throws IOException, GitAPIException {
		CloneCommand cloneCommand = Git.cloneRepository()
				.setURI(repoUrl)
				.setDirectory(targetDir.toFile())
				.setBranch(branch)
				.setDepth(1)
				.setNoCheckout(false);

		try (Git git = cloneCommand.call()) {
			return targetDir;
		}
	}
}
