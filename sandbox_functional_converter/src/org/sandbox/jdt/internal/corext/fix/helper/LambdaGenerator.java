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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Generates lambda expressions and method references for stream operations.
 * 
 * <p>This class is responsible for creating the lambda expressions and method
 * references used in stream pipeline operations. It handles:</p>
 * 
 * <ul>
 * <li>Accumulator lambdas: {@code (a, b) -> a + b}</li>
 * <li>Predicate lambda bodies for filter/match operations</li>
 * <li>Unique variable name generation to avoid scope conflicts</li>
 * </ul>
 * 
 * <p><b>Variable Name Generation:</b></p>
 * <p>The generator ensures lambda parameter names don't conflict with variables
 * already in scope by tracking used variable names and generating unique alternatives.</p>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * LambdaGenerator generator = new LambdaGenerator(ast);
 * generator.setUsedVariableNames(scopeVariables);
 * 
 * // Create an accumulator expression for REDUCE operations
 * Expression accExpr = generator.createAccumulatorExpression(ReducerType.SUM, "int", false);
 * 
 * // Create a predicate lambda body for FILTER operations
 * Expression predicateBody = generator.createPredicateLambdaBody(condition);
 * }</pre>
 * 
 * @see ProspectiveOperation
 * @see StreamPipelineBuilder
 * @see ReducerType
 */
public final class LambdaGenerator {

	private final AST ast;
	private Set<String> usedVariableNames = new HashSet<>();
	private Set<String> neededVariables = new HashSet<>();

	/**
	 * Creates a new LambdaGenerator for the given AST.
	 * 
	 * @param ast the AST to create nodes in (must not be null)
	 * @throws IllegalArgumentException if ast is null
	 */
	public LambdaGenerator(AST ast) {
		if (ast == null) {
			throw new IllegalArgumentException("ast cannot be null");
		}
		this.ast = ast;
	}

	/**
	 * Sets the collection of variable names already in use in the current scope.
	 * This is used to generate unique lambda parameter names.
	 * 
	 * @param usedNames the collection of variable names in use (may be null)
	 */
	public void setUsedVariableNames(Collection<String> usedNames) {
		if (usedNames != null) {
			this.usedVariableNames = new HashSet<>(usedNames);
		}
	}

	/**
	 * Sets the collection of variables needed/referenced by the operation.
	 * This is combined with usedVariableNames to ensure unique parameter names.
	 * 
	 * @param needed the set of needed variable names (may be null)
	 */
	public void setNeededVariables(Set<String> needed) {
		if (needed != null) {
			this.neededVariables = new HashSet<>(needed);
		}
	}

	/**
	 * Generates a unique variable name that doesn't collide with existing variables in scope.
	 * 
	 * @param baseName the base name to use (e.g., "a", "_item", "accumulator")
	 * @return a unique variable name that doesn't exist in the current scope
	 */
	public String generateUniqueVariableName(String baseName) {
		Set<String> allUsedNames = new HashSet<>(neededVariables);
		allUsedNames.addAll(usedVariableNames);

		if (!allUsedNames.contains(baseName)) {
			return baseName;
		}

		int counter = 2;
		String candidate = baseName + counter;
		while (allUsedNames.contains(candidate)) {
			counter++;
			candidate = baseName + counter;
		}
		return candidate;
	}

	/**
	 * Creates a general accumulator lambda like (a, b) -> a + b.
	 * Used as a fallback for custom aggregation patterns.
	 * 
	 * @return a LambdaExpression with generic parameters
	 */
	public LambdaExpression createAccumulatorLambda() {
		LambdaExpression lambda = ast.newLambdaExpression();

		String param1Name = generateUniqueVariableName("a");
		String param2Name = generateUniqueVariableName("b");

		VariableDeclarationFragment paramA = ast.newVariableDeclarationFragment();
		paramA.setName(ast.newSimpleName(param1Name));
		VariableDeclarationFragment paramB = ast.newVariableDeclarationFragment();
		paramB.setName(ast.newSimpleName(param2Name));
		lambda.parameters().add(paramA);
		lambda.parameters().add(paramB);

		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName(param1Name));
		operationExpr.setRightOperand(ast.newSimpleName(param2Name));
		operationExpr.setOperator(InfixExpression.Operator.PLUS);
		lambda.setBody(operationExpr);

		return lambda;
	}

	/**
	 * Creates a lambda body for predicate expressions (FILTER, ANYMATCH, etc.).
	 * 
	 * <p>Wraps InfixExpressions in parentheses for clarity. Does NOT wrap:
	 * PrefixExpression with NOT, MethodInvocation, SimpleName, BooleanLiteral.</p>
	 * 
	 * @param expression the predicate expression
	 * @return the lambda body expression, possibly wrapped in parentheses
	 */
	public Expression createPredicateLambdaBody(Expression expression) {
		// Unwrap parentheses to check the actual expression type
		Expression unwrapped = expression;
		while (unwrapped instanceof ParenthesizedExpression) {
			unwrapped = ((ParenthesizedExpression) unwrapped).getExpression();
		}

		// Don't wrap PrefixExpression with NOT - already has proper precedence
		if (unwrapped instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression) unwrapped;
			if (prefix.getOperator() == PrefixExpression.Operator.NOT) {
				return (Expression) ASTNode.copySubtree(ast, unwrapped);
			}
		}

		// Only wrap InfixExpressions for readability
		if (unwrapped instanceof InfixExpression) {
			ParenthesizedExpression parenExpr = ast.newParenthesizedExpression();
			parenExpr.setExpression((Expression) ASTNode.copySubtree(ast, unwrapped));
			return parenExpr;
		}

		// For all other expressions, no parentheses needed
		return (Expression) ASTNode.copySubtree(ast, unwrapped);
	}

	/**
	 * Creates the accumulator expression for a REDUCE operation.
	 * Delegates to the ReducerType enum which encapsulates the logic for each reducer type.
	 * 
	 * @param reducerType     the type of reducer
	 * @param accumulatorType the type of the accumulator variable
	 * @param isNullSafe      whether the operation is null-safe
	 * @return an Expression suitable for the second argument of reduce()
	 */
	public Expression createAccumulatorExpression(ReducerType reducerType,
			String accumulatorType, boolean isNullSafe) {
		if (reducerType == null) {
			return createAccumulatorLambda();
		}
		return reducerType.createAccumulatorExpression(ast, accumulatorType, isNullSafe);
	}
}
