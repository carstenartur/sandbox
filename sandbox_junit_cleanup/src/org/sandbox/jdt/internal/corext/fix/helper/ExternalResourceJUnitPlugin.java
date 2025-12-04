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

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
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
	void applyRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder mh) {
		TypeDeclaration node= mh.getTypeDeclaration();
		modifyExternalResourceClass(node, null, false, rewriter, ast, group, importRewriter);
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
