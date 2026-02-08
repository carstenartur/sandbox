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

import java.util.List;
import java.util.Optional;

import org.sandbox.ast.api.expr.ASTExpr;

/**
 * Immutable record representing a traditional for loop statement.
 * Provides fluent access to initializers, condition, updaters, and body.
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Old style:
 * if (node instanceof ForStatement) {
 *     ForStatement fs = (ForStatement) node;
 *     Expression condition = fs.getExpression();
 *     // ...
 * }
 * 
 * // New style:
 * stmt.asForLoop()
 *     .flatMap(ForLoopStmt::condition)
 *     .filter(cond -&gt; cond.hasType("boolean"))
 *     .ifPresent(cond -&gt; { });
 * </pre>
 */
public record ForLoopStmt(
	List<ASTExpr> initializers,
	Optional<ASTExpr> condition,
	List<ASTExpr> updaters,
	Optional<ASTStmt> body
) implements ASTStmt {
	
	/**
	 * Creates a ForLoopStmt record.
	 * 
	 * @param initializers the loop initialization expressions
	 * @param condition the loop condition
	 * @param updaters the loop update expressions
	 * @param body the loop body statement
	 */
	public ForLoopStmt {
		initializers = initializers == null ? List.of() : List.copyOf(initializers);
		condition = condition == null ? Optional.empty() : condition;
		updaters = updaters == null ? List.of() : List.copyOf(updaters);
		body = body == null ? Optional.empty() : body;
	}
	
	/**
	 * Checks if this loop has initializers.
	 * 
	 * @return true if initializers are present
	 */
	public boolean hasInitializers() {
		return !initializers.isEmpty();
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
	 * Checks if this loop has updaters.
	 * 
	 * @return true if updaters are present
	 */
	public boolean hasUpdaters() {
		return !updaters.isEmpty();
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
	 * Gets the number of initializers.
	 * 
	 * @return initializer count
	 */
	public int initializerCount() {
		return initializers.size();
	}
	
	/**
	 * Gets the number of updaters.
	 * 
	 * @return updater count
	 */
	public int updaterCount() {
		return updaters.size();
	}
	
	/**
	 * Gets an initializer by index.
	 * 
	 * @param index the initializer index
	 * @return the initializer, or empty if index out of bounds
	 */
	public Optional<ASTExpr> initializer(int index) {
		if (index < 0 || index >= initializers.size()) {
			return Optional.empty();
		}
		return Optional.of(initializers.get(index));
	}
	
	/**
	 * Gets an updater by index.
	 * 
	 * @param index the updater index
	 * @return the updater, or empty if index out of bounds
	 */
	public Optional<ASTExpr> updater(int index) {
		if (index < 0 || index >= updaters.size()) {
			return Optional.empty();
		}
		return Optional.of(updaters.get(index));
	}
	
	/**
	 * Checks if this is an infinite loop (no condition).
	 * 
	 * @return true if condition is absent
	 */
	public boolean isInfiniteLoop() {
		return condition.isEmpty();
	}
	
	/**
	 * Builder for creating ForLoopStmt instances.
	 */
	public static class Builder {
		private java.util.ArrayList<ASTExpr> initializers = new java.util.ArrayList<>();
		private Optional<ASTExpr> condition = Optional.empty();
		private java.util.ArrayList<ASTExpr> updaters = new java.util.ArrayList<>();
		private Optional<ASTStmt> body = Optional.empty();
		
		/**
		 * Sets the initializers.
		 * 
		 * @param initializers the initializers
		 * @return this builder
		 */
		public Builder initializers(List<ASTExpr> initializers) {
			this.initializers = new java.util.ArrayList<>(initializers == null ? List.of() : initializers);
			return this;
		}
		
		/**
		 * Adds a single initializer.
		 * 
		 * @param initializer the initializer
		 * @return this builder
		 */
		public Builder addInitializer(ASTExpr initializer) {
			this.initializers.add(initializer);
			return this;
		}
		
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
		 * Sets the updaters.
		 * 
		 * @param updaters the updaters
		 * @return this builder
		 */
		public Builder updaters(List<ASTExpr> updaters) {
			this.updaters = new java.util.ArrayList<>(updaters == null ? List.of() : updaters);
			return this;
		}
		
		/**
		 * Adds a single updater.
		 * 
		 * @param updater the updater
		 * @return this builder
		 */
		public Builder addUpdater(ASTExpr updater) {
			this.updaters.add(updater);
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
		 * Builds the ForLoopStmt.
		 * 
		 * @return the for loop statement
		 */
		public ForLoopStmt build() {
			return new ForLoopStmt(initializers, condition, updaters, body);
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
