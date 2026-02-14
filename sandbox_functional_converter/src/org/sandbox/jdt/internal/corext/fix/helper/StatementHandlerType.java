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

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.sandbox.jdt.internal.common.NodeMatcher;
import org.sandbox.jdt.internal.corext.util.ExpressionHelper;

/**
 * Types of statement handlers for processing loop body statements.
 * 
 * <p>Each handler type represents a specific strategy for converting loop statements
 * to stream operations. This enum implements the Strategy Pattern where each constant
 * encapsulates its own handling logic.</p>
 * 
 * <p><b>Handler Precedence:</b></p>
 * <p>The order of enum constants defines the precedence of handlers. More specific
 * handlers come first:</p>
 * <ol>
 * <li>{@link #VARIABLE_DECLARATION} - Handles variable declarations → MAP operations</li>
 * <li>{@link #ASSIGNMENT_MAP} - Handles assignments to pipeline variables → MAP operations</li>
 * <li>{@link #IF_STATEMENT} - Handles IF statements → FILTER/MATCH operations</li>
 * <li>{@link #NON_TERMINAL} - Handles non-last statements → wrapped MAP operations</li>
 * <li>{@link #TERMINAL} - Handles last statements → FOREACH/REDUCE operations</li>
 * </ol>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * for (StatementHandlerType handler : StatementHandlerType.values()) {
 *     if (handler.canHandle(stmt, context)) {
 *         return handler.handle(stmt, context, ops, handlerContext);
 *     }
 * }
 * }</pre>
 * 
 * @see LoopBodyParser
 * @see StatementParsingContext
 * @see StatementHandlerContext
 */
public enum StatementHandlerType {

	/**
	 * Handles variable declaration statements.
	 * 
	 * <p>Variable declarations with initializers are converted to MAP operations
	 * in the stream pipeline.</p>
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>{@code
	 * for (Integer num : numbers) {
	 *     int squared = num * num;  // → .map(num -> num * num)
	 *     System.out.println(squared);
	 * }
	 * }</pre>
	 */
	VARIABLE_DECLARATION {
		@Override
		public boolean canHandle(Statement stmt, StatementParsingContext context) {
			return stmt instanceof VariableDeclarationStatement;
		}

		@Override
		public LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context,
				List<ProspectiveOperation> ops, StatementHandlerContext handlerContext) {
			
			VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmt;
			String currentVarName = context.getCurrentVariableName();

			List<VariableDeclarationFragment> fragments = varDecl.fragments();
			if (fragments.isEmpty()) {
				return new LoopBodyParser.ParseResult(currentVarName);
			}

			VariableDeclarationFragment frag = fragments.get(0);
			if (frag.getInitializer() == null) {
				return new LoopBodyParser.ParseResult(currentVarName);
			}

			String newVarName = frag.getName().getIdentifier();
			
			// Create MAP operation for the variable declaration
			ProspectiveOperation mapOp = new ProspectiveOperation(frag.getInitializer(),
					OperationType.MAP, newVarName);
			ops.add(mapOp);

			// Check if we need to wrap remaining non-terminal statements in a MAP
			if (shouldWrapRemaining(context, newVarName)) {
				return wrapRemainingStatements(context, ops, newVarName, handlerContext);
			}

			return new LoopBodyParser.ParseResult(newVarName);
		}
		
		/**
		 * Checks if remaining statements should be wrapped in a single MAP operation.
		 * 
		 * @param context the parsing context
		 * @param newVarName the name of the variable being produced by this MAP operation
		 * @return true if remaining statements should be wrapped
		 */
		private boolean shouldWrapRemaining(StatementParsingContext context, String newVarName) {
			List<Statement> statements = context.getAllStatements();
			int currentIndex = context.getCurrentIndex();
			
			if (statements == null || currentIndex >= statements.size() - 2) {
				return false;
			}

			IfStatementAnalyzer ifAnalyzer = context.getIfAnalyzer();

			// Look through remaining non-terminal statements
			for (int j = currentIndex + 1; j < statements.size() - 1; j++) {
				Statement stmt = statements.get(j);
				boolean[] returnFalse = { false };
				boolean[] returnTrue = { false };

				NodeMatcher.on(stmt)
					.ifIfStatementMatching(
						ifStmt -> ifAnalyzer.isEarlyReturnIf(ifStmt,
							context.isAnyMatchPattern(),
							context.isNoneMatchPattern(),
							context.isAllMatchPattern()),
						ifStmt -> returnFalse[0] = true)
					.ifIfStatementMatching(
						ifStmt -> ifAnalyzer.isIfWithContinue(ifStmt),
						ifStmt -> returnFalse[0] = true)
					.ifIfStatementWithoutElse(
						ifStmt -> returnTrue[0] = true);

				if (returnFalse[0]) {
					return false;
				}
				if (returnTrue[0]) {
					return true;
				}
				
				// Don't wrap if the statement is an assignment to a variable that will be 
				// handled by ASSIGNMENT_MAP (assignment to the produced variable)
				if (isAssignmentToProducedVariable(stmt, context, newVarName)) {
					return false;
				}
			}

			// Also wrap if there are multiple non-terminal side-effect statements
			int nonTerminalCount = statements.size() - currentIndex - 2;
			if (nonTerminalCount > 0) {
				for (int j = currentIndex + 1; j < statements.size() - 1; j++) {
					Statement stmt = statements.get(j);
					if (!(stmt instanceof VariableDeclarationStatement)) {
						// Check if it's an assignment that will be handled by ASSIGNMENT_MAP
						if (!isAssignmentToProducedVariable(stmt, context, newVarName)) {
							return true;
						}
					}
				}
			}

			return false;
		}
		
		/**
		 * Checks if a statement is an assignment to a variable that was declared 
		 * in the current context (will be produced by this MAP operation).
		 * 
		 * @param stmt the statement to check
		 * @param context the parsing context
		 * @param newVarName the name of the variable being declared by this MAP operation
		 * @return true if the statement is an assignment to newVarName
		 */
		private boolean isAssignmentToProducedVariable(Statement stmt, StatementParsingContext context, String newVarName) {
			if (!(stmt instanceof ExpressionStatement)) {
				return false;
			}
			Expression expr = ((ExpressionStatement) stmt).getExpression();
			if (!(expr instanceof Assignment)) {
				return false;
			}
			Assignment assignment = (Assignment) expr;
			if (!(assignment.getLeftHandSide() instanceof SimpleName)) {
				return false;
			}
			// Check if the LHS is the new variable name (the one we just declared)
			SimpleName lhs = (SimpleName) assignment.getLeftHandSide();
			return lhs.getIdentifier().equals(newVarName);
		}

		/**
		 * Wraps remaining non-terminal statements in a MAP operation.
		 */
		private LoopBodyParser.ParseResult wrapRemainingStatements(StatementParsingContext context,
				List<ProspectiveOperation> ops, String newVarName, StatementHandlerContext handlerContext) {
			
			List<Statement> statements = context.getAllStatements();
			int currentIndex = context.getCurrentIndex();
			ReducePatternDetector reduceDetector = context.getReduceDetector();

			// Collect non-terminal statements to wrap
			Block wrapBlock = context.getAst().newBlock();
			for (int j = currentIndex + 1; j < statements.size() - 1; j++) {
				Statement stmt = statements.get(j);
				wrapBlock.statements().add(ASTNode.copySubtree(context.getAst(), stmt));
			}

			// Create wrapped MAP operation
			if (wrapBlock.statements().size() > 0) {
				ProspectiveOperation wrappedMapOp = new ProspectiveOperation(wrapBlock,
						OperationType.MAP, newVarName);
				ops.add(wrappedMapOp);
			}

			// Process the terminal statement
			Statement lastStmt = statements.get(statements.size() - 1);
			ProspectiveOperation reduceOp = reduceDetector.detectReduceOperation(lastStmt);
			if (reduceOp != null) {
				reduceDetector.addMapBeforeReduce(ops, reduceOp, lastStmt, newVarName, context.getAst());
				ops.add(reduceOp);
			} else {
				if (handlerContext.getSideEffectChecker().isSafeSideEffect(lastStmt, newVarName, ops)) {
					ProspectiveOperation forEachOp = new ProspectiveOperation(lastStmt,
							OperationType.FOREACH, newVarName);
					ops.add(forEachOp);
				}
			}

			// Skip to the end
			return new LoopBodyParser.ParseResult(newVarName, statements.size() - 1);
		}
	},

	/**
	 * Handles assignment statements that transform local variables into MAP operations.
	 * 
	 * <p>This handler recognizes patterns where a variable declared within the loop body
	 * is reassigned with a transformation of itself, and converts them to MAP operations
	 * in the stream pipeline.</p>
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>{@code
	 * for (String str : strs) {
	 *     String s = "foo";        // VARIABLE_DECLARATION: map(_item -> "foo")
	 *     s = s.toString();        // ASSIGNMENT_MAP: map(s -> s.toString())
	 *     System.out.println(s);   // TERMINAL: forEachOrdered(...)
	 * }
	 * }</pre>
	 */
	ASSIGNMENT_MAP {
		@Override
		public boolean canHandle(Statement stmt, StatementParsingContext context) {
			// Only handle non-last statements
			if (context.isLastStatement()) {
				return false;
			}
			
			// Must be an ExpressionStatement with an Assignment
			if (!(stmt instanceof ExpressionStatement)) {
				return false;
			}
			
			Expression expr = ((ExpressionStatement) stmt).getExpression();
			if (!(expr instanceof Assignment)) {
				return false;
			}
			
			Assignment assignment = (Assignment) expr;
			
			// LHS must be a SimpleName (simple variable assignment)
			if (!(assignment.getLeftHandSide() instanceof SimpleName)) {
				return false;
			}
			
			SimpleName lhs = (SimpleName) assignment.getLeftHandSide();
			String varName = lhs.getIdentifier();
			
			// The variable being assigned must be the current pipeline variable
			return varName.equals(context.getCurrentVariableName());
		}

		@Override
		public LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context,
				List<ProspectiveOperation> ops, StatementHandlerContext handlerContext) {
			
			ExpressionStatement exprStmt = (ExpressionStatement) stmt;
			Assignment assignment = (Assignment) exprStmt.getExpression();
			SimpleName lhs = (SimpleName) assignment.getLeftHandSide();
			String varName = lhs.getIdentifier();
			
			// Create a MAP operation with the RHS as the transformation expression
			Expression rhs = assignment.getRightHandSide();
			
			ProspectiveOperation mapOp = new ProspectiveOperation(rhs,
					OperationType.MAP, varName);
			ops.add(mapOp);
			
			// Return the same variable name since the assignment doesn't change the variable name
			return new LoopBodyParser.ParseResult(varName);
		}
	},

	/**
	 * Handles IF statements during loop body parsing.
	 * 
	 * <p>This handler processes IF statements and converts them to appropriate
	 * stream operations based on their structure:</p>
	 * 
	 * <ul>
	 * <li><b>Early return patterns</b>: anyMatch, noneMatch, allMatch</li>
	 * <li><b>Continue patterns</b>: Converted to negated filters</li>
	 * <li><b>Regular IFs</b>: Converted to filter operations with nested processing</li>
	 * </ul>
	 */
	IF_STATEMENT {
		@Override
		public boolean canHandle(Statement stmt, StatementParsingContext context) {
			return stmt instanceof IfStatement;
		}

		@Override
		public LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context,
				List<ProspectiveOperation> ops, StatementHandlerContext handlerContext) {
			
			IfStatement ifStmt = (IfStatement) stmt;
			IfStatementAnalyzer ifAnalyzer = context.getIfAnalyzer();
			String currentVarName = context.getCurrentVariableName();
			String loopVarName = context.getLoopVariableName();

			// IF with else cannot be converted
			if (ifStmt.getElseStatement() != null) {
				return new LoopBodyParser.ParseResult(currentVarName);
			}

			// Check for unconvertible patterns first
			if (ifAnalyzer.isIfWithLabeledContinue(ifStmt)) {
				return LoopBodyParser.ParseResult.abort();
			}
			if (ifAnalyzer.isIfWithBreak(ifStmt)) {
				return LoopBodyParser.ParseResult.abort();
			}
			// Check for return statements that can't be converted (return null, return someValue)
			if (ifAnalyzer.isIfWithUnconvertibleReturn(ifStmt)) {
				return LoopBodyParser.ParseResult.abort();
			}
			// Check for assignments to external variables inside the IF body
			if (ifAnalyzer.isIfWithExternalAssignment(ifStmt, loopVarName)) {
				return LoopBodyParser.ParseResult.abort();
			}

			// Try early return pattern (anyMatch/noneMatch/allMatch)
			if (tryHandleEarlyReturn(ifStmt, context, ops)) {
				return new LoopBodyParser.ParseResult(currentVarName);
			}

			// Try continue pattern (filter)
			if (tryHandleContinue(ifStmt, context, ops)) {
				return new LoopBodyParser.ParseResult(currentVarName);
			}

			// Regular IF: convert to filter with nested processing
			return handleRegularIf(ifStmt, context, ops, handlerContext);
		}
		
		/**
		 * Handles early return patterns (anyMatch/noneMatch/allMatch).
		 */
		private boolean tryHandleEarlyReturn(IfStatement ifStmt, StatementParsingContext context,
				List<ProspectiveOperation> ops) {
			
			IfStatementAnalyzer ifAnalyzer = context.getIfAnalyzer();
			
			if (ifAnalyzer.isEarlyReturnIf(ifStmt, 
					context.isAnyMatchPattern(), 
					context.isNoneMatchPattern(), 
					context.isAllMatchPattern())) {
				ProspectiveOperation matchOp = ifAnalyzer.createMatchOperation(ifStmt);
				ops.add(matchOp);
				return true;
			}
			return false;
		}
		
		/**
		 * Handles continue patterns (negated filter).
		 */
		private boolean tryHandleContinue(IfStatement ifStmt, StatementParsingContext context,
				List<ProspectiveOperation> ops) {
			
			IfStatementAnalyzer ifAnalyzer = context.getIfAnalyzer();
			
			if (ifAnalyzer.isIfWithContinue(ifStmt)) {
				// Negate the condition for the filter
				Expression condition = ifStmt.getExpression();
				Expression negatedCondition = ExpressionHelper.createNegatedExpression(context.getAst(), condition);
				
				ProspectiveOperation filterOp = new ProspectiveOperation(negatedCondition,
						OperationType.FILTER);
				ops.add(filterOp);
				return true;
			}
			return false;
		}
		
		/**
		 * Handles regular IF statements (filter with nested processing).
		 */
		private LoopBodyParser.ParseResult handleRegularIf(IfStatement ifStmt, StatementParsingContext context,
				List<ProspectiveOperation> ops, StatementHandlerContext handlerContext) {
			
			String currentVarName = context.getCurrentVariableName();
			
			// Create filter operation
			ProspectiveOperation filterOp = new ProspectiveOperation(ifStmt.getExpression(),
					OperationType.FILTER);
			ops.add(filterOp);

			// Recursively parse the then-branch
			List<ProspectiveOperation> nestedOps = handlerContext.getParser().parse(
					ifStmt.getThenStatement(), currentVarName);
			ops.addAll(nestedOps);
			
			return new LoopBodyParser.ParseResult(currentVarName);
		}
	},

	/**
	 * Handles non-terminal statements (not the last statement in a block).
	 * 
	 * <p>Non-terminal statements that are not variable declarations or IF statements
	 * are typically side-effect statements that need to be wrapped in a MAP operation
	 * that returns the current variable.</p>
	 */
	NON_TERMINAL {
		@Override
		public boolean canHandle(Statement stmt, StatementParsingContext context) {
			// This handler is for non-last statements that are not IF or VariableDeclaration
			return !context.isLastStatement() 
					&& !(stmt instanceof IfStatement)
					&& !(stmt instanceof VariableDeclarationStatement);
		}

		@Override
		public LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context,
				List<ProspectiveOperation> ops, StatementHandlerContext handlerContext) {
			
			ReducePatternDetector reduceDetector = context.getReduceDetector();
			String currentVarName = context.getCurrentVariableName();
			SideEffectChecker sideEffectChecker = handlerContext.getSideEffectChecker();

			// Check if this is a REDUCE operation (shouldn't be in middle of block, but check anyway)
			ProspectiveOperation reduceCheck = reduceDetector.detectReduceOperation(stmt);
			if (reduceCheck != null) {
				// REDUCE in middle of block - just continue, don't add it
				return new LoopBodyParser.ParseResult(currentVarName);
			}

			// Check if this is a safe side-effect
			if (!sideEffectChecker.isSafeSideEffect(stmt, currentVarName, ops)) {
				return LoopBodyParser.ParseResult.abort();
			}

			// Create a MAP operation with side effect that returns the current variable
			ProspectiveOperation mapOp = new ProspectiveOperation(stmt,
					OperationType.MAP, currentVarName);
			ops.add(mapOp);

			return new LoopBodyParser.ParseResult(currentVarName);
		}
	},

	/**
	 * Handles terminal statements (last statement in a block).
	 * 
	 * <p>Terminal statements can be:</p>
	 * <ul>
	 * <li><b>REDUCE operations</b>: Accumulator patterns (i++, sum += x, etc.)</li>
	 * <li><b>FOREACH operations</b>: Side-effect statements that can be safely converted</li>
	 * <li><b>Unconvertible</b>: Return, continue, throw statements</li>
	 * </ul>
	 */
	TERMINAL {
		@Override
		public boolean canHandle(Statement stmt, StatementParsingContext context) {
			// This handler is for last statements only
			return context.isLastStatement();
		}

		@Override
		public LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context,
				List<ProspectiveOperation> ops, StatementHandlerContext handlerContext) {
			
			CollectPatternDetector collectDetector = context.getCollectDetector();
			ReducePatternDetector reduceDetector = context.getReduceDetector();
			String currentVarName = context.getCurrentVariableName();
			SideEffectChecker sideEffectChecker = handlerContext.getSideEffectChecker();

			// Try COLLECT pattern first
			ProspectiveOperation collectOp = collectDetector.detectCollectOperation(stmt);
			if (collectOp != null) {
				// For COLLECT, we might need a MAP operation before it if the added expression is not identity
				Expression addedExpr = collectDetector.extractCollectExpression(stmt);
				if (addedExpr != null && !isIdentityMapping(addedExpr, currentVarName)) {
					// Create a MAP operation for the transformation
					ProspectiveOperation mapOp = new ProspectiveOperation(addedExpr, OperationType.MAP, currentVarName);
					ops.add(mapOp);
				}
				ops.add(collectOp);
				return new LoopBodyParser.ParseResult(currentVarName);
			}

			// Try REDUCE pattern
			ProspectiveOperation reduceOp = reduceDetector.detectReduceOperation(stmt);
			if (reduceOp != null) {
				reduceDetector.addMapBeforeReduce(ops, reduceOp, stmt, currentVarName, context.getAst());
				ops.add(reduceOp);
				return new LoopBodyParser.ParseResult(currentVarName);
			}

			// Check for unconvertible statements
			if (isUnconvertibleStatement(stmt)) {
				return LoopBodyParser.ParseResult.abort();
			}

			// Check for unsafe side effects
			if (!sideEffectChecker.isSafeSideEffect(stmt, currentVarName, ops)) {
				return LoopBodyParser.ParseResult.abort();
			}

			// Regular FOREACH operation
			ProspectiveOperation forEachOp = new ProspectiveOperation(stmt,
					OperationType.FOREACH, currentVarName);
			ops.add(forEachOp);
			
			return new LoopBodyParser.ParseResult(currentVarName);
		}
		
		/**
		 * Checks if an expression is an identity mapping (just returns the variable unchanged).
		 */
		private boolean isIdentityMapping(Expression expr, String varName) {
			if (expr instanceof SimpleName) {
				return varName.equals(((SimpleName) expr).getIdentifier());
			}
			return false;
		}
		
		/**
		 * Checks if a statement cannot be converted to stream operations.
		 */
		private boolean isUnconvertibleStatement(Statement stmt) {
			return stmt instanceof ReturnStatement 
					|| stmt instanceof ContinueStatement 
					|| stmt instanceof ThrowStatement;
		}
	};

	/**
	 * Checks if this handler can process the given statement.
	 * 
	 * @param stmt    the statement to check
	 * @param context the parsing context
	 * @return true if this handler can process the statement
	 */
	public abstract boolean canHandle(Statement stmt, StatementParsingContext context);

	/**
	 * Handles the statement and adds operations to the list.
	 * 
	 * @param stmt           the statement to process
	 * @param context        the parsing context
	 * @param ops            the list of operations to add to
	 * @param handlerContext the context containing handler dependencies
	 * @return the parse result
	 */
	public abstract LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context, 
			List<ProspectiveOperation> ops, StatementHandlerContext handlerContext);
	
	/**
	 * Finds the first handler that can handle the given statement.
	 * 
	 * @param stmt    the statement to find a handler for
	 * @param context the parsing context
	 * @return the handler type, or null if no handler can handle the statement
	 */
	public static StatementHandlerType findHandler(Statement stmt, StatementParsingContext context) {
		for (StatementHandlerType handler : values()) {
			if (handler.canHandle(stmt, context)) {
				return handler;
			}
		}
		return null;
	}
}
