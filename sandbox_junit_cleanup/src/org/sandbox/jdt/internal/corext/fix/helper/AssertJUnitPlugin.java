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
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 *
 * 
 */
public class AssertJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static final String ORG_JUNIT_JUPITER_API_ASSERTIONS = "org.junit.jupiter.api.Assertions";
	private static final String ASSERT_EQUALS = "assertEquals";
	private static final String ORG_JUNIT_ASSERT = "org.junit.Assert";

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder = new ReferenceHolder<>();
		HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_ASSERT, ASSERT_EQUALS, compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, MethodInvocation node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh = new JunitHolder();
		mh.minv = node;
		dataholder.put(dataholder.size(), mh);
		operations.add(fixcore.rewrite(dataholder));
		return false;
	}

	@Override
	public void rewrite(JUnitCleanUpFixCore upp, final ReferenceHolder<Integer, JunitHolder> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		ImportRewrite importRemover = cuRewrite.getImportRewrite();
		for (Entry<Integer, JunitHolder> entry : hit.entrySet()) {
			JunitHolder mh = entry.getValue();
			MethodInvocation minv = mh.getMethodInvocation();
			reorderParameters(minv, rewrite, group);
			importRemover.removeImport(ORG_JUNIT_ASSERT);
			addImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, cuRewrite, ast);
		}
	}
	
	private void reorderParameters(MethodInvocation node, ASTRewrite rewriter, TextEditGroup group) {
		List<Expression> arguments = node.arguments();

		if (arguments.size() == 3) {
			// In JUnit 4: (String message, Object expected, Object actual)
			// In JUnit 5: (Object expected, Object actual, String message)

			Expression firstArg =  arguments.get(0); // message
			Expression secondArg = arguments.get(1); // expected
			Expression thirdArg = arguments.get(2); // actual

			// Vertausche die Argumente für die neue Reihenfolge
			ListRewrite listRewrite = rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
			listRewrite.replace(firstArg, ASTNodes.createMoveTarget(rewriter,secondArg), group);  // Ersetze message durch actual
			listRewrite.replace(secondArg, ASTNodes.createMoveTarget(rewriter,thirdArg), group); // Ersetze expected durch actual
			listRewrite.replace(thirdArg, ASTNodes.createMoveTarget(rewriter,firstArg), group); // Ersetze actual durch expected
			
		} 
//		else if (arguments.size() == 2) {
//			// In JUnit 4: (Object expected, Object actual)
//			// In JUnit 5 bleibt die Reihenfolge gleich, aber falls sie vertauscht ist, korrigieren wir sie
//
//			Expression expectedArg = arguments.get(0); // expected
//			Expression actualArg = arguments.get(1);   // actual
//
//			// Tausche expected und actual nur, wenn es notwendig ist
//			ListRewrite listRewrite = rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
//			listRewrite.replace(arguments.get(0), ASTNodes.createMoveTarget(rewriter, actualArg), group); // expected wird durch actual ersetzt
//			listRewrite.replace(arguments.get(1), ASTNodes.createMoveTarget(rewriter, expectedArg), group); // actual wird durch expected ersetzt
//		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (!afterRefactoring) {
			return	"""
						;
					"""; //$NON-NLS-1$
		}
		return	"""
					;
				"""; //$NON-NLS-1$
	}
}
