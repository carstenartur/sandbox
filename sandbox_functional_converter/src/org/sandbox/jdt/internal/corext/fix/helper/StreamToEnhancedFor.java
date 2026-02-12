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
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Transformer for converting Stream forEach expressions to enhanced for-loops.
 * 
 * <p>Transformation: {@code collection.forEach(item -> ...)} → {@code for (T item : collection) { ... }}</p>
 * 
 * <p><b>Note:</b> This transformer uses direct AST manipulation, not the ULR pipeline.
 * A future enhancement could introduce a ULR-based {@code EnhancedForRenderer} to unify
 * all transformations through the ULR pipeline.</p>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453</a>
 * @see <a href="https://github.com/carstenartur/sandbox/issues/549">Issue #549</a>
 */
public class StreamToEnhancedFor extends AbstractFunctionalCall<ASTNode> {

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, Object> dataHolder = ReferenceHolder.create();
		
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
				
				// Safety: reject chained stream operations (map/filter/reduce/flatMap/sorted/distinct)
				// Only simple collection.forEach() or collection.stream().forEach() can be converted
				if (StreamOperationDetector.hasChainedStreamOperations(visited)) {
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
		
		// Determine parameter type (use var or infer from context)
		org.eclipse.jdt.core.dom.Type paramType;
		if (param instanceof SingleVariableDeclaration) {
			SingleVariableDeclaration svd = (SingleVariableDeclaration) param;
			if (svd.getType() != null) {
				paramType = (org.eclipse.jdt.core.dom.Type) ASTNode.copySubtree(ast, svd.getType());
			} else {
				// Use String as fallback
				paramType = ast.newSimpleType(ast.newName("String")); //$NON-NLS-1$
			}
		} else {
			// Simple parameter, use String as fallback
			paramType = ast.newSimpleType(ast.newName("String")); //$NON-NLS-1$
		}
		
		// Create enhanced for parameter
		SingleVariableDeclaration forParam = ast.newSingleVariableDeclaration();
		forParam.setType(paramType);
		forParam.setName((SimpleName) ASTNode.copySubtree(ast, paramName));
		
		// Create enhanced for-loop
		EnhancedForStatement forStmt = ast.newEnhancedForStatement();
		forStmt.setParameter(forParam);
		forStmt.setExpression((Expression) ASTNode.copySubtree(ast, collectionExpr));
		
		// Extract lambda body, preserving comments via createCopyTarget
		ASTNode lambdaBody = lambda.getBody();
		Block forBody = ast.newBlock();
		
		if (lambdaBody instanceof Block) {
			// Lambda has block body
			Block lambdaBlock = (Block) lambdaBody;
			for (Object stmt : lambdaBlock.statements()) {
				forBody.statements().add(rewrite.createCopyTarget((Statement) stmt));
			}
		} else if (lambdaBody instanceof Expression) {
			// Lambda has expression body - wrap in expression statement
			ExpressionStatement exprStmt = ast.newExpressionStatement(
				(Expression) ASTNode.copySubtree(ast, (Expression) lambdaBody));
			forBody.statements().add(exprStmt);
		}
		
		forStmt.setBody(forBody);
		
		// Replace the forEach statement with the for-loop
		ExpressionStatement forEachStmt = (ExpressionStatement) forEach.getParent();
		rewrite.replace(forEachStmt, forStmt, group);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					for (String item : items) {
						System.out.println(item);
					}
					""";
		}
		return """
				items.forEach(item -> System.out.println(item));
				""";
	}
}
