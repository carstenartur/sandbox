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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternIndex;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;

/**
 * Tests for the {@link PatternIndex} performance optimization.
 * 
 * @since 1.3.3
 */
public class PatternIndexTest {

	@Test
	public void testEmptyIndex() {
		PatternIndex index = new PatternIndex(List.of());
		assertEquals(0, index.size());
		assertEquals(0, index.kindCount());
	}

	@Test
	public void testIndexGroupsByKind() {
		TransformationRule exprRule = createRule("$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		TransformationRule ctorRule = createRule("new Boolean($val)", PatternKind.CONSTRUCTOR, "Boolean.valueOf($val)"); //$NON-NLS-1$ //$NON-NLS-2$
		TransformationRule methodCallRule = createRule("$x.toString()", PatternKind.METHOD_CALL, "$x"); //$NON-NLS-1$ //$NON-NLS-2$

		PatternIndex index = new PatternIndex(List.of(exprRule, ctorRule, methodCallRule));

		assertEquals(3, index.size());
		assertEquals(3, index.kindCount());
		assertEquals(1, index.getRulesForKind(PatternKind.EXPRESSION).size());
		assertEquals(1, index.getRulesForKind(PatternKind.CONSTRUCTOR).size());
		assertEquals(1, index.getRulesForKind(PatternKind.METHOD_CALL).size());
		assertTrue(index.getRulesForKind(PatternKind.STATEMENT).isEmpty());
	}

	@Test
	public void testMultipleRulesSameKind() {
		TransformationRule rule1 = createRule("$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		TransformationRule rule2 = createRule("$x * 1", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$

		PatternIndex index = new PatternIndex(List.of(rule1, rule2));

		assertEquals(2, index.size());
		assertEquals(1, index.kindCount());
		assertEquals(2, index.getRulesForKind(PatternKind.EXPRESSION).size());
	}

	@Test
	public void testFindAllMatchesSinglePattern() {
		String code = "class Test { void m() { int x = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule("$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertFalse(results.isEmpty(), "Should find at least one match"); //$NON-NLS-1$
		assertTrue(results.containsKey(rule));
		assertFalse(results.get(rule).isEmpty());
	}

	@Test
	public void testFindAllMatchesMultiplePatterns() {
		String code = "class Test { void m() { int x = 1 + 0; int y = 2 * 1; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule addZero = createRule("$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		TransformationRule mulOne = createRule("$x * 1", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		PatternIndex index = new PatternIndex(List.of(addZero, mulOne));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertEquals(2, results.size(), "Should find matches for both rules"); //$NON-NLS-1$
		assertTrue(results.containsKey(addZero));
		assertTrue(results.containsKey(mulOne));
	}

	@Test
	public void testFindAllMatchesNoMatch() {
		String code = "class Test { void m() { int x = 1 + 2; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule("$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);
		assertTrue(results.isEmpty(), "Should find no matches"); //$NON-NLS-1$
	}

	@Test
	public void testFindAllMatchesMultipleSamePatternMatches() {
		String code = "class Test { void m() { int a = 1 + 0; int b = 2 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule("$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertTrue(results.containsKey(rule));
		assertEquals(2, results.get(rule).size(), "Should find two matches"); //$NON-NLS-1$
	}

	@Test
	public void testFindAllMatchesNullCu() {
		TransformationRule rule = createRule("$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(null);
		assertTrue(results.isEmpty(), "Should return empty for null CU"); //$NON-NLS-1$
	}

	@Test
	public void testFindAllMatchesMixedKinds() {
		String code = "class Test { void m() { new Boolean(true); String s = \"hello\".toString(); } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule ctorRule = createRule("new Boolean($val)", PatternKind.CONSTRUCTOR, "Boolean.valueOf($val)"); //$NON-NLS-1$ //$NON-NLS-2$
		TransformationRule methodCallRule = createRule("$x.toString()", PatternKind.METHOD_CALL, "String.valueOf($x)"); //$NON-NLS-1$ //$NON-NLS-2$
		PatternIndex index = new PatternIndex(List.of(ctorRule, methodCallRule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		// Should find both the constructor and method call
		assertTrue(results.size() >= 1, "Should find at least one match kind"); //$NON-NLS-1$
	}

	@Test
	public void testMatchBindings() {
		String code = "class Test { void m() { int x = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule("$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);
		assertFalse(results.isEmpty());

		Match match = results.get(rule).get(0);
		assertNotNull(match.getBinding("$x"), "Should have binding for $x"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	// --- Helper methods ---

	private TransformationRule createRule(String sourcePattern, PatternKind kind, String replacement) {
		Pattern srcPattern = new Pattern(sourcePattern, kind);
		RewriteAlternative alt = new RewriteAlternative(replacement, null);
		return new TransformationRule(null, srcPattern, null, List.of(alt));
	}

	private CompilationUnit parseCode(String code) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, "17"); //$NON-NLS-1$
		astParser.setCompilerOptions(options);
		return (CompilationUnit) astParser.createAST(null);
	}
}
