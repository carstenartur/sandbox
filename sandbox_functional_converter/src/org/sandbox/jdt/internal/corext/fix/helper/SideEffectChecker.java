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
 * Checks if a statement is a safe side-effect that can be included in a stream pipeline.
 * 
 * <p>A side-effect is considered safe if it doesn't modify variables that would
 * violate stream semantics (e.g., loop variables, accumulator variables, or
 * external variables).</p>
 * 
 * <p><b>Safe side-effects:</b></p>
 * <ul>
 * <li>Method calls (System.out.println, logging, etc.)</li>
 * <li>Array element assignments</li>
 * <li>Field assignments</li>
 * </ul>
 * 
 * <p><b>Unsafe side-effects:</b></p>
 * <ul>
 * <li>Assignments to the current pipeline variable</li>
 * <li>Assignments to accumulator variables</li>
 * <li>Assignments to external simple variables</li>
 * </ul>
 * 
 * @see LoopBodyParser
 * @see ProspectiveOperation
 */
public class SideEffectChecker {

	/**
	 * Checks if a statement is a safe side-effect.
	 * 
	 * @param stmt           the statement to check
	 * @param currentVarName the current variable name in the pipeline
	 * @param operations     the list of operations (to check for accumulators)
	 * @return true if safe, false otherwise
	 */
	public boolean isSafeSideEffect(Statement stmt, String currentVarName, 
			List<ProspectiveOperation> operations) {
		
		if (stmt == null) {
			return false;
		}

		if (!(stmt instanceof ExpressionStatement)) {
			return false;
		}

		ExpressionStatement exprStmt = (ExpressionStatement) stmt;
		Expression expr = exprStmt.getExpression();

		if (expr == null) {
			return false;
		}

		// Check for assignments
		if (expr instanceof Assignment) {
			return isAssignmentSafe((Assignment) expr, currentVarName, operations);
		}

		// Method calls and other expressions are generally safe
		return true;
	}

	/**
	 * Checks if an assignment is safe for stream conversion.
	 */
	private boolean isAssignmentSafe(Assignment assignment, String currentVarName,
			List<ProspectiveOperation> operations) {
		
		Expression lhs = assignment.getLeftHandSide();

		// Only validate SimpleName assignments (simple variables)
		// Array access and field access are conservatively allowed
		if (lhs instanceof SimpleName) {
			String varName = ((SimpleName) lhs).getIdentifier();

			// Assignment to current pipeline variable is unsafe
			if (varName.equals(currentVarName)) {
				return false;
			}

			// Assignment to accumulator variables is handled by REDUCE
			if (isAccumulatorVariable(varName, operations)) {
				return false;
			}

			// Other assignments to external variables are unsafe
			return false;
		}

		// Non-SimpleName assignments (array access, field access) are allowed
		return true;
	}

	/**
	 * Checks if a variable is an accumulator variable in any REDUCE operation.
	 */
	private boolean isAccumulatorVariable(String varName, List<ProspectiveOperation> operations) {
		for (ProspectiveOperation op : operations) {
				if (op.getOperationType() == OperationType.REDUCE) {
				if (varName.equals(op.getAccumulatorVariableName())) {
					return true;
				}
			}
		}
		return false;
	}
}
