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
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/**
 * Tests for {@link BatchTransformationProcessor}.
 *
 * @since 1.3.3
 */
public class BatchTransformationProcessorTest {

	@Test
	public void testProcessSimpleRule() {
		TransformationRule rule = createRule(
				"$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		HintFile hintFile = createHintFile(List.of(rule));
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

		String code = "class Test { void m() { int r = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);

		assertFalse(results.isEmpty(), "Should find at least one match"); //$NON-NLS-1$
		TransformationResult result = results.get(0);
		assertTrue(result.hasReplacement());
		assertNotNull(result.matchedText());
		assertTrue(result.lineNumber() > 0);
	}

	@Test
	public void testProcessMultipleRules() {
		TransformationRule rule1 = createRule(
				"$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		TransformationRule rule2 = createRule(
				"$x * 1", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		HintFile hintFile = createHintFile(List.of(rule1, rule2));
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

		String code = "class Test { void m() { int a = 1 + 0; int b = 2 * 1; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);

		assertEquals(2, results.size(), "Should find matches for both rules"); //$NON-NLS-1$
	}

	@Test
	public void testProcessNoMatch() {
		TransformationRule rule = createRule(
				"$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		HintFile hintFile = createHintFile(List.of(rule));
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

		String code = "class Test { void m() { int r = 1 + 2; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);

		assertTrue(results.isEmpty(), "Should find no matches"); //$NON-NLS-1$
	}

	@Test
	public void testProcessNullCu() {
		TransformationRule rule = createRule(
				"$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		HintFile hintFile = createHintFile(List.of(rule));
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

		List<TransformationResult> results = processor.process(null);

		assertTrue(results.isEmpty(), "Should return empty for null CU"); //$NON-NLS-1$
	}

	@Test
	public void testProcessHintOnly() {
		// Rule with no alternatives = hint only
		Pattern srcPattern = new Pattern("$x + 0", PatternKind.EXPRESSION); //$NON-NLS-1$
		TransformationRule hintOnlyRule = new TransformationRule(
				"Addition of zero is redundant", srcPattern, null, List.of()); //$NON-NLS-1$
		HintFile hintFile = createHintFile(List.of(hintOnlyRule));
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

		String code = "class Test { void m() { int r = 1 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);

		assertFalse(results.isEmpty());
		TransformationResult result = results.get(0);
		assertFalse(result.hasReplacement(), "Hint-only rule should not have replacement"); //$NON-NLS-1$
		assertEquals("Addition of zero is redundant", result.description()); //$NON-NLS-1$
	}

	@Test
	public void testProcessWithHintFileParser() throws Exception {
		String hintContent = """
				// Test hint file
				<!id: test-batch>
				<!description: Test batch processing>
				<!severity: warning>

				$x + 0
				=> $x
				;;

				$x * 1
				=> $x
				;;
				"""; //$NON-NLS-1$

		HintFileParser parser = new HintFileParser();
		HintFile hintFile = parser.parse(hintContent);
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

		String code = "class Test { void m() { int a = 1 + 0; int b = 2 * 1; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);

		assertEquals(2, results.size(), "Should find matches for both rules"); //$NON-NLS-1$
		for (TransformationResult result : results) {
			assertTrue(result.hasReplacement());
		}
	}

	@Test
	public void testProcessMultipleMatchesSameRule() {
		TransformationRule rule = createRule(
				"$x + 0", PatternKind.EXPRESSION, "$x"); //$NON-NLS-1$ //$NON-NLS-2$
		HintFile hintFile = createHintFile(List.of(rule));
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

		String code = "class Test { void m() { int a = 1 + 0; int b = 2 + 0; int c = 3 + 0; } }"; //$NON-NLS-1$
		CompilationUnit cu = parseCode(code);

		List<TransformationResult> results = processor.process(cu);

		assertEquals(3, results.size(), "Should find three matches"); //$NON-NLS-1$
	}

	@Test
	public void testGetHintFile() {
		HintFile hintFile = createHintFile(List.of());
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

		assertEquals(hintFile, processor.getHintFile());
		assertNotNull(processor.getPatternIndex());
	}

	// --- Helper methods ---

	private TransformationRule createRule(String sourcePattern, PatternKind kind, String replacement) {
		Pattern srcPattern = new Pattern(sourcePattern, kind);
		RewriteAlternative alt = new RewriteAlternative(replacement, null);
		return new TransformationRule(null, srcPattern, null, List.of(alt));
	}

	private HintFile createHintFile(List<TransformationRule> rules) {
		HintFile hf = new HintFile();
		hf.setId("test"); //$NON-NLS-1$
		hf.setDescription("Test hint file"); //$NON-NLS-1$
		for (TransformationRule rule : rules) {
			hf.addRule(rule);
		}
		return hf;
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
