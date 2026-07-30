/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;

import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/** Dispatches the shared JUnit 3 cleanup option to its fail-closed migration slices. */
public final class CompositeJUnit3Plugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private final TestJUnit3Plugin standalone= new TestJUnit3Plugin();
	private final JdtCoreLeafJUnitPlugin jdtCoreLeaf= new JdtCoreLeafJUnitPlugin();

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		standalone.find(fixcore, compilationUnit, operations, nodesprocessed);
		jdtCoreLeaf.find(fixcore, compilationUnit, operations, nodesprocessed);
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder holder) {
		if (jdtCoreLeaf.handles(holder)) {
			jdtCoreLeaf.process2Rewrite(group, rewriter, ast, importRewriter, holder);
		} else {
			standalone.process2Rewrite(group, rewriter, ast, importRewriter, holder);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		return standalone.getPreview(afterRefactoring);
	}

	@Override
	public String toString() {
		return standalone.toString();
	}
}
