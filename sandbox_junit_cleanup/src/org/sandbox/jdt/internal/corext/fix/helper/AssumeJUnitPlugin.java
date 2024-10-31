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
import org.eclipse.jdt.core.dom.IMethodBinding;
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
public class AssumeJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static final Set<String> twoparam= Set.of("assumeTrue", "assumeFalse", "assumeNotNull","assumeThat");
	private static final Set<String> oneparam= Set.of("assumeTrue", "assumeFalse", "assumeNotNull");
	private static final Set<String> allassumemethods= Stream.of(twoparam, oneparam).flatMap(Set::stream)
			.collect(Collectors.toSet());

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder= new ReferenceHolder<>();
		allassumemethods.forEach(assertionmethod -> {
			HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_ASSUME, assertionmethod, compilationUnit, dataholder,
					nodesprocessed, (visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		allassumemethods.forEach(assertionmethod -> {
			HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSUME + "." + assertionmethod, compilationUnit,
					dataholder, nodesprocessed,
					(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSUME, compilationUnit, dataholder, nodesprocessed,
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
				if ("assumeThat".equals(minv.getName().getIdentifier()) && isJUnitAssume(minv)) {
					importRewriter.addStaticImport("org.hamcrest.junit.MatcherAssume", "assumeThat", true);
					importRewriter.removeStaticImport("org.junit.Assume.assumeThat");
					MethodInvocation newAssumeThatCall = ast.newMethodInvocation();
					newAssumeThatCall.setName(ast.newSimpleName("assumeThat"));
					for (Object arg : minv.arguments()) {
						newAssumeThatCall.arguments().add(rewrite.createCopyTarget((ASTNode) arg));
					}
					ASTNodes.replaceButKeepComment(rewrite,minv, newAssumeThatCall, group);
				} else {
					reorderParameters(minv, rewrite, group, oneparam, twoparam);
					SimpleName newQualifier= ast.newSimpleName(ASSUMPTIONS);
					Expression expression= minv.getExpression();
					if (expression != null) {
						ASTNodes.replaceButKeepComment(rewrite, expression, newQualifier, group);
					}
				}
			} else {
				changeImportDeclaration(mh.getImportDeclaration(), importRewriter, group);
			}
		}
	}

	// Helper-Methode, um zu prüfen, ob `assumeThat` zu `org.junit.Assume` gehört
	private boolean isJUnitAssume(MethodInvocation node) {
		IMethodBinding binding = node.resolveMethodBinding();
		return binding != null && ORG_JUNIT_ASSUME.equals(binding.getDeclaringClass().getQualifiedName());
	}

	public void changeImportDeclaration(ImportDeclaration node, ImportRewrite importRewriter, TextEditGroup group) {
		String importName= node.getName().getFullyQualifiedName();
		if (node.isStatic() && importName.equals(ORG_JUNIT_ASSUME)) {
			importRewriter.removeStaticImport(ORG_JUNIT_ASSUME + ".*");
			importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSUMPTIONS, "*", false);
			return;
		}
		if (importName.equals(ORG_JUNIT_ASSUME)) {
			importRewriter.removeImport(ORG_JUNIT_ASSUME);
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_ASSUMPTIONS);
			return;
		}
		if (node.isStatic() && importName.startsWith(ORG_JUNIT_ASSUME + ".")) {
			String methodName= importName.substring((ORG_JUNIT_ASSUME + ".").length());
			importRewriter.removeStaticImport(ORG_JUNIT_ASSUME + "." + methodName);
			importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSUMPTIONS, methodName, false);
		}
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
