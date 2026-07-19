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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/** Binding-aware support used only by permanent promoted-candidate fixtures. */
abstract class StrictHintRuleTestSupport {

	protected HintFile parseHint(String hintContent) throws Exception {
		return new HintFileParser().parse(hintContent);
	}

	protected void assertFullReplacement(HintFile hintFile, String beforeCode,
			String expectedCode, String sourceVersion) {
		List<TransformationResult> results = process(hintFile, beforeCode, sourceVersion);
		List<TransformationResult> replacements = results.stream()
				.filter(TransformationResult::hasReplacement)
				.toList();
		assertTrue(replacements.size() == 1,
				"Expected exactly one replacement but got: " + replacements); //$NON-NLS-1$
		String actualCode = replaceUsingOffset(beforeCode, replacements.get(0));
		CompilationUnit actual = parseCode(actualCode, sourceVersion);
		CompilationUnit expected = parseCode(expectedCode, sourceVersion);
		assertTrue(actual.subtreeMatch(new ASTMatcher(true), expected),
				"Transformed syntax tree differs from expected source"); //$NON-NLS-1$
	}

	protected void assertNoMatch(HintFile hintFile, String code, String sourceVersion) {
		List<TransformationResult> results = process(hintFile, code, sourceVersion);
		assertTrue(results.isEmpty(), "Expected no match but got: " + results); //$NON-NLS-1$
	}

	protected void registerBuiltInGuards() {
		HashMap<String, GuardFunction> guards = new HashMap<>();
		BuiltInGuardRegistration.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
	}

	protected String readUtf8Resource(ClassLoader classLoader, String path) throws Exception {
		try (var stream = classLoader.getResourceAsStream(path)) {
			assertTrue(stream != null, "Resource not found: " + path); //$NON-NLS-1$
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static List<TransformationResult> process(HintFile hintFile, String code,
			String sourceVersion) {
		BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);
		return processor.process(parseCode(code, sourceVersion), compilerOptions(sourceVersion));
	}

	private static String replaceUsingOffset(String source, TransformationResult result) {
		int offset = result.match().getOffset();
		int length = result.match().getLength();
		assertTrue(offset >= 0 && length >= 0 && offset + length <= source.length(),
				"Invalid match range: offset=" + offset + ", length=" + length); //$NON-NLS-1$ //$NON-NLS-2$
		return source.substring(0, offset) + result.replacement()
				+ source.substring(offset + length);
	}

	private static CompilationUnit parseCode(String code, String sourceVersion) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(compilerOptions(sourceVersion));
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(false);
		parser.setStatementsRecovery(false);
		parser.setUnitName("BehaviorFixture.java"); //$NON-NLS-1$
		parser.setEnvironment(runtimeClasspath(), null, null, true);
		CompilationUnit unit = (CompilationUnit) parser.createAST(null);
		for (IProblem problem : unit.getProblems()) {
			assertFalse(problem.isError(),
					"Java compile error at line " + problem.getSourceLineNumber() //$NON-NLS-1$
							+ ": " + problem.getMessage()); //$NON-NLS-1$
		}
		return unit;
	}

	private static Map<String, String> compilerOptions(String sourceVersion) {
		Map<String, String> options = new HashMap<>(JavaCore.getOptions());
		JavaCore.setComplianceOptions(sourceVersion, options);
		options.put(JavaCore.COMPILER_SOURCE, sourceVersion);
		return options;
	}

	private static String[] runtimeClasspath() {
		String classpath = System.getProperty("java.class.path", ""); //$NON-NLS-1$ //$NON-NLS-2$
		if (classpath.isBlank()) {
			return new String[0];
		}
		return Arrays.stream(classpath.split(Pattern.quote(File.pathSeparator)))
				.filter(entry -> !entry.isBlank())
				.toArray(String[]::new);
	}
}
