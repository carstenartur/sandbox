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
import org.eclipse.jdt.core.dom.NormalAnnotation;
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

	private static final String ORG_JUNIT_RUNWITH = "org.junit.runner.RunWith";
	private static final String ORG_JUNIT_JUPITER_SUITE = "org.junit.platform.suite.api.Suite";
	private static final String SUITE = "Suite";

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataholder = new ReferenceHolder<>();
		HelperVisitor.callMarkerAnnotationVisitor(ORG_JUNIT_RUNWITH, compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		HelperVisitor.callNormalAnnotationVisitor(ORG_JUNIT_RUNWITH, compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		HelperVisitor.callSingleMemberAnnotationVisitor(ORG_JUNIT_RUNWITH, compilationUnit, dataholder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataholder) {
		JunitHolder mh = new JunitHolder();
		mh.minv = node;
		mh.minvname = node.getTypeName().getFullyQualifiedName();
		if(node instanceof SingleMemberAnnotation mynode) {
			Expression value = mynode.getValue();
			if(value instanceof TypeLiteral myvalue) {

			ITypeBinding typeBinding = myvalue.resolveTypeBinding();
			if (typeBinding != null) {
				mh.value = typeBinding.getQualifiedName();
				if (!"org.junit.runners.Suite".equals(mh.value)) {
					return false;
				}
				dataholder.put(dataholder.size(), mh);
				operations.add(fixcore.rewrite(dataholder));
			}
			}
		}
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
			Annotation minv = mh.minv;
			Annotation newAnnotation = null;
			if(minv instanceof SingleMemberAnnotation mynode) {
				newAnnotation = ast.newSingleMemberAnnotation();
				((SingleMemberAnnotation)newAnnotation).setValue(ASTNodes.createMoveTarget(rewrite, mynode.getValue()));
			} else {
				newAnnotation = ast.newMarkerAnnotation();
			}
			newAnnotation.setTypeName(ast.newSimpleName(SUITE));
			addImport(ORG_JUNIT_JUPITER_SUITE, cuRewrite, ast);
			ASTNodes.replaceButKeepComment(rewrite, minv, newAnnotation, group);
			importRemover.removeImport(ORG_JUNIT_RUNWITH);
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (!afterRefactoring) {
			return """
						;
					"""; //$NON-NLS-1$
		}
		return """
					;
				"""; //$NON-NLS-1$
	}
}
