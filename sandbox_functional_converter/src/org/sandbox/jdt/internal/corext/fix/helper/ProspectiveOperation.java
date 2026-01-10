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
import java.util.Collection;
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
 * This class encapsulates a single operation in a stream pipeline being
 * constructed from an enhanced for-loop. Each operation corresponds to a
 * stream method (filter, map, forEach, reduce, etc.) and maintains information
 * about the expression to transform, variables consumed/produced, and any
 * special handling required.
 * </p>
 * 
 * <p><b>Operation Types:</b></p>
 * <ul>
 * <li><b>FILTER</b>: Conditional filtering ({@code .filter(predicate)})</li>
 * <li><b>MAP</b>: Transformation ({@code .map(function)})</li>
 * <li><b>FOREACH</b>: Terminal action ({@code .forEach(consumer)})</li>
 * <li><b>REDUCE</b>: Aggregation ({@code .reduce(identity, accumulator)})</li>
 * <li><b>ANYMATCH</b>: Short-circuit match ({@code .anyMatch(predicate)})</li>
 * <li><b>NONEMATCH</b>: Short-circuit non-match ({@code .noneMatch(predicate)})</li>
 * <li><b>ALLMATCH</b>: Short-circuit all-match ({@code .allMatch(predicate)})</li>
 * </ul>
 * 
 * <p><b>Variable Tracking:</b></p>
 * <p>
 * The class tracks three types of variables:
 * <ul>
 * <li><b>Consumed variables</b>: Variables read by this operation</li>
 * <li><b>Produced variable</b>: Variable created by MAP operations (e.g., {@code int x = ...})</li>
 * <li><b>Accumulator variable</b>: Variable modified by REDUCE operations (e.g., {@code sum += ...})</li>
 * </ul>
 * This tracking enables proper scoping and validation in the stream pipeline.
 * </p>
 * 
 * <p><b>Reducer Patterns:</b></p>
 * <p>
 * For REDUCE operations, this class supports various reducer types:
 * <ul>
 * <li>INCREMENT/DECREMENT: {@code i++}, {@code i--}</li>
 * <li>SUM: {@code sum += x} → {@code .reduce(sum, Integer::sum)}</li>
 * <li>PRODUCT: {@code product *= x} → {@code .reduce(product, (a,b) -> a*b)}</li>
 * <li>MAX/MIN: {@code max = Math.max(max, x)} → {@code .reduce(max, Integer::max)}</li>
 * <li>STRING_CONCAT: {@code str += s} → {@code .reduce(str, String::concat)} (when null-safe)</li>
 * </ul>
 * </p>
 * 
 * <p><b>Lambda Generation:</b></p>
 * <p>
 * The {@link #getArguments(AST, String)} method generates lambda expressions
 * or method references appropriate for each operation type. It handles:
 * <ul>
 * <li>Parameter naming based on variable tracking</li>
 * <li>Identity element generation for reducers</li>
 * <li>Method reference optimization (e.g., Integer::sum vs explicit lambda)</li>
 * <li>Expression copying and AST node creation</li>
 * </ul>
 * </p>
 * 
 * <p><b>Thread Safety:</b> This class is not thread-safe.</p>
 * 
 * <p>
 * This class is final to prevent subclassing and potential finalizer attacks,
 * since constructors call analysis methods that could potentially throw
 * exceptions.
 * </p>
 * 
 * @see StreamPipelineBuilder
 * @see OperationType
 * @see ReducerType
 * @see StreamConstants
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

	/**
	 * Collection of variable names already in use in the scope. Used to generate
	 * unique lambda parameter names that don't clash with existing variables.
	 */
	private Collection<String> usedVariableNames = new HashSet<>();

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

	/** 
	 * Returns the suitable stream method name for this operation type.
	 * 
	 * @return the method name (e.g., "map", "filter", "forEach")
	 * @see StreamConstants
	 */
	public String getSuitableMethod() {
		switch (operationType) {
		case MAP:
			return StreamConstants.MAP_METHOD;
		case FILTER:
			return StreamConstants.FILTER_METHOD;
		case FOREACH:
			return StreamConstants.FOR_EACH_ORDERED_METHOD;
		case REDUCE:
			return StreamConstants.REDUCE_METHOD;
		case ANYMATCH:
			return StreamConstants.ANY_MATCH_METHOD;
		case NONEMATCH:
			return StreamConstants.NONE_MATCH_METHOD;
		case ALLMATCH:
			return StreamConstants.ALL_MATCH_METHOD;
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
		// Use the provided paramName, or generate a unique default name
		VariableDeclarationFragment param = ast.newVariableDeclarationFragment();
		String effectiveParamName;
		if (paramName != null && !paramName.isEmpty()) {
			effectiveParamName = paramName;
		} else {
			// Generate a unique default name to avoid clashes
			effectiveParamName = generateUniqueVariableName("item");
		}
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
	 * Sets the collection of variable names already in use in the current scope.
	 * This is used to generate unique lambda parameter names that don't clash
	 * with existing variables.
	 * 
	 * @param usedNames the collection of variable names in use (may be null)
	 */
	public void setUsedVariableNames(Collection<String> usedNames) {
		if (usedNames != null) {
			this.usedVariableNames = usedNames;
		}
	}

	/**
	 * Generates a unique variable name that doesn't collide with existing variables in scope.
	 * 
	 * <p>This method ensures the generated lambda parameter name doesn't conflict with other
	 * variables visible at the transformation point. If the base name is already in use,
	 * a numeric suffix is appended (e.g., "a2", "a3", etc.).</p>
	 * 
	 * @param baseName the base name to use (e.g., "a", "_item", "accumulator")
	 * @return a unique variable name that doesn't exist in the current scope
	 */
	private String generateUniqueVariableName(String baseName) {
		// Combine neededVariables (from expression analysis) with usedVariableNames (from scope)
		Set<String> allUsedNames = new HashSet<>(neededVariables);
		allUsedNames.addAll(usedVariableNames);
		
		// If base name is not used, return it
		if (!allUsedNames.contains(baseName)) {
			return baseName;
		}
		
		// Otherwise, append a number until we find an unused name
		int counter = 2;
		String candidate = baseName + counter;
		while (allUsedNames.contains(candidate)) {
			counter++;
			candidate = baseName + counter;
		}
		return candidate;
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
			// Use wrapper class method references (Integer::max, Double::max, etc.) to avoid overload ambiguity
			return createMaxMinMethodReference(ast, "max");
		case MIN:
			// Use wrapper class method references (Integer::min, Double::min, etc.) to avoid overload ambiguity
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

		// Generate unique parameter names to avoid clashes with existing variables
		String param1Name = generateUniqueVariableName("a");
		String param2Name = generateUniqueVariableName("b");

		// Parameters: (a, b) - using VariableDeclarationFragment for simple parameters
		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName(param1Name));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName(param2Name));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		// Body: a + b (or other operator)
		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName(param1Name));
		operationExpr.setRightOperand(ast.newSimpleName(param2Name));
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

		// Generate unique parameter names to avoid clashes with existing variables
		String param1Name = generateUniqueVariableName("accumulator");
		String param2Name = generateUniqueVariableName("_item");

		// Parameters: (accumulator, _item) - use VariableDeclarationFragment to avoid type annotations
		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName(param1Name));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName(param2Name));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		// Body: accumulator + _item (or other operator)
		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName(param1Name));
		operationExpr.setRightOperand(ast.newSimpleName(param2Name));
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

		// Generate unique parameter name for accumulator to avoid clashes
		// Note: _item is unused in body but still needs unique name to avoid shadowing
		String param1Name = generateUniqueVariableName("accumulator");
		String param2Name = generateUniqueVariableName("_item");

		// Parameters: (accumulator, _item) - use VariableDeclarationFragment to avoid type annotations
		VariableDeclarationFragment param1 = ast.newVariableDeclarationFragment();
		param1.setName(ast.newSimpleName(param1Name));
		VariableDeclarationFragment param2 = ast.newVariableDeclarationFragment();
		param2.setName(ast.newSimpleName(param2Name));
		lambda.parameters().add(param1);
		lambda.parameters().add(param2);

		// Body: accumulator + 1 (literal 1, not _item)
		InfixExpression operationExpr = ast.newInfixExpression();
		operationExpr.setLeftOperand(ast.newSimpleName(param1Name));
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

		// Generate unique parameter names to avoid clashes with existing variables
		String param1Name = generateUniqueVariableName("a");
		String param2Name = generateUniqueVariableName("b");

		// Use VariableDeclarationFragment to avoid type annotations
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

	@Override
	public String toString() {
		return "ProspectiveOperation{" + "expression=" + originalExpression + ", operationType=" + operationType
				+ ", neededVariables=" + neededVariables + '}';
	}

	/**
	 * Types of stream operations that can be extracted from loop bodies.
	 * 
	 * <p>Each operation type corresponds to a specific stream method:
	 * <ul>
	 * <li><b>MAP</b>: Transforms elements ({@code .map(x -> f(x))}). 
	 *     Example: {@code String s = item.toString();} → {@code .map(item -> item.toString())}</li>
	 * <li><b>FILTER</b>: Selects elements based on a predicate ({@code .filter(x -> condition)}). 
	 *     Example: {@code if (item != null)} → {@code .filter(item -> item != null)}</li>
	 * <li><b>FOREACH</b>: Terminal operation performing an action on each element ({@code .forEachOrdered(x -> action(x))}). 
	 *     Example: {@code System.out.println(item);} → {@code .forEachOrdered(item -> System.out.println(item))}</li>
	 * <li><b>REDUCE</b>: Terminal accumulation operation ({@code .reduce(identity, accumulator)}). 
	 *     Example: {@code sum += item;} → {@code .reduce(sum, Integer::sum)}</li>
	 * <li><b>ANYMATCH</b>: Terminal predicate returning true if any element matches ({@code .anyMatch(x -> condition)}). 
	 *     Example: {@code if (condition) return true;} → {@code if (stream.anyMatch(x -> condition)) return true;}</li>
	 * <li><b>NONEMATCH</b>: Terminal predicate returning true if no elements match ({@code .noneMatch(x -> condition)}). 
	 *     Example: {@code if (condition) return false;} → {@code if (!stream.noneMatch(x -> condition)) return false;}</li>
	 * <li><b>ALLMATCH</b>: Terminal predicate returning true if all elements match ({@code .allMatch(x -> condition)}). 
	 *     Example: {@code if (!condition) return false;} → {@code if (!stream.allMatch(x -> condition)) return false;}</li>
	 * </ul>
	 * 
	 * @see #getSuitableMethod()
	 */
	public enum OperationType {
		MAP, FOREACH, FILTER, REDUCE, ANYMATCH, NONEMATCH, ALLMATCH
	}

	/**
	 * Types of reduction operations supported for REDUCE operations.
	 * 
	 * <p>Each reducer type represents a specific accumulation pattern:
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
	 * @see ProspectiveOperation#getArgumentsForReducer(AST)
	 */
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