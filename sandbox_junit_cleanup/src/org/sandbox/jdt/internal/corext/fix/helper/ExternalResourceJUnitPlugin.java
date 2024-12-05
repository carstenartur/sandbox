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
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
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
public class ExternalResourceJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder= new ReferenceHolder<>();
		HelperVisitor.callTypeDeclarationVisitor(ORG_JUNIT_RULES_EXTERNAL_RESOURCE, compilationUnit, dataholder,
				nodesprocessed, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder,nodesprocessed));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, TypeDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataholder, Set<ASTNode> nodesprocessed) {
		if (!nodesprocessed.contains(node)) {
			if (!isDirectlyExtendingExternalResource(node.resolveBinding())) {
				boolean nothingtochange=true;
				for (MethodDeclaration method : node.getMethods()) {
					if (isLifecycleMethod(method, METHOD_BEFORE) || isLifecycleMethod(method, METHOD_AFTER)) {
						nothingtochange=false;
					}
				}
				if(nothingtochange) {
					return false;
				}
			}
			nodesprocessed.add(node);
			JunitHolder mh= new JunitHolder();
			mh.minv= node;
			dataholder.put(dataholder.size(), mh);
			operations.add(fixcore.rewrite(dataholder));
		}
		return false;
	}

	@Override
	public void rewrite(JUnitCleanUpFixCore upp, final ReferenceHolder<Integer, JunitHolder> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewriter= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();
		ImportRewrite importRewriter= cuRewrite.getImportRewrite();
		hit.values().forEach(holder -> {
			TypeDeclaration node= holder.getTypeDeclaration();
			modifyExternalResourceClass(node, null, false, rewriter, ast, group, importRewriter);
		});
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
						private String testName;

						@BeforeEach
						void init(TestInfo testInfo) {
							this.testName = testInfo.getDisplayName();
						}
						@Test
						public void test(){
							System.out.println("Test name: " + testName);
						}
					"""; //$NON-NLS-1$
		}
		return """
					@Rule
					public TestName tn = new TestName();

					@Test
					public void test(){
						System.out.println("Test name: " + tn.getMethodName());
					}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "ExternalResource"; //$NON-NLS-1$
	}
}
