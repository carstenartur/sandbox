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
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
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
 * Plugin to migrate JUnit 4 @Ignore annotations to JUnit 5 @Disabled.
 */
public class IgnoreJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= new ReferenceHolder<>();
		HelperVisitor.callMarkerAnnotationVisitor(ORG_JUNIT_IGNORE, compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		HelperVisitor.callNormalAnnotationVisitor(ORG_JUNIT_IGNORE, compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
		HelperVisitor.callSingleMemberAnnotationVisitor(ORG_JUNIT_IGNORE, compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh= new JunitHolder();
		mh.minv= node;
		mh.minvname= node.getTypeName().getFullyQualifiedName();
		if (node instanceof SingleMemberAnnotation mynode) {
			Expression value= mynode.getValue();
			mh.value= value.toString();
		}
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		return false;
	}

	@Override
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		Annotation minv= junitHolder.getAnnotation();
		Annotation newAnnotation= null;
		if (minv instanceof SingleMemberAnnotation mynode) {
			newAnnotation= ast.newSingleMemberAnnotation();
			((SingleMemberAnnotation) newAnnotation)
					.setValue(ASTNodes.createMoveTarget(rewriter, mynode.getValue()));
		} else {
			newAnnotation= ast.newMarkerAnnotation();
		}
		newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_DISABLED));
		importRewriter.addImport(ORG_JUNIT_JUPITER_DISABLED);
		ASTNodes.replaceButKeepComment(rewriter, minv, newAnnotation, group);
		importRewriter.removeImport(ORG_JUNIT_IGNORE);
	}
	
	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@Disabled("not implemented")
					@Test
					public void test() {
						fail("Not yet implemented");
					}
					"""; //$NON-NLS-1$
		}
		return """
				@Ignore("not implemented")
				@Test
				public void test() {
					fail("Not yet implemented");
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Ignore"; //$NON-NLS-1$
	}
}
