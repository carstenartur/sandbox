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
package org.sandbox.mining.gemini.action;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * Creates pull requests for YELLOW-rated DSL proposals.
 *
 * <p>Appends a mined DSL rule to the target hint file on a new branch
 * so that it can be reviewed via a pull request.</p>
 */
public class PullRequestCreator {

	/**
	 * Prepares a branch with the proposed DSL rule for pull request creation.
	 *
	 * @param repoDir        the local repository directory
	 * @param targetHintFile the relative path to the .sandbox-hint file
	 * @param dslRule        the DSL rule to append
	 * @param commitHash     the source commit hash
	 * @param category       the category of the rule
	 * @param summary        a summary of the rule
	 * @return the name of the created branch
	 * @throws IOException     if an I/O error occurs
	 * @throws GitAPIException if a Git operation fails
	 */
	public String prepareBranch(Path repoDir, String targetHintFile, String dslRule,
			String commitHash, String category, String summary)
			throws IOException, GitAPIException {
		String branchName = "mining/proposal-" + commitHash;

		try (Git git = Git.open(repoDir.toFile())) {
			// Create and checkout new branch
			git.checkout().setCreateBranch(true).setName(branchName).call();

			// Append the rule
			Path hintFile = repoDir.resolve(targetHintFile);
			Path parent = hintFile.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}

			StringBuilder content = new StringBuilder();
			content.append("\n// Mined from commit ").append(commitHash).append('\n');
			content.append("// Category: ").append(category).append('\n');
			content.append(dslRule).append('\n');

			Files.writeString(hintFile, content.toString(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);

			// Stage and commit
			git.add().addFilepattern(targetHintFile).call();
			git.commit()
					.setMessage("mining: DSL rule from " + commitHash + "\n\n" + summary)
					.call();
		}

		return branchName;
	}
}
