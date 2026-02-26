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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.sandbox.jdt.triggerpattern.mining.analysis.CommitInfo;
import org.sandbox.jdt.triggerpattern.mining.analysis.DiffHunk;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;

/**
 * {@link GitHistoryProvider} implementation that uses JGit to read Git history.
 *
 * <p>This provider works with any local Git repository and does not require
 * the {@code git} command-line tool. It reuses the JGit library that is already
 * available in the Eclipse runtime (via EGit) and in {@code sandbox_common_core}.</p>
 *
 * <p>This is the Eclipse Git Bridge (Phase 1 of Issue #727): it provides the
 * same {@link GitHistoryProvider} interface as {@link CommandLineGitProvider}
 * but uses JGit directly, which is more reliable and portable.</p>
 *
 * @since 1.2.6
 */
public class JGitHistoryProvider implements GitHistoryProvider {

	@Override
	public List<CommitInfo> getHistory(Path repositoryPath, int maxCommits) {
		List<CommitInfo> commits = new ArrayList<>();
		try (Git git = Git.open(repositoryPath.toFile())) {
			LogCommand log = git.log().setMaxCount(maxCommits);
			for (RevCommit commit : log.call()) {
				int fileCount = countChangedFiles(git.getRepository(), commit);
				commits.add(toCommitInfo(commit, fileCount));
			}
		} catch (IOException | GitAPIException e) {
			throw new GitProviderException("Failed to read Git history from " //$NON-NLS-1$
					+ repositoryPath, e);
		}
		return commits;
	}

	@Override
	public List<FileDiff> getDiffs(Path repositoryPath, String commitId) {
		List<FileDiff> diffs = new ArrayList<>();
		try (Git git = Git.open(repositoryPath.toFile())) {
			Repository repository = git.getRepository();
			RevCommit commit = resolveCommit(repository, commitId);
			if (commit == null) {
				return diffs;
			}

			AbstractTreeIterator oldTreeIter = getParentTreeIterator(repository, commit);
			AbstractTreeIterator newTreeIter = prepareTreeParser(repository, commit);

			try (DiffFormatter formatter = new DiffFormatter(new ByteArrayOutputStream())) {
				formatter.setRepository(repository);
				formatter.setPathFilter(PathSuffixFilter.create(".java")); //$NON-NLS-1$
				List<DiffEntry> entries = formatter.scan(oldTreeIter, newTreeIter);

				for (DiffEntry entry : entries) {
					if (entry.getChangeType() != DiffEntry.ChangeType.MODIFY) {
						continue;
					}
					String filePath = entry.getNewPath();
					String contentBefore = getFileContentAtCommit(repository,
							commit.getParent(0), filePath);
					String contentAfter = getFileContentAtCommit(repository,
							commit, filePath);
					if (contentBefore == null || contentAfter == null) {
						continue;
					}
					List<DiffHunk> hunks = extractHunks(formatter, entry);
					diffs.add(new FileDiff(filePath, contentBefore, contentAfter, hunks));
				}
			}
		} catch (IOException | GitAPIException e) {
			throw new GitProviderException("Failed to get diffs for commit " //$NON-NLS-1$
					+ commitId, e);
		}
		return diffs;
	}

	@Override
	public String getFileContent(Path repositoryPath, String commitId, String filePath) {
		try (Git git = Git.open(repositoryPath.toFile())) {
			Repository repository = git.getRepository();
			RevCommit commit = resolveCommit(repository, commitId);
			if (commit == null) {
				return null;
			}
			return getFileContentAtCommit(repository, commit, filePath);
		} catch (IOException e) {
			return null;
		}
	}

	// ---- internal helpers ----

	private static CommitInfo toCommitInfo(RevCommit commit, int fileCount) {
		LocalDateTime timestamp = LocalDateTime.ofInstant(
				Instant.ofEpochSecond(commit.getCommitTime()),
				ZoneId.systemDefault());
		return new CommitInfo(
				commit.getName(),
				commit.abbreviate(7).name(),
				commit.getShortMessage(),
				commit.getAuthorIdent().getName(),
				timestamp,
				fileCount);
	}

	private static RevCommit resolveCommit(Repository repository, String commitId)
			throws IOException {
		ObjectId objectId = repository.resolve(commitId);
		if (objectId == null) {
			return null;
		}
		try (org.eclipse.jgit.revwalk.RevWalk revWalk =
				new org.eclipse.jgit.revwalk.RevWalk(repository)) {
			return revWalk.parseCommit(objectId);
		}
	}

	private static int countChangedFiles(Repository repository, RevCommit commit)
			throws IOException {
		try (DiffFormatter formatter = new DiffFormatter(
				new ByteArrayOutputStream())) {
			formatter.setRepository(repository);
			AbstractTreeIterator oldIter = getParentTreeIterator(repository, commit);
			AbstractTreeIterator newIter = prepareTreeParser(repository, commit);
			return formatter.scan(oldIter, newIter).size();
		}
	}

	private static AbstractTreeIterator getParentTreeIterator(Repository repository,
			RevCommit commit) throws IOException {
		if (commit.getParentCount() == 0) {
			return new EmptyTreeIterator();
		}
		RevCommit parent;
		try (org.eclipse.jgit.revwalk.RevWalk revWalk =
				new org.eclipse.jgit.revwalk.RevWalk(repository)) {
			parent = revWalk.parseCommit(commit.getParent(0).getId());
		}
		return prepareTreeParser(repository, parent);
	}

	private static CanonicalTreeParser prepareTreeParser(Repository repository,
			RevCommit commit) throws IOException {
		RevTree tree = commit.getTree();
		CanonicalTreeParser parser = new CanonicalTreeParser();
		try (ObjectReader reader = repository.newObjectReader()) {
			parser.reset(reader, tree.getId());
		}
		return parser;
	}

	private static String getFileContentAtCommit(Repository repository,
			RevCommit commit, String filePath) throws IOException {
		RevTree tree = commit.getTree();
		try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, tree)) {
			if (treeWalk == null) {
				return null;
			}
			ObjectId blobId = treeWalk.getObjectId(0);
			ObjectLoader loader = repository.open(blobId);
			return new String(loader.getBytes(), StandardCharsets.UTF_8);
		}
	}

	private static List<DiffHunk> extractHunks(DiffFormatter formatter,
			DiffEntry entry) throws IOException {
		List<DiffHunk> hunks = new ArrayList<>();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (DiffFormatter hunkFormatter = new DiffFormatter(out)) {
			hunkFormatter.setRepository(formatter.getRepository());
			hunkFormatter.format(entry);
		}
		String diffText = out.toString(StandardCharsets.UTF_8);
		// Parse the unified diff output into hunks
		return parseHunksFromDiff(diffText);
	}

	/**
	 * Parses unified diff output into a list of diff hunks.
	 *
	 * @param diffText the unified diff text
	 * @return list of parsed diff hunks
	 */
	static List<DiffHunk> parseHunksFromDiff(String diffText) {
		List<DiffHunk> hunks = new ArrayList<>();
		if (diffText == null || diffText.isEmpty()) {
			return hunks;
		}

		String[] lines = diffText.split("\n"); //$NON-NLS-1$
		int i = 0;
		while (i < lines.length) {
			if (lines[i].startsWith("@@")) { //$NON-NLS-1$
				int hunkStart = i;
				i++;
				StringBuilder beforeText = new StringBuilder();
				StringBuilder afterText = new StringBuilder();
				while (i < lines.length && !lines[i].startsWith("@@") //$NON-NLS-1$
						&& !lines[i].startsWith("diff ")) { //$NON-NLS-1$
					if (lines[i].startsWith("-")) { //$NON-NLS-1$
						beforeText.append(lines[i].substring(1)).append('\n');
					} else if (lines[i].startsWith("+")) { //$NON-NLS-1$
						afterText.append(lines[i].substring(1)).append('\n');
					} else if (lines[i].startsWith(" ")) { //$NON-NLS-1$
						beforeText.append(lines[i].substring(1)).append('\n');
						afterText.append(lines[i].substring(1)).append('\n');
					}
					i++;
				}
				int[] ranges = parseHunkHeader(lines[hunkStart]);
				hunks.add(new DiffHunk(ranges[0], ranges[1], ranges[2], ranges[3],
						beforeText.toString(), afterText.toString()));
			} else {
				i++;
			}
		}
		return hunks;
	}

	private static int[] parseHunkHeader(String header) {
		// Parse @@ -startLine,count +startLine,count @@
		int atEnd = header.indexOf("@@", 2); //$NON-NLS-1$
		if (atEnd < 0) {
			return new int[] { 1, 0, 1, 0 };
		}
		String range = header.substring(3, atEnd).strip();
		String[] parts = range.split("\\s+"); //$NON-NLS-1$
		if (parts.length < 2) {
			return new int[] { 1, 0, 1, 0 };
		}
		int[] before = parseRange(parts[0].substring(1));
		int[] after = parseRange(parts[1].substring(1));
		return new int[] { before[0], before[1], after[0], after[1] };
	}

	private static int[] parseRange(String rangeStr) {
		String[] parts = rangeStr.split(","); //$NON-NLS-1$
		try {
			int start = Integer.parseInt(parts[0]);
			int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
			return new int[] { start, count };
		} catch (NumberFormatException e) {
			return new int[] { 1, 0 };
		}
	}
}
