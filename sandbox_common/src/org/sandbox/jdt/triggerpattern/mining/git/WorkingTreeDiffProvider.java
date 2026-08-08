/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.mining.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.sandbox.jdt.triggerpattern.mining.analysis.DiffHunk;
import org.sandbox.jdt.triggerpattern.mining.analysis.FileDiff;

/**
 * Computes Java source changes between {@code HEAD} and the current working tree.
 *
 * <p>This deliberately compares the committed tree directly with the filesystem
 * working tree, so the result represents the source the user currently sees:
 * staged and unstaged changes are both included. Added and deleted Java files are
 * represented with an empty before/after side respectively.</p>
 */
public final class WorkingTreeDiffProvider {

	/** Returns Java-file changes from HEAD to the current working tree. */
	public List<FileDiff> getDiffs(Path repositoryPath) {
		List<FileDiff> result = new ArrayList<>();
		try (Git git = Git.open(repositoryPath.toFile())) {
			Repository repository = git.getRepository();
			RevCommit head = resolveHead(repository);
			AbstractTreeIterator oldTree = head != null
					? treeParser(repository, head)
					: new EmptyTreeIterator();
			FileTreeIterator workingTree = new FileTreeIterator(repository);

			List<DiffEntry> entries = git.diff()
					.setOldTree(oldTree)
					.setNewTree(workingTree)
					.setPathFilter(PathSuffixFilter.create(".java")) //$NON-NLS-1$
					.call();

			Path workTree = repository.getWorkTree().toPath();
			for (DiffEntry entry : entries) {
				String filePath = entry.getChangeType() == DiffEntry.ChangeType.DELETE
						? entry.getOldPath()
						: entry.getNewPath();
				String before = entry.getChangeType() == DiffEntry.ChangeType.ADD || head == null
						? "" //$NON-NLS-1$
						: readCommittedFile(repository, head, entry.getOldPath());
				String after = entry.getChangeType() == DiffEntry.ChangeType.DELETE
						? "" //$NON-NLS-1$
						: readWorkingFile(workTree.resolve(entry.getNewPath()));
				if (before == null) {
					before = ""; //$NON-NLS-1$
				}
				if (after == null) {
					after = ""; //$NON-NLS-1$
				}
				if (!before.equals(after)) {
					DiffHunk completeFile = new DiffHunk(1, lineCount(before), 1, lineCount(after), before, after);
					result.add(new FileDiff(filePath, before, after, List.of(completeFile)));
				}
			}
		} catch (IOException | GitAPIException e) {
			throw new GitProviderException("Failed to diff HEAD against working tree at " + repositoryPath, e); //$NON-NLS-1$
		}
		return result;
	}

	private static RevCommit resolveHead(Repository repository) throws IOException {
		ObjectId headId = repository.resolve(Constants.HEAD);
		if (headId == null) {
			return null;
		}
		try (RevWalk walk = new RevWalk(repository)) {
			return walk.parseCommit(headId);
		}
	}

	private static CanonicalTreeParser treeParser(Repository repository, RevCommit commit) throws IOException {
		CanonicalTreeParser parser = new CanonicalTreeParser();
		try (ObjectReader reader = repository.newObjectReader()) {
			parser.reset(reader, commit.getTree().getId());
		}
		return parser;
	}

	private static String readCommittedFile(Repository repository, RevCommit commit, String path) throws IOException {
		try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
			if (treeWalk == null) {
				return null;
			}
			ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
			return new String(loader.getBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String readWorkingFile(Path path) throws IOException {
		return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : null;
	}

	private static int lineCount(String text) {
		return text.isEmpty() ? 0 : text.split("\\n", -1).length; //$NON-NLS-1$
	}
}
