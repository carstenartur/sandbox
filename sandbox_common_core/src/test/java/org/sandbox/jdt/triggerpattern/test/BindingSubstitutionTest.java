/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;

class BindingSubstitutionTest {

	@TempDir
	Path compilerOutput;

	@Test
	void overlappingNamesAreReplacedAsWholeTokens() throws Exception {
		assertReplacement("old($x, $xyz)", "modern($xyz, $x)",
				"old(first, second)", "modern(second, first)");
	}

	@Test
	void sourceIdentifiersContainingDollarSignsArePreserved() throws Exception {
		assertReplacement("old($x)", "modern($x)", "old(customer$value)", "modern(customer$value)");
	}

	@Test
	void sourceEscapedStringLiteralsArePreserved() throws Exception {
		assertReplacement("old($x)", "modern($x)", "old(\"C:\\\\temp\")", "modern(\"C:\\\\temp\")");
	}

	@Test
	void placeholderTextInsideStringLiteralsIsNotCode() throws Exception {
		assertReplacement("old($x)", "modern(\"$x\", $x)", "old(value)", "modern(\"$x\", value)");
	}

	@Test
	void bindingsAreNotExpandedAgainWhenSourceNamesLookLikePlaceholders() throws Exception {
		assertReplacement("old($x, $xyz)", "modern($xyz, $x)", "old($xyz, value)", "modern(value, $xyz)");
	}

	@Test
	void compoundExpressionsRetainTheirGroupingAndEvaluation() throws Exception {
		assertReplacement("old($x)", "$x * 2", "old(a + b)", "(a + b) * 2");
		assertReplacement("old($x)", "$x * 2", "old(test ? a : b)", "(test ? a : b) * 2");
		assertReplacement("old($x)", "$x * 2", "old((int) number)", "((int)number) * 2");
		assertReplacement("old($x)", "-$x", "old(-number)", "-(-number)");
		assertReplacement("old($x)", "$x * 2", "old(number = other)", "(number=other) * 2");
		assertReplacement("old($x)", "$x.toString()", "old(test ? a : b)", "(test ? a : b).toString()");
		assertReplacement("old($x)", "$x", "consume(2 * old(number = other))", "(number=other)");
		assertReplacement("old($x)", "$x", "consume(2 * old(number++))", "(number++)");
	}

	@Test
	void postfixUpdatesCanBecomeExpressionStatements() throws Exception {
		assertStatementReplacement("old($x);", "$x;", "old(i++)", "i++;");
	}

	@Test
	void assignmentsCanBecomeExpressionStatements() throws Exception {
		assertStatementReplacement("old($x);", "$x;", "old(i = other)", "i=other;");
	}

	@Test
	void prefixUpdatesCanBecomeExpressionStatements() throws Exception {
		assertStatementReplacement("old($x);", "$x;", "old(++i)", "++i;");
	}

	@Test
	void bareExpressionReplacementsUseTheOriginalStatementDestination() throws Exception {
		assertReplacement("old($x)", "$x", "old(i = other)", "i=other");
		assertReplacement("old($x)", "$x", "old(i++)", "i++");
	}

	@Test
	void bareExpressionReplacementsUseTheOriginalForDestinations() throws Exception {
		assertReplacement("old($x)", "$x", "for (old(i = start); condition; ) {}", "i=start");
		assertReplacement("old($x)", "$x", "for (; condition; old(i++)) {}", "i++");
	}

	@ParameterizedTest
	@ValueSource(strings = { "i++", "++i", "i = other" })
	void wholeLambdaBodyBindingsRemainVoidCompatible(String binding) throws Exception {
		String source = "class Example { int i, other; int old(int value) { return value; } "
				+ "void run() { Runnable action = () -> old(" + binding + "); } }";
		assertCompiles(source);
		assertCompiles(replaceOriginalCall(source, "$x"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "value++", "++value", "value = other" })
	void generatedLambdaBodiesRemainVoidCompatible(String binding) throws Exception {
		String source = "class Example { int value, other; static Runnable identity(Runnable r) { return r; } "
				+ "Runnable run() { return identity(() -> " + binding + "); } }";
		assertCompiles(source);
		String transformed = replaceCall(source, "identity(() -> $x)", "() -> $x");
		assertTrue(transformed.contains("return () -> "), "The update must remain deferred inside the lambda");
		assertCompiles(transformed);
	}

	@Test
	void generatedLambdaArithmeticRetainsNestedAssignmentGrouping() throws Exception {
		String source = "class Example { int value, other; static java.util.function.IntSupplier identity("
				+ "java.util.function.IntSupplier r) { return r; } "
				+ "java.util.function.IntSupplier run() { return identity(() -> value = other); } }";
		assertCompiles(source);
		String transformed = replaceCall(source, "identity(() -> $x)", "() -> 2 * $x");
		assertTrue(transformed.contains("2 * (value=other)"));
		assertCompiles(transformed);
	}

	@ParameterizedTest
	@ValueSource(strings = { "() -> /* keep */ $x", "() -> $x /* keep */" })
	void generatedLambdaCommentsRemainVoidCompatible(String replacement) throws Exception {
		String source = "class Example { int value; static Runnable identity(Runnable r) { return r; } "
				+ "Runnable run() { return identity(() -> value++); } }";
		assertCompiles(source);
		// Keep the actual replacement comments: the DSL parser intentionally strips
		// them, while callers of the substitution API can supply the complete text.
		var file = new HintFileParser().parse("identity(() -> $x)\n;;\n");
		var rule = TransformationRule.of(file.getRules().getFirst().sourcePattern(),
				List.of(RewriteAlternative.otherwise(replacement)));
		var results = new BatchTransformationProcessor(file, List.of(rule)).process(parseCompilationUnit(source));
		assertEquals(1, results.size());
		var result = results.getFirst();
		String transformed = source.substring(0, result.match().getOffset()) + result.replacement()
				+ source.substring(result.match().getOffset() + result.match().getLength());
		assertTrue(transformed.contains("/* keep */"));
		assertCompiles(transformed);
	}

	@Test
	void nestedLambdaBodyBindingsRetainPrecedenceGrouping() throws Exception {
		String source = "class Example { int i, other; int old(int value) { return value; } "
				+ "void run() { java.util.function.IntSupplier action = () -> 2 * old(i = other); } }";
		String transformed = replaceOriginalCall(source, "$x");
		assertTrue(transformed.contains("2 * (i=other)"));
		assertCompiles(transformed);
	}

	@Test
	void partialLambdaBodyBindingsRetainPrecedenceGrouping() throws Exception {
		String source = "class Example { int i, other; int old(int value) { return value; } "
				+ "void run() { java.util.function.IntSupplier action = () -> old(i = other); } }";
		String transformed = replaceOriginalCall(source, "$x * 2");
		assertTrue(transformed.contains("() -> (i=other) * 2"));
		assertCompiles(transformed);
	}

	@Test
	void forInitializersAndUpdatersRetainStatementExpressionSyntax() throws Exception {
		assertStatementReplacement("old($initial, $update);", "for ($initial; condition; $update) {}",
				"old(i = start, i++)", "for (i=start; condition; i++) {}");
	}

	@Test
	void variadicForInitializersRetainStatementExpressionSyntax() throws Exception {
		assertStatementReplacement("old($initial$);", "for ($initial$; condition; ) {}",
				"old(i = start, j = other)", "for (i=start, j=other; condition; ) {}");
	}

	@Test
	void repeatedBindingsAreGroupedAccordingToEachDestination() throws Exception {
		assertStatementReplacement("old($x);", "{ $x; consume($x); }", "old(i++)",
				"{ i++; consume((i++)); }");
	}

	@Test
	void nestedAssignmentStillRequiresGroupingInForUpdater() throws Exception {
		assertStatementReplacement("old($x);", "for (; condition; i += $x) {}", "old(j = other)",
				"for (; condition; i += (j=other)) {}");
	}

	@Test
	void negatedPatternInstanceofGroupsTheWholeBoundExpression() throws Exception {
		assertReplacement("old($x)", "!$x", "old(value instanceof String text)",
				"!(value instanceof String text)");
	}

	@Test
	void variadicStatementsUseStatementSeparators() throws Exception {
		String before = "before(); first(); second(); after();";
		TransformationRule rule = TransformationRule.of(
				Pattern.of("{ before(); $body$; after(); }", PatternKind.BLOCK),
				List.of(new RewriteAlternative("{ $body$; }", null)));
		var file = new HintFileParser().parse("$x\n;;\n");
		var results = new BatchTransformationProcessor(file, List.of(rule))
				.process(parse(before));
		assertEquals(1, results.size());
		assertEquals("{ first();\nsecond(); }", results.getFirst().replacement());
	}

	private void assertReplacement(String sourcePattern, String replacement, String source, String expected)
			throws Exception {
		var file = new HintFileParser().parse(sourcePattern + "\n=> " + replacement + "\n;;\n");
		var results = new BatchTransformationProcessor(file).process(parse(source + ";"));
		assertEquals(1, results.size());
		assertEquals(expected, results.getFirst().replacement());
	}

	private void assertStatementReplacement(String sourcePattern, String replacement, String source, String expected)
			throws Exception {
		assertReplacement(sourcePattern, replacement, source, expected);
		assertFalse(Arrays.stream(parse(expected).getProblems()).anyMatch(problem -> problem.isError()),
				"The substituted statement must remain valid Java syntax");
	}

	private String replaceOriginalCall(String source, String replacement) throws Exception {
		return replaceCall(source, "old($x)", replacement);
	}

	private String replaceCall(String source, String pattern, String replacement) throws Exception {
		var file = new HintFileParser().parse(pattern + "\n=> " + replacement + "\n;;\n");
		var results = new BatchTransformationProcessor(file).process(parseCompilationUnit(source));
		assertEquals(1, results.size());
		var result = results.getFirst();
		return source.substring(0, result.match().getOffset()) + result.replacement()
				+ source.substring(result.match().getOffset() + result.match().getLength());
	}

	private void assertCompiles(String source) throws Exception {
		var compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "Compiler-backed regression tests require a JDK");
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		var javaSource = new SimpleJavaFileObject(URI.create("string:///Example.java"), JavaFileObject.Kind.SOURCE) {
			@Override
			public CharSequence getCharContent(boolean ignoreEncodingErrors) {
				return source;
			}
		};
		try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
			assertTrue(compiler.getTask(null, fileManager, diagnostics,
					List.of("-proc:none", "-d", compilerOutput.toString()), null, List.of(javaSource)).call(),
					() -> "Java compiler rejected the transformed source: " + diagnostics.getDiagnostics());
		}
	}

	private CompilationUnit parse(String statements) {
		return parseCompilationUnit("class Example { void run() { " + statements + " } }");
	}

	private CompilationUnit parseCompilationUnit(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		var options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_25, options);
		parser.setCompilerOptions(options);
		parser.setSource(source.toCharArray());
		return (CompilationUnit) parser.createAST(null);
	}
}
