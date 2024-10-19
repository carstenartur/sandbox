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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeLiteral;
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
public class RunWithJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static final String ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES = "org.junit.platform.suite.api.SelectClasses";
	private static final String SELECT_CLASSES = "SelectClasses";
	private static final String ORG_JUNIT_RUNWITH = "org.junit.runner.RunWith";
	private static final String ORG_JUNIT_JUPITER_SUITE = "org.junit.platform.suite.api.Suite";
	private static final String SUITE = "Suite";
	private static final String ORG_JUNIT_SUITE = "org.junit.runners.Suite";
	private static final String ORG_JUNIT_SUITE_SUITECLASSES = "org.junit.runners.Suite.SuiteClasses";

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder = new ReferenceHolder<>();
		HelperVisitor.callSingleMemberAnnotationVisitor(ORG_JUNIT_RUNWITH, compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNodeRunWith(fixcore, operations, visited, aholder));
		HelperVisitor.callSingleMemberAnnotationVisitor(ORG_JUNIT_SUITE_SUITECLASSES, compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNodeSuite(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNodeRunWith(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh = new JunitHolder();
		mh.minv = node;
		mh.minvname = node.getTypeName().getFullyQualifiedName();
		if(node instanceof SingleMemberAnnotation mynode) {
			Expression value = mynode.getValue();
			if(value instanceof TypeLiteral myvalue) {
				ITypeBinding classBinding = myvalue.resolveTypeBinding();
				// Überprüfe, ob der Typ ein generischer Typ ist (Class<T>)
				if (classBinding != null && classBinding.isParameterizedType()) {
					// Extrahiere den tatsächlichen Typ (T) aus Class<T>
					ITypeBinding[] typeArguments = classBinding.getTypeArguments();
					if (typeArguments.length > 0) {
						ITypeBinding actualTypeBinding = typeArguments[0];
						// Prüfe, ob es sich um org.junit.runners.Suite handelt
						if (ORG_JUNIT_SUITE.equals(actualTypeBinding.getQualifiedName())) {
							mh.value=ORG_JUNIT_RUNWITH;
							dataholder.put(dataholder.size(), mh);
							operations.add(fixcore.rewrite(dataholder));
						}
						return false;
					}
				}
			}
		}
		return false;
	}
	
	private boolean processFoundNodeSuite(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh = new JunitHolder();
		mh.minv = node;
		mh.minvname = node.getTypeName().getFullyQualifiedName();
		mh.value=ORG_JUNIT_SUITE_SUITECLASSES;
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
			Annotation minv = mh.getAnnotation();
			Annotation newAnnotation = null;
			SingleMemberAnnotation mynode=(SingleMemberAnnotation) minv;
			if(ORG_JUNIT_SUITE_SUITECLASSES.equals(mh.value)) {
				newAnnotation = ast.newSingleMemberAnnotation();
				((SingleMemberAnnotation)newAnnotation).setValue(ASTNodes.createMoveTarget(rewrite, mynode.getValue()));
				newAnnotation.setTypeName(ast.newSimpleName(SELECT_CLASSES));
			} else {
				newAnnotation = ast.newMarkerAnnotation();
				newAnnotation.setTypeName(ast.newSimpleName(SUITE));
			}
			ASTNodes.replaceButKeepComment(rewrite, minv, newAnnotation, group);
			importRemover.removeImport(ORG_JUNIT_SUITE);
			importRemover.removeImport(ORG_JUNIT_RUNWITH);
			addImport(ORG_JUNIT_JUPITER_SUITE, cuRewrite, ast);
			addImport(ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES, cuRewrite, ast);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return 
"""
@Suite
@SelectClasses({
	MyTest2.class
})
"""; //$NON-NLS-1$
		}
		return 
"""
@RunWith(Suite.class)
@Suite.SuiteClasses({
	MyTest.class
})
"""; //$NON-NLS-1$
	}
}
