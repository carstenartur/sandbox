/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.views.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Filesystem contract for the Refactoring Mining project chooser. */
public class RefactoringMiningViewRepositorySelectionTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	public void acceptsProjectsNestedInsideAGitWorkingTree() throws Exception {
		Path repository= temporaryDirectory.resolve("repository"); //$NON-NLS-1$
		Files.createDirectories(repository.resolve(".git")); //$NON-NLS-1$
		Path project= repository.resolve("modules/example"); //$NON-NLS-1$
		Files.createDirectories(project);

		assertTrue(RefactoringMiningView.isGitBackedLocation(project));
	}

	@Test
	public void acceptsWorktreesWhoseGitMarkerIsAFile() throws Exception {
		Path repository= temporaryDirectory.resolve("worktree"); //$NON-NLS-1$
		Files.createDirectories(repository);
		Files.writeString(repository.resolve(".git"), //$NON-NLS-1$
				"gitdir: ../common/.git/worktrees/worktree\n"); //$NON-NLS-1$

		assertTrue(RefactoringMiningView.isGitBackedLocation(repository));
	}

	@Test
	public void rejectsOrdinaryDirectories() throws Exception {
		Path project= temporaryDirectory.resolve("ordinary-project"); //$NON-NLS-1$
		Files.createDirectories(project);

		assertFalse(RefactoringMiningView.isGitBackedLocation(project));
		assertFalse(RefactoringMiningView.isGitBackedLocation(null));
	}
}
