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
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.Severity;

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

	/**
	 * Creates a {@code HintFinding} from a DSL {@link TransformationResult}.
	 *
	 * <p>Maps the DSL {@link Severity} to an {@link IMarker} severity constant
	 * and uses the result's description or source pattern as the message.</p>
	 *
	 * @param result the transformation result from the DSL engine
	 * @param cu     the compilation unit (used for line number calculation)
	 * @return a new {@code HintFinding}
	 */
	public static HintFinding fromTransformationResult(TransformationResult result, CompilationUnit cu) {
		ASTNode node = result.match().getMatchedNode();
		String msg = result.description() != null && !result.description().isEmpty()
				? result.description()
				: "Hint: " + result.rule().sourcePattern().getValue(); //$NON-NLS-1$
		int markerSeverity = mapSeverity(result.rule().getSeverity());
		return new HintFinding(
				msg,
				cu.getLineNumber(node.getStartPosition()),
				node.getStartPosition(),
				node.getStartPosition() + node.getLength(),
				markerSeverity);
	}

	/**
	 * Maps a DSL {@link Severity} to an {@link IMarker} severity constant.
	 *
	 * @param severity the DSL severity (may be {@code null})
	 * @return the corresponding {@link IMarker} severity
	 */
	private static int mapSeverity(Severity severity) {
		if (severity == null) {
			return IMarker.SEVERITY_INFO;
		}
		return switch (severity) {
		case ERROR -> IMarker.SEVERITY_ERROR;
		case WARNING -> IMarker.SEVERITY_WARNING;
		case INFO, HINT -> IMarker.SEVERITY_INFO;
		};
	}
}
