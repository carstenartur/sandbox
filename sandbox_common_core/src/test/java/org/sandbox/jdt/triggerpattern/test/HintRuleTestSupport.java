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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuards;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/**
 * Reusable support for behavior tests of {@code .sandbox-hint} rules.
 *
 * <p>The goal is to keep tests for promoted mining candidates nearly as compact
 * as the hint rules themselves: load a rule file, provide before/after snippets,
 * and assert either a full-source replacement or no match.</p>
 */
abstract class HintRuleTestSupport {

	private static final String BUNDLED_HINT_PREFIX =
			"org/sandbox/jdt/triggerpattern/internal/"; //$NON-NLS-1$
	private static final String DEFAULT_SOURCE_VERSION = "21"; //$NON-NLS-1$

	protected HintFile loadBundledHint(String resourceName) throws Exception {
		String resourcePath = BUNDLED_HINT_PREFIX + resourceName;
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		try (var stream = classLoader.getResourceAsStream(resourcePath)) {
			assertTrue(stream != null, "Bundled hint resource not found: " + resourcePath); //$NON-NLS-1$
			HintFileParser parser = new HintFileParser();
			return parser.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
		}
	}

	protected HintFile parseHint(String hintContent) throws Exception {
		return new HintFileParser().parse(hintContent);
	}

	protected List<TransformationResult> process(HintFile hintFile, String code) {
		return process(hintFile, code, DEFAULT_SOURCE_VERSION);
	}

	protected List<TransformationResult> process(HintFile hintFile, String code, String sourceVersion) {
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);
		return processor.process(parseCode(code, sourceVersion), Map.of(JavaCore.COMPILER_SOURCE, sourceVersion));
	}

	/**
	 * Low-level assertion for the raw replacement fragment returned by the processor.
	 * Promotion tests should normally prefer {@link #assertFullReplacement}.
	 */
	protected void assertReplacementFragment(HintFile hintFile, String beforeCode, String expectedReplacement) {
		List<TransformationResult> results = process(hintFile, beforeCode);
		assertFalse(results.isEmpty(), "Expected at least one match"); //$NON-NLS-1$
		assertTrue(results.stream()
				.filter(TransformationResult::hasReplacement)
				.map(TransformationResult::replacement)
				.anyMatch(expectedReplacement::equals),
				"Expected replacement not produced: " + expectedReplacement); //$NON-NLS-1$
	}

	/**
	 * Asserts that the first replacement can be embedded back into the original
	 * source and produce the expected full source. This avoids misleading tests
	 * where a complete class snippet is compared only with an expression-level
	 * replacement fragment.
	 */
	protected void assertFullReplacement(HintFile hintFile, String beforeCode, String expectedCode) {
		assertFullReplacement(hintFile, beforeCode, expectedCode, DEFAULT_SOURCE_VERSION);
	}

	protected void assertFullReplacement(HintFile hintFile, String beforeCode, String expectedCode,
			String sourceVersion) {
		List<TransformationResult> results = process(hintFile, beforeCode, sourceVersion);
		assertFalse(results.isEmpty(), "Expected at least one match"); //$NON-NLS-1$
		TransformationResult result = results.stream()
				.filter(TransformationResult::hasReplacement)
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected at least one replacement")); //$NON-NLS-1$
		String actualCode = replaceFirstLiteral(beforeCode, result.matchedText(), result.replacement());
		assertEquals(normalizeSource(expectedCode), normalizeSource(actualCode));
	}

	protected void assertHintOnlyMatch(HintFile hintFile, String code) {
		List<TransformationResult> results = process(hintFile, code);
		assertFalse(results.isEmpty(), "Expected at least one hint-only match"); //$NON-NLS-1$
		assertTrue(results.stream().noneMatch(TransformationResult::hasReplacement),
				"Expected hint-only match, but at least one replacement was produced: " + results); //$NON-NLS-1$
	}

	protected void assertNoMatch(HintFile hintFile, String code) {
		assertNoMatch(hintFile, code, DEFAULT_SOURCE_VERSION);
	}

	protected void assertNoMatch(HintFile hintFile, String code, String sourceVersion) {
		List<TransformationResult> results = process(hintFile, code, sourceVersion);
		assertTrue(results.isEmpty(), "Expected no match but got: " + results); //$NON-NLS-1$
	}

	protected void registerBuiltInGuards() {
		java.util.HashMap<String, GuardFunction> guards = new java.util.HashMap<>();
		BuiltInGuards.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
	}

	private String replaceFirstLiteral(String source, String matchedText, String replacement) {
		int index = source.indexOf(matchedText);
		assertTrue(index >= 0, "Matched text not found in source: " + matchedText); //$NON-NLS-1$
		return source.substring(0, index) + replacement + source.substring(index + matchedText.length());
	}

	private String normalizeSource(String source) {
		return source.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private CompilationUnit parseCode(String code, String sourceVersion) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(code.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, sourceVersion);
		astParser.setCompilerOptions(options);
		return (CompilationUnit) astParser.createAST(null);
	}
}
