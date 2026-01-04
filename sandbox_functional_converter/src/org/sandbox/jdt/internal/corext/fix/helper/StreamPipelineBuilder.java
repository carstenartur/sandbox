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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Builder class for constructing stream pipelines from enhanced for-loops.
 * 
 * <p>
 * This class analyzes the body of an enhanced for-loop and determines if it can
 * be converted into a stream pipeline. It handles various patterns including:
 * <ul>
 * <li>Simple forEach operations</li>
 * <li>MAP operations (variable declarations with initializers)</li>
 * <li>FILTER operations (IF statements)</li>
 * <li>REDUCE operations (accumulator patterns including SUM, PRODUCT,
 * INCREMENT, MAX, MIN)</li>
 * <li>ANYMATCH/NONEMATCH operations (early returns)</li>
 * </ul>
 * 
 * <p>
 * <b>Supported Reduction Patterns:</b>
 * <ul>
 * <li>INCREMENT: {@code i++}, {@code ++i}</li>
 * <li>DECREMENT: {@code i--}, {@code --i}, {@code i -= 1}</li>
 * <li>SUM: {@code sum += value}</li>
 * <li>PRODUCT: {@code product *= value}</li>
 * <li>STRING_CONCAT: {@code str += substring}</li>
 * <li>MAX: {@code max = Math.max(max, value)}</li>
 * <li>MIN: {@code min = Math.min(min, value)}</li>
 * <li>CUSTOM_AGGREGATE: Custom aggregation patterns</li>
 * </ul>
 * 
 * <p>
 * Based on the NetBeans mapreduce hints implementation:
 * https://github.com/apache/netbeans/tree/master/java/java.hints/src/org/netbeans/modules/java/hints/jdk/mapreduce
 * 
 * @see ProspectiveOperation
 * @see PreconditionsChecker
 */
public class StreamPipelineBuilder {
	private final EnhancedForStatement forLoop;
	private final PreconditionsChecker preconditions;
	private final AST ast;

	private List<ProspectiveOperation> operations;
	private String loopVariableName;
	private boolean analyzed = false;
	private boolean convertible = false;
	private String accumulatorVariable = null;
	private String accumulatorType = null;
	private boolean isAnyMatchPattern = false;
	private boolean isNoneMatchPattern = false;
	private boolean isAllMatchPattern = false;

	/**
	 * Creates a new StreamPipelineBuilder for the given for-loop.
	 * 
	 * @param forLoop       the enhanced for-loop to analyze
	 * @param preconditions the preconditions checker for the loop
	 * @throws IllegalArgumentException if forLoop or preconditions is null
	 */
	public StreamPipelineBuilder(EnhancedForStatement forLoop, PreconditionsChecker preconditions) {
		if (forLoop == null) {
			throw new IllegalArgumentException("forLoop cannot be null");
		}
		if (preconditions == null) {
			throw new IllegalArgumentException("preconditions cannot be null");
		}

		this.forLoop = forLoop;
		this.preconditions = preconditions;
		this.ast = forLoop.getAST();

		// Internal invariant: EnhancedForStatement must have a parameter with a name
		assert forLoop.getParameter() != null && forLoop.getParameter().getName() != null
				: "forLoop must have a valid parameter with a name";

		this.loopVariableName = forLoop.getParameter().getName().getIdentifier();
		this.operations = new ArrayList<>();
		this.isAnyMatchPattern = preconditions.isAnyMatchPattern();
		this.isNoneMatchPattern = preconditions.isNoneMatchPattern();
		this.isAllMatchPattern = preconditions.isAllMatchPattern();
	}

	/**
	 * Analyzes the loop body to determine if it can be converted to a stream
	 * pipeline.
	 * 
	 * <p>
	 * This method should be called before attempting to build the pipeline. It
	 * inspects the loop body and extracts a sequence of
	 * {@link ProspectiveOperation}s that represent the transformation.
	 * 
	 * @return true if the loop can be converted to a stream pipeline, false
	 *         otherwise
	 */
	public boolean analyze() {
		if (analyzed) {
			return convertible;
		}

		analyzed = true;

		// Check basic preconditions
		if (!preconditions.isSafeToRefactor() 
//				|| !preconditions.iteratesOverIterable()
				) {
			convertible = false;
			return false;
		}

		// Parse the loop body into operations
		Statement loopBody = forLoop.getBody();
		operations = parseLoopBody(loopBody, loopVariableName);

		// Check if we have any operations
		if (operations.isEmpty()) {
			convertible = false;
			return false;
		}

		// Validate variable scoping
		if (!validateVariableScope(operations, loopVariableName)) {
			convertible = false;
			return false;
		}

		convertible = true;
		return true;
	}

	/**
	 * Builds the stream pipeline from the analyzed operations.
	 * 
	 * <p>
	 * This method should be called after {@link #analyze()} returns true. It
	 * constructs a {@link MethodInvocation} representing the complete stream
	 * pipeline.
	 * 
	 * @return a MethodInvocation representing the stream pipeline, or null if the
	 *         loop cannot be converted
	 */
	public MethodInvocation buildPipeline() {
		if (!analyzed || !convertible) {
			return null;
		}

		// Check if we need .stream() or can use direct .forEach()
		boolean needsStream = requiresStreamPrefix();

		MethodInvocation pipeline;
		if (needsStream) {
			// Start with .stream()
			pipeline = ast.newMethodInvocation();
			pipeline.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
			pipeline.setName(ast.newSimpleName("stream"));

			// Chain each operation
			for (int i = 0; i < operations.size(); i++) {
				ProspectiveOperation op = operations.get(i);
				MethodInvocation next = ast.newMethodInvocation();
				next.setExpression(pipeline);
				next.setName(ast.newSimpleName(op.getSuitableMethod()));

				// Get the current parameter name for this operation
				String paramName = getVariableNameFromPreviousOp(operations, i, loopVariableName);

				// Use the current paramName for this operation
				List<Expression> args = op.getArguments(ast, paramName);
				for (Expression arg : args) {
					next.arguments().add(arg);
				}
				pipeline = next;
			}
		} else {
			// Simple forEach without stream()
			ProspectiveOperation op = operations.get(0);
			pipeline = ast.newMethodInvocation();
			pipeline.setExpression((Expression) ASTNode.copySubtree(ast, forLoop.getExpression()));
			pipeline.setName(ast.newSimpleName("forEach"));
			List<Expression> args = op.getArguments(ast, loopVariableName);
			for (Expression arg : args) {
				pipeline.arguments().add(arg);
			}
		}

		return pipeline;
	}

	/**
	 * Wraps the pipeline in an appropriate statement.
	 * 
	 * <p>
	 * Wraps the method invocation in an ExpressionStatement. For REDUCE operations
	 * with a tracked accumulator variable, wraps the pipeline in an assignment
	 * statement (e.g., "i = stream.reduce(...)") instead of a plain expression
	 * statement.
	 * 
	 * <p>
	 * For ANYMATCH, NONEMATCH, and ALLMATCH operations, wraps the pipeline in an IF
	 * statement:
	 * <ul>
	 * <li>ANYMATCH: {@code if (stream.anyMatch(...)) { return true; }}</li>
	 * <li>NONEMATCH: {@code if (!stream.noneMatch(...)) { return false; }}</li>
	 * <li>ALLMATCH: {@code if (!stream.allMatch(...)) { return false; }}</li>
	 * </ul>
	 * 
	 * @param pipeline the pipeline method invocation
	 * @return a Statement wrapping the pipeline
	 */
	public Statement wrapPipeline(MethodInvocation pipeline) {
		if (pipeline == null) {
			return null;
		}

		// Check for ANYMATCH, NONEMATCH, or ALLMATCH operations
		boolean hasAnyMatch = operations.stream()
				.anyMatch(op -> op.getOperationType() == ProspectiveOperation.OperationType.ANYMATCH);
		boolean hasNoneMatch = operations.stream()
				.anyMatch(op -> op.getOperationType() == ProspectiveOperation.OperationType.NONEMATCH);
		boolean hasAllMatch = operations.stream()
				.anyMatch(op -> op.getOperationType() == ProspectiveOperation.OperationType.ALLMATCH);

		if (hasAnyMatch) {
			// Wrap in: if (stream.anyMatch(...)) { return true; }
			IfStatement ifStmt = ast.newIfStatement();
			ifStmt.setExpression(pipeline);

			Block thenBlock = ast.newBlock();
			ReturnStatement returnStmt = ast.newReturnStatement();
			returnStmt.setExpression(ast.newBooleanLiteral(true));
			thenBlock.statements().add(returnStmt);
			ifStmt.setThenStatement(thenBlock);

			return ifStmt;
		} else if (hasNoneMatch) {
			// Wrap in: if (!stream.noneMatch(...)) { return false; }
			return createNegatedEarlyReturnIf(pipeline, false);
		} else if (hasAllMatch) {
			// Wrap in: if (!stream.allMatch(...)) { return false; }
			return createNegatedEarlyReturnIf(pipeline, false);
		}

		// Check if we have a REDUCE operation
		boolean hasReduce = operations.stream()
				.anyMatch(op -> op.getOperationType() == ProspectiveOperation.OperationType.REDUCE);

		if (hasReduce && accumulatorVariable != null) {
			// Wrap in assignment: variable = pipeline
			Assignment assignment = ast.newAssignment();
			assignment.setLeftHandSide(ast.newSimpleName(accumulatorVariable));
			assignment.setOperator(Assignment.Operator.ASSIGN);
			assignment.setRightHandSide(pipeline);

			ExpressionStatement exprStmt = ast.newExpressionStatement(assignment);
			return exprStmt;
		} else {
			// Wrap in an ExpressionStatement for FOREACH and other operations
			ExpressionStatement exprStmt = ast.newExpressionStatement(pipeline);
			return exprStmt;
		}
	}

	/**
	 * Returns the list of operations extracted from the loop body.
	 * 
	 * @return the list of prospective operations
	 */
	public List<ProspectiveOperation> getOperations() {
		return operations;
	}

	/**
	 * Extracts the expression from a REDUCE operation's right-hand side. For
	 * example, in "i += foo(l)", extracts "foo(l)".
	 * 
	 * @param stmt the statement containing the reduce operation
	 * @return the expression to be mapped, or null if none
	 */
	private Expression extractReduceExpression(Statement stmt) {
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
	 * Extracts the non-accumulator argument from Math.max/min call. For example, in
	 * "max = Math.max(max, foo(l))", extracts "foo(l)". Returns null if the
	 * non-accumulator argument is just the loop variable (identity mapping).
	 * 
	 * @param stmt           the statement containing the Math.max/min operation
	 * @param accumulatorVar the accumulator variable name
	 * @param currentVarName the current variable name in the pipeline (loop
	 *                       variable or mapped variable)
	 * @return the expression to be mapped, or null if it's an identity mapping or
	 *         no mapping needed
	 */
	private Expression extractMathMaxMinArgument(Statement stmt, String accumulatorVar, String currentVarName) {
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
	 * <li><b>SUM/PRODUCT/STRING_CONCAT:</b> Extracts and maps the RHS
	 * expression</li>
	 * <li><b>MAX/MIN:</b> Extracts and maps the non-accumulator argument from
	 * Math.max/min</li>
	 * </ul>
	 * 
	 * <p>
	 * This method ensures that the stream pipeline properly transforms elements
	 * before applying the reduction operation.
	 * 
	 * @param ops            the list to add the MAP operation to (must not be null)
	 * @param reduceOp       the REDUCE operation (must not be null and must be a
	 *                       REDUCE type)
	 * @param stmt           the statement containing the reduce operation (must not
	 *                       be null)
	 * @param currentVarName the current variable name in the pipeline (must not be
	 *                       null)
	 * @throws IllegalArgumentException if any parameter is null or reduceOp is not
	 *                                  a REDUCE operation
	 */
	private void addMapBeforeReduce(List<ProspectiveOperation> ops, ProspectiveOperation reduceOp, Statement stmt,
			String currentVarName) {
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
			Expression mapExpr = createTypedLiteralOne();
			ProspectiveOperation mapOp = new ProspectiveOperation(mapExpr, ProspectiveOperation.OperationType.MAP,
					"_item");
			ops.add(mapOp);
		} else if (isArithmeticReducer(reducerType)) {
			// For SUM/PRODUCT/STRING_CONCAT: extract RHS expression
			Expression mapExpression = extractReduceExpression(stmt);
			if (mapExpression != null) {
				// Skip identity mapping: if the expression is just the current variable, don't add MAP
				if (!isIdentityMapping(mapExpression, currentVarName)) {
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
	 * Creates a typed literal "1" appropriate for the accumulator type. Handles
	 * int, long, float, double, byte, short, char types.
	 * 
	 * @return an Expression representing the typed literal 1 (never null)
	 */
	private Expression createTypedLiteralOne() {
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
			return createCastExpression(org.eclipse.jdt.core.dom.PrimitiveType.BYTE, "1");
		case "short":
			return createCastExpression(org.eclipse.jdt.core.dom.PrimitiveType.SHORT, "1");
		case "char":
			return createCastExpression(org.eclipse.jdt.core.dom.PrimitiveType.CHAR, "1");
		default:
			return ast.newNumberLiteral("1");
		}
	}

	/**
	 * Creates a cast expression for the given primitive type. Example: (byte) 1,
	 * (short) 1
	 * 
	 * @param typeCode the primitive type code
	 * @param literal  the literal value to cast
	 * @return a CastExpression
	 */
	private org.eclipse.jdt.core.dom.CastExpression createCastExpression(
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
	private boolean isArithmeticReducer(ProspectiveOperation.ReducerType type) {
		return type == ProspectiveOperation.ReducerType.SUM || type == ProspectiveOperation.ReducerType.PRODUCT
				|| type == ProspectiveOperation.ReducerType.STRING_CONCAT;
	}

	/**
	 * Checks if the reducer type is a min/max reducer.
	 * 
	 * @param type the reducer type to check
	 * @return true if it's MAX or MIN
	 */
	private boolean isMinMaxReducer(ProspectiveOperation.ReducerType type) {
		return type == ProspectiveOperation.ReducerType.MAX || type == ProspectiveOperation.ReducerType.MIN;
	}

	/**
	 * Checks if an expression represents an identity mapping (e.g., num -> num).
	 * 
	 * @param expression the expression to check
	 * @param varName    the variable name to compare against
	 * @return true if the expression is just a reference to varName (identity
	 *         mapping), false otherwise
	 */
	private boolean isIdentityMapping(Expression expression, String varName) {
		if (expression instanceof SimpleName && varName != null) {
			SimpleName simpleName = (SimpleName) expression;
			return simpleName.getIdentifier().equals(varName);
		}
		return false;
	}

	/**
	 * Detects if a statement contains a REDUCE pattern (i++, sum += x, etc.). Also
	 * detects MAX/MIN patterns like: max = Math.max(max, x)
	 * 
	 * @param stmt the statement to check
	 * @return a ProspectiveOperation for REDUCE if detected, null otherwise
	 */
	private ProspectiveOperation detectReduceOperation(Statement stmt) {
		if (!(stmt instanceof ExpressionStatement)) {
			return null;
		}

		ExpressionStatement exprStmt = (ExpressionStatement) stmt;
		Expression expr = exprStmt.getExpression();

		// Check for postfix increment/decrement: i++, i--
		if (expr instanceof PostfixExpression) {
			PostfixExpression postfix = (PostfixExpression) expr;
			if (postfix.getOperand() instanceof SimpleName) {
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
				accumulatorType = getVariableType(varName);
				return new ProspectiveOperation(stmt, varName, reducerType);
			}
		}

		// Check for prefix increment/decrement: ++i, --i
		if (expr instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression) expr;
			if (prefix.getOperand() instanceof SimpleName) {
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
				accumulatorType = getVariableType(varName);
				return new ProspectiveOperation(stmt, varName, reducerType);
			}
		}

		// Check for compound assignments: +=, -=, *=, etc.
		if (expr instanceof Assignment) {
			Assignment assignment = (Assignment) expr;
			if (assignment.getLeftHandSide() instanceof SimpleName) {
				String varName = ((SimpleName) assignment.getLeftHandSide()).getIdentifier();

				// Check for simple assignment operators first
				if (assignment.getOperator() != Assignment.Operator.ASSIGN) {
					ProspectiveOperation.ReducerType reducerType;

					if (assignment.getOperator() == Assignment.Operator.PLUS_ASSIGN) {
						reducerType = ProspectiveOperation.ReducerType.SUM;
					} else if (assignment.getOperator() == Assignment.Operator.TIMES_ASSIGN) {
						reducerType = ProspectiveOperation.ReducerType.PRODUCT;
					} else if (assignment.getOperator() == Assignment.Operator.MINUS_ASSIGN) {
						reducerType = ProspectiveOperation.ReducerType.DECREMENT;
					} else {
						// Other assignment operators not yet supported
						return null;
					}

					accumulatorVariable = varName;
					accumulatorType = getVariableType(varName);
					return new ProspectiveOperation(stmt, varName, reducerType);
				}

				// Check for regular assignment with Math.max/Math.min pattern
				// Pattern: max = Math.max(max, x) or min = Math.min(min, x)
				if (assignment.getOperator() == Assignment.Operator.ASSIGN) {
					Expression rhs = assignment.getRightHandSide();
					ProspectiveOperation.ReducerType reducerType = detectMathMaxMinPattern(varName, rhs);
					if (reducerType != null) {
						accumulatorVariable = varName;
						accumulatorType = getVariableType(varName);
						return new ProspectiveOperation(stmt, varName, reducerType);
					}
				}
			}
		}

		return null;
	}

	/**
	 * Detects Math.max/Math.min patterns in an expression. Patterns: max =
	 * Math.max(max, x) or min = Math.min(min, x)
	 * 
	 * @param varName the accumulator variable name
	 * @param expr    the right-hand side expression to check
	 * @return MAX or MIN if pattern detected, null otherwise
	 */
	private ProspectiveOperation.ReducerType detectMathMaxMinPattern(String varName, Expression expr) {
		if (!(expr instanceof MethodInvocation)) {
			return null;
		}

		MethodInvocation methodInv = (MethodInvocation) expr;

		// Check if it's a Math.max or Math.min call
		if (methodInv.getExpression() instanceof SimpleName) {
			SimpleName className = (SimpleName) methodInv.getExpression();
			if (!"Math".equals(className.getIdentifier())) {
				return null;
			}

			String methodName = methodInv.getName().getIdentifier();
			if (!"max".equals(methodName) && !"min".equals(methodName)) {
				return null;
			}

			// Check if one of the arguments is the accumulator variable
			List<?> args = methodInv.arguments();
			if (args.size() != 2) {
				return null;
			}

			boolean hasAccumulatorArg = false;
			for (Object argObj : args) {
				if (argObj instanceof SimpleName) {
					SimpleName argName = (SimpleName) argObj;
					if (varName.equals(argName.getIdentifier())) {
						hasAccumulatorArg = true;
						break;
					}
				}
			}

			if (hasAccumulatorArg) {
				return "max".equals(methodName) ? ProspectiveOperation.ReducerType.MAX
						: ProspectiveOperation.ReducerType.MIN;
			}
		}

		return null;
	}

	/**
	 * Analyzes the body of an enhanced for-loop and extracts a list of
	 * {@link ProspectiveOperation} objects representing the operations that can be
	 * mapped to stream operations.
	 * 
	 * <p>
	 * This method inspects the statements within the loop body to identify possible
	 * stream operations such as {@code map} (for variable declarations with
	 * initializers), {@code filter} (for IF statements), and {@code forEach} (for
	 * the final or sole statement). For block bodies, it processes each statement
	 * in order, treating: - IF statements with single block body as FILTER
	 * operations - Variable declarations with initializers as MAP operations - The
	 * last statement as a FOREACH operation
	 *
	 * @param body        the {@link Statement} representing the loop body; may be a
	 *                    {@link Block} or a single statement
	 * @param loopVarName the name of the loop variable currently in scope; may be
	 *                    updated if a map operation is found
	 * @return a list of {@link ProspectiveOperation} objects, in the order they
	 *         should be applied, representing the sequence of stream operations
	 *         inferred from the loop body; returns an empty list if body is null or
	 *         cannot be converted
	 * @see ProspectiveOperation
	 */
	private List<ProspectiveOperation> parseLoopBody(Statement body, String loopVarName) {
		List<ProspectiveOperation> ops = new ArrayList<>();
		String currentVarName = loopVarName; // Track the current variable name through the pipeline

		if (body instanceof Block) {
			Block block = (Block) body;
			List<Statement> statements = block.statements();

			for (int i = 0; i < statements.size(); i++) {
				Statement stmt = statements.get(i);
				boolean isLast = (i == statements.size() - 1);

				if (stmt instanceof VariableDeclarationStatement) {
					// Variable declaration → MAP operation
					VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmt;
					List<VariableDeclarationFragment> fragments = varDecl.fragments();
					if (!fragments.isEmpty()) {
						VariableDeclarationFragment frag = fragments.get(0);
						if (frag.getInitializer() != null) {
							String newVarName = frag.getName().getIdentifier();
							ProspectiveOperation mapOp = new ProspectiveOperation(frag.getInitializer(),
									ProspectiveOperation.OperationType.MAP, newVarName);
							ops.add(mapOp);

							// Update current var name for subsequent operations
							currentVarName = newVarName;
						}
					}
				} else if (stmt instanceof IfStatement && !isLast) {
					// IF statement (not the last statement) → potential FILTER or nested processing
					IfStatement ifStmt = (IfStatement) stmt;

					// Check if this is a filtering IF (simple condition with block body)
					if (ifStmt.getElseStatement() == null) {
						Statement thenStmt = ifStmt.getThenStatement();

						// Check if this is an early return pattern (anyMatch/noneMatch/allMatch)
						if (isEarlyReturnIf(ifStmt)) {
							// Create ANYMATCH, NONEMATCH, or ALLMATCH operation
							ProspectiveOperation.OperationType opType;
							if (isAnyMatchPattern) {
								opType = ProspectiveOperation.OperationType.ANYMATCH;
							} else if (isNoneMatchPattern) {
								opType = ProspectiveOperation.OperationType.NONEMATCH;
							} else {
								// allMatchPattern
								// For allMatch, we need to negate the condition since
								// the pattern is "if (!condition) return false"
								// We want "allMatch(condition)" not "allMatch(!condition)"
								opType = ProspectiveOperation.OperationType.ALLMATCH;
							}

							// For allMatch with negated condition, strip the negation
							Expression condition = ifStmt.getExpression();
							if (isAllMatchPattern && condition instanceof PrefixExpression) {
								PrefixExpression prefixExpr = (PrefixExpression) condition;
								if (prefixExpr.getOperator() == PrefixExpression.Operator.NOT) {
									// Use the operand without negation for allMatch
									condition = prefixExpr.getOperand();
								}
							}

							ProspectiveOperation matchOp = new ProspectiveOperation(condition, opType);
							ops.add(matchOp);
							// Don't process the body since it's just a return statement
						} else if (isIfWithContinue(ifStmt)) {
							// Convert "if (condition) continue;" to ".filter(x -> !(condition))"
							Expression negatedCondition = createNegatedExpression(ast, ifStmt.getExpression());
							ProspectiveOperation filterOp = new ProspectiveOperation(negatedCondition,
									ProspectiveOperation.OperationType.FILTER);
							ops.add(filterOp);
							// Don't process the body since it's just a continue statement
						} else {
							// Regular filter with nested processing
							currentVarName = processIfAsFilter(ops, ifStmt, currentVarName, true);
						}
					}
				} else if (stmt instanceof IfStatement && isLast) {
					// Last statement is an IF → check for early return or process as filter with
					// nested body
					IfStatement ifStmt = (IfStatement) stmt;
					if (ifStmt.getElseStatement() == null) {
						// Check if this is an early return pattern (anyMatch/noneMatch/allMatch)
						if (isEarlyReturnIf(ifStmt)) {
							// Create ANYMATCH, NONEMATCH, or ALLMATCH operation
							ProspectiveOperation.OperationType opType;
							if (isAnyMatchPattern) {
								opType = ProspectiveOperation.OperationType.ANYMATCH;
							} else if (isNoneMatchPattern) {
								opType = ProspectiveOperation.OperationType.NONEMATCH;
							} else {
								// allMatchPattern
								opType = ProspectiveOperation.OperationType.ALLMATCH;
							}

							// For allMatch with negated condition, strip the negation
							Expression condition = ifStmt.getExpression();
							if (isAllMatchPattern && condition instanceof PrefixExpression) {
								PrefixExpression prefixExpr = (PrefixExpression) condition;
								if (prefixExpr.getOperator() == PrefixExpression.Operator.NOT) {
									// Use the operand without negation for allMatch
									condition = prefixExpr.getOperand();
								}
							}

							ProspectiveOperation matchOp = new ProspectiveOperation(condition, opType);
							ops.add(matchOp);
						} else {
							processIfAsFilter(ops, ifStmt, currentVarName, false);
						}
					}
				} else if (!isLast) {
					// Non-last statement that's not a variable declaration or IF
					// This is a side-effect statement like foo(l) - wrap it in a MAP that returns
					// the current variable
					// Only do this if it's not a REDUCE operation (which should only be the last
					// statement)
					ProspectiveOperation reduceCheck = detectReduceOperation(stmt);
					if (reduceCheck == null) {
						// Check if this is a safe side-effect
						if (!isSafeSideEffect(stmt, currentVarName, ops)) {
							// Unsafe side-effect - don't convert this loop
							// Return empty list to signal conversion should be rejected
							return new ArrayList<>();
						}

						// Create a MAP operation with side effect that returns the current variable
						// Note: For side-effect MAPs, the third parameter is the variable to return,
						// not the loop variable
						ProspectiveOperation mapOp = new ProspectiveOperation(stmt,
								ProspectiveOperation.OperationType.MAP, currentVarName);
						ops.add(mapOp);
					}
				} else if (isLast) {
					// Last statement → Check for REDUCE first, otherwise FOREACH
					ProspectiveOperation reduceOp = detectReduceOperation(stmt);
					if (reduceOp != null) {
						// Add MAP operation before REDUCE based on reducer type
						addMapBeforeReduce(ops, reduceOp, stmt, currentVarName);
						ops.add(reduceOp);
					} else {
						// Regular FOREACH operation
						ProspectiveOperation forEachOp = new ProspectiveOperation(stmt,
								ProspectiveOperation.OperationType.FOREACH, currentVarName);
						ops.add(forEachOp);
					}
				}
			}
		} else if (body instanceof IfStatement) {
			// Single IF statement → process as filter with nested body
			IfStatement ifStmt = (IfStatement) body;
			if (ifStmt.getElseStatement() == null) {
				// Add FILTER operation
				ProspectiveOperation filterOp = new ProspectiveOperation(ifStmt.getExpression(),
						ProspectiveOperation.OperationType.FILTER);
				ops.add(filterOp);

				// Process the then statement
				List<ProspectiveOperation> nestedOps = parseLoopBody(ifStmt.getThenStatement(), currentVarName);
				ops.addAll(nestedOps);
			}
		} else {
			// Single statement → Check for REDUCE first, otherwise FOREACH
			ProspectiveOperation reduceOp = detectReduceOperation(body);
			if (reduceOp != null) {
				// Add MAP operation before REDUCE based on reducer type
				addMapBeforeReduce(ops, reduceOp, body, currentVarName);
				ops.add(reduceOp);
			} else {
				// Regular FOREACH operation
				ProspectiveOperation forEachOp = new ProspectiveOperation(body,
						ProspectiveOperation.OperationType.FOREACH, currentVarName);
				ops.add(forEachOp);
			}
		}

		return ops;
	}

	/**
	 * Determines the variable name to use for the current operation in a chain of
	 * stream operations.
	 * 
	 * <p>
	 * This method inspects the list of {@link ProspectiveOperation}s up to
	 * {@code currentIndex - 1} to find if a previous MAP operation exists. If so,
	 * it returns the produced variable name from that MAP operation. Otherwise, it
	 * returns the loop variable name.
	 * </p>
	 *
	 * <p>
	 * <b>Example:</b>
	 * </p>
	 * 
	 * <pre>
	 * for (Integer num : numbers) {
	 * 	int squared = num * num; // MAP: num -> squared
	 * 	if (squared > 100) { // FILTER uses 'squared', not 'num'
	 * 		System.out.println(squared);
	 * 	}
	 * }
	 * </pre>
	 *
	 * @param operations   the list of prospective operations representing the loop
	 *                     body transformation (must not be null)
	 * @param currentIndex the index of the current operation in the list;
	 *                     operations before this index are considered
	 * @param loopVarName  the original loop variable name (must not be null)
	 * @return the variable name produced by the most recent MAP operation, or the
	 *         loop variable name if none found
	 * @throws IllegalArgumentException if operations or loopVarName is null
	 */
	private String getVariableNameFromPreviousOp(List<ProspectiveOperation> operations, int currentIndex,
			String loopVarName) {
		if (operations == null) {
			throw new IllegalArgumentException("operations cannot be null");
		}
		if (loopVarName == null) {
			throw new IllegalArgumentException("loopVarName cannot be null");
		}

		// Look back to find the most recent operation that produces a variable
		for (int i = currentIndex - 1; i >= 0; i--) {
			ProspectiveOperation op = operations.get(i);
			if (op != null && op.getProducedVariableName() != null) {
				return op.getProducedVariableName();
			}
		}
		return loopVarName;
	}

	/**
	 * Determines whether the stream pipeline requires an explicit .stream() prefix.
	 * 
	 * <p>
	 * Returns false if the pipeline consists of a single FOREACH operation, which
	 * can be called directly on the collection. Returns true for all other cases,
	 * including multiple operations or non-FOREACH terminal operations.
	 * 
	 * @return true if .stream() is required, false if direct collection method can
	 *         be used
	 */
	private boolean requiresStreamPrefix() {
		if (operations.isEmpty()) {
			return true;
		}
		return operations.size() > 1
				|| operations.get(0).getOperationType() != ProspectiveOperation.OperationType.FOREACH;
	}

	/**
	 * Processes an IF statement as a filter operation with nested body parsing.
	 * Adds a FILTER operation for the condition and recursively processes the body.
	 * 
	 * @param ops            the list of operations to add to
	 * @param ifStmt         the IF statement to process
	 * @param currentVarName the current variable name in the pipeline
	 * @param updateVarName  whether to update currentVarName based on nested
	 *                       operations
	 * @return the updated currentVarName (only modified if updateVarName is true)
	 */
	private String processIfAsFilter(List<ProspectiveOperation> ops, IfStatement ifStmt, String currentVarName,
			boolean updateVarName) {
		// Add FILTER operation for the condition
		ProspectiveOperation filterOp = new ProspectiveOperation(ifStmt.getExpression(),
				ProspectiveOperation.OperationType.FILTER);
		ops.add(filterOp);

		// Process the body of the IF statement recursively
		List<ProspectiveOperation> nestedOps = parseLoopBody(ifStmt.getThenStatement(), currentVarName);
		ops.addAll(nestedOps);

		// Update current var name if requested and nested operations produced a new
		// variable
		if (updateVarName && !nestedOps.isEmpty()) {
			ProspectiveOperation lastNested = nestedOps.get(nestedOps.size() - 1);
			if (lastNested.getProducedVariableName() != null) {
				return lastNested.getProducedVariableName();
			}
		}

		return currentVarName;
	}

	/**
	 * Checks if an IF statement contains a continue statement. This pattern should
	 * be converted to a negated filter.
	 * 
	 * <p>
	 * This method identifies whether an IF statement contains a continue that can
	 * be converted to a filter operation. The actual rejection of labeled continues
	 * happens earlier in {@link PreconditionsChecker#isSafeToRefactor()}.
	 * 
	 * @param ifStatement the IF statement to check
	 * @return true if the then branch contains an unlabeled continue statement
	 */
	private boolean isIfWithContinue(IfStatement ifStatement) {
		Statement thenStatement = ifStatement.getThenStatement();
		if (thenStatement instanceof ContinueStatement) {
			ContinueStatement continueStmt = (ContinueStatement) thenStatement;
			// Only allow unlabeled continues
			return continueStmt.getLabel() == null;
		}
		if (thenStatement instanceof Block) {
			Block block = (Block) thenStatement;
			if (block.statements().size() == 1 && block.statements().get(0) instanceof ContinueStatement) {
				ContinueStatement continueStmt = (ContinueStatement) block.statements().get(0);
				// Only allow unlabeled continues
				return continueStmt.getLabel() == null;
			}
		}
		return false;
	}

	/**
	 * Checks if the IF statement contains an early return (for
	 * anyMatch/noneMatch/allMatch patterns).
	 * 
	 * @param ifStatement the IF statement to check
	 * @return true if the IF contains a return statement matching the detected
	 *         pattern
	 */
	private boolean isEarlyReturnIf(IfStatement ifStatement) {
		if (!isAnyMatchPattern && !isNoneMatchPattern && !isAllMatchPattern) {
			return false;
		}

		// Check if this IF statement is the early return IF from preconditions
		IfStatement earlyReturnIf = preconditions.getEarlyReturnIf();
		return earlyReturnIf != null && earlyReturnIf == ifStatement;
	}

	/**
	 * Creates a negated expression for filter operations. Used when converting "if
	 * (condition) continue;" to ".filter(x -> !(condition))".
	 * 
	 * @param ast       the AST to create nodes in
	 * @param condition the condition to negate
	 * @return a negated expression
	 */
	private Expression createNegatedExpression(AST ast, Expression condition) {
		PrefixExpression negation = ast.newPrefixExpression();
		negation.setOperator(PrefixExpression.Operator.NOT);
		negation.setOperand((Expression) ASTNode.copySubtree(ast, condition));
		return negation;
	}

	/**
	 * Attempts to determine the type name of a variable by searching for its
	 * declaration in the method containing the for-loop and parent scopes.
	 * 
	 * @param varName the variable name to look up
	 * @return the simple type name (e.g., "double", "int") or null if not found
	 */
	private String getVariableType(String varName) {
		// Walk up the AST tree searching for the variable in each scope
		ASTNode currentNode = forLoop.getParent();

		while (currentNode != null) {
			// Search in blocks
			if (currentNode instanceof org.eclipse.jdt.core.dom.Block) {
				org.eclipse.jdt.core.dom.Block block = (org.eclipse.jdt.core.dom.Block) currentNode;
				String type = searchBlockForVariableType(block, varName);
				if (type != null) {
					return type;
				}
			}
			// Search in method bodies
			else if (currentNode instanceof org.eclipse.jdt.core.dom.MethodDeclaration) {
				org.eclipse.jdt.core.dom.MethodDeclaration method = (org.eclipse.jdt.core.dom.MethodDeclaration) currentNode;
				if (method.getBody() != null) {
					String type = searchBlockForVariableType(method.getBody(), varName);
					if (type != null) {
						return type;
					}
				}
			}
			// Search in initializer blocks (instance or static)
			else if (currentNode instanceof org.eclipse.jdt.core.dom.Initializer) {
				org.eclipse.jdt.core.dom.Initializer initializer = (org.eclipse.jdt.core.dom.Initializer) currentNode;
				if (initializer.getBody() != null) {
					String type = searchBlockForVariableType(initializer.getBody(), varName);
					if (type != null) {
						return type;
					}
				}
			}
			// Search in lambda expressions
			else if (currentNode instanceof org.eclipse.jdt.core.dom.LambdaExpression) {
				org.eclipse.jdt.core.dom.LambdaExpression lambda = (org.eclipse.jdt.core.dom.LambdaExpression) currentNode;
				if (lambda.getBody() instanceof org.eclipse.jdt.core.dom.Block) {
					String type = searchBlockForVariableType((org.eclipse.jdt.core.dom.Block) lambda.getBody(),
							varName);
					if (type != null) {
						return type;
					}
				}
			}

			// Move up to parent scope
			currentNode = currentNode.getParent();
		}

		return null;
	}

	/**
	 * Searches a block for a variable declaration and returns its type.
	 * 
	 * @param block   the block to search
	 * @param varName the variable name to find
	 * @return the simple type name or null if not found
	 */
	private String searchBlockForVariableType(org.eclipse.jdt.core.dom.Block block, String varName) {
		if (block == null) {
			return null;
		}

		for (Object stmtObj : block.statements()) {
			if (stmtObj instanceof VariableDeclarationStatement) {
				VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmtObj;
				for (Object fragObj : varDecl.fragments()) {
					if (fragObj instanceof VariableDeclarationFragment) {
						VariableDeclarationFragment frag = (VariableDeclarationFragment) fragObj;
						if (frag.getName().getIdentifier().equals(varName)) {
							org.eclipse.jdt.core.dom.Type type = varDecl.getType();
							// Robustly extract the simple type name
							if (type.isPrimitiveType()) {
								// For primitive types: int, double, etc.
								return ((org.eclipse.jdt.core.dom.PrimitiveType) type).getPrimitiveTypeCode()
										.toString();
							} else if (type.isSimpleType()) {
								// For reference types: get the simple name
								org.eclipse.jdt.core.dom.SimpleType simpleType = (org.eclipse.jdt.core.dom.SimpleType) type;
								// Try to use binding if available
								org.eclipse.jdt.core.dom.ITypeBinding binding = simpleType.resolveBinding();
								if (binding != null) {
									return binding.getName();
								} else {
									return simpleType.getName().getFullyQualifiedName();
								}
							} else if (type.isArrayType()) {
								// For array types, get the element type recursively and append "[]"
								org.eclipse.jdt.core.dom.ArrayType arrayType = (org.eclipse.jdt.core.dom.ArrayType) type;
								org.eclipse.jdt.core.dom.Type elementType = arrayType.getElementType();
								String elementTypeName;
								if (elementType.isPrimitiveType()) {
									elementTypeName = ((org.eclipse.jdt.core.dom.PrimitiveType) elementType)
											.getPrimitiveTypeCode().toString();
								} else if (elementType.isSimpleType()) {
									org.eclipse.jdt.core.dom.SimpleType simpleType = (org.eclipse.jdt.core.dom.SimpleType) elementType;
									org.eclipse.jdt.core.dom.ITypeBinding binding = simpleType.resolveBinding();
									if (binding != null) {
										elementTypeName = binding.getName();
									} else {
										elementTypeName = simpleType.getName().getFullyQualifiedName();
									}
								} else {
									// Fallback for other types
									elementTypeName = elementType.toString();
								}
								return elementTypeName + "[]";
							} else {
								// Fallback for other types (e.g., parameterized, qualified, etc.)
								return type.toString();
							}
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Creates an IF statement that wraps a negated pipeline expression with an
	 * early return. Used for noneMatch and allMatch patterns.
	 * 
	 * @param pipeline    the stream pipeline expression
	 * @param returnValue the boolean value to return (true or false)
	 * @return an IF statement: if (!pipeline) { return returnValue; }
	 */
	private IfStatement createNegatedEarlyReturnIf(MethodInvocation pipeline, boolean returnValue) {
		IfStatement ifStmt = ast.newIfStatement();

		// Create negated expression: !pipeline
		PrefixExpression negation = ast.newPrefixExpression();
		negation.setOperator(PrefixExpression.Operator.NOT);
		negation.setOperand(pipeline);
		ifStmt.setExpression(negation);

		// Create then block with return statement
		Block thenBlock = ast.newBlock();
		ReturnStatement returnStmt = ast.newReturnStatement();
		returnStmt.setExpression(ast.newBooleanLiteral(returnValue));
		thenBlock.statements().add(returnStmt);
		ifStmt.setThenStatement(thenBlock);

		return ifStmt;
	}

	/**
	 * Validates that variables used in operations are properly scoped.
	 * 
	 * <p>
	 * This method ensures that:
	 * <ul>
	 * <li>Consumed variables are available in the current scope (defined earlier in
	 * pipeline)</li>
	 * <li>Produced variables don't shadow loop variables improperly</li>
	 * <li>Accumulator variables don't leak into lambda scopes</li>
	 * </ul>
	 * 
	 * <p>
	 * <b>Relationship with {@link #isSafeSideEffect}:</b>
	 * </p>
	 * <p>
	 * While {@code isSafeSideEffect} performs early detection of obvious assignment
	 * issues during pipeline construction, this method performs comprehensive scope
	 * checking across the entire pipeline to catch variable availability issues.
	 * Both methods work together to ensure safe conversions:
	 * <ul>
	 * <li>{@code isSafeSideEffect}: Detects unsafe assignments to external/loop
	 * variables</li>
	 * <li>{@code validateVariableScope}: Validates all variables are properly
	 * defined and scoped</li>
	 * </ul>
	 * 
	 * <p>
	 * <b>Algorithm:</b>
	 * </p>
	 * <p>
	 * Tracks available variables as we process operations in sequence. For each
	 * operation:
	 * <ol>
	 * <li>Check that all consumed variables (except loop var and accumulators) are
	 * available</li>
	 * <li>Add any produced variables to the available set for subsequent
	 * operations</li>
	 * <li>Return false if any consumed variable is used before being defined</li>
	 * </ol>
	 * 
	 * @param operations  the list of operations to validate (must not be null)
	 * @param loopVarName the loop variable name (must not be null)
	 * @return true if all variables are properly scoped, false otherwise
	 * @throws IllegalArgumentException if operations or loopVarName is null
	 */
	private boolean validateVariableScope(List<ProspectiveOperation> operations, String loopVarName) {
		if (operations == null) {
			throw new IllegalArgumentException("operations cannot be null");
		}
		if (loopVarName == null) {
			throw new IllegalArgumentException("loopVarName cannot be null");
		}

		Set<String> availableVars = new HashSet<>();
		availableVars.add(loopVarName);
		
		// Track if we've moved past the loop variable to a mapped variable
		boolean loopVarConsumed = false;

		for (ProspectiveOperation op : operations) {
			if (op == null) {
				throw new IllegalStateException("Encountered null ProspectiveOperation in operations list");
			}

			// Check consumed variables are available
			Set<String> consumed = op.getConsumedVariables();
			for (String var : consumed) {
				// Accumulator variables are in outer scope, always available
				if (isAccumulatorVariable(var, operations)) {
					continue;
				}
				
				// After a MAP produces a new variable, the loop variable should not be used
				// unless it's the current operation that consumes it
				if (var.equals(loopVarName)) {
					if (loopVarConsumed && op.getProducedVariableName() != null) {
						// Loop variable used after it's been replaced by a MAP - scope violation
						return false;
					}
				} else {
					// Non-loop, non-accumulator variable - must be in availableVars
					if (!availableVars.contains(var)) {
						// Variable used before it's defined - this is a scope violation
						return false;
					}
				}
			}

			// Add produced variables to available set and mark loop var as consumed if applicable
			String produced = op.getProducedVariableName();
			if (produced != null && !produced.isEmpty()) {
				availableVars.add(produced);
				
				// If this MAP operation consumed the loop variable, mark it as consumed
				if (consumed.contains(loopVarName)) {
					loopVarConsumed = true;
					// Remove loop variable from available vars - it's now been replaced
					availableVars.remove(loopVarName);
				}
			}
		}

		return true;
	}

	/**
	 * Checks if a variable is an accumulator variable in any REDUCE operation.
	 * 
	 * @param varName    the variable name to check
	 * @param operations the list of operations
	 * @return true if the variable is an accumulator, false otherwise
	 */
	private boolean isAccumulatorVariable(String varName, List<ProspectiveOperation> operations) {
		for (ProspectiveOperation op : operations) {
			if (op.getOperationType() == ProspectiveOperation.OperationType.REDUCE) {
				if (varName.equals(op.getAccumulatorVariableName())) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Checks if a statement is safe to convert as a side-effect in a stream
	 * pipeline. Side-effects are statements that don't directly produce a value but
	 * perform actions.
	 * 
	 * <p>
	 * Safe side-effects include:
	 * <ul>
	 * <li>Method calls that don't modify loop variables or accumulators</li>
	 * <li>System.out.println and similar logging</li>
	 * <li>Statements that don't contain assignments to variables outside the lambda
	 * scope</li>
	 * </ul>
	 * 
	 * <p>
	 * Unsafe side-effects that should prevent conversion:
	 * <ul>
	 * <li>Assignments to variables declared outside the loop (except accumulators
	 * in last statement)</li>
	 * <li>Modifications to shared mutable state via simple variable
	 * assignments</li>
	 * </ul>
	 * 
	 * <p>
	 * <b>Design Decision - Limited Scope:</b>
	 * </p>
	 * <p>
	 * This method only validates simple variable assignments (SimpleName on LHS).
	 * Array element assignments, field assignments, and method calls are
	 * conservatively allowed, as they may be part of valid stream pipeline
	 * patterns. This design choice prioritizes not rejecting valid patterns over
	 * catching all potential issues.
	 * 
	 * <p>
	 * <b>Relationship with {@link #validateVariableScope}:</b>
	 * </p>
	 * <p>
	 * While {@code isSafeSideEffect} performs early detection of obvious assignment
	 * issues during pipeline construction, {@link #validateVariableScope} performs
	 * comprehensive scope checking across the entire pipeline to catch variable
	 * availability issues. Both methods work together to ensure safe conversions.
	 * 
	 * @param stmt           the statement to check
	 * @param currentVarName the current variable name in the pipeline (may differ
	 *                       from loop variable if mapped)
	 * @param operations     the list of operations (to check for accumulators)
	 * @return true if the statement is safe to include in a stream pipeline, false
	 *         otherwise
	 */
	private boolean isSafeSideEffect(Statement stmt, String currentVarName, List<ProspectiveOperation> operations) {
		if (stmt == null) {
			return false;
		}

		if (!(stmt instanceof ExpressionStatement)) {
			// Only expression statements can be safe side-effects
			// Other statement types (if, while, for, etc.) should be handled differently
			return false;
		}

		ExpressionStatement exprStmt = (ExpressionStatement) stmt;
		Expression expr = exprStmt.getExpression();

		// Defensive null check: a null expression indicates a malformed or incomplete
		// AST.
		// In that case we conservatively abort stream conversion by returning false.
		if (expr == null) {
			return false;
		}

		// Check for assignments - these are potentially unsafe if they modify external
		// variables
		if (expr instanceof Assignment) {
			Assignment assignment = (Assignment) expr;
			Expression lhs = assignment.getLeftHandSide();

			// Only validate SimpleName assignments (simple variables)
			// Array access and field access are conservatively allowed
			if (lhs instanceof SimpleName) {
				String varName = ((SimpleName) lhs).getIdentifier();

				// Assignment to current pipeline variable is unsafe (would modify loop/mapped
				// var)
				if (varName.equals(currentVarName)) {
					return false;
				}

				// Assignment to accumulator variables is handled by REDUCE, not side-effects
				if (isAccumulatorVariable(varName, operations)) {
					return false;
				}

				// Other assignments to external variables are unsafe for conversion
				// This is a conservative approach - we could refine this further if needed
				return false;
			}

			// Non-SimpleName assignments (array access, field access) are allowed
			// This is a deliberate design choice - see method documentation
		}

		// Method calls and other expressions are generally safe
		return true;
	}
}
