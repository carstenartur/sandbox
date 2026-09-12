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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CleanupScenarioRunnerTest {

	private static final String SOURCE= "src/A.java"; //$NON-NLS-1$
	private static final String OTHER= "src/Other.java"; //$NON-NLS-1$

	@Test
	void undoesTheDeclaredOutstandingChangeExactlyOnce() throws Exception {
		var workspace= new TestWorkspace();
		CleanupScenarioRunner.run(scenario(Set.of(SOURCE)), PreviewContract.FILE_COMBINED_DIFF, workspace, () -> {
			workspace.files.put(SOURCE, new byte[] { 9 });
			workspace.canUndo= true;
		});
		assertEquals(1, workspace.undos);
		assertEquals(2, workspace.historyClears);
		assertEquals(3, workspace.snapshots);
		assertFalse(workspace.canUndo);
	}

	@Test
	void doesNotUndoAgainWhenTheScenarioAlreadyRestoredItsSources() throws Exception {
		var workspace= new TestWorkspace();
		var atomic= new CleanupScreenshotScenario("atomic", PreviewContract.ATOMIC_CANDIDATE, //$NON-NLS-1$
				"Project", Set.of(SOURCE, OTHER), Set.of()); //$NON-NLS-1$
		CleanupScenarioRunner.run(atomic, PreviewContract.ATOMIC_CANDIDATE, workspace, () -> {
			workspace.files.put(SOURCE, new byte[] { 9 });
			workspace.files.put(SOURCE, new byte[] { 1 });
		});
		assertEquals(0, workspace.undos);
		assertEquals(2, workspace.historyClears);
		assertEquals(2, workspace.snapshots);
	}

	@Test
	void rejectsTheWrongPreviewContractBeforeTouchingTheWorkspace() {
		var workspace= new TestWorkspace();
		assertThrows(IllegalArgumentException.class, () -> CleanupScenarioRunner.run(
				scenario(Set.of()), PreviewContract.ATOMIC_CANDIDATE, workspace, () -> workspace.ran= true));
		assertFalse(workspace.ran);
		assertEquals(0, workspace.snapshots);
		assertEquals(0, workspace.historyClears);
	}

	@Test
	void rejectsAMissingDeclaredSourceBeforeInvokingTheScenario() {
		var workspace= new TestWorkspace();
		workspace.files.remove(SOURCE);
		assertThrows(AssertionError.class, () -> CleanupScenarioRunner.run(
				scenario(Set.of()), PreviewContract.FILE_COMBINED_DIFF, workspace, () -> workspace.ran= true));
		assertFalse(workspace.ran);
		assertEquals(0, workspace.historyClears);
	}

	@Test
	void rejectsUnexpectedChangesOutsideTheDeclaredFilesBeforeUndo() {
		var workspace= new TestWorkspace();
		AssertionError failure= assertThrows(AssertionError.class, () -> CleanupScenarioRunner.run(
				scenario(Set.of(SOURCE)), PreviewContract.FILE_COMBINED_DIFF, workspace, () -> {
				workspace.files.put(SOURCE, new byte[] { 9 });
				workspace.files.put(OTHER, new byte[] { 9 });
				workspace.canUndo= true;
			}));
		assertTrue(failure.getMessage().contains(OTHER));
		assertEquals(0, workspace.undos);
		assertEquals(2, workspace.historyClears);
	}

	@Test
	void rejectsAnUnexpectedNewJavaFile() {
		var workspace= new TestWorkspace();
		assertThrows(AssertionError.class, () -> CleanupScenarioRunner.run(
				scenario(Set.of()), PreviewContract.FILE_COMBINED_DIFF, workspace,
				() -> workspace.files.put("src/New.java", new byte[0]))); //$NON-NLS-1$
	}

	@Test
	void rejectsADeletedJavaFile() {
		var workspace= new TestWorkspace();
		assertThrows(AssertionError.class, () -> CleanupScenarioRunner.run(
				scenario(Set.of()), PreviewContract.FILE_COMBINED_DIFF, workspace, () -> workspace.files.remove(OTHER)));
	}

	@Test
	void rejectsDeletionOfAPendingUndoFileBeforeAttemptingRestoration() {
		var workspace= new TestWorkspace();
		assertThrows(AssertionError.class, () -> CleanupScenarioRunner.run(
				scenario(Set.of(SOURCE)), PreviewContract.FILE_COMBINED_DIFF, workspace, () -> {
				workspace.files.remove(SOURCE);
				workspace.canUndo= true;
			}));
		assertEquals(0, workspace.undos);
	}

	@Test
	void requiresAnUndoEntryForOutstandingChanges() {
		var workspace= new TestWorkspace();
		workspace.canUndo= true; // A foreign undo entry must be cleared before the scenario starts.
		assertThrows(AssertionError.class, () -> CleanupScenarioRunner.run(
				scenario(Set.of(SOURCE)), PreviewContract.FILE_COMBINED_DIFF, workspace,
				() -> workspace.files.put(SOURCE, new byte[] { 9 })));
		assertEquals(0, workspace.undos);
		assertEquals(2, workspace.historyClears);
	}

	@Test
	void rejectsIncompleteByteRestorationAfterUndo() {
		var workspace= new TestWorkspace();
		workspace.restoreOnUndo= false;
		assertThrows(AssertionError.class, () -> CleanupScenarioRunner.run(
				scenario(Set.of(SOURCE)), PreviewContract.FILE_COMBINED_DIFF, workspace, () -> {
				workspace.files.put(SOURCE, new byte[] { 9 });
				workspace.canUndo= true;
			}));
		assertEquals(1, workspace.undos);
		assertEquals(2, workspace.historyClears);
	}

	@Test
	void preservesTheOriginalScenarioFailureAndClearsUndoHistory() {
		var workspace= new TestWorkspace();
		var original= new IllegalStateException("semantic preview failure"); //$NON-NLS-1$
		Exception failure= assertThrows(IllegalStateException.class, () -> CleanupScenarioRunner.run(
				scenario(Set.of()), PreviewContract.FILE_COMBINED_DIFF, workspace, () -> { throw original; }));
		assertSame(original, failure);
		assertEquals(2, workspace.historyClears);
		assertEquals(1, workspace.snapshots);
	}

	private static CleanupScreenshotScenario scenario(Set<String> pendingUndo) {
		return new CleanupScreenshotScenario("example", PreviewContract.FILE_COMBINED_DIFF, //$NON-NLS-1$
				"Project", Set.of(SOURCE), pendingUndo); //$NON-NLS-1$
	}

	private static final class TestWorkspace implements CleanupScenarioRunner.Workspace {
		private final Map<String, byte[]> files= new HashMap<>(Map.of(SOURCE, new byte[] { 1 }, OTHER, new byte[] { 2 }));
		private int historyClears;
		private int snapshots;
		private int undos;
		private boolean canUndo;
		private boolean ran;
		private boolean restoreOnUndo= true;

		@Override
		public CleanupSourceSnapshot snapshot() {
			snapshots++;
			return new CleanupSourceSnapshot(files);
		}

		@Override
		public void clearUndoHistory() {
			historyClears++;
			canUndo= false;
		}

		@Override
		public boolean canUndo() {
			return canUndo;
		}

		@Override
		public void undo() {
			undos++;
			if (restoreOnUndo) {
				files.put(SOURCE, new byte[] { 1 });
			}
			canUndo= false;
		}
	}
}
