package org.sandbox.jdt.internal.common.test;

/*******************************************************************************
 * Copyright (c) 2026 Sandbox Contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Sandbox Contributors - initial API and implementation
 *******************************************************************************/

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.common.util.ASTNodeUtils;

/**
 * Tests for ASTNodeUtils - OSGi-free utility methods for AST manipulation.
 */
class ASTNodeUtilsTest {

	private AST createAST() {
		return AST.newAST(AST.JLS21, false);
	}

	@Test
	void testGetParent_Statement() {
		AST ast = createAST();
		Block block = ast.newBlock();
		ExpressionStatement stmt = ast.newExpressionStatement(ast.newNullLiteral());
		block.statements().add(stmt);
		
		Block foundBlock = ASTNodeUtils.getParent(stmt, Block.class);
		assertNotNull(foundBlock);
		assertEquals(block, foundBlock);
	}

	@Test
	void testGetParent_NullNode() {
		Block result = ASTNodeUtils.getParent(null, Block.class);
		assertNull(result);
	}

	@Test
	void testGetParent_NullClass() {
		AST ast = createAST();
		Statement stmt = ast.newEmptyStatement();
		
		Statement result = ASTNodeUtils.getParent(stmt, null);
		assertNull(result);
	}

	@Test
	void testGetParent_NotFound() {
		AST ast = createAST();
		Statement stmt = ast.newEmptyStatement();
		
		// Looking for a MethodDeclaration but stmt is not inside one
		MethodDeclaration result = ASTNodeUtils.getParent(stmt, MethodDeclaration.class);
		assertNull(result);
	}

	@Test
	void testIsParent_DirectParent() {
		AST ast = createAST();
		Block block = ast.newBlock();
		ExpressionStatement stmt = ast.newExpressionStatement(ast.newNullLiteral());
		block.statements().add(stmt);
		
		assertTrue(ASTNodeUtils.isParent(stmt, block));
	}

	@Test
	void testIsParent_NotParent() {
		AST ast = createAST();
		Block block1 = ast.newBlock();
		Block block2 = ast.newBlock();
		ExpressionStatement stmt = ast.newExpressionStatement(ast.newNullLiteral());
		block1.statements().add(stmt);
		
		assertFalse(ASTNodeUtils.isParent(stmt, block2));
	}

	@Test
	void testIsParent_NullNode() {
		AST ast = createAST();
		Block block = ast.newBlock();
		
		assertFalse(ASTNodeUtils.isParent(null, block));
	}

	@Test
	void testIsParent_NullParent() {
		AST ast = createAST();
		Statement stmt = ast.newEmptyStatement();
		
		assertFalse(ASTNodeUtils.isParent(stmt, null));
	}

	@Test
	void testGetContainingStatement() {
		AST ast = createAST();
		Block block = ast.newBlock();
		ExpressionStatement stmt = ast.newExpressionStatement(ast.newNullLiteral());
		block.statements().add(stmt);
		
		// The NullLiteral's containing statement should be the ExpressionStatement
		NullLiteral nullLit = (NullLiteral) stmt.getExpression();
		Statement containing = ASTNodeUtils.getContainingStatement(nullLit);
		
		assertNotNull(containing);
		assertEquals(stmt, containing);
	}

	@Test
	void testGetContainingMethod() {
		AST ast = createAST();
		MethodDeclaration method = ast.newMethodDeclaration();
		method.setName(ast.newSimpleName("testMethod"));
		Block body = ast.newBlock();
		method.setBody(body);
		ExpressionStatement stmt = ast.newExpressionStatement(ast.newNullLiteral());
		body.statements().add(stmt);
		
		MethodDeclaration found = ASTNodeUtils.getContainingMethod(stmt);
		assertNotNull(found);
		assertEquals(method, found);
	}

	@Test
	void testGetContainingType() {
		AST ast = createAST();
		TypeDeclaration type = ast.newTypeDeclaration();
		type.setName(ast.newSimpleName("TestClass"));
		MethodDeclaration method = ast.newMethodDeclaration();
		method.setName(ast.newSimpleName("testMethod"));
		type.bodyDeclarations().add(method);
		
		AbstractTypeDeclaration found = ASTNodeUtils.getContainingType(method);
		assertNotNull(found);
		assertEquals(type, found);
	}
}
