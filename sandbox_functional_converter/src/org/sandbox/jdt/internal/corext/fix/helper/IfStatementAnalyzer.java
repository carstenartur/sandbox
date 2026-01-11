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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;

/**
 * Analyzes IF statements to detect patterns that can be converted to stream operations.
 * 
 * <p>This class identifies various IF statement patterns:</p>
 * <ul>
 * <li><b>Early Return Patterns</b>: anyMatch, noneMatch, allMatch</li>
 * <li><b>Continue Patterns</b>: Can be converted to negated filters</li>
 * <li><b>Break/Labeled Continue</b>: Cannot be converted (rejects conversion)</li>
 * </ul>
 * 
 * <p><b>Pattern Examples:</b></p>
 * <pre>{@code
 * // anyMatch: if (condition) return true; ... return false;
 * // noneMatch: if (condition) return false; ... return true;
 * // allMatch: if (!condition) return false; ... return true;
 * // filter: if (condition) continue; → .filter(x -> !condition)
 * }</pre>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * IfStatementAnalyzer analyzer = new IfStatementAnalyzer(forLoop);
 * if (analyzer.isEarlyReturnIf(ifStmt)) {
 *     MatchPatternType pattern = analyzer.getMatchPatternType();
 *     // Create appropriate stream operation
 * }
 * }</pre>
 * 
 * @see StreamPipelineBuilder
 * @see OperationType
 */
public final class IfStatementAnalyzer {

	/**
	 * Represents the type of match pattern detected in an IF statement.
	 */
	public enum MatchPatternType {
		/** {@code if (condition) return true;} followed by {@code return false;} */
		ANY_MATCH,
		/** {@code if (condition) return false;} followed by {@code return true;} */
		NONE_MATCH,
		/** {@code if (!condition) return false;} followed by {@code return true;} */
		ALL_MATCH,
		/** No match pattern detected */
		NONE
	}

	private final EnhancedForStatement forLoop;
	private MatchPatternType detectedPattern = MatchPatternType.NONE;

	/**
	 * Creates a new IfStatementAnalyzer for the given for-loop context.
	 * 
	 * @param forLoop the enhanced for-loop being analyzed
	 * @throws IllegalArgumentException if forLoop is null
	 */
	public IfStatementAnalyzer(EnhancedForStatement forLoop) {
		if (forLoop == null) {
			throw new IllegalArgumentException("forLoop cannot be null");
		}
		this.forLoop = forLoop;
	}

	/**
	 * Returns the match pattern type detected by the last call to
	 * {@link #isEarlyReturnIf(IfStatement)}.
	 * 
	 * @return the detected pattern type, or {@link MatchPatternType#NONE} if none detected
	 */
	public MatchPatternType getDetectedPattern() {
		return detectedPattern;
	}

	/**
	 * Checks if an IF statement contains an unlabeled continue statement.
	 * This pattern can be converted to a negated filter.
	 * 
	 * <p>Pattern: {@code if (condition) continue;} → {@code .filter(x -> !condition)}</p>
	 * 
	 * @param ifStatement the IF statement to check
	 * @return true if the then branch contains an unlabeled continue statement
	 */
	public boolean isIfWithContinue(IfStatement ifStatement) {
		if (ifStatement == null) {
			return false;
		}
		Statement thenStatement = ifStatement.getThenStatement();
		if (thenStatement instanceof ContinueStatement) {
			ContinueStatement continueStmt = (ContinueStatement) thenStatement;
			return continueStmt.getLabel() == null;
		}
		if (thenStatement instanceof Block) {
			Block block = (Block) thenStatement;
			if (block.statements().size() == 1 && block.statements().get(0) instanceof ContinueStatement) {
				ContinueStatement continueStmt = (ContinueStatement) block.statements().get(0);
				return continueStmt.getLabel() == null;
			}
		}
		return false;
	}

	/**
	 * Checks if the IF statement contains a labeled continue statement.
	 * Labeled continues cannot be converted to stream operations.
	 * 
	 * @param ifStatement the IF statement to check
	 * @return true if the IF contains a labeled continue statement
	 */
	public boolean isIfWithLabeledContinue(IfStatement ifStatement) {
		if (ifStatement == null) {
			return false;
		}
		Statement thenStatement = ifStatement.getThenStatement();
		if (thenStatement instanceof ContinueStatement) {
			ContinueStatement continueStmt = (ContinueStatement) thenStatement;
			return continueStmt.getLabel() != null;
		}
		if (thenStatement instanceof Block) {
			Block block = (Block) thenStatement;
			if (block.statements().size() == 1 && block.statements().get(0) instanceof ContinueStatement) {
				ContinueStatement continueStmt = (ContinueStatement) block.statements().get(0);
				return continueStmt.getLabel() != null;
			}
		}
		return false;
	}

	/**
	 * Checks if the IF statement contains a break statement.
	 * Break statements cannot be converted to stream operations.
	 * 
	 * @param ifStatement the IF statement to check
	 * @return true if the IF contains a break statement
	 */
	public boolean isIfWithBreak(IfStatement ifStatement) {
		if (ifStatement == null) {
			return false;
		}
		Statement thenStatement = ifStatement.getThenStatement();
		if (thenStatement instanceof BreakStatement) {
			return true;
		}
		if (thenStatement instanceof Block) {
			Block block = (Block) thenStatement;
			if (block.statements().size() == 1 && block.statements().get(0) instanceof BreakStatement) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the IF statement contains a return statement that cannot be converted.
	 * Returns that are not boolean literals (like return null, return someValue) 
	 * cannot be converted to stream operations because they would lose the return semantics.
	 * 
	 * @param ifStatement the IF statement to check
	 * @return true if the IF contains a non-convertible return statement
	 */
	public boolean isIfWithUnconvertibleReturn(IfStatement ifStatement) {
		if (ifStatement == null) {
			return false;
		}
		Statement thenStatement = ifStatement.getThenStatement();
		ReturnStatement returnStmt = extractReturnStatement(thenStatement);
		
		if (returnStmt == null) {
			return false;
		}
		
		// Check if it's a boolean literal - those can be converted to anyMatch/noneMatch/allMatch
		Expression returnExpr = returnStmt.getExpression();
		if (returnExpr instanceof BooleanLiteral) {
			// This could be part of a match pattern - let other methods handle it
			return false;
		}
		
		// Any other return (null, variable, method call, etc.) cannot be converted
		return true;
	}

	/**
	 * Checks if the IF statement body contains an assignment to an external variable.
	 * Such assignments cannot be safely converted to stream operations because
	 * they would either be lost or would break stream semantics.
	 * 
	 * <p><b>Note:</b> Compound assignments (+=, -=, *=, etc.) are allowed because they
	 * represent accumulator/reducer patterns that CAN be converted to reduce() operations.</p>
	 * 
	 * @param ifStatement the IF statement to check
	 * @param loopVarName the loop variable name (assignments to this are internal)
	 * @return true if the IF body contains a simple assignment to an external variable
	 */
	public boolean isIfWithExternalAssignment(IfStatement ifStatement, String loopVarName) {
		if (ifStatement == null) {
			return false;
		}
		Statement thenStatement = ifStatement.getThenStatement();
		return containsExternalSimpleAssignment(thenStatement, loopVarName);
	}

	/**
	 * Recursively checks if a statement contains a simple assignment (=) to an external variable.
	 * Compound assignments (+=, -=, *=, etc.) are NOT flagged because they are accumulator patterns.
	 */
	private boolean containsExternalSimpleAssignment(Statement stmt, String loopVarName) {
		if (stmt == null) {
			return false;
		}
		
		if (stmt instanceof org.eclipse.jdt.core.dom.ExpressionStatement) {
			org.eclipse.jdt.core.dom.ExpressionStatement exprStmt = (org.eclipse.jdt.core.dom.ExpressionStatement) stmt;
			Expression expr = exprStmt.getExpression();
			if (expr instanceof org.eclipse.jdt.core.dom.Assignment) {
				org.eclipse.jdt.core.dom.Assignment assignment = (org.eclipse.jdt.core.dom.Assignment) expr;
				
				// Compound assignments (+=, -=, *=, etc.) are accumulator patterns - allow them
				if (assignment.getOperator() != org.eclipse.jdt.core.dom.Assignment.Operator.ASSIGN) {
					return false; // Not a simple assignment, it's an accumulator pattern - this single statement is OK
				}
				
				// Simple assignment (=) to something other than the loop variable
				Expression lhs = assignment.getLeftHandSide();
				if (lhs instanceof org.eclipse.jdt.core.dom.SimpleName) {
					String varName = ((org.eclipse.jdt.core.dom.SimpleName) lhs).getIdentifier();
					// If simple-assigning to something other than the loop variable, it's problematic
					if (!varName.equals(loopVarName)) {
						return true;
					}
				}
			}
			// Other expression statements (method calls, etc.) are OK
			return false;
		} else if (stmt instanceof Block) {
			Block block = (Block) stmt;
			for (Object obj : block.statements()) {
				if (containsExternalSimpleAssignment((Statement) obj, loopVarName)) {
					return true;
				}
			}
			return false;
		} else if (stmt instanceof IfStatement) {
			IfStatement nested = (IfStatement) stmt;
			if (containsExternalSimpleAssignment(nested.getThenStatement(), loopVarName)) {
				return true;
			}
			if (nested.getElseStatement() != null && containsExternalSimpleAssignment(nested.getElseStatement(), loopVarName)) {
				return true;
			}
			return false;
		}
		
		// Other statement types (variable declarations, etc.) are OK
		return false;
	}

	/**
	 * Checks if the IF statement contains an early return pattern that can be
	 * converted to anyMatch/noneMatch/allMatch.
	 * 
	 * <p>This method detects patterns by examining:</p>
	 * <ol>
	 * <li>The IF statement structure (no else, returns boolean literal)</li>
	 * <li>The return statement following the loop</li>
	 * <li>Whether the condition is negated (for allMatch)</li>
	 * </ol>
	 * 
	 * <p>After calling this method, use {@link #getDetectedPattern()} to determine
	 * which pattern was detected.</p>
	 * 
	 * @param ifStatement the IF statement to check
	 * @return true if an early return pattern is detected
	 */
	public boolean isEarlyReturnIf(IfStatement ifStatement) {
		detectedPattern = MatchPatternType.NONE;

		if (ifStatement == null || ifStatement.getElseStatement() != null) {
			return false;
		}

		Statement thenStmt = ifStatement.getThenStatement();
		ReturnStatement returnStmt = extractReturnStatement(thenStmt);

		if (returnStmt == null || !(returnStmt.getExpression() instanceof BooleanLiteral)) {
			return false;
		}

		BooleanLiteral returnValue = (BooleanLiteral) returnStmt.getExpression();
		BooleanLiteral followingReturn = getReturnAfterLoop();

		if (followingReturn == null) {
			return false;
		}

		// Detect pattern based on return values
		if (returnValue.booleanValue() && !followingReturn.booleanValue()) {
			// if (condition) return true; ... return false; → anyMatch
			detectedPattern = MatchPatternType.ANY_MATCH;
			return true;
		} else if (!returnValue.booleanValue() && followingReturn.booleanValue()) {
			// if (condition) return false; ... return true; → noneMatch or allMatch
			Expression condition = ifStatement.getExpression();
			if (ExpressionUtils.isNegatedExpression(condition)) {
				// if (!condition) return false; ... return true; → allMatch
				detectedPattern = MatchPatternType.ALL_MATCH;
			} else {
				// if (condition) return false; ... return true; → noneMatch
				detectedPattern = MatchPatternType.NONE_MATCH;
			}
			return true;
		}

		return false;
	}

	/**
	 * Checks if this IF statement is an early return pattern based on precomputed flags.
	 * This is used when the pattern type has already been determined by PreconditionsChecker.
	 * 
	 * @param ifStatement the IF statement to check
	 * @param isAnyMatch true if this is an anyMatch pattern
	 * @param isNoneMatch true if this is a noneMatch pattern
	 * @param isAllMatch true if this is an allMatch pattern
	 * @return true if the IF matches the specified pattern
	 */
	public boolean isEarlyReturnIf(IfStatement ifStatement, boolean isAnyMatch, boolean isNoneMatch, boolean isAllMatch) {
		if (ifStatement == null || ifStatement.getElseStatement() != null) {
			return false;
		}

		Statement thenStmt = ifStatement.getThenStatement();
		ReturnStatement returnStmt = extractReturnStatement(thenStmt);

		if (returnStmt == null || !(returnStmt.getExpression() instanceof BooleanLiteral)) {
			return false;
		}

		BooleanLiteral returnValue = (BooleanLiteral) returnStmt.getExpression();

		// Check against precomputed flags
		if (isAnyMatch && returnValue.booleanValue()) {
			detectedPattern = MatchPatternType.ANY_MATCH;
			return true;
		} else if (isNoneMatch && !returnValue.booleanValue()) {
			detectedPattern = MatchPatternType.NONE_MATCH;
			return true;
		} else if (isAllMatch && !returnValue.booleanValue()) {
			detectedPattern = MatchPatternType.ALL_MATCH;
			return true;
		}

		// Fall back to auto-detection
		return isEarlyReturnIf(ifStatement);
	}

	/**
	 * Creates a match operation from an IF statement based on the detected pattern.
	 * 
	 * @param ifStmt the IF statement containing the early return pattern
	 * @return a ProspectiveOperation for the match, or null if no pattern detected
	 */
	public ProspectiveOperation createMatchOperation(IfStatement ifStmt) {
		if (detectedPattern == MatchPatternType.NONE) {
			return null;
		}

		OperationType opType;
		switch (detectedPattern) {
		case ANY_MATCH:
			opType = OperationType.ANYMATCH;
			break;
		case NONE_MATCH:
			opType = OperationType.NONEMATCH;
			break;
		case ALL_MATCH:
			opType = OperationType.ALLMATCH;
			break;
		default:
			return null;
		}

		Expression condition = ifStmt.getExpression();
		// For allMatch, strip the negation since the pattern is "if (!condition) return false"
		if (detectedPattern == MatchPatternType.ALL_MATCH) {
			condition = ExpressionUtils.stripNegation(condition);
		}

		return new ProspectiveOperation(condition, opType);
	}

	/**
	 * Extracts a return statement from an IF's then-branch.
	 * Handles both direct return statements and blocks containing a single return.
	 */
	private ReturnStatement extractReturnStatement(Statement thenStmt) {
		if (thenStmt instanceof ReturnStatement) {
			return (ReturnStatement) thenStmt;
		}
		if (thenStmt instanceof Block) {
			Block block = (Block) thenStmt;
			if (block.statements().size() == 1 && block.statements().get(0) instanceof ReturnStatement) {
				return (ReturnStatement) block.statements().get(0);
			}
		}
		return null;
	}

	/**
	 * Gets the boolean return value from the statement immediately following the loop.
	 * 
	 * @return the BooleanLiteral returned after the loop, or null if not found
	 */
	private BooleanLiteral getReturnAfterLoop() {
		ASTNode parent = forLoop.getParent();

		if (!(parent instanceof Block)) {
			return null;
		}

		Block block = (Block) parent;
		List<?> statements = block.statements();

		int loopIndex = statements.indexOf(forLoop);
		if (loopIndex == -1 || loopIndex >= statements.size() - 1) {
			return null;
		}

		Statement nextStmt = (Statement) statements.get(loopIndex + 1);

		if (nextStmt instanceof ReturnStatement) {
			ReturnStatement returnStmt = (ReturnStatement) nextStmt;
			Expression expr = returnStmt.getExpression();
			if (expr instanceof BooleanLiteral) {
				return (BooleanLiteral) expr;
			}
		}

		return null;
	}
}
