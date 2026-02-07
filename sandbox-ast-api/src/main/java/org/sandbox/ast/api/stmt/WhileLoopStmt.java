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

import org.sandbox.ast.api.expr.ASTExpr;

/**
 * Immutable record representing a while loop statement.
 * Provides fluent access to the condition expression and body statement.
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Old style:
 * if (node instanceof WhileStatement) {
 *     WhileStatement ws = (WhileStatement) node;
 *     Expression condition = ws.getExpression();
 *     // ...
 * }
 * 
 * // New style:
 * stmt.asWhileLoop()
 *     .flatMap(WhileLoopStmt::condition)
 *     .filter(cond -&gt; cond.hasType("boolean"))
 *     .ifPresent(cond -&gt; { });
 * </pre>
 */
public record WhileLoopStmt(
	Optional<ASTExpr> condition,
	Optional<ASTStmt> body
) implements ASTStmt {
	
	/**
	 * Creates a WhileLoopStmt record.
	 * 
	 * @param condition the loop condition
	 * @param body the loop body statement
	 */
	public WhileLoopStmt {
		condition = condition == null ? Optional.empty() : condition;
		body = body == null ? Optional.empty() : body;
	}
	
	/**
	 * Checks if this loop has a condition.
	 * 
	 * @return true if condition is present
	 */
	public boolean hasCondition() {
		return condition.isPresent();
	}
	
	/**
	 * Checks if this loop has a body statement.
	 * 
	 * @return true if body is present
	 */
	public boolean hasBody() {
		return body.isPresent();
	}
	
	/**
	 * Checks if the condition has a specific type.
	 * 
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if condition has this type
	 */
	public boolean conditionHasType(String qualifiedTypeName) {
		return condition.flatMap(ASTExpr::type)
		                .map(t -> t.is(qualifiedTypeName))
		                .orElse(false);
	}
	
	/**
	 * Checks if the condition expression has the primitive boolean type.
	 * 
	 * @return true if the condition is present and has type {@code boolean}
	 */
	public boolean hasBooleanTypedCondition() {
		return condition.map(c -> c.hasType("boolean")).orElse(false);
	}
	
	/**
	 * Checks if the condition expression has the primitive boolean type.
	 * <p>
	 * Note: despite its name, this method does <strong>not</strong> check whether
	 * the condition is a compile-time constant; it only checks that the
	 * condition is of type {@code boolean}.
	 * </p>
	 * 
	 * @return true if the condition is present and has type {@code boolean}
	 * @deprecated Use {@link #hasBooleanTypedCondition()} instead.
	 */
	@Deprecated
	public boolean hasConstantCondition() {
		return hasBooleanTypedCondition();
	}
	
	/**
	 * Builder for creating WhileLoopStmt instances.
	 */
	public static class Builder {
		private Optional<ASTExpr> condition = Optional.empty();
		private Optional<ASTStmt> body = Optional.empty();
		
		/**
		 * Sets the condition expression.
		 * 
		 * @param condition the loop condition
		 * @return this builder
		 */
		public Builder condition(ASTExpr condition) {
			this.condition = Optional.ofNullable(condition);
			return this;
		}
		
		/**
		 * Sets the body statement.
		 * 
		 * @param body the loop body
		 * @return this builder
		 */
		public Builder body(ASTStmt body) {
			this.body = Optional.ofNullable(body);
			return this;
		}
		
		/**
		 * Builds the WhileLoopStmt.
		 * 
		 * @return the while loop statement
		 */
		public WhileLoopStmt build() {
			return new WhileLoopStmt(condition, body);
		}
	}
	
	/**
	 * Creates a new builder.
	 * 
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}
}
