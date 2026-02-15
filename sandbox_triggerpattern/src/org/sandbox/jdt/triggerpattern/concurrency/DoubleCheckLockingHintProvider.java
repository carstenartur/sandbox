/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.concurrency;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.swt.graphics.Image;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.eclipse.HintContext;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;

/**
 * Hint provider for detecting the double-checked locking anti-pattern.
 *
 * <p>Double-checked locking is a concurrency pattern where a field is checked
 * for null, then a synchronized block is entered, and the field is checked
 * again. This pattern can be problematic in Java without proper memory
 * visibility guarantees.</p>
 *
 * <p>Inspired by the
 * <a href="https://github.com/apache/netbeans/blob/master/java/java.hints/src/org/netbeans/modules/java/hints/DoubleCheck.java">
 * NetBeans DoubleCheck hint</a>.</p>
 *
 * <h3>Detected Pattern</h3>
 * <pre>
 * if (field == null) {
 *     synchronized (lock) {
 *         if (field == null) {
 *             field = new Something();
 *         }
 *     }
 * }
 * </pre>
 *
 * <h3>Suggested Fix</h3>
 * <p>The fix removes the outer null check, keeping only the synchronized block
 * with the inner null check. This eliminates the double-checked locking pattern
 * at the cost of always entering the synchronized block:</p>
 * <pre>
 * synchronized (lock) {
 *     if (field == null) {
 *         field = new Something();
 *     }
 * }
 * </pre>
 *
 * @since 1.2.5
 * @see <a href="https://en.wikipedia.org/wiki/Double-checked_locking">Double-checked locking (Wikipedia)</a>
 */
public class DoubleCheckLockingHintProvider {

	/**
	 * Detects the double-checked locking pattern and suggests removing the outer
	 * null check to eliminate the anti-pattern.
	 *
	 * <p>The expression-level pattern {@code $field == null} is used as the entry
	 * point. When a null check is found, the method walks up the AST tree to verify
	 * the full double-checked locking structure: an outer {@code if} wrapping a
	 * {@code synchronized} block that contains an inner {@code if} with the same
	 * null check condition.</p>
	 *
	 * <p><b>Before:</b></p>
	 * <pre>
	 * if (field == null) {
	 *     synchronized (lock) {
	 *         if (field == null) {
	 *             field = new Something();
	 *         }
	 *     }
	 * }
	 * </pre>
	 *
	 * <p><b>After:</b></p>
	 * <pre>
	 * synchronized (lock) {
	 *     if (field == null) {
	 *         field = new Something();
	 *     }
	 * }
	 * </pre>
	 *
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "$field == null", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Double-checked locking",
	      description = "Detects double-checked locking pattern. "
	                   + "Suggests removing the outer null check to use plain synchronization instead.",
	      category = "concurrency",
	      suppressWarnings = "DoubleCheckedLocking")
	public static IJavaCompletionProposal detectDoubleCheckLocking(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof InfixExpression)) {
			return null;
		}

		// Walk up to find the enclosing IfStatement
		IfStatement innerIf = findEnclosingIf(matchedNode);
		if (innerIf == null || innerIf.getElseStatement() != null) {
			return null;
		}

		// Check that this inner if is inside a synchronized block
		SynchronizedStatement syncStmt = findEnclosingSynchronized(innerIf);
		if (syncStmt == null) {
			return null;
		}

		// Check that the synchronized block is inside an outer if with the same condition
		IfStatement outerIf = findEnclosingIf(syncStmt);
		if (outerIf == null || outerIf.getElseStatement() != null) {
			return null;
		}

		// Verify both if conditions are null checks on the same expression
		if (!isSameNullCheck(outerIf.getExpression(), innerIf.getExpression())) {
			return null;
		}

		// Create the fix: replace the outer if with the synchronized block
		ASTRewrite rewrite = ctx.getASTRewrite();
		AST ast = rewrite.getAST();

		SynchronizedStatement replacement = (SynchronizedStatement) ASTNode.copySubtree(ast, syncStmt);
		rewrite.replace(outerIf, replacement, null);

		// Create the proposal
		String label = "Remove outer null check (double-checked locking)"; //$NON-NLS-1$
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(
			label,
			ctx.getICompilationUnit(),
			rewrite,
			10, // relevance
			(Image) null
		);

		return proposal;
	}

	/**
	 * Finds the nearest enclosing {@link IfStatement} for a given node.
	 *
	 * @param node the starting node
	 * @return the enclosing IfStatement, or null if not found
	 */
	private static IfStatement findEnclosingIf(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof IfStatement ifStatement) {
				return ifStatement;
			}
			if (current instanceof Block) {
				current = current.getParent();
				continue;
			}
			// Stop at other statement types
			if (current instanceof Statement) {
				return null;
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Finds the nearest enclosing {@link SynchronizedStatement} for a given node.
	 *
	 * @param node the starting node
	 * @return the enclosing SynchronizedStatement, or null if not found
	 */
	private static SynchronizedStatement findEnclosingSynchronized(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof SynchronizedStatement syncStatement) {
				return syncStatement;
			}
			if (current instanceof Block) {
				current = current.getParent();
				continue;
			}
			// Stop at other statement types
			if (current instanceof Statement) {
				return null;
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Checks whether two expressions represent the same null check
	 * ({@code expr == null}).
	 *
	 * @param expr1 the first expression
	 * @param expr2 the second expression
	 * @return true if both are null checks on the same variable
	 */
	private static boolean isSameNullCheck(org.eclipse.jdt.core.dom.Expression expr1,
			org.eclipse.jdt.core.dom.Expression expr2) {
		if (!(expr1 instanceof InfixExpression infix1) || !(expr2 instanceof InfixExpression infix2)) {
			return false;
		}
		if (infix1.getOperator() != InfixExpression.Operator.EQUALS
				|| infix2.getOperator() != InfixExpression.Operator.EQUALS) {
			return false;
		}
		// Check null on right side for both
		if (!(infix1.getRightOperand() instanceof NullLiteral)
				|| !(infix2.getRightOperand() instanceof NullLiteral)) {
			return false;
		}
		// Compare the left operand (the field being checked) using structural comparison
		return infix1.getLeftOperand().subtreeMatch(new ASTMatcher(), infix2.getLeftOperand());
	}
}
