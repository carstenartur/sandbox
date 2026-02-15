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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.mining.analysis.AstDiff;
import org.sandbox.jdt.triggerpattern.mining.analysis.AstDiffAnalyzer;
import org.sandbox.jdt.triggerpattern.mining.analysis.InferredRule;
import org.sandbox.jdt.triggerpattern.mining.analysis.PlaceholderGeneralizer;

/**
 * Tests for {@link PlaceholderGeneralizer}.
 */
public class PlaceholderGeneralizerTest {

	private final AstDiffAnalyzer diffAnalyzer = new AstDiffAnalyzer();
	private final PlaceholderGeneralizer generalizer = new PlaceholderGeneralizer();

	@Test
	public void testGeneralizeConstructorRewrite() {
		String before = "new String(bytes, \"UTF-8\")"; //$NON-NLS-1$
		String after = "new String(bytes, StandardCharsets.UTF_8)"; //$NON-NLS-1$

		CompilationUnit cuBefore = parse(wrapExpression(before));
		CompilationUnit cuAfter = parse(wrapExpression(after));

		ASTNode beforeNode = extractExpression(cuBefore);
		ASTNode afterNode = extractExpression(cuAfter);
		assertNotNull(beforeNode);
		assertNotNull(afterNode);

		AstDiff diff = diffAnalyzer.computeDiff(beforeNode, afterNode);
		InferredRule rule = generalizer.generalize(diff, before, after, PatternKind.CONSTRUCTOR);

		assertNotNull(rule, "Should produce an inferred rule"); //$NON-NLS-1$
		assertTrue(rule.confidence() > 0.0, "Confidence should be positive"); //$NON-NLS-1$

		// The identical sub-tree "bytes" should have been replaced by a placeholder
		assertTrue(rule.sourcePattern().contains("$"), //$NON-NLS-1$
				"Source pattern should contain placeholders: " + rule.sourcePattern()); //$NON-NLS-1$
		assertTrue(rule.replacementPattern().contains("$"), //$NON-NLS-1$
				"Replacement pattern should contain placeholders: " + rule.replacementPattern()); //$NON-NLS-1$
	}

	@Test
	public void testGeneralizeSimpleApiReplacement() {
		String before = "Collections.emptyList()"; //$NON-NLS-1$
		String after = "List.of()"; //$NON-NLS-1$

		CompilationUnit cuBefore = parse(wrapExpression(before));
		CompilationUnit cuAfter = parse(wrapExpression(after));

		ASTNode beforeNode = extractExpression(cuBefore);
		ASTNode afterNode = extractExpression(cuAfter);
		assertNotNull(beforeNode);
		assertNotNull(afterNode);

		AstDiff diff = diffAnalyzer.computeDiff(beforeNode, afterNode);
		InferredRule rule = generalizer.generalize(diff, before, after, PatternKind.EXPRESSION);

		assertNotNull(rule, "Should produce a rule for API replacement"); //$NON-NLS-1$
		// Both are entirely different, so the patterns stay mostly literal
		assertNotNull(rule.sourcePattern());
		assertNotNull(rule.replacementPattern());
	}

	@Test
	public void testGeneralizeReturnsNullForEmptyDiff() {
		AstDiff emptyDiff = new AstDiff(true, java.util.List.of());
		InferredRule rule = generalizer.generalize(emptyDiff, "a", "b", PatternKind.EXPRESSION); //$NON-NLS-1$ //$NON-NLS-2$
		assertNull(rule, "Empty diff should produce null"); //$NON-NLS-1$
	}

	// ---- helpers ----

	private String wrapExpression(String expr) {
		return "class T { void m() { Object r = " + expr + "; } }"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private ASTNode extractExpression(CompilationUnit cu) {
		TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
		MethodDeclaration method = type.getMethods()[0];
		VariableDeclarationStatement stmt =
				(VariableDeclarationStatement) method.getBody().statements().get(0);
		VariableDeclarationFragment frag =
				(VariableDeclarationFragment) stmt.fragments().get(0);
		return frag.getInitializer();
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
