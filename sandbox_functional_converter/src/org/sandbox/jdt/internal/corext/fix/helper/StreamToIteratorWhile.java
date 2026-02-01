/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Transformer for converting Stream forEach expressions to iterator-based while-loops.
 * 
 * <p>Transformation: {@code collection.forEach(item -> ...)} → {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }}</p>
 * 
 * <p><b>Status:</b> Stub implementation - Phase 9 bidirectional loop transformations</p>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453</a>
 * @see <a href="https://github.com/carstenartur/sandbox/issues/549">Issue #549</a>
 */
public class StreamToIteratorWhile extends AbstractFunctionalCall<ASTNode> {

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, Object> dataHolder = new ReferenceHolder<>();
		
		// Find forEach method calls
		HelperVisitor.callMethodInvocationVisitor("forEach", compilationUnit, dataHolder, nodesprocessed, //$NON-NLS-1$
			(visited, aholder) -> {
				// Check if this is collection.forEach(...) or collection.stream().forEach(...)
				if (visited.arguments().size() != 1) {
					return false;
				}
				
				Object arg = visited.arguments().get(0);
				if (!(arg instanceof LambdaExpression)) {
					return false; // Only handle simple lambda expressions
				}
				
				// Make sure parent is an ExpressionStatement (standalone forEach call)
				if (!(visited.getParent() instanceof ExpressionStatement)) {
					return false;
				}
				
				operations.add(fixcore.rewrite(visited, new ReferenceHolder<>()));
				nodesprocessed.add(visited);
				return false;
			});
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore useExplicitEncodingFixCore, ASTNode visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group, ReferenceHolder<ASTNode, Object> data)
			throws CoreException {
		if (!(visited instanceof MethodInvocation)) {
			return;
		}
		
		MethodInvocation forEach = (MethodInvocation) visited;
		
		// Get the lambda expression
		if (forEach.arguments().isEmpty() || !(forEach.arguments().get(0) instanceof LambdaExpression)) {
			return;
		}
		
		LambdaExpression lambda = (LambdaExpression) forEach.arguments().get(0);
		
		// Extract collection expression (either collection or collection.stream())
		Expression collectionExpr = forEach.getExpression();
		if (collectionExpr instanceof MethodInvocation) {
			MethodInvocation methodInv = (MethodInvocation) collectionExpr;
			if ("stream".equals(methodInv.getName().getIdentifier())) { //$NON-NLS-1$
				// It's collection.stream().forEach(...), use the collection part
				collectionExpr = methodInv.getExpression();
			}
		}
		
		if (collectionExpr == null) {
			return;
		}
		
		AST ast = cuRewrite.getAST();
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		
		// Extract parameter name and type from lambda
		if (lambda.parameters().isEmpty()) {
			return;
		}
		
		VariableDeclaration param = (VariableDeclaration) lambda.parameters().get(0);
		SimpleName paramName = param.getName();
		
		// Determine parameter type
		Type paramType;
		if (param instanceof SingleVariableDeclaration) {
			SingleVariableDeclaration svd = (SingleVariableDeclaration) param;
			if (svd.getType() != null) {
				paramType = (Type) ASTNode.copySubtree(ast, svd.getType());
			} else {
				// Use String as fallback
				paramType = ast.newSimpleType(ast.newName("String")); //$NON-NLS-1$
			}
		} else {
			// Simple parameter, use String as fallback
			paramType = ast.newSimpleType(ast.newName("String")); //$NON-NLS-1$
		}
		
		String iteratorName = "it"; //$NON-NLS-1$
		
		// Create Iterator<T> it = collection.iterator();
		ParameterizedType iteratorType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Iterator"))); //$NON-NLS-1$
		iteratorType.typeArguments().add(ASTNode.copySubtree(ast, paramType));
		
		VariableDeclarationFragment iterFragment = ast.newVariableDeclarationFragment();
		iterFragment.setName(ast.newSimpleName(iteratorName));
		
		MethodInvocation iteratorCall = ast.newMethodInvocation();
		iteratorCall.setExpression((Expression) ASTNode.copySubtree(ast, collectionExpr));
		iteratorCall.setName(ast.newSimpleName("iterator")); //$NON-NLS-1$
		iterFragment.setInitializer(iteratorCall);
		
		VariableDeclarationStatement iteratorDecl = ast.newVariableDeclarationStatement(iterFragment);
		iteratorDecl.setType(iteratorType);
		
		// Create while (it.hasNext())
		MethodInvocation hasNextCall = ast.newMethodInvocation();
		hasNextCall.setExpression(ast.newSimpleName(iteratorName));
		hasNextCall.setName(ast.newSimpleName("hasNext")); //$NON-NLS-1$
		
		WhileStatement whileStmt = ast.newWhileStatement();
		whileStmt.setExpression(hasNextCall);
		
		// Create T item = it.next();
		MethodInvocation nextCall = ast.newMethodInvocation();
		nextCall.setExpression(ast.newSimpleName(iteratorName));
		nextCall.setName(ast.newSimpleName("next")); //$NON-NLS-1$
		
		VariableDeclarationFragment itemFragment = ast.newVariableDeclarationFragment();
		itemFragment.setName((SimpleName) ASTNode.copySubtree(ast, paramName));
		itemFragment.setInitializer(nextCall);
		
		VariableDeclarationStatement itemDecl = ast.newVariableDeclarationStatement(itemFragment);
		itemDecl.setType((Type) ASTNode.copySubtree(ast, paramType));
		
		// Create while body with item declaration and lambda body
		Block whileBody = ast.newBlock();
		whileBody.statements().add(itemDecl);
		
		// Extract lambda body
		ASTNode lambdaBody = lambda.getBody();
		if (lambdaBody instanceof Block) {
			// Lambda has block body
			Block lambdaBlock = (Block) lambdaBody;
			for (Object stmt : lambdaBlock.statements()) {
				whileBody.statements().add(ASTNode.copySubtree(ast, (Statement) stmt));
			}
		} else if (lambdaBody instanceof Expression) {
			// Lambda has expression body - wrap in expression statement
			ExpressionStatement exprStmt = ast.newExpressionStatement(
				(Expression) ASTNode.copySubtree(ast, (Expression) lambdaBody));
			whileBody.statements().add(exprStmt);
		}
		
		whileStmt.setBody(whileBody);
		
		// Replace the forEach statement with iterator declaration + while-loop
		ExpressionStatement forEachStmt = (ExpressionStatement) forEach.getParent();
		ASTNode parent = forEachStmt.getParent();
		
		if (parent instanceof Block) {
			Block parentBlock = (Block) parent;
			int index = parentBlock.statements().indexOf(forEachStmt);
			
			org.eclipse.jdt.core.dom.rewrite.ListRewrite listRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
			listRewrite.insertBefore(iteratorDecl, forEachStmt, group);
			listRewrite.replace(forEachStmt, whileStmt, group);
		} else {
			// If not in a block, create one
			Block newBlock = ast.newBlock();
			newBlock.statements().add(iteratorDecl);
			newBlock.statements().add(whileStmt);
			rewrite.replace(forEachStmt, newBlock, group);
		}
		
		// Add Iterator import
		cuRewrite.getImportRewrite().addImport("java.util.Iterator"); //$NON-NLS-1$
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					Iterator<String> it = items.iterator();
					while (it.hasNext()) {
						String item = it.next();
						System.out.println(item);
					}
					""";
		}
		return """
				items.forEach(item -> System.out.println(item));
				""";
	}
}
