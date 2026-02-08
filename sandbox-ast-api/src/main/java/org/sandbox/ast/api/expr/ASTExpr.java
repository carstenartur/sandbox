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

import org.sandbox.ast.api.core.ASTWrapper;
import org.sandbox.ast.api.info.TypeInfo;

/**
 * Base interface for expression wrappers.
 * Provides fluent query methods for working with AST expressions.
 * 
 * <p>This is a marker interface extended by specific expression types like
 * {@link MethodInvocationExpr}, {@link FieldAccessExpr}, etc.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * ASTExpr expr = ...;
 * expr.asMethodInvocation()
 *     .flatMap(MethodInvocationExpr::receiver)
 *     .filter(receiver -&gt; receiver.isSimpleName())
 *     .ifPresent(name -&gt; { });
 * </pre>
 */
public interface ASTExpr extends ASTWrapper {
	
	/**
	 * Gets the type of this expression, if available.
	 * 
	 * @return the type information, or empty if type cannot be resolved
	 */
	Optional<TypeInfo> type();
	
	/**
	 * Attempts to cast this expression to a {@link MethodInvocationExpr}.
	 * 
	 * @return the method invocation, or empty if this is not a method invocation
	 */
	default Optional<MethodInvocationExpr> asMethodInvocation() {
		return this instanceof MethodInvocationExpr mi ? Optional.of(mi) : Optional.empty();
	}
	
	/**
	 * Attempts to cast this expression to a {@link SimpleNameExpr}.
	 * 
	 * @return the simple name, or empty if this is not a simple name
	 */
	default Optional<SimpleNameExpr> asSimpleName() {
		return this instanceof SimpleNameExpr sn ? Optional.of(sn) : Optional.empty();
	}
	
	/**
	 * Attempts to cast this expression to a {@link FieldAccessExpr}.
	 * 
	 * @return the field access, or empty if this is not a field access
	 */
	default Optional<FieldAccessExpr> asFieldAccess() {
		return this instanceof FieldAccessExpr fa ? Optional.of(fa) : Optional.empty();
	}
	
	/**
	 * Attempts to cast this expression to a {@link CastExpr}.
	 * 
	 * @return the cast expression, or empty if this is not a cast
	 */
	default Optional<CastExpr> asCast() {
		return this instanceof CastExpr ce ? Optional.of(ce) : Optional.empty();
	}
	
	/**
	 * Attempts to cast this expression to an {@link InfixExpr}.
	 * 
	 * @return the infix expression, or empty if this is not an infix expression
	 */
	default Optional<InfixExpr> asInfix() {
		return this instanceof InfixExpr ie ? Optional.of(ie) : Optional.empty();
	}
	
	/**
	 * Checks if this expression is a simple name.
	 * 
	 * @return true if this is a simple name
	 */
	default boolean isSimpleName() {
		return this instanceof SimpleNameExpr;
	}
	
	/**
	 * Checks if this expression is a method invocation.
	 * 
	 * @return true if this is a method invocation
	 */
	default boolean isMethodInvocation() {
		return this instanceof MethodInvocationExpr;
	}
	
	/**
	 * Checks if this expression is a field access.
	 * 
	 * @return true if this is a field access
	 */
	default boolean isFieldAccess() {
		return this instanceof FieldAccessExpr;
	}
	
	/**
	 * Checks if this expression has a specific type.
	 * 
	 * @param qualifiedTypeName the fully qualified type name
	 * @return true if the expression has this type
	 */
	default boolean hasType(String qualifiedTypeName) {
		return type().map(t -> t.is(qualifiedTypeName)).orElse(false);
	}
	
	/**
	 * Checks if this expression has a specific type.
	 * 
	 * @param clazz the class to check
	 * @return true if the expression has this type
	 */
	default boolean hasType(Class<?> clazz) {
		return type().map(t -> t.is(clazz)).orElse(false);
	}
}
