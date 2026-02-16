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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.cleanup.ExceptionCleanupHelper;

/**
 * Unit tests for {@link ExceptionCleanupHelper}.
 *
 * <p>These tests verify the AST ancestor search and exception type matching
 * logic extracted from AbstractExplicitEncoding. They run without a full
 * Eclipse plugin environment.</p>
 */
@DisplayName("ExceptionCleanupHelper Tests")
public class ExceptionCleanupHelperTest {

	@Test
	@DisplayName("findEnclosingMethodOrTry returns TryStatement when inside try block")
	void testFindEnclosingTryStatement() {
		String source = """
				class Dummy {
				    void m() {
				        try {
				            foo("UTF-8");
				        } catch (Exception e) {}
				    }
				}
				"""; //$NON-NLS-1$
		ASTNode exprNode = findFirstExpressionStatement(source);
		ASTNode enclosing = ExceptionCleanupHelper.findEnclosingMethodOrTry(exprNode);
		assertNotNull(enclosing);
		assertInstanceOf(TryStatement.class, enclosing);
	}

	@Test
	@DisplayName("findEnclosingMethodOrTry returns MethodDeclaration when no try block")
	void testFindEnclosingMethodDeclaration() {
		String source = """
				class Dummy {
				    void m() {
				        foo("UTF-8");
				    }
				}
				"""; //$NON-NLS-1$
		ASTNode exprNode = findFirstExpressionStatement(source);
		ASTNode enclosing = ExceptionCleanupHelper.findEnclosingMethodOrTry(exprNode);
		assertNotNull(enclosing);
		assertInstanceOf(MethodDeclaration.class, enclosing);
	}

	@Test
	@DisplayName("findEnclosingMethodOrTry returns null for top-level node")
	void testFindEnclosingReturnsNullForTopLevel() {
		String source = "class Dummy {}"; //$NON-NLS-1$
		CompilationUnit cu = parseCompilationUnit(source);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		ASTNode enclosing = ExceptionCleanupHelper.findEnclosingMethodOrTry(type);
		assertNull(enclosing);
	}

	@Test
	@DisplayName("isTargetException matches simple exception name")
	void testIsTargetExceptionMatches() {
		String source = """
				class Dummy {
				    void m() {
				        try {
				            foo();
				        } catch (UnsupportedEncodingException e) {}
				    }
				}
				"""; //$NON-NLS-1$
		CompilationUnit cu = parseCompilationUnit(source);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		TryStatement tryStmt = (TryStatement) method.getBody().statements().get(0);
		org.eclipse.jdt.core.dom.CatchClause catchClause = (org.eclipse.jdt.core.dom.CatchClause) tryStmt.catchClauses().get(0);
		org.eclipse.jdt.core.dom.Type exType = catchClause.getException().getType();

		assertTrue(ExceptionCleanupHelper.isTargetException(exType, "UnsupportedEncodingException")); //$NON-NLS-1$
		assertFalse(ExceptionCleanupHelper.isTargetException(exType, "IOException")); //$NON-NLS-1$
	}

	/**
	 * Parses the given source as a compilation unit.
	 */
	private static CompilationUnit parseCompilationUnit(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setSource(source.toCharArray());
		parser.setUnitName("Dummy.java"); //$NON-NLS-1$
		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * Finds the first ExpressionStatement inside the first method body.
	 */
	private static ASTNode findFirstExpressionStatement(String source) {
		CompilationUnit cu = parseCompilationUnit(source);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		Object firstStmt = method.getBody().statements().get(0);
		if (firstStmt instanceof TryStatement tryStmt) {
			return (ASTNode) tryStmt.getBody().statements().get(0);
		}
		if (firstStmt instanceof ExpressionStatement) {
			return (ASTNode) firstStmt;
		}
		return (ASTNode) firstStmt;
	}
}
