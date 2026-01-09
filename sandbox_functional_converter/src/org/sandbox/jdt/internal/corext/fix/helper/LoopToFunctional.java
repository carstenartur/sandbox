/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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

import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Find: for (Integer l : ls){
 * 		  System.out.println(l);
 * 		}
 *
 * Rewrite: ls.forEach(l -> {
 * 			System.out.println(l);
 * 		});
 *
 */
public class LoopToFunctional extends AbstractFunctionalCall<EnhancedForStatement> {

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, FunctionalHolder> dataHolder= new ReferenceHolder<>();
		HelperVisitor.callEnhancedForStatementVisitor(compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder),(visited, aholder) -> {});
	}

	private boolean processFoundNode(UseFunctionalCallFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, EnhancedForStatement visited,
			ReferenceHolder<Integer, FunctionalHolder> dataHolder) {
		FunctionalHolder mh= new FunctionalHolder();
		mh.minv= visited;

		PreconditionsChecker pc = new PreconditionsChecker(visited, (CompilationUnit) visited.getRoot());
		if (!pc.isSafeToRefactor()) {
			// Loop cannot be safely refactored to functional style
			return false;
		}
		// Check if the loop can be analyzed for stream conversion
		StreamPipelineBuilder builder = new StreamPipelineBuilder(visited, pc);
		if (!builder.analyze()) {
			// Cannot convert this loop to functional style
			return false;
		}
		operations.add(fixcore.rewrite(visited));
		return false;
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore upp, final EnhancedForStatement visited,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) throws CoreException {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		PreconditionsChecker pc = new PreconditionsChecker(visited, (CompilationUnit) visited.getRoot());
		Refactorer refactorer = new Refactorer(visited, rewrite, pc, group);
		// Preconditions already checked in find(), but refactorer.refactor() handles edge cases
		refactorer.refactor();
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "ls.forEach(l -> {\n	System.out.println(l);\n});\n"; //$NON-NLS-1$
		}
		return "for (Integer l : ls)\n	System.out.println(l);\n\n"; //$NON-NLS-1$
	}
}
