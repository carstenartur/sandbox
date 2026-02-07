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
 * Immutable record representing an if statement.
 * Provides fluent access to the condition, then-branch, and optional else-branch.
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Old style:
 * if (node instanceof IfStatement) {
 *     IfStatement is = (IfStatement) node;
 *     Expression condition = is.getExpression();
 *     Statement elseStmt = is.getElseStatement();
 *     // ...
 * }
 * 
 * // New style:
 * stmt.asIfStatement()
 *     .flatMap(IfStatementStmt::condition)
 *     .filter(cond -&gt; cond.hasType("boolean"))
 *     .ifPresent(cond -&gt; { });
 * </pre>
 */
public record IfStatementStmt(
	Optional<ASTExpr> condition,
	Optional<ASTStmt> thenStatement,
	Optional<ASTStmt> elseStatement
) implements ASTStmt {
	
	/**
	 * Creates an IfStatementStmt record.
	 * 
	 * @param condition the if condition
	 * @param thenStatement the then-branch statement
	 * @param elseStatement the else-branch statement (may be absent)
	 */
	public IfStatementStmt {
		condition = condition == null ? Optional.empty() : condition;
		thenStatement = thenStatement == null ? Optional.empty() : thenStatement;
		elseStatement = elseStatement == null ? Optional.empty() : elseStatement;
	}
	
	/**
	 * Checks if this statement has a condition.
	 * 
	 * @return true if condition is present
	 */
	public boolean hasCondition() {
		return condition.isPresent();
	}
	
	/**
	 * Checks if this statement has a then-branch.
	 * 
	 * @return true if then-branch is present
	 */
	public boolean hasThenStatement() {
		return thenStatement.isPresent();
	}
	
	/**
	 * Checks if this statement has an else-branch.
	 * 
	 * @return true if else-branch is present
	 */
	public boolean hasElseStatement() {
		return elseStatement.isPresent();
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
	 * Checks if this is an else-if chain (else statement is another if).
	 * 
	 * @return true if else statement is an if statement
	 */
	public boolean hasElseIf() {
		return elseStatement.map(ASTStmt::isIfStatement).orElse(false);
	}
	
	/**
	 * Gets the else-if statement if this is an else-if chain.
	 * 
	 * @return the else-if statement, or empty if not an else-if
	 */
	public Optional<IfStatementStmt> elseIf() {
		return elseStatement.flatMap(ASTStmt::asIfStatement);
	}
	
	/**
	 * Builder for creating IfStatementStmt instances.
	 */
	public static class Builder {
		private Optional<ASTExpr> condition = Optional.empty();
		private Optional<ASTStmt> thenStatement = Optional.empty();
		private Optional<ASTStmt> elseStatement = Optional.empty();
		
		/**
		 * Sets the condition expression.
		 * 
		 * @param condition the if condition
		 * @return this builder
		 */
		public Builder condition(ASTExpr condition) {
			this.condition = Optional.ofNullable(condition);
			return this;
		}
		
		/**
		 * Sets the then-branch statement.
		 * 
		 * @param thenStatement the then-branch
		 * @return this builder
		 */
		public Builder thenStatement(ASTStmt thenStatement) {
			this.thenStatement = Optional.ofNullable(thenStatement);
			return this;
		}
		
		/**
		 * Sets the else-branch statement.
		 * 
		 * @param elseStatement the else-branch
		 * @return this builder
		 */
		public Builder elseStatement(ASTStmt elseStatement) {
			this.elseStatement = Optional.ofNullable(elseStatement);
			return this;
		}
		
		/**
		 * Builds the IfStatementStmt.
		 * 
		 * @return the if statement
		 */
		public IfStatementStmt build() {
			return new IfStatementStmt(condition, thenStatement, elseStatement);
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
