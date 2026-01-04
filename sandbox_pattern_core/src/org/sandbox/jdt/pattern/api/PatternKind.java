/*******************************************************************************
 * Copyright (c) 2026 Sandbox contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Sandbox contributors - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.pattern.api;

/**
 * Specifies the kind of pattern to match in Java source code.
 * <p>
 * Pattern kinds determine what type of AST node the pattern will match against.
 * </p>
 * 
 * @since 1.0
 */
public enum PatternKind {
	/**
	 * Pattern matches Java expressions.
	 * <p>
	 * Examples: {@code $x.toString()}, {@code $a + $b}, {@code new ArrayList<>()}
	 * </p>
	 */
	EXPRESSION,

	/**
	 * Pattern matches Java statements.
	 * <p>
	 * Examples: {@code if ($cond) $then;}, {@code for ($i : $list) $body}
	 * </p>
	 */
	STATEMENT
}
