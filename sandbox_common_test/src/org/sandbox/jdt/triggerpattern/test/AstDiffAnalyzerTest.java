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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.mining.analysis.AlignmentKind;
import org.sandbox.jdt.triggerpattern.mining.analysis.AstDiff;
import org.sandbox.jdt.triggerpattern.mining.analysis.AstDiffAnalyzer;
import org.sandbox.jdt.triggerpattern.mining.analysis.NodeAlignment;

/**
 * Tests for {@link AstDiffAnalyzer}.
 */
public class AstDiffAnalyzerTest {

	private final AstDiffAnalyzer analyzer = new AstDiffAnalyzer();

	@Test
	public void testIdenticalExpressions() {
		String code = "class T { void m() { int x = a + 1; } }"; //$NON-NLS-1$
		CompilationUnit cu1 = parse(code);
		CompilationUnit cu2 = parse(code);

		ASTNode before = extractExpression(cu1);
		ASTNode after = extractExpression(cu2);
		assertNotNull(before);
		assertNotNull(after);

		AstDiff diff = analyzer.computeDiff(before, after);

		assertTrue(diff.structurallyCompatible(), "Identical expressions should be compatible"); //$NON-NLS-1$
		assertEquals(1, diff.alignments().size());
		assertEquals(AlignmentKind.IDENTICAL, diff.alignments().get(0).kind());
	}

	@Test
	public void testLeafModification() {
		CompilationUnit cuBefore = parse(
				"class T { void m() { String s = new String(bytes, \"UTF-8\"); } }"); //$NON-NLS-1$
		CompilationUnit cuAfter = parse(
				"class T { void m() { String s = new String(bytes, StandardCharsets.UTF_8); } }"); //$NON-NLS-1$

		ASTNode before = extractExpression(cuBefore);
		ASTNode after = extractExpression(cuAfter);
		assertNotNull(before);
		assertNotNull(after);

		AstDiff diff = analyzer.computeDiff(before, after);

		assertFalse(diff.structurallyCompatible(),
				"Different arguments should make the diff incompatible"); //$NON-NLS-1$

		boolean hasIdentical = diff.alignments().stream()
				.anyMatch(a -> a.kind() == AlignmentKind.IDENTICAL);
		boolean hasModified = diff.alignments().stream()
				.anyMatch(a -> a.kind() == AlignmentKind.MODIFIED);

		assertTrue(hasIdentical, "Should have identical sub-trees (e.g. 'bytes' argument)"); //$NON-NLS-1$
		assertTrue(hasModified, "Should have modified sub-trees (the encoding argument)"); //$NON-NLS-1$
	}

	@Test
	public void testMethodCallRename() {
		CompilationUnit cuBefore = parse(
				"class T { void m() { Assert.assertEquals(msg, expected, actual); } }"); //$NON-NLS-1$
		CompilationUnit cuAfter = parse(
				"class T { void m() { Assertions.assertEquals(expected, actual, msg); } }"); //$NON-NLS-1$

		ASTNode before = extractMethodCall(cuBefore);
		ASTNode after = extractMethodCall(cuAfter);
		assertNotNull(before);
		assertNotNull(after);

		AstDiff diff = analyzer.computeDiff(before, after);

		assertNotNull(diff);
		assertFalse(diff.alignments().isEmpty(), "Should produce alignments"); //$NON-NLS-1$
	}

	@Test
	public void testNullHandling() {
		CompilationUnit cu = parse("class T { void m() { int x = a + 1; } }"); //$NON-NLS-1$
		ASTNode node = extractExpression(cu);

		AstDiff diffNull1 = analyzer.computeDiff(null, node);
		assertFalse(diffNull1.structurallyCompatible());
		assertEquals(1, diffNull1.alignments().size());
		assertEquals(AlignmentKind.INSERTED, diffNull1.alignments().get(0).kind());

		AstDiff diffNull2 = analyzer.computeDiff(node, null);
		assertFalse(diffNull2.structurallyCompatible());
		assertEquals(1, diffNull2.alignments().size());
		assertEquals(AlignmentKind.DELETED, diffNull2.alignments().get(0).kind());
	}

	@Test
	public void testBothNull() {
		AstDiff diff = analyzer.computeDiff(null, null);
		assertTrue(diff.structurallyCompatible());
		assertTrue(diff.alignments().isEmpty());
	}

	// ---- helpers ----

	private ASTNode extractExpression(CompilationUnit cu) {
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		VariableDeclarationStatement stmt =
				(VariableDeclarationStatement) method.getBody().statements().get(0);
		VariableDeclarationFragment frag =
				(VariableDeclarationFragment) stmt.fragments().get(0);
		return frag.getInitializer();
	}

	private ASTNode extractMethodCall(CompilationUnit cu) {
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		ExpressionStatement stmt =
				(ExpressionStatement) method.getBody().statements().get(0);
		return stmt.getExpression();
	}

	private CompilationUnit parse(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = new HashMap<>(JavaCore.getOptions());
		options.put(JavaCore.COMPILER_SOURCE, "21"); //$NON-NLS-1$
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}
}
