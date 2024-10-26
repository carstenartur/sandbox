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

import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
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

	static final Set<String> twoparam= Set.of("assertEquals", "assertNotEquals", "assertArrayEquals",
			"assertTrue", "assertFalse", "assertNull", "assertNotNull", "fail");
	static final Set<String> oneparam= Set.of("assertTrue", "assertFalse", "assertNull", "assertNotNull");
	private static final Set<String> noparam= Set.of("fail");
	private static final Set<String> allassertionmethods= Stream.of(twoparam, oneparam, noparam).flatMap(Set::stream)
			.collect(Collectors.toSet());

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder= new ReferenceHolder<>();
		allassertionmethods.forEach(assertionmethod -> {
			HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_ASSERT, assertionmethod, compilationUnit, dataholder,
					nodesprocessed, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		allassertionmethods.forEach(assertionmethod -> {
			HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSERT + "." + assertionmethod, compilationUnit,
					dataholder, nodesprocessed,
					(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSERT, compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh= new JunitHolder();
		mh.minv= node;
		dataholder.put(dataholder.size(), mh);
		operations.add(fixcore.rewrite(dataholder));
		return false;
	}

	@Override
	public void rewrite(JUnitCleanUpFixCore upp, final ReferenceHolder<Integer, JunitHolder> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite importRewriter= cuRewrite.getImportRewrite();
		for (Entry<Integer, JunitHolder> entry : hit.entrySet()) {
			JunitHolder mh= entry.getValue();
			if (mh.minv instanceof MethodInvocation) {
				MethodInvocation minv= mh.getMethodInvocation();
				reorderParameters(minv, rewrite, group, oneparam, twoparam);
				SimpleName newQualifier= ast.newSimpleName(ASSERTIONS);
				Expression expression= minv.getExpression();
				if (expression != null) {
					ASTNodes.replaceButKeepComment(rewrite, expression, newQualifier, group);
				}
			} else {
				changeImportDeclaration(mh.getImportDeclaration(), importRewriter, group);
			}
		}
	}

	public void changeImportDeclaration(ImportDeclaration node, ImportRewrite importRewriter, TextEditGroup group) {
		String importName= node.getName().getFullyQualifiedName();
		if (node.isStatic() && importName.equals(ORG_JUNIT_ASSERT)) {
			importRewriter.removeStaticImport(ORG_JUNIT_ASSERT + ".*");
			importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "*", false);
			return;
		}
		if (importName.equals(ORG_JUNIT_ASSERT)) {
			importRewriter.removeImport(ORG_JUNIT_ASSERT);
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_ASSERTIONS);
			return;
		}
		if (node.isStatic() && importName.startsWith(ORG_JUNIT_ASSERT + ".")) {
			String methodName= importName.substring((ORG_JUNIT_ASSERT + ".").length());
			importRewriter.removeStaticImport(ORG_JUNIT_ASSERT + "." + methodName);
			importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, methodName, false);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					Assertions.assertNotEquals(5,result, "failuremessage");  // expected = 5, actual = result
					Assertions.assertTrue(false,"failuremessage");
					"""; //$NON-NLS-1$
		}
		return """
				Assert.assertNotEquals("failuremessage",5, result);  // expected = 5, actual = result
				Assert.assertTrue("failuremessage",false);
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Assert"; //$NON-NLS-1$
	}
}