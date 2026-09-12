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

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Shared restoration lifecycle. Concrete scenarios remain the authority for
 * preview selection, semantic diffs, capture readiness, Apply and intermediate Undo.
 */
final class CleanupScenarioRunner {

	interface Workspace {
		CleanupSourceSnapshot snapshot() throws Exception;
		void clearUndoHistory();
		boolean canUndo();
		void undo() throws Exception;
	}

	@FunctionalInterface
	interface ScenarioAction {
		void run() throws Exception;
	}

	private CleanupScenarioRunner() {
	}

	static void run(CleanupScreenshotScenario scenario, PreviewContract expectedContract,
			Workspace workspace, ScenarioAction action) throws Exception {
		Objects.requireNonNull(scenario);
		Objects.requireNonNull(expectedContract);
		Objects.requireNonNull(workspace);
		Objects.requireNonNull(action);
		if (scenario.previewContract() != expectedContract) {
			throw new IllegalArgumentException(scenario.id() + ": expected " + expectedContract //$NON-NLS-1$
					+ " but the scenario declares " + scenario.previewContract()); //$NON-NLS-1$
		}
		CleanupSourceSnapshot before= workspace.snapshot();
		Set<String> missing= new TreeSet<>(scenario.requiredSourceFiles());
		missing.removeAll(before.paths());
		if (!missing.isEmpty()) {
			throw new AssertionError(scenario.id() + ": missing declared Java sources: " + missing); //$NON-NLS-1$
		}

		workspace.clearUndoHistory();
		try {
			action.run();
			assertChangedPaths(scenario, before, workspace.snapshot(), scenario.pendingUndoFiles(),
					"after the scenario"); //$NON-NLS-1$
			if (!scenario.pendingUndoFiles().isEmpty()) {
				if (!workspace.canUndo()) {
					throw new AssertionError(scenario.id() + ": no aggregate refactoring remains available for Undo"); //$NON-NLS-1$
				}
				workspace.undo();
				assertChangedPaths(scenario, before, workspace.snapshot(), Set.of(), "after aggregate Undo"); //$NON-NLS-1$
			}
			if (workspace.canUndo()) {
				throw new AssertionError(scenario.id() + ": unexpected Undo history after source restoration"); //$NON-NLS-1$
			}
		} finally {
			workspace.clearUndoHistory();
		}
	}

	private static void assertChangedPaths(CleanupScreenshotScenario scenario, CleanupSourceSnapshot before,
			CleanupSourceSnapshot after, Set<String> expected, String phase) {
		Set<String> added= new TreeSet<>(after.paths());
		added.removeAll(before.paths());
		Set<String> deleted= new TreeSet<>(before.paths());
		deleted.removeAll(after.paths());
		if (!added.isEmpty() || !deleted.isEmpty()) {
			throw new AssertionError(scenario.id() + " " + phase + ": Java source inventory changed; added " //$NON-NLS-1$ //$NON-NLS-2$
					+ added + ", deleted " + deleted); //$NON-NLS-1$
		}
		Set<String> actual= before.changedPaths(after);
		if (!actual.equals(expected)) {
			throw new AssertionError(scenario.id() + " " + phase + ": expected changed Java files " //$NON-NLS-1$ //$NON-NLS-2$
					+ new TreeSet<>(expected) + " but found " + actual); //$NON-NLS-1$
		}
	}
}
