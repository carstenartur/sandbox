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

/**
 * Immutable input and restoration contract for the existing Java Help scenarios.
 * This descriptor does not establish upstream repository or Oomph provenance.
 *
 * @param pendingUndoFiles exact files still changed when the scenario callback
 *        returns; an empty set means the callback already completed its undo,
 *        otherwise the runner must undo exactly one aggregate refactoring
 */
record CleanupScreenshotScenario(String id, PreviewContract previewContract, String projectName,
		Set<String> requiredSourceFiles, Set<String> pendingUndoFiles) {

	CleanupScreenshotScenario {
		Objects.requireNonNull(id, "id"); //$NON-NLS-1$
		if (!id.matches("[a-z][a-z0-9-]*")) { //$NON-NLS-1$
			throw new IllegalArgumentException("Invalid screenshot scenario id: " + id); //$NON-NLS-1$
		}
		Objects.requireNonNull(previewContract, "previewContract"); //$NON-NLS-1$
		requireSegment(projectName);
		requiredSourceFiles= copySourcePaths(requiredSourceFiles);
		pendingUndoFiles= copySourcePaths(pendingUndoFiles);
		if (requiredSourceFiles.isEmpty()) {
			throw new IllegalArgumentException("A scenario must identify its source files"); //$NON-NLS-1$
		}
		if (!requiredSourceFiles.containsAll(pendingUndoFiles)) {
			throw new IllegalArgumentException("Pending undo files must belong to the declared source selection"); //$NON-NLS-1$
		}
	}

	private static Set<String> copySourcePaths(Set<String> paths) {
		Set<String> copy= Set.copyOf(paths);
		for (String path : copy) {
			if (!path.endsWith(".java")) { //$NON-NLS-1$
				throw new IllegalArgumentException("Expected a project-relative Java source path: " + path); //$NON-NLS-1$
			}
			for (String segment : path.split("/", -1)) { //$NON-NLS-1$
				requireSegment(segment);
			}
		}
		return copy;
	}

	private static void requireSegment(String segment) {
		Objects.requireNonNull(segment, "path segment"); //$NON-NLS-1$
		if (segment.isBlank() || segment.equals(".") || segment.equals("..") //$NON-NLS-1$ //$NON-NLS-2$
				|| segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0 || segment.indexOf(':') >= 0
				|| segment.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("Invalid workspace path segment: " + segment); //$NON-NLS-1$
		}
	}
}
