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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.XMLCleanUpFixCore;

/**
 *
 */
public class XMLPlugin extends AbstractTool<XMLCandidateHit> {

	@Override
	public void find(XMLCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,
			boolean createForOnlyIfVarUsed) {
	
	}

	@Override
	public void rewrite(XMLCleanUpFixCore upp, final XMLCandidateHit hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();

		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		ImportRemover remover= cuRewrite.getImportRemover();


		remover.applyRemoves(importRewrite);
	}


	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "\nfor (String s : strings) {\n\n	System.out.println(s);\n}\n\n"; //$NON-NLS-1$
		}
		return "Iterator it = lists.iterator();\nwhile (it.hasNext()) {\n    String s = (String) it.next();\n	System.out.println(s);\n}\n\n"; //$NON-NLS-1$
	}
}
