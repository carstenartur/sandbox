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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Represents a prospective stream operation extracted from a loop body.
 * 
 * <p>
 * This class is final to prevent subclassing and potential finalizer attacks,
 * since constructors call analysis methods that could potentially throw
 * exceptions.
 * </p>
 */
public final class ProspectiveOperation {
	/**
	 * The original expression being analyzed or transformed.
	 * <p>
	 * This is set directly when the {@link ProspectiveOperation} is constructed
	 * with an {@link Expression}. If constructed with a
	 * {@link org.eclipse.jdt.core.dom.Statement}, this is set to the expression
	 * contained within the statement (if applicable, e.g., for
	 * {@link org.eclipse.jdt.core.dom.ExpressionStatement}).
	 */
	private Expression originalExpression;

	/**
	 * The original statement being analyzed or transformed.
	 * <p>
	 * This is set when the {@link ProspectiveOperation} is constructed with a
	 * {@link org.eclipse.jdt.core.dom.Statement}. If the statement is an
	 * {@link org.eclipse.jdt.core.dom.ExpressionStatement}, its expression is also
	 * extracted and stored in {@link #originalExpression}. Otherwise,
	 * {@link #originalExpression} may be null.
	 */
	private org.eclipse.jdt.core.dom.Statement originalStatement;

	private OperationType operationType;
	private Set<String> neededVariables = new HashSet<>();
	/**
	 * The name of the loop variable associated with this operation, if applicable.
	 * <p>
	 * This is set when the {@link ProspectiveOperation} is constructed with a
	 * statement and a loop variable name. It is used to track the variable iterated
	 * over in enhanced for-loops or similar constructs.
	 */
	private String loopVariableName;

	/**
	 * The name of the variable produced by this operation (for MAP operations).
	 * This is used to track variable names through the stream pipeline.
	 */
	private String producedVariableName;

	/**
	 * The name of the accumulator variable for REDUCE operations. Used to track
	 * which variable is being accumulated (e.g., "i" in "i++").
	 */
	private String accumulatorVariableName;

	/**
	 * The reducer type for REDUCE operations (INCREMENT, DECREMENT, SUM, etc.).
	 */
	private ReducerType reducerType;

	/**
	 * The type of the accumulator variable for REDUCE operations (e.g., "int", "double", "long").
	 * Used to generate the correct method reference (Integer::sum vs Double::sum).
	 */
	private String accumulatorType;

	/**
	 * Indicates if this operation is null-safe (e.g., variables are annotated with @NotNull).
	 * When true for STRING_CONCAT, String::concat method reference can be used safely.
	 */
	private boolean isNullSafe = false;

	/**
	 * Set of variables consumed by this operation. Used for tracking variable scope
	 * and preventing leaks.
	 */
	private Set<String> consumedVariables = new HashSet<>();

	// Sammelt alle verwendeten Variablen
	private void collectNeededVariables(Expression expression) {
		if (expression == null)
			return;
		expression.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				// Only collect SimpleName nodes that are actual variable references,
				// not part of qualified names (e.g., System.out) or method/field names
				ASTNode parent = node.getParent();
				
				// Skip if this is any part of a qualified name (e.g., "System" or "out" in "System.out")
				if (parent instanceof org.eclipse.jdt.core.dom.QualifiedName) {
					// Skip both qualifier and name parts of qualified names
					return super.visit(node);
				}
				
				// Skip if this is any part of a field access (e.g., explicit field accesses)
				if (parent instanceof org.eclipse.jdt.core.dom.FieldAccess) {
					// Skip both the expression (qualifier) and the name (field name)
					return super.visit(node);
				}
				
				// Skip if this is the name part of a method invocation (e.g., "println" in "out.println()")
				if (parent instanceof MethodInvocation) {
					MethodInvocation mi = (MethodInvocation) parent;
					if (mi.getName() == node) {
						return super.visit(node); // Skip method name
					}
				}
				
				// Skip if this is part of a type reference (e.g., class names)
				if (parent instanceof org.eclipse.jdt.core.dom.Type) {
					return super.visit(node);
				}
				
				// Skip if this is the type name in a constructor invocation (e.g., "MyClass" in "new MyClass()")
				if (parent instanceof org.eclipse.jdt.core.dom.ClassInstanceCreation) {
					return super.visit(node);
				}
				
				// Skip if this is the name of a type declaration (e.g., class or interface name)
				if (parent instanceof org.eclipse.jdt.core.dom.TypeDeclaration) {
					org.eclipse.jdt.core.dom.TypeDeclaration typeDecl = (org.eclipse.jdt.core.dom.TypeDeclaration) parent;
					if (typeDecl.getName() == node) {
						return super.visit(node);
					}
				}
				
				// Otherwise, this is a variable reference - collect it
				neededVariables.add(node.getIdentifier());
				return super.visit(node);
			}
		});
	}

	/**
	 * Constructor for operations with an expression.
	 * 
	 * @param expression    the expression to process (must not be null)
	 * @param operationType the type of operation (must not be null)
	 */
	public ProspectiveOperation(Expression expression, OperationType operationType) {
		assert expression != null : "expression cannot be null";
		assert operationType != null : "operationType cannot be null";

		this.originalExpression = expression;
		this.operationType = operationType;
		collectNeededVariables(expression);
		updateConsumedVariables();
	}

	/**
	 * Constructor for operations with a statement.
	 * 
	 * @param statement     the statement to process (must not be null)
	 * @param operationType the type of operation (MAP, FOREACH, etc.) (must not be
	 *                      null)
	 * @param loopVarName   the loop variable name; for side-effect MAP operations,
	 *                      this represents the variable to be returned in the
	 *                      lambda body (may be the current variable name in the
	 *                      pipeline, not necessarily the original loop variable)
	 */
	public ProspectiveOperation(org.eclipse.jdt.core.dom.Statement statement, OperationType operationType,
			String loopVarName) {
		assert statement != null : "statement cannot be null";
		assert operationType != null : "operationType cannot be null";

		this.originalStatement = statement;
		this.operationType = operationType;
		this.loopVariableName = loopVarName;
		if (statement instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
			this.originalExpression = ((org.eclipse.jdt.core.dom.ExpressionStatement) statement).getExpression();
			collectNeededVariables(this.originalExpression);
		}
		updateConsumedVariables();
	}

	/**
	 * Constructor for MAP operations with a produced variable name. Used when a
	 * variable declaration creates a new variable in the stream pipeline.
	 * 
	 * @param expression      the expression that produces the new variable (must
	 *                        not be null)
	 * @param operationType   the type of operation (should be MAP) (must not be
	 *                        null)
	 * @param producedVarName the name of the variable produced by this operation
	 */
	public ProspectiveOperation(Expression expression, OperationType operationType, String producedVarName) {
		assert expression != null : "expression cannot be null";
		assert operationType != null : "operationType cannot be null";

		this.originalExpression = expression;
		this.operationType = operationType;
		this.producedVariableName = producedVarName;
		collectNeededVariables(expression);
		updateConsumedVariables();
	}

	/**
	 * Constructor for REDUCE operations with accumulator variable and reducer type.
	 * Used when a reducer pattern (i++, sum += x, etc.) is detected.
	 * 
	 * @param statement          the statement containing the reducer (must not be
	 *                           null)
	 * @param accumulatorVarName the name of the accumulator variable (e.g., "i",
	 *                           "sum") (must not be null)
	 * @param reducerType        the type of reducer (INCREMENT, SUM, etc.) (must
	 *                           not be null)
	 */
	public ProspectiveOperation(org.eclipse.jdt.core.dom.Statement statement, String accumulatorVarName,
			ReducerType reducerType) {
		assert statement != null : "statement cannot be null";
		assert accumulatorVarName != null : "accumulatorVarName cannot be null";
		assert reducerType != null : "reducerType cannot be null";

		this.originalStatement = statement;
		this.operationType = OperationType.REDUCE;
		this.accumulatorVariableName = accumulatorVarName;
		this.reducerType = reducerType;
		if (statement instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
			this.originalExpression = ((org.eclipse.jdt.core.dom.ExpressionStatement) statement).getExpression();
			collectNeededVariables(this.originalExpression);
		}
		updateConsumedVariables();
	}

	/** (2) Gibt den Typ der Operation zurück */
	public OperationType getOperationType() {
		return this.operationType;
	}

	/** Returns the suitable stream method name for this operation type */
	public String getSuitableMethod() {
		switch (operationType) {
		case MAP:
			return "map";
		case FILTER:
			return "filter";
		case FOREACH:
			return "forEachOrdered";
		case REDUCE:
			return "reduce";
		case ANYMATCH:
			return "anyMatch";
		case NONEMATCH:
			return "noneMatch";
		case ALLMATCH:
			return "allMatch";
		default:
			return "unknown";
		}
	}

	/**
	 * Generate the lambda arguments for this operation Based on NetBeans
	 * ProspectiveOperation.getArguments()
	 * 
	 * @param ast       the AST to create nodes in (must not be null)
	 * @param paramName the parameter name to use for the lambda (may be null,
	 *                  defaults to "item")
	 * @return a list of expressions to use as arguments for the stream method
	 * @throws IllegalArgumentException if ast is null
	 */
	public List<Expression> getArguments(AST ast, String paramName) {
		if (ast == null) {
			throw new IllegalArgumentException("ast cannot be null");
		}

		List<Expression> args = new ArrayList<>();

		if (operationType == OperationType.REDUCE) {
			return getArgumentsForReducer(ast);
		}

		// Create lambda expression for MAP, FILTER, FOREACH, ANYMATCH, NONEMATCH
		LambdaExpression lambda = ast.newLambdaExpression();

		// Create parameter with defensive null check
		VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
		String effectiveParamName = (paramName != null && !paramName.isEmpty()) ? paramName : "item";
		param.setName(ast.newSimpleName(effectiveParamName));
		lambda.parameters().add(param);
		
		// For single parameter without type annotation, don't use parentheses
		lambda.setParentheses(false);

		// Create lambda body based on operation type
		if (operationType == OperationType.MAP && originalExpression != null) {
			// For MAP: lambda body is the expression
			lambda.setBody(ASTNode.copySubtree(ast, originalExpression));
		} else if (operationType == OperationType.MAP && originalStatement != null) {
			// For MAP with statement: create block with statement and return
			org.eclipse.jdt.core.dom.Block block = ast.newBlock();
			
			// Handle Block statements specially - copy statements from the block
			if (originalStatement instanceof org.eclipse.jdt.core.dom.Block) {
				org.eclipse.jdt.core.dom.Block originalBlock = (org.eclipse.jdt.core.dom.Block) originalStatement;
				for (Object stmt : originalBlock.statements()) {
					block.statements().add(ASTNode.copySubtree(ast, (Statement) stmt));
				}
			} else {
				block.statements().add(ASTNode.copySubtree(ast, originalStatement));
			}

			// Add return statement if we have a loop variable to return
			if (loopVariableName != null || paramName != null) {
				ReturnStatement returnStmt = ast.newReturnStatement();
				String varToReturn = (loopVariableName != null) ? loopVariableName : effectiveParamName;
				returnStmt.setExpression(ast.newSimpleName(varToReturn));
				block.statements().add(returnStmt);
			}

			lambda.setBody(block);
		} else if (operationType == OperationType.FILTER && originalExpression != null) {
			// For FILTER: wrap condition in parentheses only if needed
			// PrefixExpression with NOT already has proper precedence, no extra parens needed
			if (originalExpression instanceof PrefixExpression) {
				PrefixExpression prefix = (PrefixExpression) originalExpression;
				if (prefix.getOperator() == PrefixExpression.Operator.NOT) {
					// Negation already has proper precedence, use as-is
					lambda.setBody((Expression) ASTNode.copySubtree(ast, originalExpression));
				} else {
					// Other prefix operators might need parentheses
					ParenthesizedExpression parenExpr = ast.newParenthesizedExpression();
					parenExpr.setExpression((Expression) ASTNode.copySubtree(ast, originalExpression));
					lambda.setBody(parenExpr);
				}
			} else {
				// For other expressions, wrap in parentheses
				ParenthesizedExpression parenExpr = ast.newParenthesizedExpression();
				parenExpr.setExpression((Expression) ASTNode.copySubtree(ast, originalExpression));
				lambda.setBody(parenExpr);
			}
		} else if (operationType == OperationType.FOREACH && originalExpression != null 
				&& originalStatement instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
			// For FOREACH with a single expression (from ExpressionStatement):
			// Use the expression directly as lambda body (without block) for cleaner code
			// This produces: l -> System.out.println(l) instead of l -> { System.out.println(l); }
			lambda.setBody(ASTNode.copySubtree(ast, originalExpression));
		} else if (operationType == OperationType.FOREACH && originalStatement != null) {
			// For FOREACH with other statement types: lambda body is the statement (as block)
			if (originalStatement instanceof org.eclipse.jdt.core.dom.Block) {
				lambda.setBody(ASTNode.copySubtree(ast, originalStatement));
			} else {
				org.eclipse.jdt.core.dom.Block block = ast.newBlock();
				block.statements().add(ASTNode.copySubtree(ast, originalStatement));
				lambda.setBody(block);
			}
		} else if (originalExpression != null) {
			// Default: use expression as body
			lambda.setBody(ASTNode.copySubtree(ast, originalExpression));
		} else {
			// Defensive: neither originalExpression nor originalStatement is available
			throw new IllegalStateException(
					"Cannot create lambda: both originalExpression and originalStatement are null for operationType "
							+ operationType);
		}

		args.add(lambda);
		return args;
	}

	/**
	 * Returns the variable name produced by this operation (for MAP operations).
	 * This is used to track variable names through the stream pipeline.
	 */
	public String getProducedVariableName() {
		return producedVariableName;
	}

	/**
	 * Returns the accumulator variable name for REDUCE operations.
	 * 
	 * @return the accumulator variable name, or null if not a REDUCE operation
	 */
	public String getAccumulatorVariableName() {
		return accumulatorVariableName;
	}

	/**
	 * Returns the reducer type for REDUCE operations.
	 * 
	 * @return the reducer type, or null if not a REDUCE operation
	 */
	public ReducerType getReducerType() {
		return reducerType;
	}

	/**
	 * Sets whether this operation is null-safe.
	 * 
	 * @param isNullSafe true if the operation is null-safe
	 */
	public void setNullSafe(boolean isNullSafe) {
		this.isNullSafe = isNullSafe;
	}

	/**
	 * Sets the accumulator type for REDUCE operations.
	 * This is used to generate the correct method reference (e.g., Integer::sum vs Double::sum).
	 * 
	 * @param accumulatorType the type of the accumulator variable (e.g., "int", "double", "long")
	 */
	public void setAccumulatorType(String accumulatorType) {
		this.accumulatorType = accumulatorType;
	}

	/**
	 * Returns the set of variables consumed by this operation. This includes all
	 * SimpleName references in the operation's expression.
	 * 
	 * @return the set of consumed variable names
	 */
	public Set<String> getConsumedVariables() {
		return consumedVariables;
	}

	/**
	 * Updates the consumed variables set by collecting all SimpleName references.
	 * This is called during operation construction to track variable usage.
	 */
	private void updateConsumedVariables() {
		consumedVariables.addAll(neededVariables);
	}

	/** (5) Ermittelt die Argumente für `reduce()` */
	private List<Expression> getArgumentsForReducer(AST ast) {
		List<Expression> arguments = new ArrayList<>();
		if (operationType == OperationType.REDUCE) {
			// First argument: identity element (the accumulator variable reference)
			if (accumulatorVariableName != null) {
				arguments.add(ast.newSimpleName(accumulatorVariableName));
			} else {
				// Fallback to default identity
				Expression identity = getIdentityElement(ast);
				if (identity != null)
					arguments.add(identity);
			}

			// Second argument: accumulator function (method reference or lambda)
			Expression accumulator = createAccumulatorExpression(ast);
			if (accumulator != null)
				arguments.add(accumulator);
		}
		return arguments;
	}

	/**
	 * Creates the accumulator expression for REDUCE operations. Returns a method
	 * reference (e.g., Integer::sum, Long::sum, Double::sum) when possible, or a lambda otherwise.
	 * The method reference type is determined by the accumulator variable type.
	 */
	private Expression createAccumulatorExpression(AST ast) {
		if (reducerType == null) {
			// Fallback to legacy behavior
			return createAccumulatorLambda(ast);
		}

		switch (reducerType) {
		case INCREMENT:
		case SUM:
			// Use appropriate method reference based on accumulator type
			// Only Integer, Long, and Double have ::sum method references in Java standard library
			if (accumulatorType != null) {
				// Handle primitive types
				if ("double".equals(accumulatorType) || "java.lang.Double".equals(accumulatorType)) {
					// For double, check if INCREMENT (use lambda) or SUM (can use Double::sum)
					if (reducerType == ReducerType.INCREMENT) {
						// For double++, use lambda: (accumulator, _item) -> accumulator + 1
						return createCountingLambda(ast, InfixExpression.Operator.PLUS);
					} else {
						// For double += x, can use Double::sum if x is double-compatible
						return createMethodReference(ast, "Double", "sum");
					}
				} else if ("float".equals(accumulatorType) || "java.lang.Float".equals(accumulatorType)) {
					// Float doesn't have ::sum, always use lambda
					if (reducerType == ReducerType.INCREMENT) {
						return createCountingLambda(ast, InfixExpression.Operator.PLUS);
					} else {
						return createBinaryOperatorLambda(ast, InfixExpression.Operator.PLUS);
					}
				} else if ("long".equals(accumulatorType) || "java.lang.Long".equals(accumulatorType)) {
					// Long::sum is available
					return createMethodReference(ast, "Long", "sum");
				} else if ("short".equals(accumulatorType) || "java.lang.Short".equals(accumulatorType)) {
					// Short doesn't have ::sum, use lambda
					if (reducerType == ReducerType.INCREMENT) {
						return createCountingLambda(ast, InfixExpression.Operator.PLUS);
					} else {
						return createBinaryOperatorLambda(ast, InfixExpression.Operator.PLUS);
					}
				} else if ("byte".equals(accumulatorType) || "java.lang.Byte".equals(accumulatorType)) {
					// Byte doesn't have ::sum, use lambda
					if (reducerType == ReducerType.INCREMENT) {
						return createCountingLambda(ast, InfixExpression.Operator.PLUS);
					} else {
						return createBinaryOperatorLambda(ast, InfixExpression.Operator.PLUS);
					}
				}
			}
			// Default to Integer::sum for int or unknown types
			return createMethodReference(ast, "Integer", "sum");
		case DECREMENT:
			// For i--, we need a different approach since we subtract
			// We can't use a simple method reference for subtraction
			return createCountingLambda(ast, InfixExpression.Operator.MINUS);
		case PRODUCT:
			// Use (accumulator, _item) -> accumulator * _item lambda
			return createBinaryOperatorLambda(ast, InfixExpression.Operator.TIMES);
		case STRING_CONCAT:
			// Use String::concat method reference when null-safe (variables have @NotNull),
			// otherwise use (a, b) -> a + b simple lambda for null-safe concatenation
			if (isNullSafe) {
				return createMethodReference(ast, "String", "concat");
			} else {
				return createSimpleBinaryLambda(ast, InfixExpression.Operator.PLUS);
			}
		case MAX:
			// Use Math::max method reference for max accumulation
			// For primitive types, use wrapper class method references (Integer::max, Double::max, etc.)
			return createMaxMinMethodReference(ast, "max");
		case MIN:
			// Use Math::min method reference for min accumulation
			// For primitive types, use wrapper class method references (Integer::min, Double::min, etc.)
			return createMaxMinMethodReference(ast, "min");
		case CUSTOM_AGGREGATE:
			// For custom aggregation, use a generic accumulator lambda
			return createAccumulatorLambda(ast);
		default:
			return createAccumulatorLambda(ast);
		}
	}

	/**
	 * Creates a method reference like Integer::sum.
	 */
	private TypeMethodReference createMethodReference(AST ast, String typeName, String methodName) {
		TypeMethodReference methodRef = ast.newTypeMethodReference();
		methodRef.setType(ast.newSimpleType(ast.newSimpleName(typeName)));
		methodRef.setName(ast.newSimpleName(methodName));
		return methodRef;
	}

	/**
	 * Creates a method reference like Math::max or Math::min.
	 * 
	 * @param ast        the AST to create nodes in
	 * @param methodName the method name ("max" or "min")
	 * @return a TypeMethodReference for Math::methodName
	 */
	private TypeMethodReference createMathMethodReference(AST ast, String methodName) {
		TypeMethodReference methodRef = ast.newTypeMethodReference();
		methodRef.setType(ast.newSimpleType(ast.newSimpleName("Math")));
		methodRef.setName(ast.newSimpleName(methodName));
		return methodRef;
	}

	/**
	 * Creates a method reference for max/min operations based on the accumulator type.
	 * Uses {@code Integer::max}, {@code Double::max}, {@code Long::max}, etc. instead of
	 * {@code Math::max} to avoid overload ambiguity in {@code reduce()} operations.
	 * 
	 * <p>
	 * The specific wrapper type is derived from the accumulator variable's type:
	 * {@code int} → {@code Integer}, {@code long} → {@code Long}, {@code double} → {@code Double},
	 * {@code float} → {@code Float}, {@code short} → {@code Short}, {@code byte} → {@code Byte}.
	 * For fully qualified {@code java.lang.*} wrapper types or simple wrapper class names,
	 * the simple class name is used. For unknown or missing types, {@code Integer} is used
	 * as a sensible default.
	 * </p>
	 * 
	 * @param ast        the AST to create nodes in
	 * @param methodName the method name ("max" or "min")
	 * @return a TypeMethodReference for the appropriate wrapper type's max/min method
	 */
	private TypeMethodReference createMaxMinMethodReference(AST ast, String methodName) {
		String typeName;
		
		if (accumulatorType != null) {
			// Map primitive types and wrapper classes to their wrapper class names
			switch (accumulatorType) {
				case "int":
				case "Integer":
					typeName = "Integer";
					break;
				case "long":
				case "Long":
					typeName = "Long";
					break;
				case "double":
				case "Double":
					typeName = "Double";
					break;
				case "float":
				case "Float":
					typeName = "Float";
					break;
				case "short":
				case "Short":
					typeName = "Short";
					break;
				case "byte":
				case "Byte":
					typeName = "Byte";
					break;
				default:
					// For fully qualified wrapper types (java.lang.Integer, etc.), extract simple name
					if (accumulatorType.startsWith("java.lang.")) {
						typeName = accumulatorType.substring("java.lang.".length());
					} else {
						// Unknown type - default to Integer as a sensible fallback
						// This should rarely happen in practice as getVariableType() usually returns valid types
						typeName = "Integer";
					}
			}
		} else {
			// Type is null - default to Integer as a sensible fallback
			typeName = "Integer";
		}
		
		TypeMethodReference methodRef = ast.newTypeMethodReference();
		methodRef.setType(ast.newSimpleType(ast.newSimpleName(typeName)));
		methodRef.setName(ast.newSimpleName(methodName));
		return methodRef;
	}

	/**
	 * Creates a simple binary operator lambda like (a, b) -> a + b without type annotations.
	 * Used for simple operations like string concatenation where type inference works well.
	 * 
	 * @param ast      the AST to create nodes in
	 * @param operator the infix operator to use (e.g., PLUS for +)
	 * @return a LambdaExpression with simple parameters
	 */
	private LambdaExpression createSimpleBinaryLambda(AST ast, InfixExpression.Operator operator) {
		LambdaExpression lambda = ast.newLambdaExpression();

		// Parameters: (a, b) - using VariableDeclarationFragment for simple parameters
		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName("a"));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName("b"));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		// Body: a + b (or other operator)
		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName("a"));
		operationExpr.setRightOperand(ast.newSimpleName("b"));
		operationExpr.setOperator(operator);
		lambda.setBody(operationExpr);

		return lambda;
	}

	/**
	 * Creates a binary operator lambda like (accumulator, _item) -> accumulator +
	 * _item.
	 */
	private LambdaExpression createBinaryOperatorLambda(AST ast, InfixExpression.Operator operator) {
		LambdaExpression lambda = ast.newLambdaExpression();

		// Parameters: (accumulator, _item) - use VariableDeclarationFragment to avoid type annotations
		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName("accumulator"));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName("_item"));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		// Body: accumulator + _item (or other operator)
		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName("accumulator"));
		operationExpr.setRightOperand(ast.newSimpleName("_item"));
		operationExpr.setOperator(operator);
		lambda.setBody(operationExpr);

		return lambda;
	}

	/**
	 * Creates a lambda like (accumulator, _item) -> accumulator - _item. Used only
	 * for DECREMENT (i--, i -= 1) operations.
	 */
	private LambdaExpression createCountingLambda(AST ast, InfixExpression.Operator operator) {
		LambdaExpression lambda = ast.newLambdaExpression();

		// Parameters: (accumulator, _item) - use VariableDeclarationFragment to avoid type annotations
		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName("accumulator"));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName("_item"));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		// Body: accumulator + 1 (literal 1, not _item)
		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName("accumulator"));
		operationExpr.setRightOperand(ast.newNumberLiteral("1"));
		operationExpr.setOperator(operator);
		lambda.setBody(operationExpr);

		return lambda;
	}

	/** (6) Ermittelt das Identitätselement (`0`, `1`) für `reduce()` */
	private Expression getIdentityElement(AST ast) {
		if (operationType == OperationType.REDUCE && originalExpression instanceof Assignment) {
			Assignment assignment = (Assignment) originalExpression;
			if (assignment.getOperator() == Assignment.Operator.PLUS_ASSIGN) {
				return ast.newNumberLiteral("0");
			} else if (assignment.getOperator() == Assignment.Operator.TIMES_ASSIGN) {
				return ast.newNumberLiteral("1");
			}
		}
		return null;
	}

	/** (7) Erstellt eine Akkumulator-Funktion für `reduce()` */
	private LambdaExpression createAccumulatorLambda(AST ast) {
		LambdaExpression lambda = ast.newLambdaExpression();
		// Use VariableDeclarationFragment to avoid type annotations
		VariableDeclarationFragment paramA = ast.newVariableDeclarationFragment();
		paramA.setName(ast.newSimpleName("a"));
		VariableDeclarationFragment paramB = ast.newVariableDeclarationFragment();
		paramB.setName(ast.newSimpleName("b"));
		lambda.parameters().add(paramA);
		lambda.parameters().add(paramB);

		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName("a"));
		operationExpr.setRightOperand(ast.newSimpleName("b"));
		operationExpr.setOperator(InfixExpression.Operator.PLUS);
		lambda.setBody(operationExpr);

		return lambda;
	}

	@Override
	public String toString() {
		return "ProspectiveOperation{" + "expression=" + originalExpression + ", operationType=" + operationType
				+ ", neededVariables=" + neededVariables + '}';
	}

	public enum OperationType {
		MAP, FOREACH, FILTER, REDUCE, ANYMATCH, NONEMATCH, ALLMATCH
	}

	public enum ReducerType {
		INCREMENT, // i++, ++i
		DECREMENT, // i--, --i, i -= 1
		SUM, // sum += x
		PRODUCT, // product *= x
		STRING_CONCAT, // s += string
		MAX, // max = Math.max(max, x)
		MIN, // min = Math.min(min, x)
		CUSTOM_AGGREGATE // Custom aggregation patterns
	}
}