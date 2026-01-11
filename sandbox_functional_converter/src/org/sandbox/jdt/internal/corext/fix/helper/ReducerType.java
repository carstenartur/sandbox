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
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Types of reduction operations supported for REDUCE operations.
 * 
 * <p>Each reducer type represents a specific accumulation pattern and knows how to
 * create its own accumulator expression for the stream pipeline.</p>
 * 
 * <ul>
 * <li><b>INCREMENT</b>: Counts elements by incrementing an accumulator. 
 *     Pattern: {@code i++}, {@code ++i}. 
 *     Maps to: {@code .map(_item -> 1).reduce(i, Integer::sum)}</li>
 * <li><b>DECREMENT</b>: Decrements an accumulator for each element. 
 *     Pattern: {@code i--}, {@code --i}, {@code i -= 1}. 
 *     Maps to: {@code .map(_item -> -1).reduce(i, Integer::sum)}</li>
 * <li><b>SUM</b>: Sums values from the stream. 
 *     Pattern: {@code sum += value}. 
 *     Maps to: {@code .reduce(sum, Integer::sum)} or {@code .map(x -> value).reduce(sum, Integer::sum)}</li>
 * <li><b>PRODUCT</b>: Multiplies values from the stream. 
 *     Pattern: {@code product *= value}. 
 *     Maps to: {@code .reduce(product, (acc, x) -> acc * x)}</li>
 * <li><b>STRING_CONCAT</b>: Concatenates strings. 
 *     Pattern: {@code str += substring}. 
 *     Maps to: {@code .reduce(str, String::concat)} (when null-safe)</li>
 * <li><b>MAX</b>: Finds the maximum value. 
 *     Pattern: {@code max = Math.max(max, value)}. 
 *     Maps to: {@code .reduce(max, Math::max)} or {@code .reduce(max, Integer::max)}</li>
 * <li><b>MIN</b>: Finds the minimum value. 
 *     Pattern: {@code min = Math.min(min, value)}. 
 *     Maps to: {@code .reduce(min, Math::min)} or {@code .reduce(min, Integer::min)}</li>
 * <li><b>CUSTOM_AGGREGATE</b>: User-defined aggregation patterns not covered by standard types.</li>
 * </ul>
 * 
 * @see ProspectiveOperation
 */
public enum ReducerType {
	
	/**
	 * Counts elements by incrementing an accumulator.
	 * Pattern: {@code i++}, {@code ++i}
	 */
	INCREMENT {
		@Override
		public Expression createAccumulatorExpression(AST ast, String accumulatorType, boolean isNullSafe) {
			return createSumExpression(ast, accumulatorType, true);
		}
	},
	
	/**
	 * Decrements an accumulator for each element.
	 * Pattern: {@code i--}, {@code --i}, {@code i -= 1}
	 */
	DECREMENT {
		@Override
		public Expression createAccumulatorExpression(AST ast, String accumulatorType, boolean isNullSafe) {
			return createCountingLambda(ast, InfixExpression.Operator.MINUS);
		}
	},
	
	/**
	 * Sums values from the stream.
	 * Pattern: {@code sum += value}
	 */
	SUM {
		@Override
		public Expression createAccumulatorExpression(AST ast, String accumulatorType, boolean isNullSafe) {
			return createSumExpression(ast, accumulatorType, false);
		}
	},
	
	/**
	 * Multiplies values from the stream.
	 * Pattern: {@code product *= value}
	 */
	PRODUCT {
		@Override
		public Expression createAccumulatorExpression(AST ast, String accumulatorType, boolean isNullSafe) {
			return createBinaryOperatorLambda(ast, InfixExpression.Operator.TIMES);
		}
	},
	
	/**
	 * Concatenates strings.
	 * Pattern: {@code str += substring}
	 */
	STRING_CONCAT {
		@Override
		public Expression createAccumulatorExpression(AST ast, String accumulatorType, boolean isNullSafe) {
			if (isNullSafe) {
				return createMethodReference(ast, "String", "concat");
			} else {
				return createBinaryOperatorLambda(ast, InfixExpression.Operator.PLUS);
			}
		}
	},
	
	/**
	 * Finds the maximum value.
	 * Pattern: {@code max = Math.max(max, value)}
	 */
	MAX {
		@Override
		public Expression createAccumulatorExpression(AST ast, String accumulatorType, boolean isNullSafe) {
			return createMaxMinMethodReference(ast, accumulatorType, "max");
		}
	},
	
	/**
	 * Finds the minimum value.
	 * Pattern: {@code min = Math.min(min, value)}
	 */
	MIN {
		@Override
		public Expression createAccumulatorExpression(AST ast, String accumulatorType, boolean isNullSafe) {
			return createMaxMinMethodReference(ast, accumulatorType, "min");
		}
	},
	
	/**
	 * User-defined aggregation patterns not covered by standard types.
	 */
	CUSTOM_AGGREGATE {
		@Override
		public Expression createAccumulatorExpression(AST ast, String accumulatorType, boolean isNullSafe) {
			return createAccumulatorLambda(ast);
		}
	};
	
	/**
	 * Creates the accumulator expression for the reduce() operation.
	 * Returns method references when possible, or lambdas otherwise.
	 * 
	 * @param ast             the AST to create nodes in
	 * @param accumulatorType the type of the accumulator variable (e.g., "int", "double")
	 * @param isNullSafe      whether the operation is null-safe
	 * @return an Expression suitable for the second argument of reduce()
	 */
	public abstract Expression createAccumulatorExpression(AST ast, 
			String accumulatorType, boolean isNullSafe);
	
	// ==================== Helper Methods ====================
	
	/**
	 * Creates the appropriate sum expression based on the accumulator type.
	 * Uses method references for Integer, Long, Double; lambdas for others.
	 */
	private static Expression createSumExpression(AST ast, String accumulatorType, boolean isIncrement) {
		if (accumulatorType == null) {
			return createMethodReference(ast, "Integer", "sum");
		}

		switch (accumulatorType) {
		case "double":
		case "java.lang.Double":
			if (isIncrement) {
				return createCountingLambda(ast, InfixExpression.Operator.PLUS);
			}
			return createMethodReference(ast, "Double", "sum");
		case "float":
		case "java.lang.Float":
			if (isIncrement) {
				return createCountingLambda(ast, InfixExpression.Operator.PLUS);
			}
			return createBinaryOperatorLambda(ast, InfixExpression.Operator.PLUS);
		case "long":
		case "java.lang.Long":
			return createMethodReference(ast, "Long", "sum");
		case "short":
		case "java.lang.Short":
		case "byte":
		case "java.lang.Byte":
			if (isIncrement) {
				return createCountingLambda(ast, InfixExpression.Operator.PLUS);
			}
			return createBinaryOperatorLambda(ast, InfixExpression.Operator.PLUS);
		default:
			return createMethodReference(ast, "Integer", "sum");
		}
	}
	
	/**
	 * Creates a method reference like Integer::sum or String::concat.
	 */
	private static TypeMethodReference createMethodReference(AST ast, String typeName, String methodName) {
		TypeMethodReference methodRef = ast.newTypeMethodReference();
		methodRef.setType(ast.newSimpleType(ast.newSimpleName(typeName)));
		methodRef.setName(ast.newSimpleName(methodName));
		return methodRef;
	}
	
	/**
	 * Creates a method reference for max/min operations based on the accumulator type.
	 */
	private static TypeMethodReference createMaxMinMethodReference(AST ast, String accumulatorType, String methodName) {
		String typeName = mapToWrapperType(accumulatorType);
		return createMethodReference(ast, typeName, methodName);
	}
	
	/**
	 * Creates a binary operator lambda like (a, b) -> a * b.
	 */
	private static LambdaExpression createBinaryOperatorLambda(AST ast, InfixExpression.Operator operator) {
		LambdaExpression lambda = ast.newLambdaExpression();

		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName("a"));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName("b"));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName("a"));
		operationExpr.setRightOperand(ast.newSimpleName("b"));
		operationExpr.setOperator(operator);
		lambda.setBody(operationExpr);

		return lambda;
	}
	
	/**
	 * Creates a counting lambda like (a, _item) -> a + 1.
	 * Used for INCREMENT/DECREMENT operations.
	 */
	private static LambdaExpression createCountingLambda(AST ast, InfixExpression.Operator operator) {
		LambdaExpression lambda = ast.newLambdaExpression();

		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName("a"));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName("_item"));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName("a"));
		operationExpr.setRightOperand(ast.newNumberLiteral("1"));
		operationExpr.setOperator(operator);
		lambda.setBody(operationExpr);

		return lambda;
	}
	
	/**
	 * Creates a general accumulator lambda like (a, b) -> a + b.
	 * Used as a fallback for custom aggregation patterns.
	 */
	private static LambdaExpression createAccumulatorLambda(AST ast) {
		return createBinaryOperatorLambda(ast, InfixExpression.Operator.PLUS);
	}
	
	/**
	 * Maps a primitive type or wrapper class name to the wrapper class simple name.
	 */
	private static String mapToWrapperType(String type) {
		if (type == null) {
			return "Integer";
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
			return "Integer";
		}
	}
}
