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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
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
		compilationUnit.accept(new ASTVisitor() {

			@Override
			public final boolean visit(final EnhancedForStatement visited) {
				operations.add(fixcore.rewrite(visited));
				nodesprocessed.add(visited);
				return false;
			}
		});
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore upp, final EnhancedForStatement forLoop,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
//		AST ast = cuRewrite.getRoot().getAST();
		/**
		 * for (Integer l : ls){
		 * 		  System.out.println(l);
		 * 		}
		 *
		 * loopBody= {  System.out.println(l);	}
		 *
		 * parameter= Integer l
		 *
		 * expr= ls
		 *
		 */

//		PreconditionsChecker pc = new PreconditionsChecker(loop, ast);
//		Refactorer refactorer = new Refactorer(loop, ast, pc,rewrite);
//		if (pc.isSafeToRefactor() && refactorer.isRefactorable()) {
//			ASTNodes.replaceButKeepComment(rewrite, loop, refactorer.refactor(rewrite), group);
//		}
		
		PreconditionsChecker pc = new PreconditionsChecker(forLoop, (CompilationUnit) forLoop.getRoot());
        Refactorer refactorer = new Refactorer(forLoop, rewrite, pc);
        if (pc.isSafeToRefactor() && refactorer.isRefactorable()) {
            refactorer.refactor();
            // Anwenden der Änderungen auf das Dokument
            // Beispiel:
            // TextEdit edits = rewrite.rewriteAST(document, null);
            // edits.apply(document);
        }
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "ls.forEach(l -> {\n	System.out.println(l);\n});\n"; //$NON-NLS-1$
		}
		return "for (Integer l : ls)\n	System.out.println(l);\n\n"; //$NON-NLS-1$
	}
}
