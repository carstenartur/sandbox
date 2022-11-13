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

import java.io.FileInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ASTProcessor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JfaceCleanUpFixCore;

/**
 *
 */
public class JFacePlugin extends AbstractTool<ReferenceHolder<String, Object>> {

	public static final String CLASS_INSTANCE_CREATION = "ClassInstanceCreation";
	public static final String METHODINVOCATION = "MethodInvocation";


	@Override
	public void find(JfaceCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed,
			boolean createForOnlyIfVarUsed) {
		ReferenceHolder<String, Object> dataholder = new ReferenceHolder<>();
		ASTProcessor<ReferenceHolder<String, Object>,String,Object> astp=new ASTProcessor<>(dataholder, nodesprocessed);
		astp
		.callMethodInvocationVisitor(IProgressMonitor.class,"beginTask",(node,holder) -> { //$NON-NLS-1$
			if(node.arguments().size()!=2) {
				return true;
			}
			System.out.println("begintask "+node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$
			SimpleName sn= ASTNodes.as(node.getExpression(), SimpleName.class);
			if (sn != null) {
				IBinding ibinding= sn.resolveBinding();
				String name = ibinding.getName();
				holder.put(METHODINVOCATION+"_varname",name);
				holder.put(METHODINVOCATION,node);
//				nodesprocessed.add(node);
			}
			return true;
		},s -> ASTNodes.getTypedAncestor(s, Block.class))
		.callClassInstanceCreationVisitor(SubProgressMonitor.class,(node,holder) -> {
			System.out.println("init "+node.getNodeType() + " :" + node); //$NON-NLS-1$ //$NON-NLS-2$
			Set<ClassInstanceCreation> nodeset=(Set<ClassInstanceCreation>) holder.computeIfAbsent(CLASS_INSTANCE_CREATION, k->new HashSet<ClassInstanceCreation>());
			nodeset.add(node);
			return true;
		})
		.build(compilationUnit);
		operations.add(fixcore.rewrite(dataholder));
		nodesprocessed.add((ASTNode) dataholder.get(METHODINVOCATION));

//		HelperVisitor.callMethodInvocationVisitor(IProgressMonitor.class,"beginTask",compilationUnit,new ReferenceHolder<ASTNode, JfaceCandidateHit>(),
//				nodesprocessed, (visited, holder) -> {
//					name
//					ExpressionStatement expr= ASTNodes.getTypedAncestor(visited, ExpressionStatement.class);
//					String name = null;
//					SimpleName sn= ASTNodes.as(visited.getExpression(), SimpleName.class);
//					if (sn != null) {
//						IBinding ibinding= sn.resolveBinding();
//						name= ibinding.getName();
//						IVariableBinding vb=(IVariableBinding) ibinding;
//						//						ITypeBinding binding= vb.getType();
//						//						if ((binding != null) && (IProgressMonitor.class.getSimpleName().equals(binding.getName()))) {

//						System.out.println("asdf"+name + " " + vb+" " +visited);	
//						return false;
//						//						}
//					}
//					return true;
//				});
	}

	@Override
	public void rewrite(JfaceCleanUpFixCore upp, final ReferenceHolder<String, Object> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		AST ast= cuRewrite.getRoot().getAST();

		ImportRewrite importRewrite= cuRewrite.getImportRewrite();
		ImportRemover remover= cuRewrite.getImportRemover();
		System.out.println("rewrite"+hit);	
		String name = (String) hit.get(METHODINVOCATION+"_varname");
		MethodInvocation minv= (MethodInvocation) hit.get(METHODINVOCATION);
		List<ASTNode> arguments= minv.arguments();
		Set<ClassInstanceCreation> nodeset=(Set<ClassInstanceCreation>) hit.get(CLASS_INSTANCE_CREATION);
		
		// monitor.beginTask(NewWizardMessages.NewSourceFolderWizardPage_operation, 3);
		// IProgressMonitor subProgressMonitor= new SubProgressMonitor(monitor, 1);
		
		// SubMonitor subMonitor = SubMonitor.convert(monitor,NewWizardMessages.NewSourceFolderWizardPage_operation, 3);
		// IProgressMonitor subProgressMonitor= subMonitor.split(1);

		SingleVariableDeclaration newVariableDeclarationStatement = ast.newSingleVariableDeclaration();
		newVariableDeclarationStatement.setName(ast.newSimpleName("subMonitor"));
		newVariableDeclarationStatement.setType(ast.newSimpleType(addImport(SubMonitor.class.getCanonicalName(),cuRewrite,ast)));
		
		MethodInvocation staticCall= ast.newMethodInvocation();
		staticCall.setExpression(ASTNodeFactory.newName(ast, SubMonitor.class.getSimpleName()));
		staticCall.setName(ast.newSimpleName("convert"));
		List<ASTNode> staticCallArguments= staticCall.arguments();
		staticCallArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(minv.getExpression())));
		staticCallArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(0))));
		staticCallArguments.add(ASTNodes.createMoveTarget(rewrite,
				ASTNodes.getUnparenthesedExpression(arguments.get(1))));
		newVariableDeclarationStatement.setInitializer(staticCall);
		
		ASTNodes.replaceButKeepComment(rewrite, minv, newVariableDeclarationStatement, group);
		System.out.println("result"+staticCall);	
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
