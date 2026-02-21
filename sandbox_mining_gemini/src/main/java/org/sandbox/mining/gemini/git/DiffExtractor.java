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
import java.io.Closeable;
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
 *
 * <p>If the commit touches more files than {@code maxFilesPerCommit}, an empty
 * string is returned so that the caller treats the commit as skipped (mass-change
 * commits such as auto-formatting or renames are not useful for DSL mining).</p>
 *
 * <p>Implements {@link Closeable} to ensure the underlying Git and
 * Repository resources are properly released.</p>
 */
public class DiffExtractor implements Closeable {

	private final Git git;
	private final Repository repository;
	private final int maxDiffLines;
	private final List<String> pathFilters;
	private final int maxFilesPerCommit;

	/**
	 * Creates a DiffExtractor for the given repository directory.
	 *
	 * @param repoDir      the local repository directory
	 * @param maxDiffLines maximum number of diff lines to include
	 * @throws IOException if the repository cannot be opened
	 */
	public DiffExtractor(Path repoDir, int maxDiffLines) throws IOException {
		this(repoDir, maxDiffLines, List.of(), Integer.MAX_VALUE);
	}

	/**
	 * Creates a DiffExtractor for the given repository directory with path filtering.
	 *
	 * @param repoDir      the local repository directory
	 * @param maxDiffLines maximum number of diff lines to include
	 * @param pathFilters  list of path prefixes to include (empty = all paths)
	 * @throws IOException if the repository cannot be opened
	 */
	public DiffExtractor(Path repoDir, int maxDiffLines, List<String> pathFilters) throws IOException {
		this(repoDir, maxDiffLines, pathFilters, Integer.MAX_VALUE);
	}

	/**
	 * Creates a DiffExtractor for the given repository directory with path filtering
	 * and a maximum file count per commit.
	 *
	 * @param repoDir           the local repository directory
	 * @param maxDiffLines      maximum number of diff lines to include
	 * @param pathFilters       list of path prefixes to include (empty = all paths)
	 * @param maxFilesPerCommit maximum number of changed files before the commit is
	 *                          skipped (returns empty string)
	 * @throws IOException if the repository cannot be opened
	 */
	public DiffExtractor(Path repoDir, int maxDiffLines, List<String> pathFilters,
			int maxFilesPerCommit) throws IOException {
		this.git = Git.open(repoDir.toFile());
		this.repository = git.getRepository();
		this.maxDiffLines = maxDiffLines;
		this.pathFilters = pathFilters != null ? pathFilters : List.of();
		this.maxFilesPerCommit = maxFilesPerCommit;
	}

	/**
	 * Extracts the diff of a commit as a string.
	 *
	 * <p>Returns an empty string if the commit touches more files (after path
	 * filtering) than {@code maxFilesPerCommit}, so that the caller can treat it as
	 * a skipped commit (mass-change commits are not useful for DSL mining).</p>
	 *
	 * @param commit the commit to extract the diff from
	 * @return the diff as a string truncated to maxDiffLines, or an empty string if
	 *         too many files are changed
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

			// Skip commits that touch too many files (mass-changes are not useful for mining)
			long matchingFiles = diffs.stream().filter(this::matchesPathFilter).count();
			if (matchingFiles > maxFilesPerCommit) {
				return ""; //$NON-NLS-1$
			}

			for (DiffEntry entry : diffs) {
				if (matchesPathFilter(entry)) {
					formatter.format(entry);
				}
			}
		}

		String fullDiff = out.toString(java.nio.charset.StandardCharsets.UTF_8);
		return truncateToMaxLines(fullDiff);
	}

	private boolean matchesPathFilter(DiffEntry entry) {
		if (pathFilters.isEmpty()) {
			return true;
		}
		String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
				? entry.getOldPath()
				: entry.getNewPath();
		return pathFilters.stream().anyMatch(path::startsWith);
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

	@Override
	public void close() {
		repository.close();
		git.close();
	}
}
