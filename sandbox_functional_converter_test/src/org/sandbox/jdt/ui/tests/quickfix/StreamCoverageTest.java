/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.ui.text.correction.AssistContext;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.functional.core.renderer.StringRenderer;
import org.sandbox.functional.core.transformer.LoopModelTransformer;
import org.sandbox.jdt.internal.corext.fix.helper.ASTStreamRenderer;
import org.sandbox.jdt.internal.corext.fix.helper.LoopTargetFormat;
import org.sandbox.jdt.internal.ui.fix.LoopConversionQuickAssistProcessor;

/** Differential compilation/execution across ULR renderers and actual IDE entry points. */
class StreamCoverageTest {
	@RegisterExtension
	final StreamChainToLoopTest.ConversionContext context = new StreamChainToLoopTest.ConversionContext();
	@TempDir
	Path temporary;

	@AfterEach
	void flushUndoBeforeWorkspaceTeardown() {
		RefactoringCore.getUndoManager().flush();
	}

	static Stream<Arguments> pipelines() {
		Stream<Arguments> direct = Stream.of("enhanced_for", "iterator_while").flatMap(target -> Stream.of(
				"Arrays.stream(new String[] {\" a \", \"\", \"bbb\"}).filter(s -> !s.isEmpty()).mapToInt(String::length).forEach(v -> result.add(label(v)));",
				"Arrays.asList(\"a\", \"bbb\").stream().mapToInt(String::length).mapToLong(v -> v * 10000000000L).mapToDouble(v -> v / 3.0).mapToObj(v -> label(v)).forEach(v -> result.add(v));",
				"Arrays.stream(new int[] {-1, 0, 2}).filter(v -> v >= 0).map(v -> v + 1).forEachOrdered(v -> result.add(label(v)));",
				"Arrays.stream(new int[] {1, 2}).<Number>mapToObj(v -> v).forEach(v -> result.add(label(v)));",
				"Arrays.stream(new int[] {1, 200]).boxed().forEach(v -> result.add(label(v)));",
				"Arrays.stream(new int[] {1, Integer.MAX_VALUE}).asLongStream().asDoubleStream().boxed().forEach(v -> result.add(label(v)));",
				"Arrays.stream(new long[] {9007199254740993L, Long.MAX_VALUE}).asDoubleStream().forEach(v -> result.add(label(v)));",
				"Arrays.stream(new long[] {1, 200}).mapToInt(v -> (int) v).mapToObj(v -> label(v)).forEach(v -> result.add(v));",
				"Arrays.stream(new double[] {-0.0, Double.NaN, Double.POSITIVE_INFINITY}).mapToLong(Double::doubleToRawLongBits).forEach(v -> result.add(label(v)));",
				"Arrays.stream(new double[] {-0.0, Double.NaN}).boxed().forEach(v -> result.add(label(v)));",
				"Arrays.stream(new String[] {\" a \", \"b\"}).peek(s -> { String local = s.trim(); result.add(local); }).peek(s -> { String local = s.toUpperCase(); result.add(local); }).forEach(s -> result.add(s));",
				"Arrays.stream(new int[] {-1, 2}).peek(v -> { if (v < 0) return; try { result.add(label(v)); } finally { result.add(\"finally\"); } }).map(v -> v + 1).forEach(v -> result.add(label(v)));",
				"Arrays.stream(new int[] {1, 2}).peek(v -> { v++; result.add(label(v)); }).map(v -> { v++; return v; }).forEach(v -> result.add(label(v)));",
				"java.util.Arrays.<Number>stream(new Integer[] {1, 2}).forEach(v -> result.add(label(v)));",
				"try { Arrays.stream((int[]) null).forEach(v -> result.add(label(v))); } catch (NullPointerException expected) { result.add(\"null\"); }")
				.map(statement -> Arguments.of(target, source(statement))));
		Stream<Arguments> factories = Stream.of(
				"java.util.stream.IntStream.rangeClosed(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)",
				"java.util.stream.LongStream.rangeClosed(Long.MAX_VALUE - 1, Long.MAX_VALUE)",
				"java.util.stream.IntStream.range(5, 3)",
				"java.util.stream.IntStream.of(1, 2)",
				"java.util.stream.DoubleStream.of(-0.0, Double.NaN)",
				"java.util.stream.Stream.of(\"a\", \"b\")",
				"java.util.stream.Stream.<String>ofNullable(null)",
				"java.util.stream.Stream.<String>empty()",
				"java.util.stream.IntStream.iterate(0, v -> v < 3, v -> v + 1)",
				"Arrays.stream(new String[] {\"a\", \"b\", \"c\"}, 1, 3)")
				.map(factory -> Arguments.of("iterator_while", source(factory + ".peek(v -> result.add(\"peek:\" + v)).forEachOrdered(v -> result.add(label(v)));")));
		return Stream.concat(direct, factories);
	}

	private static String source(String statement) {
		return """
				package test1;
				import java.util.*;
				public class Example {
					public static String run() {
						List<String> result = new ArrayList<>();
						STATEMENT
						return result.toString();
					}
					static String label(int v) { return "int:" + v; }
					static String label(long v) { return "long:" + v; }
					static String label(double v) { return "double:" + v; }
					static String label(Integer v) { return "Integer:" + v; }
					static String label(Number v) { return "Number:" + v; }
					static String label(Object v) { return "Object:" + v; }
				}
				""".replace("STATEMENT", statement);
	}

	@ParameterizedTest(name = "{0}: cleanup semantics [{index}]")
	@MethodSource("pipelines")
	void cleanupPreservesRuntime(String target, String original) throws Exception {
		String converted = context.convert(original, target);
		assertNotEquals(original, converted, context::cleanupDiagnostics);
		assertEquals(execute(original, "cleanup-original"), execute(converted, "cleanup-converted"), converted);
		assertEquals(converted, context.convert(converted, target), "A second cleanup pass is stable");
	}

	@RepeatedTest(50)
	void primitiveMethodReferenceSurvivesRepeatedWorkspaceAndProfileSetup(RepetitionInfo repetition) throws Exception {
		String original = source("Arrays.stream(new double[] {-0.0, Double.NaN, Double.POSITIVE_INFINITY})"
				+ ".mapToLong(Double::doubleToRawLongBits).forEach(v -> result.add(label(v)));");
		String target = repetition.getCurrentRepetition() % 2 == 0 ? "iterator_while" : "enhanced_for";
		String converted = context.convert(original, target);
		assertNotEquals(original, converted, context::cleanupDiagnostics);
		assertEquals(converted, context.convert(converted, target), "A second cleanup pass is stable");
	}

	@ParameterizedTest(name = "{0}: ULR and editor renderers [{index}]")
	@MethodSource("pipelines")
	void ulrAndEditorRenderersPreserveRuntime(String target, String original) throws Exception {
		var extracted = context.extract(original);
		var root = (CompilationUnit) extracted.context().statement().getRoot();
		String expected = execute(original, "original");
		String text = new LoopModelTransformer<>(new StringRenderer()).transform(extracted.model());
		var renderer = new ASTStreamRenderer(root.getAST(), ASTRewrite.create(root.getAST()), root, null);
		String ast = new LoopModelTransformer<Expression>(renderer).transform(extracted.model()).toString();
		int start = extracted.context().statement().getStartPosition();
		int end = start + extracted.context().statement().getLength();
		assertEquals(expected, execute(original.substring(0, start) + text + ";" + original.substring(end), "string-renderer"), text);
		assertEquals(expected, execute(original.substring(0, start) + ast + ";" + original.substring(end), "ast-renderer"), ast);
		var invocation = new AssistContext((org.eclipse.jdt.core.ICompilationUnit) root.getJavaElement(), start, 0);
		invocation.setASTRoot(root);
		var proposal = (FixCorrectionProposal) Arrays.stream(new LoopConversionQuickAssistProcessor().getAssists(invocation, new IProblemLocation[0]))
				.filter(item -> item.getDisplayString().equals(LoopConversionQuickAssistProcessor.label(LoopTargetFormat.fromId(target))))
				.findFirst().orElseThrow();
		String imperative = proposal.getPreviewContent();
		assertNotEquals(original, imperative);
		assertEquals(expected, execute(imperative, "editor"), imperative);
	}

	@ParameterizedTest
	@ValueSource(strings = { "java.util.stream.Stream.of(\"a\")", "java.util.stream.IntStream.range(0, 2)", "Arrays.stream(new int[] {1,2}, 0, 1)" })
	void lazyFactoriesRequireIteratorTarget(String factory) throws Exception {
		String original = source(factory + ".forEach(v -> result.add(label(v))); ");
		assertEquals(original, context.convert(original, "enhanced_for"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "-1", "4" })
	void arraySliceExceptionsAndEvaluationOrderArePreserved(String upper) throws Exception {
		String original = source("try { Arrays.stream(array(result), bound(result, 0), bound(result, " + upper + "))"
				+ ".forEach(v -> result.add(label(v))); } catch (RuntimeException expected) { result.add(expected.getClass().getSimpleName()); }")
				.replace("static String label(int v)", "static String[] array(List<String> trace) { trace.add(\"array\"); return new String[] {\"a\",\"b\"}; }\n"
						+ "static int bound(List<String> trace, int value) { trace.add(\"bound:\" + value); return value; }\nstatic String label(int v)");
		String converted = context.convert(original, "iterator_while");
		assertNotEquals(original, converted);
		assertEquals(execute(original, "bounds-original"), execute(converted, "bounds-loop"));
	}

	private String execute(String source, String directory) throws Exception {
		return StreamChainToLoopTest.executeSource(source, temporary.resolve(directory));
	}

	static Stream<String> iteratorProtocols() {
		String body = "{ final String value = it.next(); count++; if (value.isEmpty()) continue; result.add(value); if (value.equals(\"b\")) break; }";
		return Stream.of(
				"for (Iterator<String> it = values.iterator(); it.hasNext();) " + body,
				"Iterator<String> it = values.iterator(); while (it.hasNext()) " + body,
				"outer: for (Iterator<String> it = values.iterator(); it.hasNext();) " + body.replace("continue;", "continue outer;").replace("break;", "break outer;"),
				"for (Iterator<String> it = values.iterator(); it.hasNext();) { var value = it.next(); /* body comment */ result.add(value); }")
				.map(loop -> source("List<String> values = Arrays.asList(\"a\", \"\", \"b\", \"c\"); int count = 0; " + loop + " result.add(\"count:\" + count);"));
	}

	@ParameterizedTest
	@MethodSource("iteratorProtocols")
	void iteratorCleanupPreservesControlFlowAndState(String original) throws Exception {
		String converted = context.convert(original, "enhanced_for", true);
		assertNotEquals(original, converted);
		assertEquals(execute(original, "iterator-original"), execute(converted, "iterator-cleanup"), converted);
	}

	@ParameterizedTest
	@MethodSource("iteratorProtocols")
	void iteratorAssistPreservesControlFlowAndState(String original) throws Exception {
		String converted = assist(original, original.indexOf("hasNext"), LoopTargetFormat.FOR_LOOP);
		assertEquals(execute(original, "protocol-original"), execute(converted, "protocol-assist"), converted);
	}

	private String assist(String original, int offset, LoopTargetFormat target) throws Exception {
		var unit = context.getSourceFolder().createPackageFragment("test1", false, null).createCompilationUnit("Example.java", original, true, null);
		var parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		var invocation = new AssistContext(unit, offset, 0);
		invocation.setASTRoot((CompilationUnit) parser.createAST(null));
		var proposal = (FixCorrectionProposal) Arrays.stream(new LoopConversionQuickAssistProcessor().getAssists(invocation, new IProblemLocation[0]))
				.filter(item -> item.getDisplayString().equals(LoopConversionQuickAssistProcessor.label(target)))
				.findFirst().orElseThrow();
		return proposal.getPreviewContent();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"List<? extends Number> values = Arrays.asList(1, 2); for (var value : values) { result.add(label(value)); }",
			"List<? super Integer> values = Arrays.asList(1, 2); for (var value : values) { result.add(label(value)); }",
			"List<Integer> values = Arrays.asList(1, 2); for (var value : values) { result.add(label(value)); }" })
	void inferredElementTypesPreserveOverloads(String loop) throws Exception {
		String original = source(loop);
		String converted = assist(original, original.indexOf("for ("), LoopTargetFormat.WHILE_LOOP);
		assertEquals(execute(original, "var-original"), execute(converted, "var-assist"), converted);
		assertEquals(execute(original, "var-original"), execute(assist(converted, converted.indexOf("hasNext"), LoopTargetFormat.FOR_LOOP), "var-roundtrip"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"var values = Arrays.asList(new Object() { String value() { return \"a\"; } }); for (var value : values) { result.add(value.value()); }",
			"var values = Arrays.asList(new Object() { String value() { return \"a\"; } }); for (var it = values.iterator(); it.hasNext();) { var value = it.next(); result.add(value.value()); }" })
	void nonDenotableElementTypesStayUnchanged(String loop) throws Exception {
		String original = source(loop);
		assertEquals(original, context.convert(original, "iterator_while", true));
		assertEquals(original, context.convert(original, "enhanced_for", true));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"while (it.hasNext()) { String value = it.next(); System.out.println(value); } result.add(it.toString());",
			"while (it.hasNext()) { String value = it.next(); result.add(value); } result.add(it.toString());",
			"while (it.hasNext()) { String value = it.next(); for (int j = 0; j < 1; j++) { result.add(it.next()); } }",
			"while (it.hasNext()) { String value = it.next(); Runnable task = () -> result.add(it.toString()); task.run(); }",
			"while (it.hasNext()) { String value = it.next(); result.add(value); it.remove(); }" })
	void escapingOrNonstandardIteratorProtocolStaysUnchanged(String loop) throws Exception {
		String original = source("List<String> values = new ArrayList<>(Arrays.asList(\"a\", \"b\")); Iterator<String> it = values.iterator(); " + loop);
		assertEquals(original, context.convert(original, "enhanced_for", true));
		assertEquals(original, context.convert(original, "stream", true));
	}
}
