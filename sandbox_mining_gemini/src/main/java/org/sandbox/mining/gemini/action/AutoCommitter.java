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
 * Automatically commits GREEN-rated DSL proposals directly to the repository.
 *
 * <p>Appends a mined DSL rule to the target hint file and commits it
 * with an appropriate commit message.</p>
 */
public class AutoCommitter {

	/**
	 * Commits a DSL rule to the given repository.
	 *
	 * @param repoDir        the local repository directory
	 * @param targetHintFile the relative path to the .sandbox-hint file
	 * @param dslRule        the DSL rule to append
	 * @param commitHash     the source commit hash
	 * @param category       the category of the rule
	 * @param summary        a summary of the rule
	 * @throws IOException     if an I/O error occurs
	 * @throws GitAPIException if a Git operation fails
	 */
	public void commit(Path repoDir, String targetHintFile, String dslRule,
			String commitHash, String category, String summary)
			throws IOException, GitAPIException {
		Path hintFile = repoDir.resolve(targetHintFile);

		// Ensure parent directory exists
		Path parent = hintFile.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		// Append the rule
		StringBuilder content = new StringBuilder();
		content.append("\n// Mined from commit ").append(commitHash).append('\n');
		content.append("// Category: ").append(category).append('\n');
		content.append(dslRule).append('\n');

		Files.writeString(hintFile, content.toString(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);

		// Commit
		try (Git git = Git.open(repoDir.toFile())) {
			git.add().addFilepattern(targetHintFile).call();
			git.commit()
					.setMessage("mining: DSL rule from " + commitHash + " [" + category + "]\n\n" + summary)
					.call();
		}
	}
}
