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
package org.sandbox.jdt.cleanup.multifile.api;

import org.eclipse.jdt.core.IJavaProject;

/**
 * Optional capability for cleanups that expose the structured diagnostics of
 * their most recent project-wide planning attempt.
 *
 * <p>The value is intended for explicit preview, CI and documentation evidence.
 * Implementations must not expose workspace paths or Java-model handles; the
 * shared diagnostics model already replaces those values with opaque stable
 * identifiers.</p>
 */
public interface IMultiFileCleanUpDiagnosticsProvider {

	/**
	 * Returns deterministic JSON for the most recent planning attempt in the
	 * supplied project.
	 *
	 * @param project Java project whose cleanup preconditions were checked
	 * @return diagnostics JSON, or an empty string when no planning attempt exists
	 */
	String getLastPlanningDiagnosticsJson(IJavaProject project);
}
