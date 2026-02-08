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
package org.sandbox.ast.api.stmt;

import java.util.Optional;

import org.sandbox.ast.api.core.ASTWrapper;

/**
 * Base interface for statement wrappers.
 * Provides fluent query methods for working with AST statements.
 * 
 * <p>This is a marker interface extended by specific statement types like
 * {@link EnhancedForStmt}, {@link WhileLoopStmt}, {@link ForLoopStmt}, and {@link IfStmt}.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * ASTStmt stmt = ...;
 * stmt.asEnhancedFor()
 *     .flatMap(EnhancedForStmt::iterable)
 *     .filter(expr -&gt; expr.hasType("java.util.List"))
 *     .ifPresent(list -&gt; { });
 * </pre>
 */
public interface ASTStmt extends ASTWrapper {
	
	/**
	 * Attempts to cast this statement to an {@link EnhancedForStmt}.
	 * 
	 * @return the enhanced for statement, or empty if this is not an enhanced for statement
	 */
	default Optional<EnhancedForStmt> asEnhancedFor() {
		return this instanceof EnhancedForStmt ef ? Optional.of(ef) : Optional.empty();
	}
	
	/**
	 * Attempts to cast this statement to a {@link WhileLoopStmt}.
	 * 
	 * @return the while loop statement, or empty if this is not a while loop
	 */
	default Optional<WhileLoopStmt> asWhileLoop() {
		return this instanceof WhileLoopStmt wl ? Optional.of(wl) : Optional.empty();
	}
	
	/**
	 * Attempts to cast this statement to a {@link ForLoopStmt}.
	 * 
	 * @return the for loop statement, or empty if this is not a for loop
	 */
	default Optional<ForLoopStmt> asForLoop() {
		return this instanceof ForLoopStmt fl ? Optional.of(fl) : Optional.empty();
	}
	
	/**
	 * Attempts to cast this statement to an {@link IfStmt}.
	 * 
	 * @return the if statement, or empty if this is not an if statement
	 */
	default Optional<IfStmt> asIfStatement() {
		return this instanceof IfStmt is ? Optional.of(is) : Optional.empty();
	}
	
	/**
	 * Checks if this statement is an enhanced for statement.
	 * 
	 * @return true if this is an enhanced for statement
	 */
	default boolean isEnhancedFor() {
		return this instanceof EnhancedForStmt;
	}
	
	/**
	 * Checks if this statement is a while loop.
	 * 
	 * @return true if this is a while loop
	 */
	default boolean isWhileLoop() {
		return this instanceof WhileLoopStmt;
	}
	
	/**
	 * Checks if this statement is a for loop.
	 * 
	 * @return true if this is a for loop
	 */
	default boolean isForLoop() {
		return this instanceof ForLoopStmt;
	}
	
	/**
	 * Checks if this statement is an if statement.
	 * 
	 * @return true if this is an if statement
	 */
	default boolean isIfStatement() {
		return this instanceof IfStmt;
	}
}
