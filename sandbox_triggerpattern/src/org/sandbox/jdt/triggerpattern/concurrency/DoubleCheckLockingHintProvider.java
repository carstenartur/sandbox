/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.swt.graphics.Image;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;

/**
 * Hint provider for detecting the double-checked locking anti-pattern.
 *
 * <p>Double-checked locking is a concurrency pattern where a field is checked
 * for null, then a synchronized block is entered, and the field is checked
 * again. This pattern is broken in Java unless the field is declared
 * {@code volatile} (Java 5+).</p>
 *
 * <p>Inspired by the
 * <a href="https://github.com/apache/netbeans/blob/master/java/java.hints/src/org/netbeans/modules/java/hints/DoubleCheck.java">
 * NetBeans DoubleCheck hint</a>.</p>
 *
 * <h3>Detected Pattern</h3>
 * <pre>{@code
 * if (field == null) {
 *     synchronized (lock) {
 *         if (field == null) {
 *             field = new Something();
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h3>Suggested Fix</h3>
 * <p>The fix removes the outer null check, keeping only the synchronized block
 * with the inner null check. This eliminates the double-checked locking pattern
 * at the cost of always entering the synchronized block:</p>
 * <pre>{@code
 * synchronized (lock) {
 *     if (field == null) {
 *         field = new Something();
 *     }
 * }
 * }</pre>
 *
 * @since 1.2.5
 * @see <a href="https://en.wikipedia.org/wiki/Double-checked_locking">Double-checked locking (Wikipedia)</a>
 */
public class DoubleCheckLockingHintProvider {

	/**
	 * Detects the double-checked locking pattern and suggests removing the outer
	 * null check to eliminate the anti-pattern.
	 *
	 * <p>The pattern matches an {@code if} statement that checks a variable for
	 * null, contains a {@code synchronized} block, which in turn contains another
	 * {@code if} statement checking the same variable for null.</p>
	 *
	 * <p><b>Before:</b></p>
	 * <pre>{@code
	 * if (field == null) {
	 *     synchronized (lock) {
	 *         if (field == null) {
	 *             field = new Something();
	 *         }
	 *     }
	 * }
	 * }</pre>
	 *
	 * <p><b>After:</b></p>
	 * <pre>{@code
	 * synchronized (lock) {
	 *     if (field == null) {
	 *         field = new Something();
	 *     }
	 * }
	 * }</pre>
	 *
	 * @param ctx the hint context containing the match and AST information
	 * @return a completion proposal, or null if the pattern doesn't match
	 */
	@TriggerPattern(value = "if ($field == null) { synchronized ($lock) { if ($field == null) { $stmt; } } }", kind = PatternKind.STATEMENT)
	@Hint(displayName = "Double-checked locking",
	      description = "Detects double-checked locking pattern which is unsafe without volatile. "
	                   + "Suggests removing the outer null check to use plain synchronization instead.",
	      category = "concurrency",
	      suppressWarnings = "DoubleCheckedLocking")
	public static IJavaCompletionProposal detectDoubleCheckLocking(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();

		if (!(matchedNode instanceof IfStatement)) {
			return null;
		}

		IfStatement outerIf = (IfStatement) matchedNode;

		// Validate: outer if should have no else branch
		if (outerIf.getElseStatement() != null) {
			return null;
		}

		// Extract the synchronized statement from the outer if body
		Statement outerBody = outerIf.getThenStatement();
		SynchronizedStatement syncStmt = extractSynchronizedStatement(outerBody);
		if (syncStmt == null) {
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
	 * Extracts a {@link SynchronizedStatement} from a statement that may be
	 * either a direct synchronized statement or a block containing exactly one
	 * synchronized statement.
	 *
	 * @param statement the statement to extract from
	 * @return the synchronized statement, or null if not found
	 */
	private static SynchronizedStatement extractSynchronizedStatement(Statement statement) {
		if (statement instanceof SynchronizedStatement syncStatement) {
			return syncStatement;
		}
		if (statement instanceof Block block) {
			if (block.statements().size() == 1
					&& block.statements().get(0) instanceof SynchronizedStatement syncStatement) {
				return syncStatement;
			}
		}
		return null;
	}
}
