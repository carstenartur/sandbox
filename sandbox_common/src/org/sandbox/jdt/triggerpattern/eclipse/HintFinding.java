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
package org.sandbox.jdt.triggerpattern.eclipse;

import org.eclipse.core.resources.IMarker;

/**
 * Represents a hint-only finding that should be reported as a problem marker
 * rather than as a code rewrite operation.
 *
 * <p>Hint-only findings detect patterns that should be flagged to the user
 * (e.g., {@code ex.printStackTrace()}, {@code System.out.println()}) but
 * cannot or should not be automatically fixed because the correct replacement
 * depends on project-specific context (e.g., which logger to use).</p>
 *
 * @param message    the marker message shown in the Problems view
 * @param lineNumber the 1-based line number of the finding
 * @param charStart  the 0-based character offset of the start of the finding
 * @param charEnd    the 0-based character offset of the end of the finding
 * @param severity   the marker severity ({@link IMarker#SEVERITY_WARNING},
 *                   {@link IMarker#SEVERITY_INFO}, or {@link IMarker#SEVERITY_ERROR})
 */
public record HintFinding(
		String message,
		int lineNumber,
		int charStart,
		int charEnd,
		int severity) {
}
