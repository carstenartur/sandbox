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
		
		@Override
		public Expression createMapExpression(MapExpressionContext context) {
			return createTypedLiteralOne(context.ast(), context.accumulatorType());
		}
		
		@Override
		public String getMapVariableName() {
			return UNUSED_PARAMETER_NAME;
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
		
		@Override
		public Expression createMapExpression(MapExpressionContext context) {
			return createTypedLiteralOne(context.ast(), context.accumulatorType());
		}
		
		@Override
		public String getMapVariableName() {
			return UNUSED_PARAMETER_NAME;
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
		
		@Override
		public Expression createMapExpression(MapExpressionContext context) {
			return context.rhsExpression();
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
		
		@Override
		public Expression createMapExpression(MapExpressionContext context) {
			return context.rhsExpression();
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
		
		@Override
		public Expression createMapExpression(MapExpressionContext context) {
			return context.rhsExpression();
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
		
		@Override
		public Expression createMapExpression(MapExpressionContext context) {
			return context.mathArgExpression();
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
		
		@Override
		public Expression createMapExpression(MapExpressionContext context) {
			return context.mathArgExpression();
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
		
		@Override
		public Expression createMapExpression(MapExpressionContext context) {
			return null; // No MAP needed for custom aggregate
		}
	};
	
	private static final String UNUSED_PARAMETER_NAME = StreamConstants.UNUSED_PARAMETER_NAME;
	
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
	
	/**
	 * Creates the MAP expression to use before the REDUCE operation.
	 * 
	 * <p>Each reducer type knows what expression to map to:</p>
	 * <ul>
	 * <li><b>Counting reducers</b>: Map each element to 1 (typed appropriately)</li>
	 * <li><b>Arithmetic reducers</b>: Use the RHS expression from the assignment</li>
	 * <li><b>Min/max reducers</b>: Use the non-accumulator argument from Math.max/min</li>
	 * </ul>
	 * 
	 * @param context the context containing all necessary data for expression creation
	 * @return the expression to use in the MAP operation, or null if no MAP is needed
	 */
	public abstract Expression createMapExpression(MapExpressionContext context);
	
	/**
	 * Returns the variable name to use for the MAP operation parameter.
	 * 
	 * <p>For counting reducers (INCREMENT, DECREMENT), this returns an unused parameter name
	 * like "_item" since the element is not used. For other reducers, returns null to use
	 * the current variable name.</p>
	 * 
	 * @return the parameter name for the MAP lambda, or null to use the default
	 */
	public String getMapVariableName() {
		return null; // Default: use current variable name
	}
	
	/**
	 * Context record containing all data needed to create a MAP expression for a REDUCE operation.
	 * 
	 * @param ast              the AST to create nodes in
	 * @param accumulatorType  the type of the accumulator variable
	 * @param currentVarName   the current variable name in the pipeline
	 * @param rhsExpression    the right-hand side expression (for arithmetic reducers)
	 * @param mathArgExpression the non-accumulator argument (for min/max reducers)
	 */
	public record MapExpressionContext(
			AST ast,
			String accumulatorType,
			String currentVarName,
			Expression rhsExpression,
			Expression mathArgExpression
	) {}
	
	/**
	 * Returns whether this reducer type is an arithmetic reducer (SUM, PRODUCT, STRING_CONCAT).
	 * 
	 * <p>Arithmetic reducers accumulate values using binary operations on the stream elements.</p>
	 * 
	 * @return true if this is SUM, PRODUCT, or STRING_CONCAT
	 */
	public boolean isArithmetic() {
		return this == SUM || this == PRODUCT || this == STRING_CONCAT;
	}
	
	/**
	 * Returns whether this reducer type is a min/max reducer (MAX, MIN).
	 * 
	 * <p>Min/max reducers find the extreme value using Math.max or Math.min.</p>
	 * 
	 * @return true if this is MAX or MIN
	 */
	public boolean isMinMax() {
		return this == MAX || this == MIN;
	}
	
	/**
	 * Returns whether this reducer type is a counting reducer (INCREMENT, DECREMENT).
	 * 
	 * <p>Counting reducers count elements by incrementing or decrementing an accumulator.</p>
	 * 
	 * @return true if this is INCREMENT or DECREMENT
	 */
	public boolean isCounting() {
		return this == INCREMENT || this == DECREMENT;
	}
	
	/**
	 * Returns whether this reducer type requires a MAP operation before the REDUCE.
	 * 
	 * <p>Some reducers need a preceding MAP to transform elements before reduction:</p>
	 * <ul>
	 * <li><b>Counting reducers</b>: Map each element to 1 before summing</li>
	 * <li><b>Arithmetic reducers</b>: May need to extract the RHS expression</li>
	 * <li><b>Min/max reducers</b>: May need to extract the non-accumulator argument</li>
	 * </ul>
	 * 
	 * @return true if this reducer type may need a MAP operation before REDUCE
	 */
	public boolean mayNeedMapBeforeReduce() {
		return this != CUSTOM_AGGREGATE;
	}
	
	// ==================== Helper Methods ====================
	
	/**
	 * Creates a typed literal "1" appropriate for the accumulator type.
	 * Handles int, long, float, double, byte, short, char types.
	 * 
	 * @param ast             the AST to create nodes in
	 * @param accumulatorType the accumulator type name
	 * @return an Expression representing the typed literal 1 (never null)
	 */
	private static Expression createTypedLiteralOne(AST ast, String accumulatorType) {
		if (accumulatorType == null) {
			return ast.newNumberLiteral("1");
		}

		switch (accumulatorType) {
		case "double":
			return ast.newNumberLiteral("1.0");
		case "float":
			return ast.newNumberLiteral("1.0f");
		case "long":
			return ast.newNumberLiteral("1L");
		case "byte":
			return createCastExpression(ast, org.eclipse.jdt.core.dom.PrimitiveType.BYTE, "1");
		case "short":
			return createCastExpression(ast, org.eclipse.jdt.core.dom.PrimitiveType.SHORT, "1");
		case "char":
			return createCastExpression(ast, org.eclipse.jdt.core.dom.PrimitiveType.CHAR, "1");
		default:
			return ast.newNumberLiteral("1");
		}
	}
	
	/**
	 * Creates a cast expression for the given primitive type.
	 * Example: (byte) 1, (short) 1
	 */
	private static org.eclipse.jdt.core.dom.CastExpression createCastExpression(AST ast,
			org.eclipse.jdt.core.dom.PrimitiveType.Code typeCode, String literal) {
		org.eclipse.jdt.core.dom.CastExpression cast = ast.newCastExpression();
		cast.setType(ast.newPrimitiveType(typeCode));
		cast.setExpression(ast.newNumberLiteral(literal));
		return cast;
	}
	
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
	 * Always uses Math::max and Math::min since these patterns are detected from Math.max/min calls.
	 */
	private static TypeMethodReference createMaxMinMethodReference(AST ast, String accumulatorType, String methodName) {
		// Always use Math for max/min operations since they're detected from Math.max/min patterns
		return createMethodReference(ast, "Math", methodName);
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
}
