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
package org.sandbox.jdt.internal.corext.fix;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Fix core for threading anti-patterns using TriggerPattern hints.
 *
 * <p>Inspired by NetBeans' Tiny.java threading hints, this class detects and fixes
 * common threading mistakes such as calling {@code Thread.run()} directly instead
 * of {@code Thread.start()}.</p>
 *
 * @since 1.2.5
 * @see <a href="https://github.com/apache/netbeans/blob/master/java/java.hints/src/org/netbeans/modules/java/hints/threading/Tiny.java">NetBeans Tiny.java</a>
 */
public class ThreadingFixCore {

	private static final TriggerPatternEngine ENGINE = new TriggerPatternEngine();

	/**
	 * Finds threading anti-pattern operations in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found operations to
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations) {

		// Pattern 1: $thread.run() -> $thread.start()
		// Calling Thread.run() directly executes in the current thread instead of starting a new one
		Pattern threadRunPattern = new Pattern("$thread.run()", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> threadRunMatches = ENGINE.findMatches(compilationUnit, threadRunPattern);
		for (Match match : threadRunMatches) {
			operations.add(new ThreadRunToStartOperation(match));
		}
	}

	/**
	 * Rewrite operation for Thread.run() → Thread.start() transformation.
	 *
	 * <p>Replaces direct calls to {@code Thread.run()} with {@code Thread.start()},
	 * because calling {@code run()} directly does not start a new thread.</p>
	 */
	private static class ThreadRunToStartOperation extends CompilationUnitRewriteOperation {

		private final Match match;

		public ThreadRunToStartOperation(Match match) {
			this.match = match;
		}

		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup("Replace Thread.run() with Thread.start()", cuRewrite); //$NON-NLS-1$

			ASTNode matchedNode = match.getMatchedNode();
			if (!(matchedNode instanceof MethodInvocation)) {
				return;
			}

			MethodInvocation originalInvocation = (MethodInvocation) matchedNode;

			// Get the receiver expression ($thread)
			ASTNode threadNode = match.getBinding("$thread"); //$NON-NLS-1$

			// Create the replacement: $thread.start()
			MethodInvocation startInvocation = ast.newMethodInvocation();
			if (threadNode instanceof Expression) {
				startInvocation.setExpression((Expression) ASTNode.copySubtree(ast, (Expression) threadNode));
			}
			startInvocation.setName(ast.newSimpleName("start")); //$NON-NLS-1$

			rewrite.replace(originalInvocation, startInvocation, group);
		}
	}
}
