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
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Types of stream operations that can be extracted from loop bodies.
 * 
 * <p>Each operation type corresponds to a specific stream method and knows how to
 * create its lambda body expression.</p>
 * 
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
 * @see ProspectiveOperation
 * @see StreamConstants
 */
public enum OperationType {
	
	/**
	 * Transforms elements using a mapping function.
	 * Stream method: {@code .map(x -> f(x))}
	 */
	MAP(StreamConstants.MAP_METHOD, false, false) {
		@Override
		public ASTNode createLambdaBody(AST ast, LambdaBodyContext context) {
			// Check for side-effect MAP operations FIRST (originalStatement != null with loopVariableName)
			// These need a block body with return statement
			if (context.originalStatement() != null && context.loopVariableName() != null) {
				// For MAP with side-effect statement: create block with statement and return
				Block block = ast.newBlock();
				
				// Handle Block statements specially - copy statements from the block
				if (context.originalStatement() instanceof Block) {
					Block originalBlock = (Block) context.originalStatement();
					for (Object stmt : originalBlock.statements()) {
						block.statements().add(ASTNode.copySubtree(ast, (Statement) stmt));
					}
				} else {
					block.statements().add(ASTNode.copySubtree(ast, context.originalStatement()));
				}

				// Add return statement to return the current pipeline variable
				ReturnStatement returnStmt = ast.newReturnStatement();
				returnStmt.setExpression(ast.newSimpleName(context.loopVariableName()));
				block.statements().add(returnStmt);

				return block;
			} else if (context.originalExpression() != null) {
				// For MAP with expression only: lambda body is just the expression
				return ASTNode.copySubtree(ast, context.originalExpression());
			}
			return null;
		}
	},
	
	/**
	 * Terminal operation performing an action on each element.
	 * Stream method: {@code .forEachOrdered(x -> action(x))}
	 */
	FOREACH(StreamConstants.FOR_EACH_ORDERED_METHOD, false, true) {
		@Override
		public ASTNode createLambdaBody(AST ast, LambdaBodyContext context) {
			if (context.originalExpression() != null 
					&& context.originalStatement() instanceof ExpressionStatement) {
				// For FOREACH with a single expression (from ExpressionStatement):
				// Use the expression directly as lambda body (without block) for cleaner code
				return ASTNode.copySubtree(ast, context.originalExpression());
			} else if (context.originalStatement() != null) {
				// For FOREACH with other statement types: lambda body is the statement (as block)
				if (context.originalStatement() instanceof Block) {
					return ASTNode.copySubtree(ast, context.originalStatement());
				} else {
					Block block = ast.newBlock();
					block.statements().add(ASTNode.copySubtree(ast, context.originalStatement()));
					return block;
				}
			}
			return null;
		}
	},
	
	/**
	 * Selects elements based on a predicate.
	 * Stream method: {@code .filter(x -> condition)}
	 */
	FILTER(StreamConstants.FILTER_METHOD, true, false) {
		@Override
		public ASTNode createLambdaBody(AST ast, LambdaBodyContext context) {
			if (context.originalExpression() != null) {
				return createPredicateLambdaBody(ast, context.originalExpression());
			}
			return null;
		}
	},
	
	/**
	 * Terminal accumulation operation.
	 * Stream method: {@code .reduce(identity, accumulator)}
	 */
	REDUCE(StreamConstants.REDUCE_METHOD, false, true) {
		@Override
		public ASTNode createLambdaBody(AST ast, LambdaBodyContext context) {
			// REDUCE has special handling via getArgumentsForReducer()
			// This method is not used for REDUCE operations
			return null;
		}
		
		@Override
		public boolean hasSpecialArgumentHandling() {
			return true;
		}
	},
	
	/**
	 * Terminal predicate returning true if any element matches.
	 * Stream method: {@code .anyMatch(x -> condition)}
	 */
	ANYMATCH(StreamConstants.ANY_MATCH_METHOD, true, true) {
		@Override
		public ASTNode createLambdaBody(AST ast, LambdaBodyContext context) {
			if (context.originalExpression() != null) {
				return createPredicateLambdaBody(ast, context.originalExpression());
			}
			return null;
		}
	},
	
	/**
	 * Terminal predicate returning true if no elements match.
	 * Stream method: {@code .noneMatch(x -> condition)}
	 */
	NONEMATCH(StreamConstants.NONE_MATCH_METHOD, true, true) {
		@Override
		public ASTNode createLambdaBody(AST ast, LambdaBodyContext context) {
			if (context.originalExpression() != null) {
				return createPredicateLambdaBody(ast, context.originalExpression());
			}
			return null;
		}
	},
	
	/**
	 * Terminal predicate returning true if all elements match.
	 * Stream method: {@code .allMatch(x -> condition)}
	 */
	ALLMATCH(StreamConstants.ALL_MATCH_METHOD, true, true) {
		@Override
		public ASTNode createLambdaBody(AST ast, LambdaBodyContext context) {
			if (context.originalExpression() != null) {
				return createPredicateLambdaBody(ast, context.originalExpression());
			}
			return null;
		}
	};

	private final String methodName;
	private final boolean predicate;
	private final boolean terminal;

	OperationType(String methodName, boolean isPredicate, boolean isTerminal) {
		this.methodName = methodName;
		this.predicate = isPredicate;
		this.terminal = isTerminal;
	}

	/**
	 * Returns the stream method name for this operation type.
	 * 
	 * @return the method name (e.g., "map", "filter", "forEachOrdered", "reduce")
	 */
	public String getMethodName() {
		return methodName;
	}
	
	/**
	 * Creates the lambda body for this operation type.
	 * 
	 * @param ast the AST to create nodes in
	 * @param context the context containing expression, statement, and variable information
	 * @return the lambda body (Expression or Block), or null if cannot be created
	 */
	public abstract ASTNode createLambdaBody(AST ast, LambdaBodyContext context);
	
	/**
	 * Returns whether this operation type has special argument handling.
	 * If true, the operation uses a custom method (like getArgumentsForReducer) instead of createLambdaBody.
	 * 
	 * @return true if special handling is required
	 */
	public boolean hasSpecialArgumentHandling() {
		return false;
	}
	
	/**
	 * Returns whether this operation type uses a predicate (boolean condition).
	 * 
	 * @return true if this is a predicate-based operation (FILTER, ANYMATCH, NONEMATCH, ALLMATCH)
	 */
	public boolean isPredicate() {
		return predicate;
	}
	
	/**
	 * Returns whether this operation type is a terminal operation.
	 * 
	 * @return true if this is a terminal operation (FOREACH, REDUCE, ANYMATCH, NONEMATCH, ALLMATCH)
	 */
	public boolean isTerminal() {
		return terminal;
	}
	
	// ==================== Helper Methods ====================
	
	/**
	 * Creates a lambda body for predicate expressions.
	 * Wraps the expression in parentheses only for InfixExpressions.
	 */
	protected static Expression createPredicateLambdaBody(AST ast, Expression expression) {
		LambdaGenerator generator = new LambdaGenerator(ast);
		return generator.createPredicateLambdaBody(expression);
	}
	
	/**
	 * Context record containing the information needed to create a lambda body.
	 * 
	 * @param originalExpression the original expression being transformed (may be null)
	 * @param originalStatement the original statement being transformed (may be null)
	 * @param loopVariableName the loop variable name for side-effect operations (may be null)
	 */
	public record LambdaBodyContext(
			Expression originalExpression,
			Statement originalStatement,
			String loopVariableName
	) {
		/**
		 * Creates a context with only an expression.
		 */
		public static LambdaBodyContext ofExpression(Expression expression) {
			return new LambdaBodyContext(expression, null, null);
		}
		
		/**
		 * Creates a context with a statement and loop variable name.
		 */
		public static LambdaBodyContext ofStatement(Statement statement, String loopVarName) {
			Expression expr = null;
			if (statement instanceof ExpressionStatement) {
				expr = ((ExpressionStatement) statement).getExpression();
			}
			return new LambdaBodyContext(expr, statement, loopVarName);
		}
	}
}
