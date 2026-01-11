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

import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.ThrowStatement;

/**
 * Handles terminal statements (last statement in a block) during loop body parsing.
 * 
 * <p>Terminal statements can be:</p>
 * <ul>
 * <li><b>REDUCE operations</b>: Accumulator patterns (i++, sum += x, etc.)</li>
 * <li><b>FOREACH operations</b>: Side-effect statements that can be safely converted</li>
 * <li><b>Unconvertible</b>: Return, continue, throw statements</li>
 * </ul>
 * 
 * @see StatementHandler
 * @see ReducePatternDetector
 */
public class TerminalStatementHandler implements StatementHandler {

	private final SideEffectChecker sideEffectChecker;

	/**
	 * Creates a new TerminalStatementHandler.
	 * 
	 * @param sideEffectChecker the side effect checker
	 */
	public TerminalStatementHandler(SideEffectChecker sideEffectChecker) {
		this.sideEffectChecker = sideEffectChecker;
	}

	@Override
	public boolean canHandle(Statement stmt, StatementParsingContext context) {
		// This handler is for last statements only
		return context.isLastStatement();
	}

	@Override
	public LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context,
			List<ProspectiveOperation> ops) {
		
		ReducePatternDetector reduceDetector = context.getReduceDetector();
		String currentVarName = context.getCurrentVariableName();

		// Try REDUCE pattern first
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
				ProspectiveOperation.OperationType.FOREACH, currentVarName);
		ops.add(forEachOp);
		
		return new LoopBodyParser.ParseResult(currentVarName);
	}

	/**
	 * Checks if a statement cannot be converted to stream operations.
	 */
	private boolean isUnconvertibleStatement(Statement stmt) {
		return stmt instanceof ReturnStatement 
				|| stmt instanceof ContinueStatement 
				|| stmt instanceof ThrowStatement;
	}
}
