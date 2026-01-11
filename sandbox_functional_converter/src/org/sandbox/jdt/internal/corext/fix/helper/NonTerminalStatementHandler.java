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

import org.eclipse.jdt.core.dom.Statement;

/**
 * Handles non-terminal statements (not the last statement in a block).
 * 
 * <p>Non-terminal statements that are not variable declarations or IF statements
 * are typically side-effect statements that need to be wrapped in a MAP operation
 * that returns the current variable.</p>
 * 
 * <p><b>Example:</b></p>
 * <pre>{@code
 * for (String item : items) {
 *     System.out.println(item);  // non-terminal side-effect
 *     doSomething(item);         // → wrapped in .map(item -> { println(item); return item; })
 * }
 * }</pre>
 * 
 * @see StatementHandler
 * @see SideEffectChecker
 */
public class NonTerminalStatementHandler implements StatementHandler {

	private final SideEffectChecker sideEffectChecker;

	/**
	 * Creates a new NonTerminalStatementHandler.
	 * 
	 * @param sideEffectChecker the side effect checker
	 */
	public NonTerminalStatementHandler(SideEffectChecker sideEffectChecker) {
		this.sideEffectChecker = sideEffectChecker;
	}

	@Override
	public boolean canHandle(Statement stmt, StatementParsingContext context) {
		// This handler is for non-last statements that are not IF or VariableDeclaration
		return !context.isLastStatement() 
				&& !(stmt instanceof org.eclipse.jdt.core.dom.IfStatement)
				&& !(stmt instanceof org.eclipse.jdt.core.dom.VariableDeclarationStatement);
	}

	@Override
	public LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context,
			List<ProspectiveOperation> ops) {
		
		ReducePatternDetector reduceDetector = context.getReduceDetector();
		String currentVarName = context.getCurrentVariableName();

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
				ProspectiveOperation.OperationType.MAP, currentVarName);
		ops.add(mapOp);

		return new LoopBodyParser.ParseResult(currentVarName);
	}
}
