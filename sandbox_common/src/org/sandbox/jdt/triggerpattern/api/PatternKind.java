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
	STATEMENT,
	
	/**
	 * Pattern represents a Java annotation (e.g., {@code @Before}, {@code @Test(expected=$ex)})
	 * @since 1.2.3
	 */
	ANNOTATION,
	
	/**
	 * Pattern represents a Java method invocation (e.g., {@code Assert.assertEquals($a, $b)})
	 * @since 1.2.3
	 */
	METHOD_CALL,
	
	/**
	 * Pattern represents an import declaration (e.g., {@code import org.junit.Assert})
	 * @since 1.2.3
	 */
	IMPORT,
	
	/**
	 * Pattern represents a field declaration (e.g., {@code @Rule public TemporaryFolder $name})
	 * @since 1.2.3
	 */
	FIELD,
	
	/**
	 * Pattern represents a constructor invocation (e.g., {@code new String($bytes, $enc)})
	 * @since 1.2.5
	 */
	CONSTRUCTOR,
	
	/**
	 * Pattern represents a method declaration (e.g., {@code void dispose()}, {@code void $name($params$)})
	 * @since 1.2.6
	 */
	METHOD_DECLARATION,
	
	/**
	 * Pattern represents a block of statements (e.g., {@code { $before$; return $x; }}).
	 * Used for matching statement sequences with variadic placeholders.
	 * @since 1.3.2
	 */
	BLOCK,
	
	/**
	 * Pattern represents a sequence of consecutive statements to match within a block
	 * using a sliding-window approach. Unlike {@link #BLOCK}, which matches the entire
	 * block, this matches N consecutive statements anywhere within a block.
	 * 
	 * <p>Example: A two-statement pattern {@code "$T[] $copy = new $T[$len]; System.arraycopy($src, 0, $copy, 0, $len);"}
	 * would match those two consecutive statements inside any method body.</p>
	 * 
	 * @since 1.3.2
	 */
	STATEMENT_SEQUENCE
}
