/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/** Executable conservative comparisons with JDT's substring and isBlank cleanups. */
class JdtStringCleanupContractTest {

	private HintFile substring;
	private HintFile isBlank;

	@BeforeEach
	void setUp() throws Exception {
		Map<String, GuardFunction> guards= new HashMap<>();
		BuiltInGuardRegistration.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
		substring= load("string-modernization.sandbox-hint"); //$NON-NLS-1$
		isBlank= load("string-isblank.sandbox-hint"); //$NON-NLS-1$
	}

	@Test
	void substringRewritesAStableStringReceiverAndIsIdempotent() {
		assertRewrite(substring,
				"class Test { String m(String text, int start) { return text.substring(start, text.length()); } }", //$NON-NLS-1$
				"class Test { String m(String text, int start) { return text.substring(start); } }"); //$NON-NLS-1$
		assertRewrite(substring,
				"class Test { String m(String text, int start) { return text.substring(start + 1, text.length()); } }", //$NON-NLS-1$
				"class Test { String m(String text, int start) { return text.substring((start + 1)); } }"); //$NON-NLS-1$
	}

	@Test
	void substringRejectsLookalikeApisAndDifferentReceivers() {
		assertNoRewrite(substring, """
				class Test {
				  static class Lookalike { int length() { return 3; }
				    String substring(int a, int b) { return "changed"; } }
				  String m(Lookalike text, int start) { return text.substring(start, text.length()); }
				}
				"""); //$NON-NLS-1$
		assertNoRewrite(substring,
				"class Test { String m(String a, String b) { return a.substring(0, b.length()); } }"); //$NON-NLS-1$
	}

	@Test
	void substringRejectsUnresolvedAndRecoveredBindings() {
		assertNoRewrite(substring,
				"class Test { String m(Missing text) { return text.substring(0, text.length()); } }"); //$NON-NLS-1$
		assertTrue(process(substring,
				"class Test { String m(String text) { return text.substring(0, text.length()); } }", "11", false).isEmpty()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void substringRejectsReceiverEvaluationAndInterveningWrites() {
		assertNoRewrite(substring, """
				class Test {
				  String next() { return "abc"; }
				  String m() { return next().substring(0, next().length()); }
				}
				"""); //$NON-NLS-1$
		assertNoRewrite(substring,
				"class Test { String m(String text) { return text.substring((text = \"x\").length(), text.length()); } }"); //$NON-NLS-1$
		assertNoRewrite(substring,
				"class Test { volatile String text; String m() { return text.substring(0, text.length()); } }"); //$NON-NLS-1$
		assertNoRewrite(substring,
				"class Test { String m(String text, int start) { return text.substring(start++, text.length()); } }"); //$NON-NLS-1$
	}

	@Test
	void isBlankSupportsAllThreeUnicodeStripMethods() {
		for (String method : List.of("strip", "stripLeading", "stripTrailing")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertRewrite(isBlank,
					"class Test { boolean m(String text) { return text." + method + "().isEmpty(); } }", //$NON-NLS-1$ //$NON-NLS-2$
					"class Test { boolean m(String text) { return text.isBlank(); } }"); //$NON-NLS-1$
			assertRewrite(isBlank,
					"class Test { boolean m(String text) { return \"\".equals(text." + method + "()); } }", //$NON-NLS-1$ //$NON-NLS-2$
					"class Test { boolean m(String text) { return text.isBlank(); } }"); //$NON-NLS-1$
			assertRewrite(isBlank,
					"class Test { boolean m(String text) { return text." + method + "().equals(\"\"); } }", //$NON-NLS-1$ //$NON-NLS-2$
					"class Test { boolean m(String text) { return text.isBlank(); } }"); //$NON-NLS-1$
		}
	}

	@Test
	void isBlankSupportsPositiveAndNegativeLengthVariants() {
		for (String method : List.of("strip", "stripLeading", "stripTrailing")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			for (String comparison : List.of("== 0", "<= 0", "< 1", "!= 0", "> 0", ">= 1")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
				boolean positive= comparison.equals("== 0") || comparison.equals("<= 0") || comparison.equals("< 1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				assertRewrite(isBlank,
						"class Test { boolean m(String text) { return text." + method + "().length() " + comparison + "; } }", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						"class Test { boolean m(String text) { return " + (positive ? "" : "!") + "text.isBlank(); } }"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings= { "== 0", "<= 0", "< 1" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	void positiveLengthComparisonPreservesValueOnlyLambdaOverloadResolution(String comparison) {
		String source= """
				class Test {
				  void use(java.util.function.Predicate<String> action) {}
				  void use(java.util.function.Consumer<String> action) {}
				  void m() { use(text -> text.strip().length() %s); }
				}
				""".formatted(comparison); //$NON-NLS-1$
		assertCompiles(source);
		List<TransformationResult> results= process(isBlank, source, "11", true); //$NON-NLS-1$
		for (TransformationResult result : results) {
			String transformed= source.substring(0, result.match().getOffset()) + result.replacement()
					+ source.substring(result.match().getOffset() + result.match().getLength());
			assertCompiles(transformed);
		}
		assertTrue(results.isEmpty(), "A direct value-only lambda body must retain its compatibility"); //$NON-NLS-1$
	}

	@Test
	void parenthesizedLengthComparisonKeepsItsLambdaValueOnlyAfterTheRewrite() {
		assertRewrite(isBlank, """
				class Test {
				  void use(java.util.function.Predicate<String> action) {}
				  void use(java.util.function.Consumer<String> action) {}
				  void m() { use(text -> (text.strip().length() == 0)); }
				}
				""", """
				class Test {
				  void use(java.util.function.Predicate<String> action) {}
				  void use(java.util.function.Consumer<String> action) {}
				  void m() { use(text -> (text.isBlank())); }
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void trimMustNotBecomeIsBlankBecauseTheWhitespaceContractsDiffer() {
		assertTrue("\u0000".trim().isEmpty()); //$NON-NLS-1$
		assertFalse("\u0000".isBlank()); //$NON-NLS-1$
		assertFalse("\u2003".trim().isEmpty()); //$NON-NLS-1$
		assertTrue("\u2003".isBlank()); //$NON-NLS-1$
		for (String use : List.of("text.trim().isEmpty()", "text.trim().length() == 0", "\"\".equals(text.trim())")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			assertNoRewrite(isBlank, "class Test { boolean m(String text) { return " + use + "; } }"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@Test
	void isBlankRequiresJava11AndResolvedStringMethods() {
		String code= "class Test { boolean m(String text) { return text.strip().isEmpty(); } }"; //$NON-NLS-1$
		assertTrue(process(isBlank, code, "1.8", true).isEmpty()); //$NON-NLS-1$
		assertTrue(process(isBlank, code, "11", false).isEmpty()); //$NON-NLS-1$
		assertNoRewrite(isBlank, """
				class Test {
				  static class Lookalike { Lookalike strip() { return this; }
				    boolean isEmpty() { return false; } }
				  boolean m(Lookalike text) { return text.strip().isEmpty(); }
				}
				"""); //$NON-NLS-1$
		assertNoRewrite(isBlank,
				"class Test { boolean m(Missing text) { return text.strip().isEmpty(); } }"); //$NON-NLS-1$
	}

	@Test
	void isBlankPreservesOneEvaluationOfAnActiveReceiver() {
		assertRewrite(isBlank,
				"class Test { String next() { return \" \"; } boolean m() { return next().strip().isEmpty(); } }", //$NON-NLS-1$
				"class Test { String next() { return \" \"; } boolean m() { return next().isBlank(); } }"); //$NON-NLS-1$
	}

	@Test
	void missingStringBindingsProduceAnExplanationWithoutARewrite() {
		String code= "class Test { boolean m(String text) { return text.strip().isEmpty(); } }"; //$NON-NLS-1$
		List<TransformationResult> results= new BatchTransformationProcessor(isBlank)
				.process(parse(code, "11", false), options("11")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(1, results.size());
		assertFalse(results.get(0).hasReplacement());
		assertTrue(results.get(0).isSemanticUnknown());
		assertTrue(results.get(0).unknownSemanticRequirements().stream()
				.anyMatch(requirement -> requirement.guardName().equals("stringIsBlankSafe"))); //$NON-NLS-1$
	}

	@Test
	void surroundingCommentsAndImportsSurviveTheTextRewrite() {
		assertRewrite(isBlank, """
				import java.util.List;
				class Test {
				  // Keep the explanation.
				  boolean m(String text) { return text.strip().isEmpty(); } // Keep this too.
				}
				""", """
				import java.util.List;
				class Test {
				  // Keep the explanation.
				  boolean m(String text) { return text.isBlank(); } // Keep this too.
				}
				"""); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void inlineCommentsArePreservedByDecliningAnUnsafeTextReplacement() {
		assertNoRewrite(isBlank,
				"class Test { boolean m(String text) { return text./* reason */strip().isEmpty(); } }"); //$NON-NLS-1$
		assertNoRewrite(substring,
				"class Test { String m(String text) { return text.substring(0, /* retain */text.length()); } }"); //$NON-NLS-1$
	}

	private void assertRewrite(HintFile hints, String before, String expected) {
		List<TransformationResult> results= process(hints, before, "11", true); //$NON-NLS-1$
		assertEquals(1, results.size(), () -> "Expected one rewrite: " + results); //$NON-NLS-1$
		TransformationResult result= results.get(0);
		assertTrue(result.hasReplacement());
		String actual= before.substring(0, result.match().getOffset()) + result.replacement()
				+ before.substring(result.match().getOffset() + result.match().getLength());
		assertEquals(expected, actual);
		CompilationUnit transformed= parse(actual, "11", true); //$NON-NLS-1$
		for (var problem : transformed.getProblems()) {
			assertFalse(problem.isError(), problem::getMessage);
		}
		assertTrue(process(hints, actual, "11", true).isEmpty(), "Second application must be a no-op"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void assertNoRewrite(HintFile hints, String code) {
		assertTrue(process(hints, code, "11", true).isEmpty(), () -> "Unexpected rewrite for: " + code); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void assertCompiles(String code) {
		for (var problem : parse(code, "11", true).getProblems()) { //$NON-NLS-1$
			assertFalse(problem.isError(), problem::getMessage);
		}
	}

	private List<TransformationResult> process(HintFile hints, String code, String version, boolean bindings) {
		return new BatchTransformationProcessor(hints).process(parse(code, version, bindings), options(version))
				.stream().filter(TransformationResult::hasReplacement).toList();
	}

	private static CompilationUnit parse(String code, String version, boolean bindings) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(options(version));
		parser.setResolveBindings(bindings);
		parser.setBindingsRecovery(bindings);
		parser.setUnitName("Test.java"); //$NON-NLS-1$
		parser.setEnvironment(new String[0], null, null, true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static Map<String, String> options(String version) {
		Map<String, String> options= new HashMap<>(JavaCore.getOptions());
		JavaCore.setComplianceOptions(version, options);
		return options;
	}

	private static HintFile load(String file) throws Exception {
		String path= "org/sandbox/jdt/triggerpattern/internal/" + file; //$NON-NLS-1$
		try (var stream= JdtStringCleanupContractTest.class.getClassLoader().getResourceAsStream(path)) {
			assertTrue(stream != null, () -> "Missing hint resource: " + path); //$NON-NLS-1$
			return new HintFileParser().parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
		}
	}
}
