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
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

/**
 *
 *
 */
public class AssertJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= new ReferenceHolder<>();
		allassertionmethods.forEach(assertionmethod -> {
			HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_ASSERT, assertionmethod, compilationUnit, dataHolder,
					nodesprocessed, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		allassertionmethods.forEach(assertionmethod -> {
			HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSERT + "." + assertionmethod, compilationUnit,
					dataHolder, nodesprocessed,
					(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSERT, compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh= new JunitHolder();
		mh.minv= node;
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		return false;
	}

	@Override
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		if (junitHolder.minv instanceof MethodInvocation) {
			MethodInvocation node= junitHolder.getMethodInvocation();
			Expression assertexpression= node.getExpression();
			if ("assertThat".equals(node.getName().getIdentifier()) &&
					assertexpression instanceof SimpleName &&
					"Assert".equals(((SimpleName) assertexpression).getIdentifier())) {
				rewriter.set(node, MethodInvocation.EXPRESSION_PROPERTY, null, group);
				importRewriter.addStaticImport("org.hamcrest.MatcherAssert", "assertThat", false);
				importRewriter.removeImport("org.junit.Assert");
				if (node.arguments().size() == 3) {
					Expression errorMessage = (Expression) node.arguments().get(0);
					Expression actualValue = (Expression) node.arguments().get(1);
					Expression matcher = (Expression) node.arguments().get(2);
					ListRewrite argumentRewrite = rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
					argumentRewrite.replace((ASTNode) node.arguments().get(0), errorMessage, group);
					argumentRewrite.replace((ASTNode) node.arguments().get(1), actualValue, group);
					argumentRewrite.replace((ASTNode) node.arguments().get(2), matcher, group);
				}
			} else {
				reorderParameters(node, rewriter, group, oneparam, twoparam);
				SimpleName newQualifier= ast.newSimpleName(ASSERTIONS);
				Expression expression= assertexpression;
				if (expression != null) {
					ASTNodes.replaceButKeepComment(rewriter, expression, newQualifier, group);
				}
			}
		} else {
			changeImportDeclaration(junitHolder.getImportDeclaration(), importRewriter, group);
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
