/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer.
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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;

/**
 * Side Effect Analyzer - Analyzes whether code replacement is semantically safe
 * 
 * This class checks for side effects and ensures that replacing an inline code
 * sequence with a method call preserves the original semantics.
 */
public class SideEffectAnalyzer {
	
	/**
	 * Check if a sequence of statements is safe to replace with a method call
	 * 
	 * @param statements The statements to analyze
	 * @return true if replacement is safe
	 */
	public static boolean isSafeToReplace(List<Statement> statements) {
		if (statements == null || statements.isEmpty()) {
			return false;
		}
		
		// Check each statement for problematic patterns
		for (Statement stmt : statements) {
			if (!isSafeStatement(stmt)) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Check if a single statement is safe
	 */
	private static boolean isSafeStatement(Statement stmt) {
		if (stmt == null) {
			return false;
		}
		
		// Check for field modifications
		if (hasFieldModifications(stmt)) {
			return false;
		}
		
		// Check for method calls that might have side effects
		if (hasUnsafeMethodCalls(stmt)) {
			return false;
		}
		
		// Check for complex control flow
		if (hasComplexControlFlow(stmt)) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Check if statement modifies fields (not local variables)
	 */
	private static boolean hasFieldModifications(Statement stmt) {
		FieldModificationVisitor visitor = new FieldModificationVisitor();
		stmt.accept(visitor);
		return visitor.hasFieldModification;
	}
	
	/**
	 * Check if statement contains method calls that might have side effects
	 * 
	 * Note: This is a conservative check. We allow known safe methods like
	 * String.trim(), but reject most other method calls to be safe.
	 */
	private static boolean hasUnsafeMethodCalls(Statement stmt) {
		UnsafeMethodCallVisitor visitor = new UnsafeMethodCallVisitor();
		stmt.accept(visitor);
		return visitor.hasUnsafeCall;
	}
	
	/**
	 * Check for complex control flow (loops, try-catch, etc.)
	 */
	private static boolean hasComplexControlFlow(Statement stmt) {
		// Simple check: reject if statement contains certain node types
		int nodeType = stmt.getNodeType();
		
		// These statement types indicate complex control flow
		switch (nodeType) {
			case ASTNode.FOR_STATEMENT:
			case ASTNode.ENHANCED_FOR_STATEMENT:
			case ASTNode.WHILE_STATEMENT:
			case ASTNode.DO_STATEMENT:
			case ASTNode.TRY_STATEMENT:
			case ASTNode.SYNCHRONIZED_STATEMENT:
			case ASTNode.SWITCH_STATEMENT:
			case ASTNode.BREAK_STATEMENT:
			case ASTNode.CONTINUE_STATEMENT:
				return true;
			default:
				return false;
		}
	}
	
	/**
	 * Visitor to detect field modifications
	 */
	private static class FieldModificationVisitor extends ASTVisitor {
		boolean hasFieldModification = false;
		
		@Override
		public boolean visit(Assignment node) {
			Expression left = node.getLeftHandSide();
			if (isFieldAccess(left)) {
				hasFieldModification = true;
				return false;
			}
			return true;
		}
		
		@Override
		public boolean visit(PrefixExpression node) {
			// Check for ++ and -- on fields
			PrefixExpression.Operator op = node.getOperator();
			if (op == PrefixExpression.Operator.INCREMENT || op == PrefixExpression.Operator.DECREMENT) {
				if (isFieldAccess(node.getOperand())) {
					hasFieldModification = true;
					return false;
				}
			}
			return true;
		}
		
		@Override
		public boolean visit(PostfixExpression node) {
			// Check for ++ and -- on fields
			if (isFieldAccess(node.getOperand())) {
				hasFieldModification = true;
				return false;
			}
			return true;
		}
		
		private boolean isFieldAccess(Expression expr) {
			return expr instanceof FieldAccess || 
			       expr instanceof SuperFieldAccess ||
			       (expr instanceof QualifiedName && isFieldReference((QualifiedName) expr));
		}
		
		private boolean isFieldReference(QualifiedName name) {
			// Simple heuristic: qualified names might be field accesses
			// More sophisticated analysis would use type bindings
			return true;
		}
	}
	
	/**
	 * Visitor to detect potentially unsafe method calls
	 */
	private static class UnsafeMethodCallVisitor extends ASTVisitor {
		boolean hasUnsafeCall = false;
		
		@Override
		public boolean visit(MethodInvocation node) {
			// Check if this is a known safe method
			if (!isKnownSafeMethod(node)) {
				hasUnsafeCall = true;
				return false;
			}
			return true;
		}
		
		@Override
		public boolean visit(SuperMethodInvocation node) {
			// Super method calls might have side effects
			hasUnsafeCall = true;
			return false;
		}
		
		/**
		 * Check if a method is known to be side-effect free
		 */
		private boolean isKnownSafeMethod(MethodInvocation node) {
			String methodName = node.getName().getIdentifier();
			
			// Known safe String methods
			if ("trim".equals(methodName) || "length".equals(methodName) || 
			    "isEmpty".equals(methodName) || "toString".equals(methodName)) {
				return true;
			}
			
			// For now, be conservative and reject other methods
			// A more sophisticated analysis would check method purity
			return false;
		}
	}
}
