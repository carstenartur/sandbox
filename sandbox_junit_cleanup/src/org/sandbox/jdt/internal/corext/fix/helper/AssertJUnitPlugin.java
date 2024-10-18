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

import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
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

	private static final String ASSERTIONS = "Assertions";
	private static final String ORG_JUNIT_JUPITER_API_ASSERTIONS = "org.junit.jupiter.api.Assertions";
	private static final String ORG_JUNIT_ASSERT = "org.junit.Assert";
	private static final Set<String> twoparam = Set.of("assertEquals", "assertNotEquals","assertArrayEquals","assertTrue","assertFalse","assertNull","assertNotNull","fail");
	private static final Set<String> oneparam = Set.of("assertTrue","assertFalse","assertNull","assertNotNull");
	private static final Set<String> noparam = Set.of("fail");
	private static final Set<String> allassertionmethods= Stream.of(twoparam,oneparam,noparam).flatMap(Set::stream).collect(Collectors.toSet());

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder = new ReferenceHolder<>();
		allassertionmethods.forEach(assertionmethod->{
			HelperVisitor.callMethodInvocationVisitor(ORG_JUNIT_ASSERT, assertionmethod, compilationUnit, dataholder, nodesprocessed,
					(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		});
		HelperVisitor.callImportDeclarationVisitor(ORG_JUNIT_ASSERT, compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, ASTNode node,
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
			if(mh.minv instanceof MethodInvocation) {
				MethodInvocation minv = mh.getMethodInvocation();
				reorderParameters(minv, rewrite, group);
				SimpleName newQualifier = ast.newSimpleName(ASSERTIONS);
				Expression expression = minv.getExpression();
				if(expression!=null) {
					ASTNodes.replaceButKeepComment(rewrite, expression, newQualifier, group);
				}
			} else {
				changeImportDeclaration(mh.getImportDeclaration(), importRemover, group);
			}
		}
	}

	public void changeImportDeclaration(ImportDeclaration node, ImportRewrite importRewriter, TextEditGroup group) {
		String importName = node.getName().getFullyQualifiedName();
		if (importName.equals(ORG_JUNIT_ASSERT)) {
			importRewriter.removeImport(ORG_JUNIT_ASSERT);
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_ASSERTIONS);
		}
		if (node.isStatic() && importName.equals(ORG_JUNIT_ASSERT)) {
			importRewriter.removeStaticImport(ORG_JUNIT_ASSERT+".*");
			importRewriter.addStaticImport(ORG_JUNIT_JUPITER_API_ASSERTIONS, "*", false);
		}
	}

	private void reorderParameters(MethodInvocation node, ASTRewrite rewriter, TextEditGroup group) {
		List<Expression> arguments = node.arguments();
		SimpleName name = node.getName();
		if (arguments.size() == 3 && twoparam.contains(name.toString())) {
			// In JUnit 4: (String message, Object expected, Object actual)
			// In JUnit 5: (Object expected, Object actual, String message)
			Expression messageArg =  arguments.get(0); // message
			Expression secondArg = arguments.get(1); // expected
			Expression thirdArg = arguments.get(2); // actual
			if (isStringType(messageArg)) {
				// Vertausche die Argumente für die neue Reihenfolge
				ListRewrite listRewrite = rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
				listRewrite.replace(messageArg, ASTNodes.createMoveTarget(rewriter,secondArg), group);  // Ersetze message durch actual
				listRewrite.replace(secondArg, ASTNodes.createMoveTarget(rewriter,thirdArg), group); // Ersetze expected durch actual
				listRewrite.replace(thirdArg, ASTNodes.createMoveTarget(rewriter,messageArg), group); // Ersetze actual durch expected
			}
		} else if (arguments.size() == 2 && oneparam.contains(name.toString())) {
			// In JUnit 4: (Object message, Object actual)
			Expression messageArg = arguments.get(0); // message
			Expression actualArg = arguments.get(1);   // actual
			if (isStringType(messageArg)) {
				ListRewrite listRewrite = rewriter.getListRewrite(node, MethodInvocation.ARGUMENTS_PROPERTY);
				listRewrite.replace(arguments.get(0), ASTNodes.createMoveTarget(rewriter, actualArg), group); // message wird durch actual ersetzt
				listRewrite.replace(arguments.get(1), ASTNodes.createMoveTarget(rewriter, messageArg), group); // actual wird durch message ersetzt
			}
		}
	}

	private boolean isStringType(Expression expression) {
		ITypeBinding typeBinding = expression.resolveTypeBinding();
		return typeBinding != null && String.class.getCanonicalName().equals(typeBinding.getQualifiedName());
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
