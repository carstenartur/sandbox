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
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Generates lambda expressions and method references for stream operations.
 * 
 * <p>This class is responsible for creating the lambda expressions and method
 * references used in stream pipeline operations. It handles:</p>
 * 
 * <ul>
 * <li>Binary operator lambdas: {@code (a, b) -> a + b}</li>
 * <li>Counting lambdas: {@code (accumulator, _item) -> accumulator + 1}</li>
 * <li>Method references: {@code Integer::sum}, {@code String::concat}</li>
 * <li>Predicate lambda bodies for filter/match operations</li>
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
 * // Create a sum lambda
 * LambdaExpression sumLambda = generator.createBinaryOperatorLambda(InfixExpression.Operator.PLUS);
 * 
 * // Create a method reference
 * TypeMethodReference sumRef = generator.createMethodReference("Integer", "sum");
 * }</pre>
 * 
 * @see ProspectiveOperation
 * @see StreamPipelineBuilder
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
	 * Creates a method reference like Integer::sum or String::concat.
	 * 
	 * @param typeName   the type name (e.g., "Integer", "String")
	 * @param methodName the method name (e.g., "sum", "concat")
	 * @return a TypeMethodReference node
	 */
	public TypeMethodReference createMethodReference(String typeName, String methodName) {
		TypeMethodReference methodRef = ast.newTypeMethodReference();
		methodRef.setType(ast.newSimpleType(ast.newSimpleName(typeName)));
		methodRef.setName(ast.newSimpleName(methodName));
		return methodRef;
	}

	/**
	 * Creates a method reference for max/min operations based on the accumulator type.
	 * Uses wrapper class method references to avoid overload ambiguity.
	 * 
	 * @param accumulatorType the type of the accumulator variable (e.g., "int", "double")
	 * @param methodName      the method name ("max" or "min")
	 * @return a TypeMethodReference for the appropriate wrapper type
	 */
	public TypeMethodReference createMaxMinMethodReference(String accumulatorType, String methodName) {
		String typeName = mapToWrapperType(accumulatorType);
		return createMethodReference(typeName, methodName);
	}

	/**
	 * Maps a primitive type or wrapper class name to the wrapper class simple name.
	 * 
	 * @param type the type to map (e.g., "int", "java.lang.Integer", "Integer")
	 * @return the wrapper class simple name (e.g., "Integer")
	 */
	private String mapToWrapperType(String type) {
		if (type == null) {
			return "Integer"; // Default fallback
		}

		switch (type) {
		case "int":
		case "Integer":
			return "Integer";
		case "long":
		case "Long":
			return "Long";
		case "double":
		case "Double":
			return "Double";
		case "float":
		case "Float":
			return "Float";
		case "short":
		case "Short":
			return "Short";
		case "byte":
		case "Byte":
			return "Byte";
		default:
			if (type.startsWith("java.lang.")) {
				return type.substring("java.lang.".length());
			}
			return "Integer"; // Default fallback
		}
	}

	/**
	 * Creates a simple binary operator lambda like (a, b) -> a + b.
	 * Used for simple operations like string concatenation.
	 * 
	 * @param operator the infix operator to use
	 * @return a LambdaExpression with simple parameters
	 */
	public LambdaExpression createSimpleBinaryLambda(InfixExpression.Operator operator) {
		LambdaExpression lambda = ast.newLambdaExpression();

		String param1Name = generateUniqueVariableName("a");
		String param2Name = generateUniqueVariableName("b");

		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName(param1Name));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName(param2Name));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName(param1Name));
		operationExpr.setRightOperand(ast.newSimpleName(param2Name));
		operationExpr.setOperator(operator);
		lambda.setBody(operationExpr);

		return lambda;
	}

	/**
	 * Creates a binary operator lambda like (accumulator, _item) -> accumulator + _item.
	 * 
	 * @param operator the infix operator to use
	 * @return a LambdaExpression with accumulator-style parameters
	 */
	public LambdaExpression createBinaryOperatorLambda(InfixExpression.Operator operator) {
		LambdaExpression lambda = ast.newLambdaExpression();

		String param1Name = generateUniqueVariableName("accumulator");
		String param2Name = generateUniqueVariableName("_item");

		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName(param1Name));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName(param2Name));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName(param1Name));
		operationExpr.setRightOperand(ast.newSimpleName(param2Name));
		operationExpr.setOperator(operator);
		lambda.setBody(operationExpr);

		return lambda;
	}

	/**
	 * Creates a counting lambda like (accumulator, _item) -> accumulator + 1.
	 * Used for INCREMENT/DECREMENT operations.
	 * 
	 * @param operator the infix operator (PLUS for increment, MINUS for decrement)
	 * @return a LambdaExpression that adds/subtracts 1 from the accumulator
	 */
	public LambdaExpression createCountingLambda(InfixExpression.Operator operator) {
		LambdaExpression lambda = ast.newLambdaExpression();

		String param1Name = generateUniqueVariableName("accumulator");
		String param2Name = generateUniqueVariableName("_item");

		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName(param1Name));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName(param2Name));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName(param1Name));
		operationExpr.setRightOperand(ast.newNumberLiteral("1"));
		operationExpr.setOperator(operator);
		lambda.setBody(operationExpr);

		return lambda;
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
