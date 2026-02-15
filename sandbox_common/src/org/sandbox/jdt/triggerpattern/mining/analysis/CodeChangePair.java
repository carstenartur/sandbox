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
package org.sandbox.jdt.triggerpattern.mining.analysis;

import org.eclipse.jdt.core.dom.ASTNode;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Represents a paired before/after code change at the statement or expression level.
 *
 * @param filePath      path of the source file
 * @param lineNumber    line number where the change occurs
 * @param beforeSnippet the original source text
 * @param afterSnippet  the modified source text
 * @param beforeNode    the AST node from the original source (may be {@code null})
 * @param afterNode     the AST node from the modified source (may be {@code null})
 * @param inferredKind  the inferred {@link PatternKind} for this change
 * @since 1.2.6
 */
public record CodeChangePair(
		String filePath,
		int lineNumber,
		String beforeSnippet,
		String afterSnippet,
		ASTNode beforeNode,
		ASTNode afterNode,
		PatternKind inferredKind) {
}
