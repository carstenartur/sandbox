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
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Handles variable declaration statements during loop body parsing.
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
 * 
 * @see StatementHandler
 */
public class VariableDeclarationHandler implements StatementHandler {

	private final LoopBodyParser parser;

	/**
	 * Creates a new VariableDeclarationHandler.
	 * 
	 * @param parser the loop body parser (for checking wrap requirements)
	 */
	public VariableDeclarationHandler(LoopBodyParser parser) {
		this.parser = parser;
	}

	@Override
	public boolean canHandle(Statement stmt, StatementParsingContext context) {
		return stmt instanceof VariableDeclarationStatement;
	}

	@Override
	public LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context,
			List<ProspectiveOperation> ops) {
		
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
				ProspectiveOperation.OperationType.MAP, newVarName);
		ops.add(mapOp);

		// Check if we need to wrap remaining non-terminal statements in a MAP
		if (shouldWrapRemaining(context)) {
			return wrapRemainingStatements(context, ops, newVarName);
		}

		return new LoopBodyParser.ParseResult(newVarName);
	}

	/**
	 * Checks if remaining statements should be wrapped in a single MAP operation.
	 */
	private boolean shouldWrapRemaining(StatementParsingContext context) {
		List<Statement> statements = context.getAllStatements();
		int currentIndex = context.getCurrentIndex();
		
		if (statements == null || currentIndex >= statements.size() - 2) {
			return false;
		}

		IfStatementAnalyzer ifAnalyzer = context.getIfAnalyzer();

		// Look through remaining non-terminal statements
		for (int j = currentIndex + 1; j < statements.size() - 1; j++) {
			Statement stmt = statements.get(j);

			if (stmt instanceof org.eclipse.jdt.core.dom.IfStatement) {
				org.eclipse.jdt.core.dom.IfStatement ifStmt = (org.eclipse.jdt.core.dom.IfStatement) stmt;

				// Don't wrap if this is an early return IF or continue IF
				if (ifAnalyzer.isEarlyReturnIf(ifStmt, 
						context.isAnyMatchPattern(), 
						context.isNoneMatchPattern(), 
						context.isAllMatchPattern())) {
					return false;
				}
				if (ifAnalyzer.isIfWithContinue(ifStmt)) {
					return false;
				}
				if (ifStmt.getElseStatement() == null) {
					return true;
				}
			}
			
			// Don't wrap if the statement is an assignment to a variable that will be 
			// handled by AssignmentMapHandler (assignment to current pipeline variable)
			if (isAssignmentToProducedVariable(stmt, context)) {
				return false;
			}
		}

		// Also wrap if there are multiple non-terminal side-effect statements
		int nonTerminalCount = statements.size() - currentIndex - 2;
		if (nonTerminalCount > 0) {
			for (int j = currentIndex + 1; j < statements.size() - 1; j++) {
				Statement stmt = statements.get(j);
				if (!(stmt instanceof VariableDeclarationStatement)) {
					// Check if it's an assignment that will be handled by AssignmentMapHandler
					if (!isAssignmentToProducedVariable(stmt, context)) {
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
	 */
	private boolean isAssignmentToProducedVariable(Statement stmt, StatementParsingContext context) {
		if (!(stmt instanceof org.eclipse.jdt.core.dom.ExpressionStatement)) {
			return false;
		}
		
		org.eclipse.jdt.core.dom.Expression expr = 
				((org.eclipse.jdt.core.dom.ExpressionStatement) stmt).getExpression();
		if (!(expr instanceof org.eclipse.jdt.core.dom.Assignment)) {
			return false;
		}
		
		org.eclipse.jdt.core.dom.Assignment assignment = (org.eclipse.jdt.core.dom.Assignment) expr;
		org.eclipse.jdt.core.dom.Expression lhs = assignment.getLeftHandSide();
		
		if (!(lhs instanceof org.eclipse.jdt.core.dom.SimpleName)) {
			return false;
		}
		
		// Get the variable name being assigned to
		String varName = ((org.eclipse.jdt.core.dom.SimpleName) lhs).getIdentifier();
		
		// Get the variable name that will be produced by the current variable declaration
		// (This is the name of the variable being declared in this handler)
		List<Statement> statements = context.getAllStatements();
		int currentIndex = context.getCurrentIndex();
		if (statements != null && currentIndex < statements.size()) {
			Statement currentStmt = statements.get(currentIndex);
			if (currentStmt instanceof VariableDeclarationStatement) {
				VariableDeclarationStatement varDecl = (VariableDeclarationStatement) currentStmt;
				List<?> fragments = varDecl.fragments();
				if (!fragments.isEmpty()) {
					VariableDeclarationFragment frag = (VariableDeclarationFragment) fragments.get(0);
					String producedVarName = frag.getName().getIdentifier();
					return varName.equals(producedVarName);
				}
			}
		}
		
		return false;
	}

	/**
	 * Wraps remaining statements in a MAP operation that returns the current variable.
	 */
	private LoopBodyParser.ParseResult wrapRemainingStatements(StatementParsingContext context,
			List<ProspectiveOperation> ops, String newVarName) {
		
		List<Statement> statements = context.getAllStatements();
		int currentIndex = context.getCurrentIndex();

		Block mapBlock = context.getAst().newBlock();
		List<Statement> mapStatements = mapBlock.statements();
		
		for (int j = currentIndex + 1; j < statements.size() - 1; j++) {
			mapStatements.add((Statement) ASTNode.copySubtree(context.getAst(), statements.get(j)));
		}

		ProspectiveOperation sideEffectMapOp = new ProspectiveOperation(mapBlock,
				ProspectiveOperation.OperationType.MAP, newVarName);
		ops.add(sideEffectMapOp);

		return new LoopBodyParser.ParseResult(newVarName, statements.size() - 2);
	}
}
