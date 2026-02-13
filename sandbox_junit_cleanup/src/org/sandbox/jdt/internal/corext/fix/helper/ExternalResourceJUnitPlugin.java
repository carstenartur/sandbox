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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

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
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.ExternalResourceRefactorer;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 ExternalResource classes to JUnit 5 lifecycle callbacks.
 * <p>
 * Transforms classes that extend {@code org.junit.rules.ExternalResource} to implement
 * JUnit 5 callback interfaces ({@code BeforeEachCallback}, {@code AfterEachCallback}).
 * Renames lifecycle methods (before/after) to match JUnit 5 naming conventions.
 * </p>
 */
public class ExternalResourceJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= ReferenceHolder.createIndexed();
		HelperVisitorFactory.callTypeDeclarationVisitor(ORG_JUNIT_RULES_EXTERNAL_RESOURCE, compilationUnit, dataHolder,
				nodesprocessed, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder,nodesprocessed));
	}

	/**
	 * Processes a type declaration that extends ExternalResource.
	 * Only processes types that either:
	 * - Directly extend ExternalResource, or
	 * - Indirectly extend ExternalResource AND have before()/after() lifecycle methods
	 * 
	 * @param fixcore the cleanup fix core
	 * @param operations the set of operations to add to
	 * @param node the type declaration found
	 * @param dataHolder the reference holder for data
	 * @param nodesprocessed set of already processed nodes
	 * @return false to continue visiting
	 */
	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, TypeDeclaration node,
			ReferenceHolder<Integer, JunitHolder> dataHolder, Set<ASTNode> nodesprocessed) {
		if (nodesprocessed.contains(node)) {
			return false;
		}
		
		// For indirect subclasses, only process if they have lifecycle methods
		if (!ExternalResourceRefactorer.isDirectlyExtendingExternalResource(node.resolveBinding())) {
			boolean hasLifecycleMethod = false;
			for (MethodDeclaration method : node.getMethods()) {
				String methodName = method.getName().getIdentifier();
				if (METHOD_BEFORE.equals(methodName) || METHOD_AFTER.equals(methodName)) {
					hasLifecycleMethod = true;
					break;
				}
			}
			if (!hasLifecycleMethod) {
				return false;
			}
		}
		
		nodesprocessed.add(node);
		JunitHolder mh= new JunitHolder();
		mh.setMinv(node);
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		return false;
	}

	@Override
	protected
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		TypeDeclaration node= junitHolder.getTypeDeclaration();
		ExternalResourceRefactorer.modifyExternalResourceClass(node, null, false, rewriter, ast, group, importRewriter);
	}
	
	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					class MyExternalResource implements BeforeEachCallback, AfterEachCallback {
						@Override
						public void beforeEach(ExtensionContext context) throws Exception {
						}

						@Override
						public void afterEach(ExtensionContext context) {
						}
					}
					"""; //$NON-NLS-1$
		}
		return """
				class MyExternalResource extends ExternalResource {
					@Override
					protected void before() throws Throwable {
					}

					@Override
					protected void after() {
					}
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "ExternalResource"; //$NON-NLS-1$
	}
}
