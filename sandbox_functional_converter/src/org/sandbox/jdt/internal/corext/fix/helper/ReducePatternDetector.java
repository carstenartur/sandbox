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

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Detects and handles REDUCE patterns in loop statements.
 * 
 * <p>This class is responsible for identifying various reduction patterns
 * that can be converted to stream reduce operations:</p>
 * 
 * <ul>
 * <li><b>INCREMENT:</b> {@code i++}, {@code ++i}</li>
 * <li><b>DECREMENT:</b> {@code i--}, {@code --i}, {@code i -= 1}</li>
 * <li><b>SUM:</b> {@code sum += value}</li>
 * <li><b>PRODUCT:</b> {@code product *= value}</li>
 * <li><b>STRING_CONCAT:</b> {@code str += substring}</li>
 * <li><b>MAX:</b> {@code max = Math.max(max, value)}</li>
 * <li><b>MIN:</b> {@code min = Math.min(min, value)}</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * ReducePatternDetector detector = new ReducePatternDetector(forLoop);
 * ProspectiveOperation reduceOp = detector.detectReduceOperation(stmt);
 * if (reduceOp != null) {
 *     String accumulatorVar = detector.getAccumulatorVariable();
 *     String accumulatorType = detector.getAccumulatorType();
 *     // ... use in stream pipeline
 * }
 * }</pre>
 * 
 * @see ProspectiveOperation
 * @see ProspectiveOperation.ReducerType
 * @see StreamPipelineBuilder
 */
public final class ReducePatternDetector {

	private static final String MATH_CLASS_NAME = StreamConstants.MATH_CLASS_NAME;
	private static final String MAX_METHOD_NAME = StreamConstants.MAX_METHOD_NAME;
	private static final String MIN_METHOD_NAME = StreamConstants.MIN_METHOD_NAME;
	private static final String JAVA_LANG_MATH = StreamConstants.JAVA_LANG_MATH;
	private static final String JAVA_LANG_STRING = StreamConstants.JAVA_LANG_STRING;
	private static final String UNUSED_ITEM_NAME = StreamConstants.UNUSED_PARAMETER_NAME;

	private final ASTNode contextNode;
	private String accumulatorVariable = null;
	private String accumulatorType = null;

	/**
	 * Creates a new ReducePatternDetector.
	 * 
	 * @param contextNode the context node (typically the for-loop) for type resolution
	 * @throws IllegalArgumentException if contextNode is null
	 */
	public ReducePatternDetector(ASTNode contextNode) {
		if (contextNode == null) {
			throw new IllegalArgumentException("contextNode cannot be null");
		}
		this.contextNode = contextNode;
	}

	/**
	 * Returns the accumulator variable name detected during the last
	 * {@link #detectReduceOperation(Statement)} call.
	 * 
	 * @return the accumulator variable name, or null if no reduce was detected
	 */
	public String getAccumulatorVariable() {
		return accumulatorVariable;
	}

	/**
	 * Returns the accumulator type detected during the last
	 * {@link #detectReduceOperation(Statement)} call.
	 * 
	 * @return the accumulator type name (e.g., "int", "double"), or null if not detected
	 */
	public String getAccumulatorType() {
		return accumulatorType;
	}

	/**
	 * Detects if a statement contains a REDUCE pattern.
	 * 
	 * <p><b>Supported Patterns:</b></p>
	 * <ul>
	 * <li>Postfix/Prefix increment: {@code i++}, {@code ++i}, {@code i--}, {@code --i}</li>
	 * <li>Compound assignments: {@code sum += x}, {@code product *= y}</li>
	 * <li>Math operations: {@code max = Math.max(max, x)}</li>
	 * </ul>
	 * 
	 * <p><b>Examples:</b></p>
	 * <pre>{@code
	 * // INCREMENT pattern
	 * count++;  // → .map(_item -> 1).reduce(count, Integer::sum)
	 * 
	 * // SUM pattern
	 * sum += value;  // → .map(value).reduce(sum, Integer::sum)
	 * 
	 * // MAX pattern
	 * max = Math.max(max, num);  // → .map(num).reduce(max, Math::max)
	 * }</pre>
	 * 
	 * @param stmt the statement to check
	 * @return a REDUCE operation if detected, null otherwise
	 */
	public ProspectiveOperation detectReduceOperation(Statement stmt) {
		if (!(stmt instanceof ExpressionStatement)) {
			return null;
		}

		ExpressionStatement exprStmt = (ExpressionStatement) stmt;
		Expression expr = exprStmt.getExpression();

		// Check for postfix increment/decrement: i++, i--
		if (expr instanceof PostfixExpression) {
			return detectPostfixReducePattern((PostfixExpression) expr, stmt);
		}

		// Check for prefix increment/decrement: ++i, --i
		if (expr instanceof PrefixExpression) {
			return detectPrefixReducePattern((PrefixExpression) expr, stmt);
		}

		// Check for compound assignments: +=, -=, *=, etc.
		if (expr instanceof Assignment) {
			return detectAssignmentReducePattern((Assignment) expr, stmt);
		}

		return null;
	}

	/**
	 * Detects postfix increment/decrement patterns: i++, i--
	 */
	private ProspectiveOperation detectPostfixReducePattern(PostfixExpression postfix, Statement stmt) {
		if (!(postfix.getOperand() instanceof SimpleName)) {
			return null;
		}

		String varName = ((SimpleName) postfix.getOperand()).getIdentifier();
		ProspectiveOperation.ReducerType reducerType;

		if (postfix.getOperator() == PostfixExpression.Operator.INCREMENT) {
			reducerType = ProspectiveOperation.ReducerType.INCREMENT;
		} else if (postfix.getOperator() == PostfixExpression.Operator.DECREMENT) {
			reducerType = ProspectiveOperation.ReducerType.DECREMENT;
		} else {
			return null;
		}

		accumulatorVariable = varName;
		accumulatorType = TypeResolver.getVariableType(contextNode, varName);
		ProspectiveOperation op = new ProspectiveOperation(stmt, varName, reducerType);
		op.setAccumulatorType(accumulatorType);
		return op;
	}

	/**
	 * Detects prefix increment/decrement patterns: ++i, --i
	 */
	private ProspectiveOperation detectPrefixReducePattern(PrefixExpression prefix, Statement stmt) {
		if (!(prefix.getOperand() instanceof SimpleName)) {
			return null;
		}

		String varName = ((SimpleName) prefix.getOperand()).getIdentifier();
		ProspectiveOperation.ReducerType reducerType;

		if (prefix.getOperator() == PrefixExpression.Operator.INCREMENT) {
			reducerType = ProspectiveOperation.ReducerType.INCREMENT;
		} else if (prefix.getOperator() == PrefixExpression.Operator.DECREMENT) {
			reducerType = ProspectiveOperation.ReducerType.DECREMENT;
		} else {
			return null;
		}

		accumulatorVariable = varName;
		accumulatorType = TypeResolver.getVariableType(contextNode, varName);
		ProspectiveOperation op = new ProspectiveOperation(stmt, varName, reducerType);
		op.setAccumulatorType(accumulatorType);
		return op;
	}

	/**
	 * Detects compound assignment patterns: +=, -=, *=, and Math.max/min patterns
	 */
	private ProspectiveOperation detectAssignmentReducePattern(Assignment assignment, Statement stmt) {
		if (!(assignment.getLeftHandSide() instanceof SimpleName)) {
			return null;
		}

		String varName = ((SimpleName) assignment.getLeftHandSide()).getIdentifier();

		// Check for simple assignment operators first
		if (assignment.getOperator() != Assignment.Operator.ASSIGN) {
			return detectCompoundAssignmentPattern(assignment, stmt, varName);
		}

		// Check for regular assignment with Math.max/Math.min pattern
		// Pattern: max = Math.max(max, x) or min = Math.min(min, x)
		Expression rhs = assignment.getRightHandSide();
		ProspectiveOperation.ReducerType reducerType = detectMathMaxMinPattern(varName, rhs);
		if (reducerType != null) {
			accumulatorVariable = varName;
			accumulatorType = TypeResolver.getVariableType(contextNode, varName);
			ProspectiveOperation op = new ProspectiveOperation(stmt, varName, reducerType);
			op.setAccumulatorType(accumulatorType);
			return op;
		}

		return null;
	}

	/**
	 * Detects compound assignment patterns: +=, -=, *=
	 */
	private ProspectiveOperation detectCompoundAssignmentPattern(Assignment assignment, Statement stmt, String varName) {
		ProspectiveOperation.ReducerType reducerType;

		if (assignment.getOperator() == Assignment.Operator.PLUS_ASSIGN) {
			// Check if this is string concatenation
			ITypeBinding varType = TypeResolver.getTypeBinding(contextNode, varName);
			if (varType != null && JAVA_LANG_STRING.equals(varType.getQualifiedName())) {
				reducerType = ProspectiveOperation.ReducerType.STRING_CONCAT;
			} else {
				reducerType = ProspectiveOperation.ReducerType.SUM;
			}
		} else if (assignment.getOperator() == Assignment.Operator.TIMES_ASSIGN) {
			reducerType = ProspectiveOperation.ReducerType.PRODUCT;
		} else if (assignment.getOperator() == Assignment.Operator.MINUS_ASSIGN) {
			reducerType = ProspectiveOperation.ReducerType.DECREMENT;
		} else {
			// Other assignment operators not yet supported
			return null;
		}

		accumulatorVariable = varName;
		accumulatorType = TypeResolver.getVariableType(contextNode, varName);

		ProspectiveOperation op = new ProspectiveOperation(stmt, varName, reducerType);
		op.setAccumulatorType(accumulatorType);

		// For STRING_CONCAT, check if the accumulator variable has @NotNull
		if (reducerType == ProspectiveOperation.ReducerType.STRING_CONCAT) {
			boolean isNullSafe = TypeResolver.hasNotNullAnnotation(contextNode, varName);
			op.setNullSafe(isNullSafe);
		}

		return op;
	}

	/**
	 * Detects Math.max/Math.min patterns in an expression.
	 * Patterns: max = Math.max(max, x) or min = Math.min(min, x)
	 * 
	 * @param varName the accumulator variable name
	 * @param expr    the right-hand side expression to check
	 * @return MAX or MIN if pattern detected, null otherwise
	 */
	ProspectiveOperation.ReducerType detectMathMaxMinPattern(String varName, Expression expr) {
		if (!(expr instanceof MethodInvocation)) {
			return null;
		}

		MethodInvocation methodInv = (MethodInvocation) expr;

		// Get method name first
		String methodName = methodInv.getName().getIdentifier();
		if (!MAX_METHOD_NAME.equals(methodName) && !MIN_METHOD_NAME.equals(methodName)) {
			return null;
		}

		// Check if it's a Math.max or Math.min call
		// Try binding resolution first (more robust)
		IMethodBinding binding = methodInv.resolveMethodBinding();
		if (binding != null) {
			ITypeBinding declaringClass = binding.getDeclaringClass();
			if (declaringClass != null && JAVA_LANG_MATH.equals(declaringClass.getQualifiedName())) {
				// Confirmed it's Math.max or Math.min via binding
				if (hasAccumulatorArgument(methodInv, varName)) {
					return MAX_METHOD_NAME.equals(methodName) ? ProspectiveOperation.ReducerType.MAX
							: ProspectiveOperation.ReducerType.MIN;
				}
			}
		}

		// Fallback: Check syntactically if binding resolution failed
		Expression receiverExpr = methodInv.getExpression();
		if (receiverExpr instanceof SimpleName) {
			SimpleName className = (SimpleName) receiverExpr;
			if (MATH_CLASS_NAME.equals(className.getIdentifier())) {
				if (hasAccumulatorArgument(methodInv, varName)) {
					return MAX_METHOD_NAME.equals(methodName) ? ProspectiveOperation.ReducerType.MAX
							: ProspectiveOperation.ReducerType.MIN;
				}
			}
		} else if (receiverExpr instanceof QualifiedName) {
			// Handle fully qualified: java.lang.Math.max()
			QualifiedName qualName = (QualifiedName) receiverExpr;
			if (MATH_CLASS_NAME.equals(qualName.getName().getIdentifier())) {
				if (hasAccumulatorArgument(methodInv, varName)) {
					return MAX_METHOD_NAME.equals(methodName) ? ProspectiveOperation.ReducerType.MAX
							: ProspectiveOperation.ReducerType.MIN;
				}
			}
		}

		return null;
	}

	/**
	 * Checks if the method invocation has the accumulator variable as one of its arguments.
	 */
	private boolean hasAccumulatorArgument(MethodInvocation methodInv, String varName) {
		List<?> args = methodInv.arguments();
		if (args.size() != 2) {
			return false;
		}

		for (Object argObj : args) {
			if (argObj instanceof SimpleName) {
				SimpleName argName = (SimpleName) argObj;
				if (varName.equals(argName.getIdentifier())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Extracts the expression from a REDUCE operation's right-hand side.
	 * For example, in "i += foo(l)", extracts "foo(l)".
	 * 
	 * @param stmt the statement containing the reduce operation
	 * @return the expression to be mapped, or null if none
	 */
	public Expression extractReduceExpression(Statement stmt) {
		if (!(stmt instanceof ExpressionStatement)) {
			return null;
		}

		ExpressionStatement exprStmt = (ExpressionStatement) stmt;
		Expression expr = exprStmt.getExpression();

		if (expr instanceof Assignment) {
			Assignment assignment = (Assignment) expr;
			// Return the right-hand side expression for compound assignments
			if (assignment.getOperator() != Assignment.Operator.ASSIGN) {
				return assignment.getRightHandSide();
			}
		}

		return null;
	}

	/**
	 * Extracts the non-accumulator argument from Math.max/min call.
	 * For example, in "max = Math.max(max, foo(l))", extracts "foo(l)".
	 * Returns null if the non-accumulator argument is just the loop variable (identity mapping).
	 * 
	 * @param stmt           the statement containing the Math.max/min operation
	 * @param accumulatorVar the accumulator variable name
	 * @param currentVarName the current variable name in the pipeline
	 * @return the expression to be mapped, or null if it's an identity mapping
	 */
	public Expression extractMathMaxMinArgument(Statement stmt, String accumulatorVar, String currentVarName) {
		if (!(stmt instanceof ExpressionStatement)) {
			return null;
		}

		ExpressionStatement exprStmt = (ExpressionStatement) stmt;
		Expression expr = exprStmt.getExpression();

		if (!(expr instanceof Assignment)) {
			return null;
		}

		Assignment assignment = (Assignment) expr;
		if (assignment.getOperator() != Assignment.Operator.ASSIGN) {
			return null;
		}

		Expression rhs = assignment.getRightHandSide();
		if (!(rhs instanceof MethodInvocation)) {
			return null;
		}

		MethodInvocation methodInv = (MethodInvocation) rhs;
		List<?> args = methodInv.arguments();
		if (args.size() != 2) {
			return null;
		}

		// Find the argument that is NOT the accumulator variable
		for (Object argObj : args) {
			if (argObj instanceof Expression) {
				Expression arg = (Expression) argObj;
				// Skip if this argument is just the accumulator variable
				if (arg instanceof SimpleName) {
					SimpleName name = (SimpleName) arg;
					if (accumulatorVar.equals(name.getIdentifier())) {
						continue; // This is the accumulator, skip it
					}
					// Check if this is just the current loop/pipeline variable (identity mapping)
					if (currentVarName.equals(name.getIdentifier())) {
						return null; // Skip identity mapping
					}
				}
				// Return the non-accumulator argument
				return arg;
			}
		}

		return null;
	}

	/**
	 * Adds a MAP operation before a REDUCE operation based on the reducer type.
	 * 
	 * <p>
	 * Mapping strategy by reducer type:
	 * <ul>
	 * <li><b>INCREMENT/DECREMENT:</b> Maps to 1 (or 1.0 for double types)</li>
	 * <li><b>SUM/PRODUCT/STRING_CONCAT:</b> Extracts and maps the RHS expression</li>
	 * <li><b>MAX/MIN:</b> Extracts and maps the non-accumulator argument from Math.max/min</li>
	 * </ul>
	 * 
	 * @param ops            the list to add the MAP operation to (must not be null)
	 * @param reduceOp       the REDUCE operation (must not be null and must be a REDUCE type)
	 * @param stmt           the statement containing the reduce operation (must not be null)
	 * @param currentVarName the current variable name in the pipeline (must not be null)
	 * @param ast            the AST for creating new nodes
	 * @throws IllegalArgumentException if any parameter is null or reduceOp is not a REDUCE operation
	 */
	public void addMapBeforeReduce(List<ProspectiveOperation> ops, ProspectiveOperation reduceOp, Statement stmt,
			String currentVarName, AST ast) {
		// Defensive null checks
		if (ops == null) {
			throw new IllegalArgumentException("ops list cannot be null");
		}
		if (reduceOp == null) {
			throw new IllegalArgumentException("reduceOp cannot be null");
		}
		if (stmt == null) {
			throw new IllegalArgumentException("stmt cannot be null");
		}
		if (currentVarName == null) {
			throw new IllegalArgumentException("currentVarName cannot be null");
		}
		if (ast == null) {
			throw new IllegalArgumentException("ast cannot be null");
		}
		if (reduceOp.getOperationType() != ProspectiveOperation.OperationType.REDUCE) {
			throw new IllegalArgumentException("reduceOp must be a REDUCE operation");
		}

		ProspectiveOperation.ReducerType reducerType = reduceOp.getReducerType();
		if (reducerType == null) {
			throw new IllegalArgumentException("reduceOp must have a non-null reducerType for REDUCE operations");
		}

		if (reducerType == ProspectiveOperation.ReducerType.INCREMENT
				|| reducerType == ProspectiveOperation.ReducerType.DECREMENT) {
			// Create a MAP operation that maps each item to 1 (type-aware)
			Expression mapExpr = createTypedLiteralOne(ast);
			ProspectiveOperation mapOp = new ProspectiveOperation(mapExpr, ProspectiveOperation.OperationType.MAP,
					UNUSED_ITEM_NAME);
			ops.add(mapOp);
		} else if (isArithmeticReducer(reducerType)) {
			// For SUM/PRODUCT/STRING_CONCAT: extract RHS expression
			Expression mapExpression = extractReduceExpression(stmt);
			if (mapExpression != null) {
				// Skip identity mapping: if the expression is just the current variable, don't add MAP
				if (!ExpressionUtils.isIdentityMapping(mapExpression, currentVarName)) {
					ProspectiveOperation mapOp = new ProspectiveOperation(mapExpression,
							ProspectiveOperation.OperationType.MAP, currentVarName);
					ops.add(mapOp);
				}
			}
		} else if (isMinMaxReducer(reducerType)) {
			// For MAX/MIN: extract non-accumulator argument
			// Skip creating map if it's just an identity mapping (e.g., num -> num)
			Expression mapExpression = extractMathMaxMinArgument(stmt, accumulatorVariable, currentVarName);
			if (mapExpression != null) {
				ProspectiveOperation mapOp = new ProspectiveOperation(mapExpression,
						ProspectiveOperation.OperationType.MAP, currentVarName);
				ops.add(mapOp);
			}
		}
	}

	/**
	 * Creates a typed literal "1" appropriate for the accumulator type.
	 * Handles int, long, float, double, byte, short, char types.
	 * 
	 * @param ast the AST to create nodes in
	 * @return an Expression representing the typed literal 1 (never null)
	 */
	Expression createTypedLiteralOne(AST ast) {
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
	private org.eclipse.jdt.core.dom.CastExpression createCastExpression(AST ast,
			org.eclipse.jdt.core.dom.PrimitiveType.Code typeCode, String literal) {
		org.eclipse.jdt.core.dom.CastExpression cast = ast.newCastExpression();
		cast.setType(ast.newPrimitiveType(typeCode));
		cast.setExpression(ast.newNumberLiteral(literal));
		return cast;
	}

	/**
	 * Checks if the reducer type is an arithmetic reducer.
	 * 
	 * @param type the reducer type to check
	 * @return true if it's SUM, PRODUCT, or STRING_CONCAT
	 */
	public static boolean isArithmeticReducer(ProspectiveOperation.ReducerType type) {
		return type == ProspectiveOperation.ReducerType.SUM || type == ProspectiveOperation.ReducerType.PRODUCT
				|| type == ProspectiveOperation.ReducerType.STRING_CONCAT;
	}

	/**
	 * Checks if the reducer type is a min/max reducer.
	 * 
	 * @param type the reducer type to check
	 * @return true if it's MAX or MIN
	 */
	public static boolean isMinMaxReducer(ProspectiveOperation.ReducerType type) {
		return type == ProspectiveOperation.ReducerType.MAX || type == ProspectiveOperation.ReducerType.MIN;
	}
}
