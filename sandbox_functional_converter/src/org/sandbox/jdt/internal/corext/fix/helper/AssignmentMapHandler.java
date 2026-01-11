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

import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;

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
 *     String s = "foo";        // VariableDeclarationHandler: map(_item -> "foo")
 *     s = s.toString();        // AssignmentMapHandler: map(s -> s.toString())
 *     System.out.println(s);   // TerminalStatementHandler: forEachOrdered(...)
 * }
 * }</pre>
 * 
 * <p>Becomes:</p>
 * <pre>{@code
 * strs.stream()
 *     .map(_item -> "foo")
 *     .map(s -> s.toString())
 *     .forEachOrdered(s -> System.out.println(s));
 * }</pre>
 * 
 * <p><b>Conditions for handling:</b></p>
 * <ul>
 * <li>The statement is an ExpressionStatement containing an Assignment</li>
 * <li>The assignment target (LHS) is the current pipeline variable</li>
 * <li>This is not the last statement in the block</li>
 * </ul>
 * 
 * @see StatementHandler
 * @see VariableDeclarationHandler
 */
public class AssignmentMapHandler implements StatementHandler {

	/**
	 * Creates a new AssignmentMapHandler.
	 */
	public AssignmentMapHandler() {
		// No dependencies needed
	}

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
		// This ensures we're transforming a variable that was declared earlier
		// in the loop body (e.g., s = s.toString() where s was declared as String s = "foo")
		return varName.equals(context.getCurrentVariableName());
	}

	@Override
	public LoopBodyParser.ParseResult handle(Statement stmt, StatementParsingContext context,
			List<ProspectiveOperation> ops) {
		
		ExpressionStatement exprStmt = (ExpressionStatement) stmt;
		Assignment assignment = (Assignment) exprStmt.getExpression();
		SimpleName lhs = (SimpleName) assignment.getLeftHandSide();
		String varName = lhs.getIdentifier();
		
		// Create a MAP operation with the RHS as the transformation expression
		// The variable name stays the same (s -> s.toString() produces s)
		Expression rhs = assignment.getRightHandSide();
		
		ProspectiveOperation mapOp = new ProspectiveOperation(rhs,
				ProspectiveOperation.OperationType.MAP, varName);
		ops.add(mapOp);
		
		// Return the same variable name since the assignment doesn't change the variable name
		return new LoopBodyParser.ParseResult(varName);
	}
}
