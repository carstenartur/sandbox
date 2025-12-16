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
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;

import static org.sandbox.jdt.internal.corext.fix.helper.JUnitConstants.*;

/**
 * Plugin to migrate JUnit 4 @RunWith and @Suite.SuiteClasses to JUnit 5 equivalents.
 */
public class RunWithJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= new ReferenceHolder<>();
		HelperVisitor.callSingleMemberAnnotationVisitor(ORG_JUNIT_RUNWITH, compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNodeRunWith(fixcore, operations, visited, aholder));
		HelperVisitor.callSingleMemberAnnotationVisitor(ORG_JUNIT_SUITE_SUITECLASSES, compilationUnit, dataHolder,
				nodesprocessed, (visited, aholder) -> processFoundNodeSuite(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNodeRunWith(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh= new JunitHolder();
		mh.minv= node;
		mh.minvname= node.getTypeName().getFullyQualifiedName();
		if (node instanceof SingleMemberAnnotation mynode) {
			Expression value= mynode.getValue();
			if (value instanceof TypeLiteral myvalue) {
				ITypeBinding classBinding= myvalue.resolveTypeBinding();
				if (classBinding != null && classBinding.isParameterizedType()) {
					ITypeBinding[] typeArguments= classBinding.getTypeArguments();
					if (typeArguments.length > 0) {
						ITypeBinding actualTypeBinding= typeArguments[0];
						if (ORG_JUNIT_SUITE.equals(actualTypeBinding.getQualifiedName())) {
							mh.value= ORG_JUNIT_RUNWITH;
							dataHolder.put(dataHolder.size(), mh);
							operations.add(fixcore.rewrite(dataHolder));
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
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh= new JunitHolder();
		mh.minv= node;
		mh.minvname= node.getTypeName().getFullyQualifiedName();
		mh.value= ORG_JUNIT_SUITE_SUITECLASSES;
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		return false;
	}

	@Override
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		Annotation minv= junitHolder.getAnnotation();
		Annotation newAnnotation= null;
		SingleMemberAnnotation mynode= (SingleMemberAnnotation) minv;
		if (ORG_JUNIT_SUITE_SUITECLASSES.equals(junitHolder.value)) {
			newAnnotation= ast.newSingleMemberAnnotation();
			((SingleMemberAnnotation) newAnnotation)
					.setValue(ASTNodes.createMoveTarget(rewriter, mynode.getValue()));
			newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_SELECT_CLASSES));
			importRewriter.addImport(ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES);
		} else {
			newAnnotation= ast.newMarkerAnnotation();
			newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_SUITE));
			importRewriter.addImport(ORG_JUNIT_JUPITER_SUITE);
		}
		ASTNodes.replaceButKeepComment(rewriter, minv, newAnnotation, group);
		importRewriter.removeImport(ORG_JUNIT_SUITE);
		importRewriter.removeImport(ORG_JUNIT_RUNWITH);
	}
	
	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@Suite
					@SelectClasses({
						MyTest.class
					})
					"""; //$NON-NLS-1$
		}
		return """
				@RunWith(Suite.class)
				@Suite.SuiteClasses({
					MyTest.class
				})
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RunWith"; //$NON-NLS-1$
	}
}
