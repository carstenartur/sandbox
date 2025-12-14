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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

import static org.sandbox.jdt.internal.corext.fix.helper.JUnitConstants.*;

/**
 * Migrates JUnit 4 Assume calls to JUnit 5 Assumptions.
 */
public class AssumeJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	// Assume-specific method sets (different from assertion methods)
	private static final Set<String> TWOPARAM_ASSUMPTIONS = Set.of("assumeTrue", "assumeFalse", "assumeNotNull","assumeThat");
	private static final Set<String> ONEPARAM_ASSUMPTIONS = Set.of("assumeTrue", "assumeFalse", "assumeNotNull");
	private static final Set<String> ALL_ASSUMPTION_METHODS = Stream.of(TWOPARAM_ASSUMPTIONS, ONEPARAM_ASSUMPTIONS)
			.flatMap(Set::stream).collect(Collectors.toSet());

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= new ReferenceHolder<>();
		ALL_ASSUMPTION_METHODS.forEach(assertionmethod -> {
			HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_ASSUME, assertionmethod, compilationUnit, dataHolder,
					nodesprocessed, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		ALL_ASSUMPTION_METHODS.forEach(assertionmethod -> {
			HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSUME + "." + assertionmethod, compilationUnit,
					dataHolder, nodesprocessed,
					(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSUME, compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		return addStandardRewriteOperation(fixcore, operations, node, dataHolder);
	}
	
	@Override
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		if (junitHolder.minv instanceof MethodInvocation) {
			MethodInvocation minv= junitHolder.getMethodInvocation();
			if ("assumeThat".equals(minv.getName().getIdentifier()) && isJUnitAssume(minv)) {
				importRewriter.addStaticImport("org.hamcrest.junit.MatcherAssume", "assumeThat", true);
				importRewriter.removeStaticImport("org.junit.Assume.assumeThat");
				MethodInvocation newAssumeThatCall = ast.newMethodInvocation();
				newAssumeThatCall.setName(ast.newSimpleName("assumeThat"));
				for (Object arg : minv.arguments()) {
					newAssumeThatCall.arguments().add(rewriter.createCopyTarget((ASTNode) arg));
				}
				ASTNodes.replaceButKeepComment(rewriter,minv, newAssumeThatCall, group);
			} else {
				reorderParameters(minv, rewriter, group, ONEPARAM_ASSUMPTIONS, TWOPARAM_ASSUMPTIONS);
				SimpleName newQualifier= ast.newSimpleName(ASSUMPTIONS);
				Expression expression= minv.getExpression();
				if (expression != null) {
					ASTNodes.replaceButKeepComment(rewriter, expression, newQualifier, group);
				}
			}
		} else {
			changeImportDeclaration(junitHolder.getImportDeclaration(), importRewriter, group);
		}
	}

	/**
	 * Checks if the assumeThat method belongs to org.junit.Assume.
	 * 
	 * @param node the method invocation to check
	 * @return true if the method is from org.junit.Assume
	 */
	private boolean isJUnitAssume(MethodInvocation node) {
		IMethodBinding binding = node.resolveMethodBinding();
		return binding != null && ORG_JUNIT_ASSUME.equals(binding.getDeclaringClass().getQualifiedName());
	}

	/**
	 * Changes import declarations for JUnit 4 Assume to JUnit 5 Assumptions.
	 * Delegates to base class implementation.
	 * 
	 * @param node the import declaration to change
	 * @param importRewriter the import rewriter to use
	 * @param group text edit group (unused - import rewrites are tracked separately)
	 */
	public void changeImportDeclaration(ImportDeclaration node, ImportRewrite importRewriter, TextEditGroup group) {
		changeImportDeclaration(node, importRewriter, ORG_JUNIT_ASSUME, ORG_JUNIT_JUPITER_API_ASSUMPTIONS);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					Assumptions.assumeNotNull(object,"failuremessage");
					Assumptions.assertTrue(condition,"failuremessage");
					"""; //$NON-NLS-1$
		}
		return """
				Assume.assumeNotNull("failuremessage", object);
				Assume.assertTrue("failuremessage",condition);
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Assume"; //$NON-NLS-1$
	}
}
