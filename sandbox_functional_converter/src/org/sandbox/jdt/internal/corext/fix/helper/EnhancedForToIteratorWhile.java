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
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseFunctionalCallFixCore;

/**
 * Transformer for converting enhanced for-loops to iterator-based while-loops.
 * 
 * <p>Transformation: {@code for (T item : collection) { ... }} → {@code Iterator<T> it = c.iterator(); while (it.hasNext()) { T item = it.next(); ... }}</p>
 * 
 * <p><b>Status:</b> Stub implementation - Phase 9 bidirectional loop transformations</p>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/453">Issue #453</a>
 * @see <a href="https://github.com/carstenartur/sandbox/issues/549">Issue #549</a>
 */
public class EnhancedForToIteratorWhile extends AbstractFunctionalCall<ASTNode> {

	@Override
	public void find(UseFunctionalCallFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed) {
		org.sandbox.jdt.internal.common.HelperVisitor.callEnhancedForStatementVisitor(compilationUnit, 
			new ReferenceHolder<Integer, Object>(), nodesprocessed, (visited, aholder) -> {
				// Enhanced for-loops can always be converted to iterator while-loops
				operations.add(fixcore.rewrite(visited, new ReferenceHolder<>()));
				nodesprocessed.add(visited);
				return false;
			});
	}

	@Override
	public void rewrite(UseFunctionalCallFixCore useExplicitEncodingFixCore, ASTNode visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group, ReferenceHolder<ASTNode, Object> data)
			throws CoreException {
		if (!(visited instanceof EnhancedForStatement)) {
			return;
		}
		
		EnhancedForStatement forStmt = (EnhancedForStatement) visited;
		AST ast = cuRewrite.getAST();
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		
		// Extract components from enhanced for-loop
		SimpleName paramName = forStmt.getParameter().getName();
		Type paramType = forStmt.getParameter().getType();
		Expression collection = forStmt.getExpression();
		Statement body = forStmt.getBody();
		
		// Create iterator variable name
		String iteratorName = "it"; //$NON-NLS-1$
		
		// Create Iterator<T> it = collection.iterator();
		ParameterizedType iteratorType = ast.newParameterizedType(ast.newSimpleType(ast.newName("Iterator"))); //$NON-NLS-1$
		iteratorType.typeArguments().add(ASTNode.copySubtree(ast, paramType));
		
		VariableDeclarationFragment iterFragment = ast.newVariableDeclarationFragment();
		iterFragment.setName(ast.newSimpleName(iteratorName));
		
		MethodInvocation iteratorCall = ast.newMethodInvocation();
		iteratorCall.setExpression((Expression) ASTNode.copySubtree(ast, collection));
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
		
		// Create while body with item declaration and original body
		Block whileBody = ast.newBlock();
		whileBody.statements().add(itemDecl);
		
		// Copy original body statements into while body
		if (body instanceof Block) {
			Block origBlock = (Block) body;
			for (Object stmt : origBlock.statements()) {
				whileBody.statements().add(ASTNode.copySubtree(ast, (Statement) stmt));
			}
		} else {
			whileBody.statements().add(ASTNode.copySubtree(ast, body));
		}
		
		whileStmt.setBody(whileBody);
		
		// Replace the for-loop with iterator declaration followed by while-loop
		ASTNode parent = forStmt.getParent();
		if (parent instanceof Block) {
			Block parentBlock = (Block) parent;
			int index = parentBlock.statements().indexOf(forStmt);
			
			org.eclipse.jdt.core.dom.rewrite.ListRewrite listRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
			listRewrite.insertBefore(iteratorDecl, forStmt, group);
			listRewrite.replace(forStmt, whileStmt, group);
		} else {
			// If not in a block, we need to create one
			Block newBlock = ast.newBlock();
			newBlock.statements().add(iteratorDecl);
			newBlock.statements().add(whileStmt);
			rewrite.replace(forStmt, newBlock, group);
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
				for (String item : items) {
					System.out.println(item);
				}
				""";
	}
}
