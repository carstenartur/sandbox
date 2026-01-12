/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Parses enhanced for-loop bodies and extracts stream operations.
 * 
 * <p>This class is responsible for analyzing the statements within an enhanced
 * for-loop and extracting a list of {@link ProspectiveOperation}s that can be
 * transformed into a stream pipeline.</p>
 * 
 * <p><b>Architecture:</b></p>
 * <p>This class uses the Strategy Pattern via {@link StatementHandler} implementations
 * to process different statement types. This eliminates deep if-else-if chains and
 * makes the code more maintainable and extensible.</p>
 * 
 * <p><b>Supported Patterns:</b></p>
 * <ul>
 * <li><b>Variable declarations</b> → MAP operations (via {@link VariableDeclarationHandler})</li>
 * <li><b>IF statements</b> → FILTER operations or match patterns (via {@link IfStatementHandler})</li>
 * <li><b>Continue statements</b> → Negated FILTER operations</li>
 * <li><b>Accumulator patterns</b> → REDUCE operations (via {@link TerminalStatementHandler})</li>
 * <li><b>Side-effect statements</b> → FOREACH operations</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> This class is not thread-safe.</p>
 * 
 * @see StreamPipelineBuilder
 * @see ProspectiveOperation
 * @see StatementHandler
 */
public class LoopBodyParser {

	private final AST ast;
	private final ReducePatternDetector reduceDetector;
	private final IfStatementAnalyzer ifAnalyzer;
	private final boolean isAnyMatchPattern;
	private final boolean isNoneMatchPattern;
	private final boolean isAllMatchPattern;
	
	// Handler chain for processing statements
	private final List<StatementHandler> handlers;
	private final SideEffectChecker sideEffectChecker;

	/**
	 * Creates a new LoopBodyParser for the given for-loop.
	 * 
	 * @param forLoop           the enhanced for-loop to parse
	 * @param reduceDetector    detector for reduce patterns
	 * @param ifAnalyzer        analyzer for if statements
	 * @param isAnyMatchPattern whether anyMatch pattern is detected
	 * @param isNoneMatchPattern whether noneMatch pattern is detected
	 * @param isAllMatchPattern whether allMatch pattern is detected
	 */
	public LoopBodyParser(EnhancedForStatement forLoop, 
			ReducePatternDetector reduceDetector,
			IfStatementAnalyzer ifAnalyzer,
			boolean isAnyMatchPattern, 
			boolean isNoneMatchPattern, 
			boolean isAllMatchPattern) {
		this.ast = forLoop.getAST();
		this.reduceDetector = reduceDetector;
		this.ifAnalyzer = ifAnalyzer;
		this.isAnyMatchPattern = isAnyMatchPattern;
		this.isNoneMatchPattern = isNoneMatchPattern;
		this.isAllMatchPattern = isAllMatchPattern;
		
		// Initialize handlers
		this.sideEffectChecker = new SideEffectChecker();
		this.handlers = createHandlers();
	}
	
	/**
	 * Creates the chain of statement handlers.
	 * Order matters: more specific handlers should come first.
	 */
	private List<StatementHandler> createHandlers() {
		return Arrays.asList(
			new VariableDeclarationHandler(this),
			new AssignmentMapHandler(),
			new IfStatementHandler(this),
			new NonTerminalStatementHandler(sideEffectChecker),
			new TerminalStatementHandler(sideEffectChecker)
		);
	}

	/**
	 * Parses the loop body and extracts stream operations.
	 * 
	 * @param body        the loop body statement
	 * @param loopVarName the loop variable name
	 * @return list of prospective operations, empty if parsing fails
	 */
	public List<ProspectiveOperation> parse(Statement body, String loopVarName) {
		List<ProspectiveOperation> ops = new ArrayList<>();
		String currentVarName = loopVarName;

		if (body instanceof Block) {
			return parseBlock((Block) body, loopVarName, currentVarName, ops);
		} else if (body instanceof IfStatement) {
			return parseIfStatement((IfStatement) body, loopVarName, currentVarName, ops);
		} else {
			return parseSingleStatement(body, loopVarName, currentVarName, ops);
		}
	}

	/**
	 * Parses a block of statements.
	 */
	private List<ProspectiveOperation> parseBlock(Block block, String loopVarName, 
			String currentVarName, List<ProspectiveOperation> ops) {
		List<Statement> statements = block.statements();

		// Check if the entire block should be treated as a single forEach
		if (shouldTreatAsSimpleForEach(statements, loopVarName)) {
			// For single-statement blocks, unwrap to allow expression lambda
			Statement forEachStatement;
			if (statements.size() == 1) {
				forEachStatement = statements.get(0);
			} else {
				forEachStatement = block;
			}
			ProspectiveOperation forEachOp = new ProspectiveOperation(forEachStatement,
					OperationType.FOREACH, loopVarName);
			ops.add(forEachOp);
			return ops;
		}

		for (int i = 0; i < statements.size(); i++) {
			Statement stmt = statements.get(i);
			boolean isLast = (i == statements.size() - 1);

			ParseResult result = parseStatement(stmt, currentVarName, ops, isLast, statements, i, loopVarName);
			if (result.shouldAbort()) {
				return new ArrayList<>();
			}
			currentVarName = result.getCurrentVarName();
			
			// Handle skip index for wrapped statements
			if (result.getSkipToIndex() >= 0) {
				i = result.getSkipToIndex();
			}
		}

		return ops;
	}

	/**
	 * Parses a single statement and returns the result.
	 * Uses the handler chain to process different statement types.
	 */
	private ParseResult parseStatement(Statement stmt, String currentVarName, 
			List<ProspectiveOperation> ops, boolean isLast, 
			List<Statement> statements, int currentIndex, String loopVarName) {
		
		// Create parsing context
		StatementParsingContext context = new StatementParsingContext(
				loopVarName,
				currentVarName,
				isLast,
				currentIndex,
				statements,
				ast,
				ifAnalyzer,
				reduceDetector,
				isAnyMatchPattern,
				isNoneMatchPattern,
				isAllMatchPattern);
		
		// Find and execute the appropriate handler
		for (StatementHandler handler : handlers) {
			if (handler.canHandle(stmt, context)) {
				return handler.handle(stmt, context, ops);
			}
		}
		
		// Fallback: no handler found - should not happen with proper handlers
		return new ParseResult(currentVarName);
	}

	/**
	 * Parses a standalone IF statement (not in a block).
	 */
	private List<ProspectiveOperation> parseIfStatement(IfStatement ifStmt, String loopVarName,
			String currentVarName, List<ProspectiveOperation> ops) {
		
		if (ifStmt.getElseStatement() != null) {
			return ops;
		}

		if (ifAnalyzer.isIfWithLabeledContinue(ifStmt) || ifAnalyzer.isIfWithBreak(ifStmt)) {
			return new ArrayList<>();
		}

		if (ifAnalyzer.isEarlyReturnIf(ifStmt, isAnyMatchPattern, isNoneMatchPattern, isAllMatchPattern)) {
			ProspectiveOperation matchOp = ifAnalyzer.createMatchOperation(ifStmt);
			ops.add(matchOp);
		} else {
			ProspectiveOperation filterOp = new ProspectiveOperation(ifStmt.getExpression(),
					OperationType.FILTER);
			ops.add(filterOp);

			List<ProspectiveOperation> nestedOps = parse(ifStmt.getThenStatement(), currentVarName);
			ops.addAll(nestedOps);
		}
		return ops;
	}

	/**
	 * Parses a single statement that is not a block or IF.
	 */
	private List<ProspectiveOperation> parseSingleStatement(Statement body, String loopVarName,
			String currentVarName, List<ProspectiveOperation> ops) {
		
		ProspectiveOperation reduceOp = reduceDetector.detectReduceOperation(body);
		if (reduceOp != null) {
			reduceDetector.addMapBeforeReduce(ops, reduceOp, body, currentVarName, ast);
			ops.add(reduceOp);
		} else if (body instanceof ReturnStatement || body instanceof ContinueStatement 
				|| body instanceof ThrowStatement) {
			return new ArrayList<>();
		} else if (!sideEffectChecker.isSafeSideEffect(body, currentVarName, ops)) {
			return new ArrayList<>();
		} else {
			ProspectiveOperation forEachOp = new ProspectiveOperation(body,
					OperationType.FOREACH, currentVarName);
			ops.add(forEachOp);
		}
		return ops;
	}

	/**
	 * Determines if the loop body should be treated as a single forEach.
	 */
	private boolean shouldTreatAsSimpleForEach(List<Statement> statements, String loopVarName) {
		if (statements.isEmpty()) {
			return false;
		}

		if (statements.size() >= 1 && statements.get(0) instanceof IfStatement) {
			return false;
		}

		Set<String> declaredVariables = new HashSet<>();
		IfStatement ifStatementAfterVarDecls = null;
		boolean allSafeSideEffects = true;

		for (Statement stmt : statements) {
			if (stmt instanceof VariableDeclarationStatement) {
				VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmt;
				for (Object frag : varDecl.fragments()) {
					if (frag instanceof VariableDeclarationFragment) {
						declaredVariables.add(((VariableDeclarationFragment) frag).getName().getIdentifier());
					}
				}
				// Variable declarations are not simple side-effects
				allSafeSideEffects = false;
			} else if (stmt instanceof IfStatement) {
				IfStatement ifStmt = (IfStatement) stmt;
				if (ifAnalyzer.isEarlyReturnIf(ifStmt, isAnyMatchPattern, isNoneMatchPattern, isAllMatchPattern)
						|| ifAnalyzer.isIfWithContinue(ifStmt)) {
					return false;
				}
				if (!declaredVariables.isEmpty()) {
					ifStatementAfterVarDecls = ifStmt;
				}
				// IF statements are not simple side-effects
				allSafeSideEffects = false;
			} else if (stmt instanceof ReturnStatement || stmt instanceof ContinueStatement 
					|| stmt instanceof BreakStatement) {
				return false;
			} else if (stmt instanceof ExpressionStatement) {
				// Check if this is a method call or other safe side-effect
				ExpressionStatement exprStmt = (ExpressionStatement) stmt;
				Expression expr = exprStmt.getExpression();
				// Method invocations are safe side-effects
				if (!(expr instanceof org.eclipse.jdt.core.dom.MethodInvocation)) {
					// Non-method-call expressions might be reduce operations or assignments
					ProspectiveOperation reduceOp = reduceDetector.detectReduceOperation(stmt);
					if (reduceOp != null) {
						return false;
					}
					allSafeSideEffects = false;
				}
			} else {
				// Other statement types are not simple side-effects
				allSafeSideEffects = false;
			}

			ProspectiveOperation reduceOp = reduceDetector.detectReduceOperation(stmt);
			if (reduceOp != null) {
				return false;
			}
		}

		if (ifStatementAfterVarDecls != null && !declaredVariables.isEmpty()) {
			if (ifModifiesDeclaredVariables(ifStatementAfterVarDecls, declaredVariables)) {
				return true;
			}
			return false;
		}

		// If all statements are safe side-effects (method calls), treat as simple forEach
		return allSafeSideEffects;
	}

	/**
	 * Checks if an IF statement modifies any declared variables.
	 */
	private boolean ifModifiesDeclaredVariables(IfStatement ifStmt, Set<String> declaredVariables) {
		Statement thenStmt = ifStmt.getThenStatement();
		return statementModifiesVariables(thenStmt, declaredVariables);
	}

	/**
	 * Recursively checks if a statement modifies any variables.
	 */
	private boolean statementModifiesVariables(Statement stmt, Set<String> variables) {
		if (stmt == null || variables.isEmpty()) {
			return false;
		}

		if (stmt instanceof Block) {
			Block block = (Block) stmt;
			for (Object s : block.statements()) {
				if (statementModifiesVariables((Statement) s, variables)) {
					return true;
				}
			}
		} else if (stmt instanceof ExpressionStatement) {
			ExpressionStatement exprStmt = (ExpressionStatement) stmt;
			return expressionModifiesVariables(exprStmt.getExpression(), variables);
		} else if (stmt instanceof IfStatement) {
			IfStatement ifStmt = (IfStatement) stmt;
			if (statementModifiesVariables(ifStmt.getThenStatement(), variables)) {
				return true;
			}
			if (ifStmt.getElseStatement() != null 
					&& statementModifiesVariables(ifStmt.getElseStatement(), variables)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Checks if an expression modifies any variables.
	 */
	private boolean expressionModifiesVariables(Expression expr, Set<String> variables) {
		if (expr == null) {
			return false;
		}

		if (expr instanceof Assignment) {
			Assignment assignment = (Assignment) expr;
			Expression lhs = assignment.getLeftHandSide();
			if (lhs instanceof SimpleName) {
				return variables.contains(((SimpleName) lhs).getIdentifier());
			}
		}

		if (expr instanceof PostfixExpression) {
			PostfixExpression postfix = (PostfixExpression) expr;
			if (postfix.getOperand() instanceof SimpleName) {
				return variables.contains(((SimpleName) postfix.getOperand()).getIdentifier());
			}
		}

		if (expr instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression) expr;
			PrefixExpression.Operator op = prefix.getOperator();
			if (op == PrefixExpression.Operator.INCREMENT || op == PrefixExpression.Operator.DECREMENT) {
				if (prefix.getOperand() instanceof SimpleName) {
					return variables.contains(((SimpleName) prefix.getOperand()).getIdentifier());
				}
			}
		}

		return false;
	}

	/**
	 * Result of parsing a single statement.
	 */
	public static class ParseResult {
		private final String currentVarName;
		private final boolean abort;
		private final int skipToIndex;

		public ParseResult(String currentVarName) {
			this(currentVarName, false, -1);
		}

		public ParseResult(String currentVarName, int skipToIndex) {
			this(currentVarName, false, skipToIndex);
		}

		private ParseResult(String currentVarName, boolean abort, int skipToIndex) {
			this.currentVarName = currentVarName;
			this.abort = abort;
			this.skipToIndex = skipToIndex;
		}

		public static ParseResult abort() {
			return new ParseResult(null, true, -1);
		}

		public String getCurrentVarName() {
			return currentVarName;
		}

		public boolean shouldAbort() {
			return abort;
		}

		public int getSkipToIndex() {
			return skipToIndex;
		}
	}
}
