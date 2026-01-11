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

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Statement;

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
 * 
 * <p><b>Rejection cases:</b></p>
 * <ul>
 * <li>IF with else branch (cannot be cleanly converted)</li>
 * <li>IF with labeled continue</li>
 * <li>IF with break statement</li>
 * </ul>
 * 
 * @see StatementHandler
 * @see IfStatementAnalyzer
 */
public class IfStatementHandler implements StatementHandler {

	private final LoopBodyParser parser;

	/**
	 * Creates a new IfStatementHandler.
	 * 
	 * @param parser the loop body parser (for recursive parsing of nested statements)
	 */
	public IfStatementHandler(LoopBodyParser parser) {
		this.parser = parser;
	}

	@Override
	public boolean canHandle(Statement stmt, StatementParsingContext context) {
		return stmt instanceof IfStatement;
	}

	@Override
	public LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context,
			List<ProspectiveOperation> ops) {
		
		IfStatement ifStmt = (IfStatement) stmt;
		IfStatementAnalyzer ifAnalyzer = context.getIfAnalyzer();
		String currentVarName = context.getCurrentVariableName();

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

		// Try early return pattern (anyMatch/noneMatch/allMatch)
		if (tryHandleEarlyReturn(ifStmt, context, ops)) {
			return new LoopBodyParser.ParseResult(currentVarName);
		}

		// Try continue pattern (filter)
		if (tryHandleContinue(ifStmt, context, ops)) {
			return new LoopBodyParser.ParseResult(currentVarName);
		}

		// Regular filter with nested processing
		return handleRegularFilter(ifStmt, context, ops);
	}

	/**
	 * Handles early return patterns (anyMatch/noneMatch/allMatch).
	 * 
	 * @return true if handled, false otherwise
	 */
	private boolean tryHandleEarlyReturn(IfStatement ifStmt, StatementParsingContext context,
			List<ProspectiveOperation> ops) {
		
		IfStatementAnalyzer ifAnalyzer = context.getIfAnalyzer();
		
		if (ifAnalyzer.isEarlyReturnIf(ifStmt, 
				context.isAnyMatchPattern(), 
				context.isNoneMatchPattern(), 
				context.isAllMatchPattern())) {
			
			ProspectiveOperation matchOp = ifAnalyzer.createMatchOperation(ifStmt);
			if (matchOp != null) {
				ops.add(matchOp);
				return true;
			}
		}
		return false;
	}

	/**
	 * Handles continue patterns (converted to negated filter).
	 * 
	 * @return true if handled, false otherwise
	 */
	private boolean tryHandleContinue(IfStatement ifStmt, StatementParsingContext context,
			List<ProspectiveOperation> ops) {
		
		IfStatementAnalyzer ifAnalyzer = context.getIfAnalyzer();
		
		if (ifAnalyzer.isIfWithContinue(ifStmt)) {
			Expression negatedCondition = ExpressionUtils.createNegatedExpression(
					context.getAst(), ifStmt.getExpression());
			ProspectiveOperation filterOp = new ProspectiveOperation(negatedCondition,
					ProspectiveOperation.OperationType.FILTER);
			ops.add(filterOp);
			return true;
		}
		return false;
	}

	/**
	 * Handles regular IF statements as filter operations with nested body processing.
	 */
	private LoopBodyParser.ParseResult handleRegularFilter(IfStatement ifStmt, 
			StatementParsingContext context, List<ProspectiveOperation> ops) {
		
		String currentVarName = context.getCurrentVariableName();
		boolean updateVarName = !context.isLastStatement();

		// Add FILTER operation for the condition
		ProspectiveOperation filterOp = new ProspectiveOperation(ifStmt.getExpression(),
				ProspectiveOperation.OperationType.FILTER);
		ops.add(filterOp);

		// Process the body of the IF statement recursively
		List<ProspectiveOperation> nestedOps = parser.parse(ifStmt.getThenStatement(), currentVarName);
		ops.addAll(nestedOps);

		// Update current var name if needed and nested operations produced a new variable
		if (updateVarName && !nestedOps.isEmpty()) {
			ProspectiveOperation lastNested = nestedOps.get(nestedOps.size() - 1);
			if (lastNested.getProducedVariableName() != null) {
				return new LoopBodyParser.ParseResult(lastNested.getProducedVariableName());
			}
		}

		return new LoopBodyParser.ParseResult(currentVarName);
	}
}
