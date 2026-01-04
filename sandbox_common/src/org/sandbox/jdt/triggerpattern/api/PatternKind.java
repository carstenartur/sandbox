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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

/**
 * Defines the kinds of AST patterns that can be matched.
 * 
 * @since 1.2.2
 */
public enum PatternKind {
	/**
	 * Pattern represents a Java expression (e.g., {@code $x + 1}, {@code obj.method()})
	 */
	EXPRESSION,
	
	/**
	 * Pattern represents a Java statement (e.g., {@code if ($cond) $then;}, {@code return $x;})
	 */
	STATEMENT
}
