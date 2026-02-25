/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining.core.astdiff;

/**
 * Represents a single change at the AST node level.
 *
 * @param changeType  the type of change (REPLACE, INSERT, DELETE)
 * @param nodeType    the AST node type (e.g. "MethodInvocation", "StringLiteral")
 * @param before      the code fragment before the change
 * @param after       the code fragment after the change
 */
public record AstNodeChange(
		ChangeType changeType,
		String nodeType,
		String before,
		String after) {

	/**
	 * Type of AST change.
	 */
	public enum ChangeType {
		/** A code fragment was replaced with another */
		REPLACE,
		/** A new code fragment was inserted */
		INSERT,
		/** A code fragment was deleted */
		DELETE
	}
}
