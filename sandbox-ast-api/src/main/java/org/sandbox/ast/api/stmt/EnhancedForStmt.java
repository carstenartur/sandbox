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
import org.sandbox.ast.api.info.VariableInfo;

/**
 * Immutable record representing an enhanced for statement (for-each loop).
 * Provides fluent access to the iteration parameter, iterable expression, and body.
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Old style:
 * if (node instanceof EnhancedForStatement) {
 *     EnhancedForStatement efs = (EnhancedForStatement) node;
 *     Expression iterable = efs.getExpression();
 *     if (iterable instanceof SimpleName) {
 *         // ...
 *     }
 * }
 * 
 * // New style:
 * stmt.asEnhancedFor()
 *     .flatMap(EnhancedForStmt::iterable)
 *     .filter(expr -&gt; expr.hasType("java.util.List"))
 *     .ifPresent(list -&gt; { });
 * </pre>
 */
public record EnhancedForStmt(
	Optional<VariableInfo> parameter,
	Optional<ASTExpr> iterable,
	Optional<ASTStmt> body
) implements ASTStmt {
	
	/**
	 * Creates an EnhancedForStmt record.
	 * 
	 * @param parameter the loop variable
	 * @param iterable the expression to iterate over
	 * @param body the loop body statement
	 */
	public EnhancedForStmt {
		parameter = parameter == null ? Optional.empty() : parameter;
		iterable = iterable == null ? Optional.empty() : iterable;
		body = body == null ? Optional.empty() : body;
	}
	
	/**
	 * Checks if this loop has a parameter.
	 * 
	 * @return true if parameter is present
	 */
	public boolean hasParameter() {
		return parameter.isPresent();
	}
	
	/**
	 * Checks if this loop has an iterable expression.
	 * 
	 * @return true if iterable is present
	 */
	public boolean hasIterable() {
		return iterable.isPresent();
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
	 * Checks if the iterable has a specific type.
	 * 
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if iterable has this type
	 */
	public boolean iterableHasType(String qualifiedTypeName) {
		return iterable.flatMap(ASTExpr::type)
		               .map(t -> t.is(qualifiedTypeName))
		               .orElse(false);
	}
	
	/**
	 * Checks if the parameter has a specific type.
	 * 
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if parameter has this type
	 */
	public boolean parameterHasType(String qualifiedTypeName) {
		return parameter.map(p -> p.hasType(qualifiedTypeName))
		                .orElse(false);
	}
	
	/**
	 * Gets the parameter name if available.
	 * 
	 * @return the parameter name, or empty if not available
	 */
	public Optional<String> parameterName() {
		return parameter.map(VariableInfo::name);
	}
	
	/**
	 * Builder for creating EnhancedForStmt instances.
	 */
	public static class Builder {
		private Optional<VariableInfo> parameter = Optional.empty();
		private Optional<ASTExpr> iterable = Optional.empty();
		private Optional<ASTStmt> body = Optional.empty();
		
		/**
		 * Sets the parameter.
		 * 
		 * @param parameter the loop variable
		 * @return this builder
		 */
		public Builder parameter(VariableInfo parameter) {
			this.parameter = Optional.ofNullable(parameter);
			return this;
		}
		
		/**
		 * Sets the iterable expression.
		 * 
		 * @param iterable the expression to iterate over
		 * @return this builder
		 */
		public Builder iterable(ASTExpr iterable) {
			this.iterable = Optional.ofNullable(iterable);
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
		 * Builds the EnhancedForStmt.
		 * 
		 * @return the enhanced for statement
		 */
		public EnhancedForStmt build() {
			return new EnhancedForStmt(parameter, iterable, body);
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
