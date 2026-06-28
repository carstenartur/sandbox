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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
 * and assert either a replacement or no match.</p>
 */
abstract class HintRuleTestSupport {

	private static final String BUNDLED_HINT_PREFIX =
			"org/sandbox/jdt/triggerpattern/internal/"; //$NON-NLS-1$

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
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);
		return processor.process(parseCode(code));
	}

	protected void assertReplacement(HintFile hintFile, String beforeCode, String expectedReplacement) {
		List<TransformationResult> results = process(hintFile, beforeCode);
		assertFalse(results.isEmpty(), "Expected at least one match"); //$NON-NLS-1$
		assertTrue(results.stream()
				.filter(TransformationResult::hasReplacement)
				.map(TransformationResult::replacement)
				.anyMatch(expectedReplacement::equals),
				"Expected replacement not produced: " + expectedReplacement); //$NON-NLS-1$
	}

	protected void assertNoMatch(HintFile hintFile, String code) {
		List<TransformationResult> results = process(hintFile, code);
		assertTrue(results.isEmpty(), "Expected no match but got: " + results); //$NON-NLS-1$
	}

	protected void registerBuiltInGuards() {
		java.util.HashMap<String, GuardFunction> guards = new java.util.HashMap<>();
		BuiltInGuards.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
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
