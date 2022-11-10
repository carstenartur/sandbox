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

import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ASTProcessor;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JfaceCleanUpFixCore;

/**
 *
 */
public class JFacePlugin extends AbstractTool<JfaceCandidateHit> {

	@Override
	public void find(JfaceCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,
			boolean createForOnlyIfVarUsed) {

		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		ASTProcessor<ReferenceHolder<String, Object>,String,Object> astp=new ASTProcessor<>(dataholder, null);
		astp
		.callMethodInvocationVisitor(IProgressMonitor.class,"beginTask",(node,holder) -> { //$NON-NLS-1$
			System.out.println("init "+node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$
			return true;
		},s -> ASTNodes.getTypedAncestor(s, Block.class))
		.callClassInstanceCreationVisitor((node,holder) -> {
			System.out.println("init "+node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$
			return true;
		}).build(compilationUnit);
		
//		HelperVisitor.callMethodInvocationVisitor(IProgressMonitor.class,"beginTask",compilationUnit,new ReferenceHolder<ASTNode, JfaceCandidateHit>(),
//				nodesprocessed, (visited, holder) -> {
//					if(visited.arguments().size()!=2) {
//						return true;
//					}
//					ExpressionStatement expr= ASTNodes.getTypedAncestor(visited, ExpressionStatement.class);
//					String name = null;
//					SimpleName sn= ASTNodes.as(visited.getExpression(), SimpleName.class);
//					if (sn != null) {
//						IBinding ibinding= sn.resolveBinding();
//						name= ibinding.getName();
//						IVariableBinding vb=(IVariableBinding) ibinding;
//						//						ITypeBinding binding= vb.getType();
//						//						if ((binding != null) && (IProgressMonitor.class.getSimpleName().equals(binding.getName()))) {
//						JfaceCandidateHit invalidHit= new JfaceCandidateHit();
//						invalidHit.monitor=visited;
//						operations.add(fixcore.rewrite(invalidHit));
//						nodesprocessed.add(visited);
//						System.out.println("asdf"+name + " " + vb+" " +visited);	
//						return false;
//						//						}
//					}
//					return true;
//				});
	}

	@Override
	public void rewrite(JfaceCleanUpFixCore upp, final JfaceCandidateHit hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
//		ASTRewrite rewrite= cuRewrite.getASTRewrite();
//		AST ast= cuRewrite.getRoot().getAST();

		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		ImportRemover remover= cuRewrite.getImportRemover();
		System.out.println("rewrite"+hit);	

		remover.applyRemoves(importRewrite);
	}


	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "\nbla\n\n"; //$NON-NLS-1$
		}
		return "\nblubb\n\n"; //$NON-NLS-1$
	}
}
