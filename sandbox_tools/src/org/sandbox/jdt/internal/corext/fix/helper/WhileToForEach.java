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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.corext.fix.UseIteratorToForLoopFixCore;

/**
 * Find: for (Integer l : ls){ System.out.println(l); }
 *
 * Rewrite: ls.forEach(l -> { System.out.println(l); });
 *
 */
public class WhileToForEach extends AbstractTool<WhileStatement> {
//	private static final String ITERATOR_NAME = Iterator.class.getCanonicalName();
	
	HelperVisitor<ReferenceHolder> hv;
	
	@Override
	public void find(UseIteratorToForLoopFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		hv=new HelperVisitor<>(null);
		hv.addVariableDeclarationStatement(this::processVariableDeclarationStatement);
		hv.addSingleVariableDeclaration((declaration,holder)->{
			if (holder != null && holder.whilestatement!= null) {
				operations.add(fixcore.rewrite(holder));
				nodesprocessed.add(holder.whilestatement);
				System.out.println(""+holder.iteratordeclaration);
				holder = null;
			}
			return false;
		});
		hv.addWhileStatement(this::processWhileStatement);
		hv.build(compilationUnit);
//		compilationUnit.accept(new ASTVisitor() {
//			ReferenceHolder holder;
//
//			@Override
//			public boolean visit(VariableDeclarationStatement assignment) {
//				VariableDeclarationFragment bli = (VariableDeclarationFragment) assignment.fragments().get(0);
//				if ("java.util.Iterator".equals(bli.resolveBinding().getType().getQualifiedName())) {
//					holder=new ReferenceHolder();
//					holder.iteratordeclaration = assignment;
//					holder.iteratorvariablename = bli.resolveBinding().getName();
//				}
//				return true;
//			}
//
//			@Override
//			public boolean visit(Assignment assignment) {
//				ITypeBinding assignmentTypeBinding = assignment.resolveTypeBinding();
//				if (isOfType(assignmentTypeBinding, ITERATOR_NAME)) {
//					Expression leftSide = assignment.getLeftHandSide();
//					if (leftSide instanceof ArrayAccess) {
//						while (leftSide instanceof ArrayAccess) {
//							leftSide = ((ArrayAccess) leftSide).getArray();
//						}
//					}
//
//					IBinding leftSideBinding = null;
//					if (leftSide instanceof Name) {
//						leftSideBinding = ((Name) leftSide).resolveBinding();
//					} else if (leftSide instanceof SuperFieldAccess) {
//						leftSideBinding = ((SuperFieldAccess) leftSide).resolveFieldBinding();
//					} else if (leftSide instanceof FieldAccess) {
//						leftSideBinding = ((FieldAccess) leftSide).resolveFieldBinding();
//					}
//					if (leftSideBinding != null) {
//						if (leftSideBinding instanceof IVariableBinding) {
//							IVariableBinding leftSideVarBinding = (IVariableBinding) leftSideBinding;
//							CheckNodeForValidReferences checkNode = new CheckNodeForValidReferences(
//									assignment.getRightHandSide(),
//									!leftSideVarBinding.isField() && !leftSideVarBinding.isParameter()
//											&& !leftSideVarBinding.isRecordComponent());
//							if (checkNode.isValid()) {
//								return true;
//							}
//						}
//					}
//					throw new AbortSearchException();
//				}
//				return true;
//			}
//
//			@Override
//			public boolean visit(SingleVariableDeclaration assignment) {
//				if (holder != null && holder.whilestatement!= null) {
//					operations.add(fixcore.rewrite(holder));
//					nodesprocessed.add(holder.whilestatement);
//					holder = null;
//				}
//				return false;
//			}
//
//			@Override
//			public final boolean visit(final WhileStatement visited) {
//				if (holder != null) {
//					Expression exp = visited.getExpression();
//					if (exp instanceof MethodInvocation) {
//						MethodInvocation mi = (MethodInvocation) exp;
//						Expression expression = mi.getExpression();
//						ITypeBinding resolveTypeBinding = expression.resolveTypeBinding();
//						String name = null;
//						if (expression instanceof SimpleName) {
//							name = ((SimpleName) expression).resolveBinding().getName();
//						}
//						String mytype = resolveTypeBinding.getQualifiedName();
//
//						if (name.equals(holder.iteratorvariablename) && "java.util.Iterator".equals(mytype)) {
//							holder.whilestatement=visited;
////							operations.add(fixcore.rewrite(holder));
////							nodesprocessed.add(visited);
//							return true;
//						}
//					}
//					holder = null;
//				}
//				return true;
//			}
//		});
	}

	private Boolean processWhileStatement(WhileStatement whilestatement, ReferenceHolder holder) {
		if (holder != null) {
			Expression exp = whilestatement.getExpression();
			if (exp instanceof MethodInvocation) {
				MethodInvocation mi = (MethodInvocation) exp;
				Expression expression = mi.getExpression();
				ITypeBinding resolveTypeBinding = expression.resolveTypeBinding();
				String name = null;
				if (expression instanceof SimpleName) {
					name = ((SimpleName) expression).resolveBinding().getName();
				}
				String mytype = resolveTypeBinding.getQualifiedName();

				if (name.equals(holder.iteratorvariablename) && "java.util.Iterator".equals(mytype)) {
					holder.whilestatement=whilestatement;
//						operations.add(fixcore.rewrite(holder));
//						nodesprocessed.add(visited);
					return true;
				}
			}
			holder = null;
		}
		return true;
	}

//	private Boolean processSingleVariableDeclaration(UseIteratorToForLoopFixCore fixcore,
//			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ReferenceHolder holder) {
//		if (holder != null && holder.whilestatement!= null) {
//			operations.add(fixcore.rewrite(holder));
//			nodesprocessed.add(holder.whilestatement);
//			holder = null;
//		}
//		return false;
//	}

	public Boolean processVariableDeclarationStatement(VariableDeclarationStatement assignment,ReferenceHolder holder) {
		VariableDeclarationFragment bli = (VariableDeclarationFragment) assignment.fragments().get(0);
		if ("java.util.Iterator".equals(bli.resolveBinding().getType().getQualifiedName())) {
			holder=hv.dataholder=new ReferenceHolder();
			holder.iteratordeclaration = assignment;
			holder.iteratorvariablename = bli.resolveBinding().getName();
		}
		return true;
	}

	@Override
	public void rewrite(UseIteratorToForLoopFixCore upp, final ReferenceHolder holder,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		/**
		 * for (Integer l : ls){ System.out.println(l); }
		 * 
		 * loopBody= { System.out.println(l); }
		 * 
		 * parameter= Integer l
		 * 
		 * expr= ls
		 * 
		 */
		EnhancedForStatement newEnhancedForStatement = ast.newEnhancedForStatement();
		newEnhancedForStatement.setBody(ASTNodes.createMoveTarget(rewrite, holder.whilestatement.getBody()));
		
		SingleVariableDeclaration result= ast.newSingleVariableDeclaration();

		SimpleName name= ast.newSimpleName(holder.iteratorvariablename);
//		pg.addPosition(rewrite.track(name), true);
		result.setName(name);
		
		result.setType(ast.newSimpleType(ast.newName("String")));
		newEnhancedForStatement.setParameter(result);
//		newEnhancedForStatement.setParameter(createParameterDeclaration(ast,holder.iteratorvariablename,holder.iteratordeclaration,newEnhancedForStatement,rewrite,group));
		ASTNodes.replaceButKeepComment(rewrite, holder.whilestatement, newEnhancedForStatement, group);

	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "ls.forEach(l -> {\n	System.out.println(l);\n});\n"; // $NON-NLS-3$
		}
		return "for (Integer l : ls)\n	System.out.println(l);\n\n"; // $NON-NLS-3$
	}
	
	private SingleVariableDeclaration createParameterDeclaration(AST ast,String parameterName, VariableDeclarationFragment fragement, EnhancedForStatement statement, ImportRewrite importRewrite, ASTRewrite rewrite, TextEditGroup group) {
		

		SingleVariableDeclaration result= ast.newSingleVariableDeclaration();

		SimpleName name= ast.newSimpleName(parameterName);
//		pg.addPosition(rewrite.track(name), true);
		result.setName(name);

//		ITypeBinding arrayTypeBinding= arrayAccess.resolveTypeBinding();
//		Type type= importType(arrayTypeBinding.getElementType(), statement, importRewrite, compilationUnit,
//				arrayTypeBinding.getDimensions() == 1 ? TypeLocation.LOCAL_VARIABLE : TypeLocation.ARRAY_CONTENTS);
//		if (arrayTypeBinding.getDimensions() != 1) {
//			type= ast.newArrayType(type, arrayTypeBinding.getDimensions() - 1);
//		}
//		result.setType(type);

		if (fragement != null) {
			VariableDeclarationStatement declaration= (VariableDeclarationStatement)fragement.getParent();
			ModifierRewrite.create(rewrite, result).copyAllModifiers(declaration, group);
		}
//		if (makeFinal && (fragement == null || ASTNodes.findModifierNode(Modifier.FINAL, ASTNodes.getModifiers(fragement)) == null)) {
//			ModifierRewrite.create(rewrite, result).setModifiers(Modifier.FINAL, 0, group);
//		}

		return result;
	}
}
