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
package org.sandbox.mining.gemini.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

/**
 * Extracts the diff of a commit as a string using JGit's DiffFormatter.
 *
 * <p>Limits the diff output to a configurable maximum number of lines
 * to avoid sending overly large prompts to the Gemini API.</p>
 */
public class DiffExtractor {

	private final Repository repository;
	private final int maxDiffLines;

	/**
	 * Creates a DiffExtractor for the given repository directory.
	 *
	 * @param repoDir      the local repository directory
	 * @param maxDiffLines maximum number of diff lines to include
	 * @throws IOException if the repository cannot be opened
	 */
	public DiffExtractor(Path repoDir, int maxDiffLines) throws IOException {
		this.repository = Git.open(repoDir.toFile()).getRepository();
		this.maxDiffLines = maxDiffLines;
	}

	/**
	 * Extracts the diff of a commit as a string.
	 *
	 * @param commit the commit to extract the diff from
	 * @return the diff as a string, truncated to maxDiffLines
	 * @throws IOException if a Git operation fails
	 */
	public String extractDiff(RevCommit commit) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (DiffFormatter formatter = new DiffFormatter(out)) {
			formatter.setRepository(repository);
			formatter.setDetectRenames(true);

			AbstractTreeIterator parentTree = getParentTree(commit);
			AbstractTreeIterator commitTree = getTree(commit);

			List<DiffEntry> diffs = formatter.scan(parentTree, commitTree);
			for (DiffEntry entry : diffs) {
				formatter.format(entry);
			}
		}

		String fullDiff = out.toString(java.nio.charset.StandardCharsets.UTF_8);
		return truncateToMaxLines(fullDiff);
	}

	private AbstractTreeIterator getParentTree(RevCommit commit) throws IOException {
		if (commit.getParentCount() == 0) {
			return new EmptyTreeIterator();
		}
		RevCommit parent = commit.getParent(0);
		return getTree(parent);
	}

	private AbstractTreeIterator getTree(RevCommit commit) throws IOException {
		try (ObjectReader reader = repository.newObjectReader()) {
			CanonicalTreeParser parser = new CanonicalTreeParser();
			parser.reset(reader, commit.getTree().getId());
			return parser;
		}
	}

	private String truncateToMaxLines(String diff) {
		String[] lines = diff.split("\n", -1);
		if (lines.length <= maxDiffLines) {
			return diff;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < maxDiffLines; i++) {
			sb.append(lines[i]).append('\n');
		}
		sb.append("\n... (truncated, ").append(lines.length - maxDiffLines)
				.append(" more lines)");
		return sb.toString();
	}
}
