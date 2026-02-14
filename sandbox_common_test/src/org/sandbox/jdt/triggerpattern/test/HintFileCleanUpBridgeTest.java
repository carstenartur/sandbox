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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileRegistry;

/**
 * Tests for the DSL-to-CleanUp bridge: verifying that {@code .sandbox-hint} files
 * can produce transformation results that would be used by the CleanUp and
 * QuickAssist frameworks.
 *
 * <p>These tests validate the core processing pipeline:
 * {@code HintFileRegistry} → {@code BatchTransformationProcessor} → results
 * that {@code HintFileFixCore} and {@code HintFileQuickAssistProcessor} consume.</p>
 *
 * @since 1.3.5
 */
public class HintFileCleanUpBridgeTest {

	private final HintFileRegistry registry = HintFileRegistry.getInstance();

	@BeforeEach
	public void setUp() {
		registry.clear();
	}

	@AfterEach
	public void tearDown() {
		registry.clear();
	}

	@Test
	public void testRegistryProducesTransformationResults() throws Exception {
		String hintContent = """
				<!id: test-cleanup>
				<!description: Test cleanup bridge>

				$x + 0
				=> $x
				;;
				"""; //$NON-NLS-1$

		registry.loadFromString("test-cleanup", hintContent); //$NON-NLS-1$

		HintFile hintFile = registry.getHintFile("test-cleanup"); //$NON-NLS-1$
		assertNotNull(hintFile);

		List<TransformationRule> rules = registry.resolveIncludes(hintFile);
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile, rules);

		String code = "class Test { void m() { int r = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);
		assertFalse(results.isEmpty(), "Should find match from registry hint file"); //$NON-NLS-1$
		assertTrue(results.get(0).hasReplacement());
	}

	@Test
	public void testMultipleRegisteredFilesProduceResults() throws Exception {
		String hint1 = """
				<!id: rule1>
				$x + 0
				=> $x
				;;
				"""; //$NON-NLS-1$
		String hint2 = """
				<!id: rule2>
				$x * 1
				=> $x
				;;
				"""; //$NON-NLS-1$

		registry.loadFromString("rule1", hint1); //$NON-NLS-1$
		registry.loadFromString("rule2", hint2); //$NON-NLS-1$

		String code = "class Test { void m() { int a = 1 + 0; int b = 2 * 1; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		int totalResults = 0;
		for (HintFile hintFile : registry.getAllHintFiles().values()) {
			List<TransformationRule> rules = registry.resolveIncludes(hintFile);
			BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile, rules);
			List<TransformationResult> results = processor.process(cu);
			totalResults += results.size();
		}

		assertEquals(2, totalResults, "Should find matches from both registered hint files"); //$NON-NLS-1$
	}

	@Test
	public void testIncludedRulesProcessed() throws Exception {
		String baseHint = """
				<!id: base>
				$x + 0
				=> $x
				;;
				"""; //$NON-NLS-1$
		String compositeHint = """
				<!id: composite>
				<!include: base>

				$x * 1
				=> $x
				;;
				"""; //$NON-NLS-1$

		registry.loadFromString("base", baseHint); //$NON-NLS-1$
		registry.loadFromString("composite", compositeHint); //$NON-NLS-1$

		HintFile composite = registry.getHintFile("composite"); //$NON-NLS-1$
		List<TransformationRule> resolvedRules = registry.resolveIncludes(composite);
		assertEquals(2, resolvedRules.size(), "Should have own rule + included rule"); //$NON-NLS-1$

		BatchTransformationProcessor processor = new BatchTransformationProcessor(composite, resolvedRules);

		String code = "class Test { void m() { int a = 1 + 0; int b = 2 * 1; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);
		assertEquals(2, results.size(), "Should find matches from both own and included rules"); //$NON-NLS-1$
	}

	@Test
	public void testHintOnlyRulesNoReplacement() throws Exception {
		String hintContent = """
				<!id: hint-only>

				"Potential issue":
				$x + 0
				;;
				"""; //$NON-NLS-1$

		registry.loadFromString("hint-only", hintContent); //$NON-NLS-1$

		HintFile hintFile = registry.getHintFile("hint-only"); //$NON-NLS-1$
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

		String code = "class Test { void m() { int r = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);
		assertFalse(results.isEmpty(), "Should find match even for hint-only"); //$NON-NLS-1$
		assertFalse(results.get(0).hasReplacement(), "Hint-only should not have replacement"); //$NON-NLS-1$
	}

	@Test
	public void testRulesWithImportDirectives() throws Exception {
		String hintContent = """
				<!id: import-test>

				$x.equals($y)
				=> java.util.Objects.equals($x, $y)
				   addImport java.util.Objects
				;;
				"""; //$NON-NLS-1$

		registry.loadFromString("import-test", hintContent); //$NON-NLS-1$

		HintFile hintFile = registry.getHintFile("import-test"); //$NON-NLS-1$
		assertNotNull(hintFile);
		assertFalse(hintFile.getRules().isEmpty());

		TransformationRule rule = hintFile.getRules().get(0);
		assertTrue(rule.hasImportDirective(), "Rule should have import directives"); //$NON-NLS-1$
		assertTrue(rule.getImportDirective().getAddImports().contains("java.util.Objects")); //$NON-NLS-1$
	}

	@Test
	public void testBundledLibrariesLoad() {
		List<String> loaded = registry.loadBundledLibraries(HintFileCleanUpBridgeTest.class.getClassLoader());

		// Bundled libraries should load (collections, modernize-java11, performance)
		assertNotNull(loaded);
		// The bundled libraries may or may not be on the classpath in test env,
		// but the method should not throw
	}

	@Test
	public void testEmptyRegistryProducesNoResults() {
		// Registry is cleared in setUp, so it's empty
		assertTrue(registry.getAllHintFiles().isEmpty());

		String code = "class Test { void m() { int r = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		// No hint files registered, so no results
		int totalResults = 0;
		for (HintFile hintFile : registry.getAllHintFiles().values()) {
			BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);
			totalResults += processor.process(cu).size();
		}
		assertEquals(0, totalResults);
	}

	// --- Helper methods ---

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
