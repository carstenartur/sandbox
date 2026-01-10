/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

/**
 * Utility class for common expression operations in the functional converter.
 * 
 * <p>
 * This class provides helper methods for manipulating and analyzing expressions
 * in the context of converting imperative loops to functional stream pipelines.
 * It centralizes expression-related operations to avoid code duplication across
 * the converter implementation.
 * </p>
 * 
 * <p><b>Key Functionality:</b></p>
 * <ul>
 * <li>Expression negation with proper parenthesization</li>
 * <li>Parentheses requirement detection</li>
 * <li>Identity mapping detection</li>
 * <li>Expression unwrapping using JDT utilities</li>
 * </ul>
 * 
 * @see StreamPipelineBuilder
 * @see ASTNodes#getUnparenthesedExpression(Expression)
 */
public final class ExpressionUtils {

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private ExpressionUtils() {
		// Utility class - no instances allowed
	}

	/**
	 * Creates a negated expression for filter operations with proper parenthesization.
	 * 
	 * <p>
	 * This method is used when converting "if (condition) continue;" patterns to
	 * ".filter(x -> !(condition))". It ensures correct operator precedence by
	 * wrapping binary and complex expressions in parentheses.
	 * </p>
	 * 
	 * <p><b>Examples:</b></p>
	 * <pre>{@code
	 * // Binary expression needs parentheses
	 * l == null  →  !(l == null)
	 * 
	 * // Simple name doesn't need parentheses
	 * flag  →  !flag
	 * 
	 * // Method call doesn't need parentheses
	 * isEmpty()  →  !isEmpty()
	 * }</pre>
	 * 
	 * @param ast       the AST to create nodes in (must not be null)
	 * @param condition the condition to negate (must not be null)
	 * @return a negated expression with proper parenthesization
	 * @throws IllegalArgumentException if ast or condition is null
	 * @see #needsParentheses(Expression)
	 */
	public static Expression createNegatedExpression(AST ast, Expression condition) {
		if (ast == null) {
			throw new IllegalArgumentException("ast cannot be null");
		}
		if (condition == null) {
			throw new IllegalArgumentException("condition cannot be null");
		}

		Expression operand = (Expression) ASTNode.copySubtree(ast, condition);

		// Wrap binary expressions and other complex expressions in parentheses
		// to ensure correct operator precedence
		if (needsParentheses(condition)) {
			ParenthesizedExpression parenthesized = ast.newParenthesizedExpression();
			parenthesized.setExpression(operand);
			operand = parenthesized;
		}

		PrefixExpression negation = ast.newPrefixExpression();
		negation.setOperator(PrefixExpression.Operator.NOT);
		negation.setOperand(operand);
		return negation;
	}

	/**
	 * Determines if an expression needs parentheses when negated.
	 * 
	 * <p>
	 * Returns {@code true} for expressions where the negation operator {@code !}
	 * would have incorrect precedence without parentheses. This includes binary
	 * expressions, conditional expressions, instanceof expressions, and assignments.
	 * </p>
	 * 
	 * <p><b>Examples requiring parentheses:</b></p>
	 * <ul>
	 * <li>Binary expressions: {@code x == y}, {@code a && b}, {@code m > n}</li>
	 * <li>Conditional expressions: {@code condition ? a : b}</li>
	 * <li>Instanceof expressions: {@code obj instanceof String}</li>
	 * <li>Assignment expressions: {@code x = y}</li>
	 * </ul>
	 * 
	 * <p><b>Examples NOT requiring parentheses:</b></p>
	 * <ul>
	 * <li>Simple names: {@code flag}, {@code isValid}</li>
	 * <li>Literals: {@code true}, {@code 42}</li>
	 * <li>Method calls: {@code isEmpty()}, {@code obj.check()}</li>
	 * <li>Field access: {@code obj.field}</li>
	 * </ul>
	 * 
	 * @param expr the expression to check (must not be null)
	 * @return true if parentheses are needed when negating this expression
	 * @throws IllegalArgumentException if expr is null
	 */
	public static boolean needsParentheses(Expression expr) {
		if (expr == null) {
			throw new IllegalArgumentException("expr cannot be null");
		}

		// Binary expressions (==, !=, <, >, <=, >=, &&, ||, etc.) need parentheses
		if (expr instanceof InfixExpression) {
			return true;
		}
		// Conditional expressions (ternary operator) need parentheses
		if (expr instanceof ConditionalExpression) {
			return true;
		}
		// instanceof expressions need parentheses
		if (expr instanceof InstanceofExpression) {
			return true;
		}
		// Assignment expressions need parentheses
		if (expr instanceof Assignment) {
			return true;
		}
		// Simple names, literals, method calls, field access, etc. don't need parentheses
		return false;
	}

	/**
	 * Checks if an expression represents an identity mapping.
	 * 
	 * <p>
	 * An identity mapping is a transformation where the input is returned unchanged,
	 * such as {@code num -> num}. This is used to detect when a MAP operation can
	 * be skipped because it doesn't transform the input.
	 * </p>
	 * 
	 * <p><b>Examples:</b></p>
	 * <pre>{@code
	 * // Identity mapping - returns true
	 * num -> num
	 * 
	 * // Non-identity mapping - returns false
	 * num -> num * 2
	 * num -> num.toString()
	 * }</pre>
	 * 
	 * @param expression the expression to check (must not be null)
	 * @param varName    the variable name to compare against (may be null)
	 * @return true if the expression is just a reference to varName (identity
	 *         mapping), false otherwise
	 * @throws IllegalArgumentException if expression is null
	 */
	public static boolean isIdentityMapping(Expression expression, String varName) {
		if (expression == null) {
			throw new IllegalArgumentException("expression cannot be null");
		}

		if (expression instanceof SimpleName && varName != null) {
			SimpleName simpleName = (SimpleName) expression;
			return simpleName.getIdentifier().equals(varName);
		}
		return false;
	}

	/**
	 * Strips the negation from a negated expression using JDT's unwrapping utility.
	 * 
	 * <p>
	 * This method handles {@link ParenthesizedExpression} wrapping using JDT's
	 * {@link ASTNodes#getUnparenthesedExpression(Expression)} to ensure proper
	 * unwrapping of complex nested expressions.
	 * </p>
	 * 
	 * <p><b>Examples:</b></p>
	 * <pre>{@code
	 * // Simple negation
	 * !condition  →  condition
	 * 
	 * // Parenthesized negation
	 * ((!condition))  →  condition
	 * 
	 * // Not a negation (returns original)
	 * condition  →  condition
	 * }</pre>
	 * 
	 * @param expr the expression to strip negation from (must not be null)
	 * @return the expression without the leading NOT operator, or the original
	 *         expression if not negated
	 * @throws IllegalArgumentException if expr is null
	 * @see ASTNodes#getUnparenthesedExpression(Expression)
	 */
	public static Expression stripNegation(Expression expr) {
		if (expr == null) {
			throw new IllegalArgumentException("expr cannot be null");
		}

		// Use JDT utility to unwrap parentheses
		Expression unwrapped = ASTNodes.getUnparenthesedExpression(expr);

		// Check if it's a negated expression
		if (unwrapped instanceof PrefixExpression) {
			PrefixExpression prefixExpr = (PrefixExpression) unwrapped;
			if (prefixExpr.getOperator() == PrefixExpression.Operator.NOT) {
				// Return the operand without the NOT
				return prefixExpr.getOperand();
			}
		}

		// Not a negated expression, return as-is
		return expr;
	}

	/**
	 * Checks if an expression is a negated expression (starts with !).
	 * 
	 * <p>
	 * This method handles {@link ParenthesizedExpression} wrapping using JDT's
	 * {@link ASTNodes#getUnparenthesedExpression(Expression)} to properly detect
	 * negation in complex nested expressions.
	 * </p>
	 * 
	 * <p><b>Examples returning true:</b></p>
	 * <pre>{@code
	 * // Simple negation
	 * !condition  →  true
	 * 
	 * // Parenthesized negation
	 * ((!condition))  →  true
	 * 
	 * // Negated comparison
	 * !(a == b)  →  true
	 * }</pre>
	 * 
	 * <p><b>Examples returning false:</b></p>
	 * <pre>{@code
	 * // Not negated
	 * condition  →  false
	 * 
	 * // Other prefix operator
	 * -value  →  false
	 * }</pre>
	 * 
	 * @param expr the expression to check (must not be null)
	 * @return true if the expression is a negation (has a leading NOT operator),
	 *         false otherwise
	 * @throws IllegalArgumentException if expr is null
	 * @see ASTNodes#getUnparenthesedExpression(Expression)
	 */
	public static boolean isNegatedExpression(Expression expr) {
		if (expr == null) {
			throw new IllegalArgumentException("expr cannot be null");
		}

		// Use JDT utility to unwrap parentheses
		Expression unwrapped = ASTNodes.getUnparenthesedExpression(expr);

		// Check if it's a negated expression
		if (unwrapped instanceof PrefixExpression) {
			PrefixExpression prefixExpr = (PrefixExpression) unwrapped;
			return prefixExpr.getOperator() == PrefixExpression.Operator.NOT;
		}

		return false;
	}

	/**
	 * Gets the unparenthesized form of an expression using JDT utilities.
	 * 
	 * <p>
	 * This is a convenience method that delegates to
	 * {@link ASTNodes#getUnparenthesedExpression(Expression)} for consistency
	 * across the codebase.
	 * </p>
	 * 
	 * <p><b>Examples:</b></p>
	 * <pre>{@code
	 * // Single level of parentheses
	 * (x)  →  x
	 * 
	 * // Multiple levels of parentheses
	 * (((x + y)))  →  x + y
	 * 
	 * // No parentheses (returns original)
	 * x  →  x
	 * }</pre>
	 * 
	 * @param expr the expression to unwrap (may be null)
	 * @return the unparenthesized expression, or null if expr is null
	 * @see ASTNodes#getUnparenthesedExpression(Expression)
	 */
	public static Expression getUnparenthesized(Expression expr) {
		if (expr == null) {
			return null;
		}
		return ASTNodes.getUnparenthesedExpression(expr);
	}
}
