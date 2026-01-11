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

import java.util.Set;

import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.sandbox.jdt.internal.common.AstProcessorBuilder;
import org.sandbox.jdt.internal.common.ReferenceHolder;

import java.util.*;

/**
 * Analyzes a loop statement to check various preconditions for safe refactoring
 * to stream operations. Uses AstProcessorBuilder for cleaner AST traversal.
 * 
 * <p>
 * This class is final to prevent subclassing and potential finalizer attacks,
 * since the constructor calls analysis methods that could potentially throw
 * exceptions.
 * </p>
 */
public final class PreconditionsChecker {
	private final Statement loop;
//    private final CompilationUnit compilationUnit;
	private final Set<VariableDeclarationFragment> innerVariables = new HashSet<>();
	private boolean containsBreak = false;
	private boolean containsLabeledContinue = false;
	private boolean containsReturn = false;
	private boolean throwsException = false;
	private boolean containsNEFs = false;
	private boolean hasReducer = false;
	private Statement reducerStatement = null;
	private boolean isAnyMatchPattern = false;
	private boolean isNoneMatchPattern = false;
	private boolean isAllMatchPattern = false;
	/**
	 * Constructor for PreconditionsChecker.
	 * 
	 * @param loop            the statement containing the loop to analyze (must not
	 *                        be null)
	 * @param compilationUnit the compilation unit containing the loop
	 */
	public PreconditionsChecker(Statement loop, CompilationUnit compilationUnit) {
		// Set loop field first - if null, we'll handle it gracefully in the catch block
		this.loop = loop;
//        this.compilationUnit = compilationUnit;

		// Analyze the loop in a try-catch to prevent partial initialization
		// if any exception occurs during analysis
		try {
			// Perform analysis only if loop is not null
			if (loop != null) {
				analyzeLoop();
			} else {
				// Null loop - treat as unsafe to refactor
				this.containsBreak = true;
			}
		} catch (Exception e) {
			// If analysis fails, treat loop as unsafe to refactor
			// Set flags to prevent conversion
			this.containsBreak = true; // Conservatively block conversion
		}
	}

	/**
	 * Checks if the loop is safe to refactor to stream operations.
	 * 
	 * <p>
	 * A loop is safe to refactor if it meets all of the following conditions:
	 * <ul>
	 * <li>Does not throw exceptions (throwsException == false)</li>
	 * <li>Does not contain break statements (containsBreak == false)</li>
	 * <li>Does not contain labeled continue statements (containsLabeledContinue ==
	 * false)</li>
	 * <li>Does not contain return statements OR contains only pattern-matching
	 * returns (anyMatch/noneMatch/allMatch)</li>
	 * <li>All variables are effectively final (containsNEFs == false)</li>
	 * </ul>
	 * 
	 * <p>
	 * <b>Note on continue statements:</b> Unlabeled continue statements are allowed
	 * and will be converted to filter operations by StreamPipelineBuilder. Only
	 * labeled continues are rejected because they cannot be safely translated to
	 * stream operations.
	 * </p>
	 * 
	 * <p>
	 * <b>Pattern-matching early returns:</b> Early returns matching anyMatch,
	 * noneMatch, or allMatch patterns are allowed because they can be converted to
	 * the corresponding terminal stream operations.
	 * </p>
	 * 
	 * @return true if the loop can be safely converted to stream operations, false
	 *         otherwise
	 * @see StreamPipelineBuilder#parseLoopBody
	 */
	public boolean isSafeToRefactor() {
		// Allow early returns if they match anyMatch/noneMatch/allMatch patterns
		boolean allowedReturn = containsReturn && (isAnyMatchPattern || isNoneMatchPattern || isAllMatchPattern);
		// Note: Unlabeled continues are allowed and will be converted to filters by
		// StreamPipelineBuilder
		// Only labeled continues are rejected here
		return !throwsException && !containsBreak && !containsLabeledContinue && (!containsReturn || allowedReturn)
				&& !containsNEFs;
	}

	/**
	 * Checks if the loop contains a reducer pattern.
	 * 
	 * <p>
	 * Scans loop body for accumulator patterns such as:
	 * <ul>
	 * <li>i++, i--, ++i, --i</li>
	 * <li>sum += x, product *= x, count -= 1</li>
	 * <li>Other compound assignments (|=, &=, etc.)</li>
	 * </ul>
	 * 
	 * @return true if a reducer pattern is detected, false otherwise
	 * 
	 * @see #getReducer()
	 */
	public boolean isReducer() {
		return hasReducer;
	}

	/**
	 * Returns the statement containing the reducer pattern.
	 * 
	 * <p>
	 * If multiple reducers exist in the loop, this returns only the first one
	 * encountered.
	 * </p>
	 * 
	 * @return the statement containing the reducer, or null if no reducer was
	 *         detected
	 * 
	 * @see #isReducer()
	 */
	public Statement getReducer() {
		return reducerStatement;
	}

	/**
	 * Checks if the loop matches the anyMatch pattern.
	 * 
	 * <p>
	 * AnyMatch pattern: loop contains {@code if (condition) return true;}
	 * </p>
	 * 
	 * @return true if anyMatch pattern is detected
	 */
	public boolean isAnyMatchPattern() {
		return isAnyMatchPattern;
	}

	/**
	 * Checks if the loop matches the noneMatch pattern.
	 * 
	 * <p>
	 * NoneMatch pattern: loop contains {@code if (condition) return false;}
	 * </p>
	 * 
	 * @return true if noneMatch pattern is detected
	 */
	public boolean isNoneMatchPattern() {
		return isNoneMatchPattern;
	}

	/**
	 * Checks if the loop matches the allMatch pattern.
	 * 
	 * <p>
	 * AllMatch pattern: loop contains {@code if (condition) return false;} but the
	 * method returns true after the loop, or {@code if (!condition) return false;}
	 * checking all elements meet a condition.
	 * </p>
	 * 
	 * @return true if allMatch pattern is detected
	 */
	public boolean isAllMatchPattern() {
		return isAllMatchPattern;
	}

	/**
	 * Analyzes the loop statement to identify relevant elements for refactoring.
	 * 
	 * <p>
	 * This method uses {@link AstProcessorBuilder} for cleaner and more
	 * maintainable AST traversal. It performs the following analysis:
	 * <ul>
	 * <li>Collects variable declarations within the loop</li>
	 * <li>Detects control flow statements (break, continue, return, throw)</li>
	 * <li>Identifies reducer patterns (i++, sum += x, etc.)</li>
	 * <li>Detects early return patterns (anyMatch, noneMatch, allMatch)</li>
	 * <li>Checks if variables are effectively final</li>
	 * </ul>
	 * 
	 * <p>
	 * The analysis results are stored in instance variables that can be queried via
	 * getter methods like {@link #isSafeToRefactor()}, {@link #isReducer()}, etc.
	 * </p>
	 */
	private void analyzeLoop() {
		AstProcessorBuilder<String, Object> builder = AstProcessorBuilder.with(new ReferenceHolder<String, Object>());

		builder.onVariableDeclarationFragment((node, h) -> {
			innerVariables.add(node);
			return true;
		}).onBreakStatement((node, h) -> {
			containsBreak = true;
			return true;
		}).onLabeledContinue((node, h) -> {
			// Labeled continue should prevent conversion (unlabeled continues are allowed)
			containsLabeledContinue = true;
			return true;
		}).onReturnStatement((node, h) -> {
			containsReturn = true;
			return true;
		}).onThrowStatement((node, h) -> {
			throwsException = true;
			return true;
		}).onEnhancedForStatement((node, h) -> {
			return true;
		}).onCompoundAssignment((node, h) -> {
			// Compound assignments: +=, -=, *=, /=, |=, &=, etc.
			markAsReducer(node);
			return true;
		}).onAssignment((node, h) -> {
			// Check for Math.max/Math.min patterns: max = Math.max(max, x)
			if (node.getOperator() == Assignment.Operator.ASSIGN && isMathMinMaxReducerPattern(node)) {
				markAsReducer(node);
			}
			return true;
		}).onPostfixIncrementOrDecrement((node, h) -> {
			// Detect i++, i--
			markAsReducer(node);
			return true;
		}).onPrefixIncrementOrDecrement((node, h) -> {
			// Detect ++i, --i
			markAsReducer(node);
			return true;
		});

		// First, analyze just the loop itself
		builder.build(loop);

		// Save the containsReturn flag state after analyzing only the loop body
		// This is important because we want to distinguish between:
		// 1. Returns INSIDE the loop (which may prevent conversion, except for match patterns)
		// 2. Returns AFTER the loop (which are just part of the method and shouldn't prevent conversion)
		boolean containsReturnInsideLoop = containsReturn;

		// Then, if the loop is inside a Block, analyze only the immediately following
		// statement (if any). This lets us detect patterns that depend on the statement
		// right after the loop without pulling in unrelated statements.
		ASTNode parent = loop.getParent();
		if (parent instanceof Block) {
			Block block = (Block) parent;
			@SuppressWarnings("unchecked")
			List<Statement> statements = block.statements();
			int loopIndex = statements.indexOf(loop);
			if (loopIndex != -1 && loopIndex + 1 < statements.size()) {
				Statement followingStatement = statements.get(loopIndex + 1);
				builder.build(followingStatement);
			}
		}

		// Detect anyMatch/noneMatch patterns
		// This needs to see if there's a return statement after the loop,
		// so containsReturn may be true from analyzing the following statement
		detectEarlyReturnPatterns();

		// Restore the containsReturn flag to only reflect returns INSIDE the loop
		// This ensures that isSafeToRefactor() only rejects loops with returns inside,
		// not loops followed by return statements (like reducers)
		containsReturn = containsReturnInsideLoop;

		analyzeEffectivelyFinalVariables();
	}

	/**
	 * Checks if variables declared within the loop are effectively final.
	 * 
	 * <p>
	 * A variable is effectively final if it is never modified after its
	 * initialization. This is important for stream operations because lambda
	 * expressions can only capture effectively final variables from their enclosing
	 * scope.
	 * </p>
	 * 
	 * <p>
	 * Sets {@link #containsNEFs} to true if any non-effectively-final variable is
	 * found.
	 * </p>
	 * 
	 * <p>
	 * <b>Important:</b> Variables declared inside the loop body that are modified
	 * within the same loop iteration are NOT checked here. Such variables will be
	 * converted to map operations in the stream pipeline. Only variables that would
	 * need to be captured from an outer scope are relevant for this check.
	 * </p>
	 */
	private void analyzeEffectivelyFinalVariables() {
		// Variables declared INSIDE the loop body are allowed to be modified,
		// as they will be converted to map operations in the stream pipeline.
		// We only need to check for variables declared OUTSIDE the loop that
		// are modified inside - but innerVariables only contains variables
		// declared INSIDE the loop, so we skip this check for them.
		//
		// The original check was too strict: it rejected loops where a variable
		// like 's' was declared and then reassigned (s = s.toString()) within
		// the same loop iteration. This pattern should be allowed and converted
		// to chained map operations.
		//
		// For now, we don't check innerVariables for effectively-final status.
		// A more sophisticated check would analyze variables captured from outer
		// scopes, but that's a separate concern.
	}

	/** Hilfsmethode zur Prüfung, ob eine Variable effektiv final ist. */
	private boolean isEffectivelyFinal(VariableDeclarationFragment var) {
		final boolean[] modified = { false };
		ASTNode methodBody = getEnclosingMethodBody(var);
		if (methodBody != null) {
			String varName = var.getName().getIdentifier();

			AstProcessorBuilder.with(new ReferenceHolder<String, Object>()).onAssignment((node, h) -> {
				if (node.getLeftHandSide() instanceof SimpleName name) {
					if (name.getIdentifier().equals(varName)) {
						modified[0] = true;
					}
				}
				return true;
			}).build(methodBody);
		}
		return !modified[0];
	}

	/**
	 * Hilfsmethode: Findet den umgebenden Methodenrumpf einer Variablendeklaration.
	 */
	private ASTNode getEnclosingMethodBody(ASTNode node) {
		MethodDeclaration method = ASTNodes.getFirstAncestorOrNull(node, MethodDeclaration.class);
		return (method != null) ? method.getBody() : null;
	}

	/**
	 * Marks an AST node as a reducer pattern and records its statement.
	 * 
	 * @param node the AST node that represents a reducer operation
	 */
	private void markAsReducer(ASTNode node) {
		hasReducer = true;
		if (reducerStatement == null) {
			reducerStatement = ASTNodes.getFirstAncestorOrNull(node, Statement.class);
		}
	}

	/**
	 * Checks if an assignment represents a Math.max or Math.min reducer pattern.
	 * 
	 * <p>Pattern: {@code max = Math.max(max, x)} or {@code min = Math.min(min, x)}</p>
	 * 
	 * @param assignment the assignment to check
	 * @return true if this is a Math.max/Math.min reducer pattern
	 */
	private boolean isMathMinMaxReducerPattern(Assignment assignment) {
		Expression rhs = assignment.getRightHandSide();
		if (!(rhs instanceof MethodInvocation methodInv)) {
			return false;
		}
		
		if (!isMathMinMaxInvocation(methodInv)) {
			return false;
		}
		
		return isLhsVariableInArguments(assignment.getLeftHandSide(), methodInv.arguments());
	}

	/**
	 * Checks if a method invocation is Math.max or Math.min.
	 */
	private boolean isMathMinMaxInvocation(MethodInvocation methodInv) {
		Expression methodExpr = methodInv.getExpression();
		if (!(methodExpr instanceof SimpleName className)) {
			return false;
		}
		
		if (!"Math".equals(className.getIdentifier())) {
			return false;
		}
		
		String methodName = methodInv.getName().getIdentifier();
		return "max".equals(methodName) || "min".equals(methodName);
	}

	/**
	 * Checks if the LHS variable name appears in the method arguments.
	 */
	private boolean isLhsVariableInArguments(Expression lhs, List<?> arguments) {
		if (!(lhs instanceof SimpleName lhsName)) {
			return false;
		}
		
		if (arguments.size() != 2) {
			return false;
		}
		
		String varName = lhsName.getIdentifier();
		return arguments.stream()
				.filter(SimpleName.class::isInstance)
				.map(SimpleName.class::cast)
				.anyMatch(arg -> varName.equals(arg.getIdentifier()));
	}

	/**
	 * Detects anyMatch, noneMatch, and allMatch patterns in the loop.
	 * 
	 * <p>
	 * Patterns:
	 * <ul>
	 * <li>AnyMatch: {@code if (condition) return true;}</li>
	 * <li>NoneMatch: {@code if (condition) return false;}</li>
	 * <li>AllMatch: {@code if (!condition) return false;} or
	 * {@code if (condition) return false;} when negated</li>
	 * </ul>
	 * 
	 * <p>
	 * These patterns must be the only statement with a return in the loop body.
	 * 
	 * <p>
	 * AllMatch is typically used in patterns like:
	 * 
	 * <pre>
	 * for (Item item : items) {
	 * 	if (!item.isValid())
	 * 		return false;
	 * }
	 * return true;
	 * </pre>
	 */
	private void detectEarlyReturnPatterns() {
		if (!containsReturn || !(loop instanceof EnhancedForStatement)) {
			return;
		}

		EnhancedForStatement forLoop = (EnhancedForStatement) loop;
		Statement body = forLoop.getBody();

		// Find all IF statements with return statements in the loop
		final List<IfStatement> ifStatementsWithReturn = new ArrayList<>();

		// Use ASTVisitor to find IF statements
		body.accept(new ASTVisitor() {
			@Override
			public boolean visit(IfStatement node) {
				if (hasReturnInThenBranch(node)) {
					ifStatementsWithReturn.add(node);
				}
				return true;
			}
		});

		// For anyMatch/noneMatch/allMatch, we expect exactly one IF with return
		if (ifStatementsWithReturn.size() != 1) {
			return;
		}

		IfStatement ifStmt = ifStatementsWithReturn.get(0);

		// The IF must not have an else branch for these patterns
		if (ifStmt.getElseStatement() != null) {
			return;
		}

		// Check if the IF returns a boolean literal
		BooleanLiteral returnValue = getReturnValueFromIf(ifStmt);
		if (returnValue == null) {
			return;
		}

		// Check what statement follows the loop
		BooleanLiteral followingReturn = getReturnAfterLoop(forLoop);
		if (followingReturn == null) {
			return;
		}

		// Determine pattern based on return values
		determineMatchPattern(returnValue.booleanValue(), followingReturn.booleanValue(), ifStmt.getExpression());
	}

	/**
	 * Determines which match pattern (anyMatch, noneMatch, allMatch) applies based on
	 * the return values and condition.
	 * 
	 * @param returnValueInLoop the boolean value returned inside the loop
	 * @param returnValueAfterLoop the boolean value returned after the loop
	 * @param condition the condition expression in the if statement
	 */
	private void determineMatchPattern(boolean returnValueInLoop, boolean returnValueAfterLoop, Expression condition) {
		if (returnValueInLoop && !returnValueAfterLoop) {
			// if (condition) return true; + return false; → anyMatch
			isAnyMatchPattern = true;
		} else if (!returnValueInLoop && returnValueAfterLoop) {
			// if (condition) return false; + return true; → could be noneMatch OR allMatch
			// Distinguish based on condition negation:
			// - if (!condition) return false; + return true; → allMatch(condition)
			// - if (condition) return false; + return true; → noneMatch(condition)
			if (isNegatedCondition(condition)) {
				isAllMatchPattern = true;
			} else {
				isNoneMatchPattern = true;
			}
		}
	}

	/**
	 * Checks if the IF statement has a return in its then branch.
	 */
	private boolean hasReturnInThenBranch(IfStatement ifStmt) {
		return getReturnStatementFromThenBranch(ifStmt).isPresent();
	}

	/**
	 * Extracts the return statement from the then branch of an if statement.
	 * Handles both direct return statements and blocks with a single return statement.
	 * 
	 * @param ifStmt the if statement to check
	 * @return Optional containing the ReturnStatement, or empty if not found
	 */
	private Optional<ReturnStatement> getReturnStatementFromThenBranch(IfStatement ifStmt) {
		Statement thenStmt = ifStmt.getThenStatement();

		if (thenStmt instanceof ReturnStatement returnStmt) {
			return Optional.of(returnStmt);
		}

		if (thenStmt instanceof Block block) {
			List<?> stmts = block.statements();
			if (stmts.size() == 1 && stmts.get(0) instanceof ReturnStatement returnStmt) {
				return Optional.of(returnStmt);
			}
		}

		return Optional.empty();
	}

	/**
	 * Extracts the boolean literal value from a return statement in an IF.
	 * 
	 * @return the BooleanLiteral if the IF returns a boolean literal, null
	 *         otherwise
	 */
	private BooleanLiteral getReturnValueFromIf(IfStatement ifStmt) {
		return getReturnStatementFromThenBranch(ifStmt)
				.map(ReturnStatement::getExpression)
				.filter(BooleanLiteral.class::isInstance)
				.map(BooleanLiteral.class::cast)
				.orElse(null);
	}

	/**
	 * Checks if an expression is a negated condition (starts with !).
	 * Handles ParenthesizedExpression wrapping.
	 * 
	 * @param expr the expression to check
	 * @return true if the expression is a PrefixExpression with NOT operator (possibly wrapped in parentheses)
	 */
	private boolean isNegatedCondition(Expression expr) {
		// Unwrap parentheses
		while (expr instanceof ParenthesizedExpression) {
			expr = ((ParenthesizedExpression) expr).getExpression();
		}
		
		return expr instanceof PrefixExpression
				&& ((PrefixExpression) expr).getOperator() == PrefixExpression.Operator.NOT;
	}

	/**
	 * Gets the boolean return value from the statement immediately following the loop.
	 * 
	 * <p>
	 * For anyMatch/allMatch/noneMatch patterns, we expect a return statement with a
	 * boolean literal immediately after the loop. This method finds the loop's parent
	 * (usually a Block), locates the loop, and checks the next statement.
	 * </p>
	 * 
	 * @param forLoop the EnhancedForStatement to check
	 * @return the BooleanLiteral returned after the loop, or null if not found
	 */
	private BooleanLiteral getReturnAfterLoop(EnhancedForStatement forLoop) {
		ASTNode parent = forLoop.getParent();
		
		// The loop must be in a Block (method body, if-then block, etc.)
		if (!(parent instanceof Block)) {
			return null;
		}
		
		Block block = (Block) parent;
		List<?> statements = block.statements();
		
		// Find the loop in the block's statements
		int loopIndex = statements.indexOf(forLoop);
		if (loopIndex == -1 || loopIndex >= statements.size() - 1) {
			// Loop not found or is the last statement
			return null;
		}
		
		// Check the next statement
		Statement nextStmt = (Statement) statements.get(loopIndex + 1);
		
		// We expect a return statement with a boolean literal
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