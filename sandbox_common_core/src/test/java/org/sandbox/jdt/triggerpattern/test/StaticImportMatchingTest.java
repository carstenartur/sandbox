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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternIndex;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuards;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/**
 * Tests for static import-aware matching in {@link PatternIndex} and
 * {@link org.sandbox.jdt.triggerpattern.internal.PlaceholderAstMatcher}.
 *
 * <p>Verifies that qualified patterns (e.g., {@code Assert.assertEquals($a, $b)})
 * match unqualified calls (e.g., {@code assertEquals(a, b)}) when a matching
 * static import exists in the compilation unit.</p>
 *
 * @since 1.3.5
 */
public class StaticImportMatchingTest {

	@BeforeEach
	public void setUp() {
		java.util.HashMap<String, org.sandbox.jdt.triggerpattern.api.GuardFunction> guards = new java.util.HashMap<>();
		BuiltInGuards.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
	}

	@Test
	public void testMatchesUnqualifiedCallWithWildcardStaticImport() {
		String code = "import static org.junit.Assert.*;\n" //$NON-NLS-1$
				+ "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    assertEquals(1, 2);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule(
				"Assert.assertEquals($expected, $actual)", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				"Assertions.assertEquals($expected, $actual)"); //$NON-NLS-1$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertFalse(results.isEmpty(), "Should match unqualified call via wildcard static import"); //$NON-NLS-1$
		assertTrue(results.containsKey(rule));
		assertEquals(1, results.get(rule).size());
	}

	@Test
	public void testMatchesUnqualifiedCallWithExplicitStaticImport() {
		String code = "import static org.junit.Assert.assertEquals;\n" //$NON-NLS-1$
				+ "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    assertEquals(1, 2);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule(
				"Assert.assertEquals($expected, $actual)", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				"Assertions.assertEquals($expected, $actual)"); //$NON-NLS-1$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertFalse(results.isEmpty(), "Should match unqualified call via explicit static import"); //$NON-NLS-1$
		assertTrue(results.containsKey(rule));
		assertEquals(1, results.get(rule).size());
	}

	@Test
	public void testDoesNotMatchUnqualifiedCallWithoutStaticImport() {
		String code = "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    assertEquals(1, 2);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule(
				"Assert.assertEquals($expected, $actual)", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				"Assertions.assertEquals($expected, $actual)"); //$NON-NLS-1$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertTrue(results.isEmpty(), "Should NOT match unqualified call without static import"); //$NON-NLS-1$
	}

	@Test
	public void testDoesNotMatchWrongStaticImport() {
		String code = "import static org.junit.Assert.assertTrue;\n" //$NON-NLS-1$
				+ "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    assertEquals(1, 2);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule(
				"Assert.assertEquals($expected, $actual)", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				"Assertions.assertEquals($expected, $actual)"); //$NON-NLS-1$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertTrue(results.isEmpty(), "Should NOT match with wrong static import"); //$NON-NLS-1$
	}

	@Test
	public void testStillMatchesQualifiedCall() {
		String code = "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    Assert.assertEquals(1, 2);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule(
				"Assert.assertEquals($expected, $actual)", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				"Assertions.assertEquals($expected, $actual)"); //$NON-NLS-1$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertFalse(results.isEmpty(), "Should still match qualified call"); //$NON-NLS-1$
		assertTrue(results.containsKey(rule));
	}

	@Test
	public void testMatchesMultipleUnqualifiedCalls() {
		String code = "import static org.junit.Assert.*;\n" //$NON-NLS-1$
				+ "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    assertEquals(1, 2);\n" //$NON-NLS-1$
				+ "    assertEquals(3, 4);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule(
				"Assert.assertEquals($expected, $actual)", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				"Assertions.assertEquals($expected, $actual)"); //$NON-NLS-1$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertFalse(results.isEmpty(), "Should find matches"); //$NON-NLS-1$
		assertEquals(2, results.get(rule).size(), "Should match both calls"); //$NON-NLS-1$
	}

	@Test
	public void testMatchesAssumeTrueWithStaticImport() {
		String code = "import static org.junit.Assume.*;\n" //$NON-NLS-1$
				+ "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    assumeTrue(true);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule(
				"Assume.assumeTrue($cond)", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				"Assumptions.assumeTrue($cond)"); //$NON-NLS-1$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertFalse(results.isEmpty(), "Should match Assume.assumeTrue via static import"); //$NON-NLS-1$
	}

	@Test
	public void testBindingsPreservedForStaticImportMatch() {
		String code = "import static org.junit.Assert.*;\n" //$NON-NLS-1$
				+ "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    assertEquals(1, 2);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		TransformationRule rule = createRule(
				"Assert.assertEquals($expected, $actual)", //$NON-NLS-1$
				PatternKind.METHOD_CALL,
				"Assertions.assertEquals($expected, $actual)"); //$NON-NLS-1$
		PatternIndex index = new PatternIndex(List.of(rule));

		Map<TransformationRule, List<Match>> results = index.findAllMatches(cu);

		assertFalse(results.isEmpty());
		Match match = results.get(rule).get(0);
		assertTrue(match.getBindings().containsKey("$expected"), "Should have binding for $expected"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(match.getBindings().containsKey("$actual"), "Should have binding for $actual"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	// --- End-to-end batch processing tests ---

	@Test
	public void testBatchProcessorMatchesUnqualifiedViaStaticImport() throws Exception {
		String hintContent = """
				<!id: test-static>

				Assert.assertEquals($expected, $actual)
				=> Assertions.assertEquals($expected, $actual)
				addImport org.junit.jupiter.api.Assertions
				removeImport org.junit.Assert
				replaceStaticImport org.junit.Assert org.junit.jupiter.api.Assertions
				;;
				"""; //$NON-NLS-1$

		String code = "import static org.junit.Assert.*;\n" //$NON-NLS-1$
				+ "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    assertEquals(1, 2);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$

		HintFileParser parser = new HintFileParser();
		HintFile hintFile = parser.parse(hintContent);
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);

		assertFalse(results.isEmpty(), "Should find match via static import"); //$NON-NLS-1$
		TransformationResult result = results.get(0);
		assertTrue(result.hasReplacement());
		assertNotNull(result.replacement());
		assertTrue(result.hasImportDirective(), "Should have import directives"); //$NON-NLS-1$
		assertFalse(result.importDirective().getReplaceStaticImports().isEmpty(),
				"Should have replaceStaticImport directive"); //$NON-NLS-1$
	}

	@Test
	public void testBatchProcessorWithTypeGuardAndStaticImport() throws Exception {
		String hintContent = """
				<!id: test-guard-static>

				Assert.assertEquals($msg, $expected, $actual)
				  :: $msg instanceof java.lang.String
				=> Assertions.assertEquals($expected, $actual, $msg)
				addImport org.junit.jupiter.api.Assertions
				removeImport org.junit.Assert
				replaceStaticImport org.junit.Assert org.junit.jupiter.api.Assertions
				;;

				Assert.assertEquals($expected, $actual)
				=> Assertions.assertEquals($expected, $actual)
				addImport org.junit.jupiter.api.Assertions
				removeImport org.junit.Assert
				replaceStaticImport org.junit.Assert org.junit.jupiter.api.Assertions
				;;
				"""; //$NON-NLS-1$

		String code = "import static org.junit.Assert.*;\n" //$NON-NLS-1$
				+ "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    assertEquals(1, 2);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$

		HintFileParser parser = new HintFileParser();
		HintFile hintFile = parser.parse(hintContent);
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);

		// The 2-arg call should match the 2-arg rule (not the guarded 3-arg rule)
		assertFalse(results.isEmpty(), "Should find match for 2-arg assertEquals"); //$NON-NLS-1$
	}

	@Test
	public void testBatchProcessorAssumeMigrationViaStaticImport() throws Exception {
		String hintContent = """
				<!id: test-assume>

				Assume.assumeTrue($cond)
				=> Assumptions.assumeTrue($cond)
				addImport org.junit.jupiter.api.Assumptions
				removeImport org.junit.Assume
				replaceStaticImport org.junit.Assume org.junit.jupiter.api.Assumptions
				;;
				"""; //$NON-NLS-1$

		String code = "import static org.junit.Assume.assumeTrue;\n" //$NON-NLS-1$
				+ "class Test {\n" //$NON-NLS-1$
				+ "  void m() {\n" //$NON-NLS-1$
				+ "    assumeTrue(true);\n" //$NON-NLS-1$
				+ "  }\n" //$NON-NLS-1$
				+ "}"; //$NON-NLS-1$

		HintFileParser parser = new HintFileParser();
		HintFile hintFile = parser.parse(hintContent);
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);

		assertFalse(results.isEmpty(), "Should find match via explicit static import"); //$NON-NLS-1$
		assertTrue(results.get(0).hasReplacement());
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
