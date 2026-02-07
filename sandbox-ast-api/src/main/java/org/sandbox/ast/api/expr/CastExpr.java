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
package org.sandbox.ast.api.expr;

import java.util.Optional;

import org.sandbox.ast.api.info.TypeInfo;

/**
 * Immutable record representing a cast expression.
 * Provides fluent access to the cast type and expression being cast.
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Old style:
 * if (node instanceof CastExpression) {
 *     CastExpression cast = (CastExpression) node;
 *     Type castType = cast.getType();
 *     Expression expr = cast.getExpression();
 *     // ...
 * }
 * 
 * // New style:
 * expr.asCast()
 *     .filter(cast -&gt; cast.castType().is("java.lang.String"))
 *     .map(CastExpr::expression)
 *     .ifPresent(inner -&gt; { });
 * </pre>
 */
public record CastExpr(
	TypeInfo castType,
	ASTExpr expression,
	Optional<TypeInfo> type
) implements ASTExpr {
	
	/**
	 * Creates a CastExpr record.
	 * 
	 * @param castType the type to cast to
	 * @param expression the expression being cast
	 * @param type the result type (same as castType)
	 */
	public CastExpr {
		if (castType == null) {
			throw new IllegalArgumentException("Cast type cannot be null");
		}
		if (expression == null) {
			throw new IllegalArgumentException("Expression cannot be null");
		}
		type = type == null ? Optional.of(castType) : type;
	}
	
	/**
	 * Checks if this casts to a specific type.
	 * 
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if casting to this type
	 */
	public boolean castsTo(String qualifiedTypeName) {
		return castType.is(qualifiedTypeName);
	}
	
	/**
	 * Checks if this casts to a specific type.
	 * 
	 * @param clazz the class to check
	 * @return true if casting to this type
	 */
	public boolean castsTo(Class<?> clazz) {
		return castType.is(clazz);
	}
	
	/**
	 * Checks if the expression being cast has a specific type.
	 * 
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if expression has this type
	 */
	public boolean expressionHasType(String qualifiedTypeName) {
		return expression.type()
		                 .map(t -> t.is(qualifiedTypeName))
		                 .orElse(false);
	}
	
	/**
	 * Checks if this is a downcast (casting to a more specific type).
	 * Note: This requires type hierarchy information which may not be available.
	 * 
	 * @return true if likely a downcast based on available information
	 */
	public boolean isDowncast() {
		// Simple heuristic: if expression type is Object or interface, likely downcast
		return expression.type()
		                 .map(t -> t.is("java.lang.Object") || 
		                          t.qualifiedName().startsWith("java.") && 
		                          !t.qualifiedName().equals(castType.qualifiedName()))
		                 .orElse(false);
	}
	
	/**
	 * Builder for creating CastExpr instances.
	 */
	public static class Builder {
		private TypeInfo castType;
		private ASTExpr expression;
		private Optional<TypeInfo> type = Optional.empty();
		
		/**
		 * Sets the cast type.
		 * 
		 * @param castType the cast type
		 * @return this builder
		 */
		public Builder castType(TypeInfo castType) {
			this.castType = castType;
			return this;
		}
		
		/**
		 * Sets the expression being cast.
		 * 
		 * @param expression the expression
		 * @return this builder
		 */
		public Builder expression(ASTExpr expression) {
			this.expression = expression;
			return this;
		}
		
		/**
		 * Sets the type information.
		 * 
		 * @param type the type
		 * @return this builder
		 */
		public Builder type(TypeInfo type) {
			this.type = Optional.ofNullable(type);
			return this;
		}
		
		/**
		 * Builds the CastExpr.
		 * 
		 * @return the cast expression
		 */
		public CastExpr build() {
			return new CastExpr(castType, expression, type);
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
