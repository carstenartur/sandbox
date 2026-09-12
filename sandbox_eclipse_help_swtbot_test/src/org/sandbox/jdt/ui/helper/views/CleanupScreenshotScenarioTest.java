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
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CleanupScreenshotScenarioTest {

	@Test
	void copiesSourceAndPendingUndoSets() {
		Set<String> sources= new HashSet<>(Set.of("src/Example.java")); //$NON-NLS-1$
		Set<String> pendingUndo= new HashSet<>(sources);
		var scenario= scenario(sources, pendingUndo);
		sources.clear();
		pendingUndo.clear();
		assertEquals(Set.of("src/Example.java"), scenario.requiredSourceFiles()); //$NON-NLS-1$
		assertEquals(Set.of("src/Example.java"), scenario.pendingUndoFiles()); //$NON-NLS-1$
		assertThrows(UnsupportedOperationException.class, () -> scenario.requiredSourceFiles().clear());
		assertThrows(UnsupportedOperationException.class, () -> scenario.pendingUndoFiles().clear());
	}

	@Test
	void rejectsMissingScenarioIdentity() {
		assertThrows(IllegalArgumentException.class, () -> new CleanupScreenshotScenario(
				" ", PreviewContract.FILE_COMBINED_DIFF, "Project", Set.of("src/A.java"), Set.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	void rejectsMissingPreviewContract() {
		assertThrows(NullPointerException.class, () -> new CleanupScreenshotScenario(
				"example", null, "Project", Set.of("src/A.java"), Set.of())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	void rejectsEmptySourceSelection() {
		assertThrows(IllegalArgumentException.class, () -> scenario(Set.of(), Set.of()));
	}

	@Test
	void rejectsNonCanonicalProjectRelativeJavaPaths() {
		for (String path : new String[] { "/src/A.java", "src/../A.java", "src/./A.java", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"src//A.java", "src\\A.java", "src/A.java/", "C:/A.java", "plugin.xml" }) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			assertThrows(IllegalArgumentException.class, () -> scenario(Set.of(path), Set.of()), path);
		}
	}

	@Test
	void rejectsPathsInProjectName() {
		for (String project : new String[] { "", " ", ".", "..", "a/b", "a\\b" }) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
			assertThrows(IllegalArgumentException.class, () -> new CleanupScreenshotScenario(
					"example", PreviewContract.FILE_COMBINED_DIFF, project, Set.of("src/A.java"), Set.of())); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@Test
	void rejectsPendingUndoOutsideTheDeclaredSources() {
		assertThrows(IllegalArgumentException.class,
				() -> scenario(Set.of("src/A.java"), Set.of("src/B.java"))); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void completedAtomicScenarioNeedsNoAdditionalUndo() {
		var scenario= new CleanupScreenshotScenario("atomic", PreviewContract.ATOMIC_CANDIDATE, //$NON-NLS-1$
				"Project", Set.of("src/Owner.java", "src/Caller.java"), Set.of()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(PreviewContract.ATOMIC_CANDIDATE, scenario.previewContract());
		assertEquals(Set.of(), scenario.pendingUndoFiles());
	}

	private static CleanupScreenshotScenario scenario(Set<String> sources, Set<String> pendingUndo) {
		return new CleanupScreenshotScenario("example", PreviewContract.FILE_COMBINED_DIFF, //$NON-NLS-1$
				"Project", sources, pendingUndo); //$NON-NLS-1$
	}
}
