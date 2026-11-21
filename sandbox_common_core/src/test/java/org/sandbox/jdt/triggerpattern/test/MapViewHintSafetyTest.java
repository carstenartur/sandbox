/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuardRegistration;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

/** Map view hints must not mistake JDT annotation values or lookalike APIs for Map views. */
@SuppressWarnings("nls")
class MapViewHintSafetyTest {

	private HintFile hints;
	private Function<String, GuardFunction> previousResolver;

	@BeforeEach
	void setUp() throws Exception {
		previousResolver= GuardFunctionResolverHolder.getResolver();
		Map<String, GuardFunction> guards= new HashMap<>();
		BuiltInGuardRegistration.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
		try (var stream= getClass().getClassLoader().getResourceAsStream(
				"org/sandbox/jdt/triggerpattern/internal/collection-performance.sandbox-hint")) {
			assertTrue(stream != null);
			hints= new HintFileParser().parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	@AfterEach
	void restoreResolver() {
		GuardFunctionResolverHolder.setResolver(previousResolver);
	}

	@ParameterizedTest
	@CsvSource({ "keySet,size,int", "values,size,int", "keySet,isEmpty,boolean", "values,isEmpty,boolean",
			"keySet,clear,void", "values,clear,void" })
	void boundMapViewsStillSimplify(String view, String operation, String returnType) {
		String prefix= "void".equals(returnType) ? "" : "return ";
		String source= "class Sample { " + returnType + " apply(java.util.Map<String, String> map) { "
				+ prefix + "map." + view + "()." + operation + "(); } }";
		assertRewrite(source, source.replace("map." + view + "().", "map."));
	}

	@ParameterizedTest
	@CsvSource({ "keySet,size,int", "values,size,int", "keySet,isEmpty,boolean", "values,isEmpty,boolean",
			"keySet,clear,void", "values,clear,void" })
	void similarlyNamedMethodsDoNotAuthorizeMapRewrites(String view, String operation, String returnType) {
		String prefix= "void".equals(returnType) ? "" : "return ";
		assertNoRewrite("""
				class Sample {
				    static class Lookalike {
				        java.util.List<String> values() { return new java.util.ArrayList<>(); }
				        java.util.Set<String> keySet() { return new java.util.HashSet<>(); }
				    }
				""" + returnType + " apply(Lookalike map) { " + prefix + "map." + view + "()." + operation + "(); } }");
	}

	@Test
	void jdtNormalAnnotationValuesAreNotAMapView() {
		assertNoRewrite("class Sample { int size(org.eclipse.jdt.core.dom.NormalAnnotation annotation) { return annotation.values().size(); } }");
	}

	@Test
	void unresolvedMapBindingsDoNotAuthorizeARewrite() {
		CompilationUnit unit= parse("class Sample { int size(java.util.Map<String, String> map) { return map.values().size(); } }", false);
		assertTrue(new BatchTransformationProcessor(hints).process(unit, options()).stream()
				.noneMatch(result -> result.hasReplacement()), "Unresolved bindings may explain a skipped hint, never authorize a rewrite");
	}

	@Test
	void aMapImplementationMustNotRewriteItsOwnViewDelegation() {
		assertNoRewrite("""
				class Sample extends java.util.AbstractMap<String, String> {
				    @Override public java.util.Set<java.util.Map.Entry<String, String>> entrySet() {
				        return java.util.Set.of();
				    }
				    @Override public int size() { return this.keySet().size(); }
				}
				""");
	}

	@Test
	void anInternalCommentIsPreserved() {
		assertNoRewrite("class Sample { int size(java.util.Map<String, String> map) { return map./* keep */values().size(); } }");
	}

	@Test
	void concreteMapSubtypesRemainEligible() {
		String source= "class Sample { int size(java.util.HashMap<String, String> map) { return map.values().size(); } }";
		assertRewrite(source, source.replace("map.values().size()", "map.size()"));
	}

	private void assertNoRewrite(String source) {
		CompilationUnit unit= parse(source, true);
		assertTrue(new BatchTransformationProcessor(hints).process(unit, options()).isEmpty(), source);
	}

	private void assertRewrite(String source, String expected) {
		CompilationUnit unit= parse(source, true);
		var results= new BatchTransformationProcessor(hints).process(unit, options());
		assertEquals(1, results.size());
		var result= results.getFirst();
		assertTrue(result.hasReplacement());
		String actual= source.substring(0, result.match().getOffset()) + result.replacement()
				+ source.substring(result.match().getOffset() + result.match().getLength());
		assertEquals(expected, actual);
		Map<String, Long> baseline= diagnostics(unit);
		CompilationUnit after= parse(actual, true);
		diagnostics(after).forEach((problem, count) ->
				assertTrue(count <= baseline.getOrDefault(problem, 0L), () -> "New diagnostic: " + problem));
		assertTrue(new BatchTransformationProcessor(hints).process(after, options()).isEmpty());
	}

	private static CompilationUnit parse(String source, boolean bindings) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setUnitName("Sample.java");
		parser.setResolveBindings(bindings);
		parser.setBindingsRecovery(false);
		parser.setCompilerOptions(options());
		String classpath= System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
		parser.setEnvironment(classpath.split(java.util.regex.Pattern.quote(File.pathSeparator)), null, null, true);
		CompilationUnit unit= (CompilationUnit) parser.createAST(null);
		for (var problem : unit.getProblems()) assertFalse(problem.isError(), problem::getMessage);
		return unit;
	}

	private static Map<String, String> options() {
		Map<String, String> options= new HashMap<>(JavaCore.getOptions());
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		options.replaceAll((key, value) -> key.startsWith(JavaCore.PLUGIN_ID + ".compiler.problem.")
				&& (JavaCore.IGNORE.equals(value) || JavaCore.INFO.equals(value)) ? JavaCore.WARNING : value);
		options.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, "0");
		return options;
	}

	private static Map<String, Long> diagnostics(CompilationUnit unit) {
		return Arrays.stream(unit.getProblems()).filter(problem -> problem.isError() || problem.isWarning())
				.collect(Collectors.groupingBy(problem -> problem.isError() + ":" + problem.getID()
						+ Arrays.toString(problem.getArguments()), Collectors.counting()));
	}
}
