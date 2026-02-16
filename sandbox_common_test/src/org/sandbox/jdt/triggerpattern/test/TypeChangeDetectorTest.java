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
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.cleanup.TypeChangeDetector;
import org.sandbox.jdt.triggerpattern.cleanup.TypeChangeInfo;

/**
 * Unit tests for {@link TypeChangeDetector}.
 */
@DisplayName("TypeChangeDetector Tests")
public class TypeChangeDetectorTest {

	@Test
	@DisplayName("detects charset type change when StringLiteral UTF-8 replaced with StandardCharsets.UTF_8")
	void testDetectsCharsetTypeChange() {
		ASTNode node = parseExpression("new String(bytes, \"UTF-8\")"); //$NON-NLS-1$

		TypeChangeInfo info = TypeChangeDetector.detectCharsetTypeChange(
				node, "new String(bytes, StandardCharsets.UTF_8)"); //$NON-NLS-1$

		assertNotNull(info);
		assertEquals("java.io.UnsupportedEncodingException", info.exceptionFQN()); //$NON-NLS-1$
		assertEquals("UnsupportedEncodingException", info.exceptionSimpleName()); //$NON-NLS-1$
	}

	@Test
	@DisplayName("no detection for non-charset string literal")
	void testNoDetectionForNonCharsetString() {
		ASTNode node = parseExpression("foo(\"hello\")"); //$NON-NLS-1$

		TypeChangeInfo info = TypeChangeDetector.detectCharsetTypeChange(
				node, "foo(\"world\")"); //$NON-NLS-1$

		assertNull(info);
	}

	@Test
	@DisplayName("no detection when replacement does not contain StandardCharsets")
	void testNoDetectionWithoutStandardCharsets() {
		ASTNode node = parseExpression("new String(bytes, \"UTF-8\")"); //$NON-NLS-1$

		TypeChangeInfo info = TypeChangeDetector.detectCharsetTypeChange(
				node, "new String(bytes, getCharset())"); //$NON-NLS-1$

		assertNull(info);
	}

	@Test
	@DisplayName("no detection for null inputs")
	void testNoDetectionForNullInputs() {
		assertNull(TypeChangeDetector.detectCharsetTypeChange(null, "x")); //$NON-NLS-1$

		ASTNode node = parseExpression("foo()"); //$NON-NLS-1$
		assertNull(TypeChangeDetector.detectCharsetTypeChange(node, null));
	}

	@Test
	@DisplayName("detects ISO-8859-1 charset change")
	void testDetectsIso88591() {
		ASTNode node = parseExpression("new String(bytes, \"ISO-8859-1\")"); //$NON-NLS-1$

		TypeChangeInfo info = TypeChangeDetector.detectCharsetTypeChange(
				node, "new String(bytes, StandardCharsets.ISO_8859_1)"); //$NON-NLS-1$

		assertNotNull(info);
	}

	@Test
	@DisplayName("no detection when node has no StringLiteral child")
	void testNoDetectionForNoStringLiteral() {
		ASTNode node = parseExpression("foo(bar)"); //$NON-NLS-1$

		TypeChangeInfo info = TypeChangeDetector.detectCharsetTypeChange(
				node, "foo(StandardCharsets.UTF_8)"); //$NON-NLS-1$

		assertNull(info);
	}

	@Test
	@DisplayName("containsCharsetStringLiteral returns true for known charsets")
	void testContainsCharsetStringLiteralTrue() {
		ASTNode node = parseExpression("new String(data, \"UTF-16\")"); //$NON-NLS-1$
		assertTrue(TypeChangeDetector.containsCharsetStringLiteral(node));
	}

	@Test
	@DisplayName("containsCharsetStringLiteral returns false for non-charset strings")
	void testContainsCharsetStringLiteralFalse() {
		ASTNode node = parseExpression("new String(\"hello world\")"); //$NON-NLS-1$
		assertFalse(TypeChangeDetector.containsCharsetStringLiteral(node));
	}

	/**
	 * Parses the given source as an expression wrapped inside a method body
	 * and returns the first expression statement's expression.
	 */
	private static ASTNode parseExpression(String expressionSource) {
		String source = "class Dummy { void m() { " + expressionSource + "; } }"; //$NON-NLS-1$ //$NON-NLS-2$
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		parser.setSource(source.toCharArray());
		parser.setUnitName("Dummy.java"); //$NON-NLS-1$
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		ExpressionStatement stmt = (ExpressionStatement) method.getBody().statements().get(0);
		return stmt.getExpression();
	}
}
