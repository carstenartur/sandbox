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

import java.util.Iterator;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.corext.fix.UseIteratorToForLoopFixCore;

/**
 * Find: while (it.hasNext()){ System.out.println(it.next()); }
 *
 * Rewrite: for(Object o:collection) { System.out.println(o); });
 *
 */
public class WhileToForEach extends AbstractTool<Hit> {
	private static final String ITERATOR_NAME = Iterator.class.getCanonicalName();

	@Override
	public void find(UseIteratorToForLoopFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		HelperVisitor<MyReferenceHolder> hv = new HelperVisitor<>(nodesprocessed, new MyReferenceHolder());
		hv.addVariableDeclarationStatement((assignment, holder) -> {
			return processVariableDeclarationStatement(fixcore, operations, assignment, holder);
		});
		hv.addWhileStatement(this::processWhileStatement);
		hv.addWhileStatement((assignment, holder) -> {
			System.out.println(assignment);
		});
		hv.addMethodInvocation((assignment, holder) -> {
			return true;
		});
		hv.build(compilationUnit);
	}

	private Boolean processVariableDeclarationStatement(UseIteratorToForLoopFixCore fixcore,
			Set<CompilationUnitRewriteOperation> operations, VariableDeclarationStatement assignment,
			MyReferenceHolder holder) {
		VariableDeclarationFragment bli = (VariableDeclarationFragment) assignment.fragments().get(0);
		Expression exp = bli.getInitializer();
		String qualifiedName = bli.resolveBinding().getType().getErasure().getQualifiedName();
		if (ITERATOR_NAME.equals(qualifiedName)) {
			if (exp instanceof MethodInvocation) {
				MethodInvocation mi = (MethodInvocation) exp;
				Expression element = mi.getExpression();
				String name = bli.resolveBinding().getName();
				if (element == null) {
					Hit hit = holder.possibleHit(name);
					hit.self = true;
					hit.iteratordeclaration = assignment;
					return true;
				}
				if (element instanceof SimpleName) {
					SimpleName sn = (SimpleName) element;
					System.out.println(mi.getName());
					if (mi.getName().toString().equals("iterator")) {
						Hit hit = holder.possibleHit(name);
						hit.collectionsimplename = sn;
						hit.iteratordeclaration = assignment;
					}
				}
			}
		} else if (exp instanceof CastExpression) {
			CastExpression ce = (CastExpression) exp;
			Expression element = ce.getExpression();
			if (element instanceof MethodInvocation) {
				MethodInvocation mi = (MethodInvocation) element;
				if (mi.getName().toString().equals("next")) {
					Expression element2 = mi.getExpression();
					if (element2 instanceof SimpleName) {
						SimpleName sn = (SimpleName) element2;
						// sn.resolveBinding().getName();
						System.out.println(sn.getFullyQualifiedName());
						if (holder.containsKey(sn.getIdentifier())) {
							Hit hit = holder.get(sn.getIdentifier());
							String identifier = sn.getIdentifier();
//							if(holder.containsKey(identifier)) {
							if (holder.getHelperVisitor().nodesprocessed.contains(hit.whilestatement)) {
								holder.remove(identifier);
								return true;
							}
							hit.loopvarname = bli.getName().getIdentifier();
							hit.loopvardeclaration = assignment;
							operations.add(fixcore.rewrite(hit));
							holder.getHelperVisitor().nodesprocessed.add(hit.whilestatement);
							System.out.println("" + hit.iteratordeclaration);
							holder.remove(identifier);
//							}
						}
					}
				}
			}
		}
		return true;
	}

	private Boolean processWhileStatement(WhileStatement whilestatement, MyReferenceHolder holder) {
		Expression exp = whilestatement.getExpression();
		if (exp instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) exp;
			Expression expression = mi.getExpression();
			if (!mi.getName().getIdentifier().equals("hasNext")) {
				return true;
			}
			ITypeBinding resolveTypeBinding = expression.resolveTypeBinding();
			String name = null;
			if (expression instanceof SimpleName) {
				IBinding resolveBinding = ((SimpleName) expression).resolveBinding();
				name = resolveBinding.getName();
			}
			String mytype = resolveTypeBinding.getErasure().getQualifiedName();
			if (holder.containsKey(name) && ITERATOR_NAME.equals(mytype)) {
				Hit hit = holder.get(name);
				hit.whilestatement = whilestatement;
				// operations.add(fixcore.rewrite(holder));
				// nodesprocessed.add(visited);
				return true;
			}
		}
		return true;
	}

	@Override
	public void rewrite(UseIteratorToForLoopFixCore upp, final Hit hit, final CompilationUnitRewrite cuRewrite,
			TextEditGroup group) {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();

		EnhancedForStatement newEnhancedForStatement = ast.newEnhancedForStatement();
		newEnhancedForStatement.setBody(ASTNodes.createMoveTarget(rewrite, hit.whilestatement.getBody()));

		SingleVariableDeclaration result = ast.newSingleVariableDeclaration();

		SimpleName name = ast.newSimpleName(hit.loopvarname);
		result.setName(name);

		Type type = hit.loopvardeclaration.getType();
		ParameterizedType mytype = null;
		SimpleType object = null;
		String looptargettype;

		Type type2 = null;
		if (type instanceof ParameterizedType) {
			mytype = (ParameterizedType) type;
			type2 = mytype.getType();
			object = (SimpleType) mytype.typeArguments().get(0);
			looptargettype = type2.resolveBinding().getErasure().getQualifiedName();
			Type collectionType = ast.newSimpleType(addImport(looptargettype, cuRewrite, ast));
			ParameterizedType genericType = ast.newParameterizedType(collectionType);
			String fullyQualifiedName = object.getName().getFullyQualifiedName();
			genericType.typeArguments().add(ast.newSimpleType(ast.newName(fullyQualifiedName)));
			result.setType(genericType);
		} else {
			looptargettype = type.resolveBinding().getQualifiedName();
			Type collectionType = ast.newSimpleType(addImport(looptargettype, cuRewrite, ast));
			result.setType(collectionType);
		}

		newEnhancedForStatement.setParameter(result);
		if (hit.self) {
			ThisExpression newThisExpression = ast.newThisExpression();
			newEnhancedForStatement.setExpression(newThisExpression);
		} else {
			SimpleName createMoveTarget = ast.newSimpleName(hit.collectionsimplename.getIdentifier());
			newEnhancedForStatement.setExpression(createMoveTarget);
		}
		ASTNodes.removeButKeepComment(rewrite, hit.iteratordeclaration, group);
		ASTNodes.removeButKeepComment(rewrite, hit.loopvardeclaration, group);
		ASTNodes.replaceButKeepComment(rewrite, hit.whilestatement, newEnhancedForStatement, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return "\nfor (String s : strings) {\n\n	System.out.println(s);\n}\n\n";
		}
		return "Iterator it = lists.iterator();\nwhile (it.hasNext()) {\n    String s = (String) it.next();\n	System.out.println(s);\n}\n\n";
	}
}
