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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCoreWrapper;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.functional.core.operation.FilterOp;
import org.sandbox.functional.core.operation.MapOp;
import org.sandbox.functional.core.renderer.StringRenderer;
import org.sandbox.functional.core.terminal.ForEachTerminal;
import org.sandbox.functional.core.transformer.LoopModelTransformer;
import org.sandbox.jdt.internal.corext.fix.helper.ASTStreamRenderer;
import org.sandbox.jdt.internal.corext.fix.helper.JdtStreamExtractor;
import org.sandbox.jdt.internal.ui.fix.UseFunctionalCallCleanUp;
import org.sandbox.jdt.internal.ui.fix.UseFunctionalCallCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Exercises the actual cleanup, compilation of both versions and runtime behavior. */
class StreamChainToLoopTest {

	@RegisterExtension
	final ConversionContext context = new ConversionContext();

	@TempDir
	Path temporary;

	static final class ConversionContext extends AbstractEclipseJava {
		private CompilationUnit cleanupAst;
		private Map<String, String> cleanupOptions = Map.of();
		private RefactoringStatus cleanupStatus;
		private boolean cleanupFixCreated;

		ConversionContext() {
			super("testresources/rtstubs_22.jar", JavaCore.VERSION_22);
		}

		@Override
		protected RefactoringStatus performRefactoring(CleanUpRefactoring ref, ICompilationUnit[] units,
				ICleanUp[] cleanups, Set<String> expectedGroups) throws CoreException {
			cleanupAst = null;
			cleanupFixCreated = false;
			cleanupOptions = Map.of();
			ICleanUp[] traced = cleanups.clone();
			for (int index = 0; index < traced.length; index++) {
				if (traced[index] instanceof UseFunctionalCallCleanUp) {
					// The production facade only delegates to this core through the same
					// JDT wrapper. Capture references without resolving bindings early.
					var core = new UseFunctionalCallCleanUpCore() {
						@Override
						public void setOptions(CleanUpOptions options) {
							super.setOptions(options);
							cleanupOptions = options.getKeys().stream()
									.filter(key -> key.startsWith("cleanup.loop_conversion"))
									.collect(java.util.stream.Collectors.toMap(key -> key, options::getValue));
						}

						@Override
						public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
							cleanupAst = context.getAST();
							ICleanUpFix fix = super.createFix(context);
							cleanupFixCreated |= fix != null;
							return fix;
						}
					};
					traced[index] = new AbstractCleanUpCoreWrapper<UseFunctionalCallCleanUpCore>(Map.of(), core) { };
				}
			}
			cleanupStatus = super.performRefactoring(ref, units, traced, expectedGroups);
			return cleanupStatus;
		}

		String cleanupDiagnostics() {
			StringBuilder diagnostic = new StringBuilder("Cleanup status: ").append(cleanupStatus)
					.append("\nFix created: ").append(cleanupFixCreated)
					.append("\nOptions: ").append(cleanupOptions);
			if (cleanupAst == null) {
				return diagnostic.append("\nNo AST reached the functional cleanup").toString();
			}
			diagnostic.append("\nAST problems: ").append(Arrays.toString(cleanupAst.getProblems()));
			cleanupAst.accept(new ASTVisitor() {
				@Override
				public void preVisit(ASTNode node) {
					if (node instanceof MethodInvocation invocation) {
						diagnostic.append("\n").append(invocation.getName()).append(" @").append(node.getStartPosition())
								.append(": ").append(binding(invocation.resolveMethodBinding()));
					} else if (node instanceof MethodReference reference) {
						diagnostic.append("\n").append(reference).append(": ").append(binding(reference.resolveMethodBinding()))
								.append("; functional type: ").append(binding(reference.resolveTypeBinding()));
					}
				}
			});
			return diagnostic.toString();
		}

		private static String binding(IBinding binding) {
			return binding == null ? "null" : binding.getKey() + " (recovered=" + binding.isRecovered() + ")";
		}

		String convert(String source, String target) throws CoreException {
			return convert(source, target, false);
		}

		String convert(String source, String target, boolean allSources) throws CoreException {
			ICompilationUnit unit = getSourceFolder().createPackageFragment("test1", false, null)
					.createCompilationUnit("Example.java", source, true, null);
			enable(MYCleanUpConstants.LOOP_CONVERSION_ENABLED);
			set(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, target);
			enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM);
			if (allSources) {
				enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR);
				enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE);
			}
			assertNoCompilationError(unit);
			performRefactoring(new ICompilationUnit[] { unit }, null);
			assertNoCompilationError(unit);
			return unit.getSource();
		}

		JdtStreamExtractor.ExtractedStream extract(String source) throws CoreException {
			ICompilationUnit unit = getSourceFolder().createPackageFragment("test1", false, null)
					.createCompilationUnit("Example.java", source, true, null);
			assertNoCompilationError(unit);
			ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
			parser.setSource(unit);
			parser.setResolveBindings(true);
			CompilationUnit root = (CompilationUnit) parser.createAST(null);
			JdtStreamExtractor.ExtractedStream[] result = { null };
			root.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodInvocation node) {
					var extracted = JdtStreamExtractor.extract(node);
					if (extracted != null) {
						result[0] = extracted;
						return false;
					}
					return true;
				}
			});
			assertNotNull(result[0]);
			return result[0];
		}
	}

	static Stream<Arguments> pipelines() {
		return Stream.of("enhanced_for", "iterator_while").flatMap(target -> positiveBodies()
				.map(body -> Arguments.of(target, body)));
	}

	static Stream<String> positiveBodies() {
		return Stream.of(
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					Arrays.asList(" a ", "", "abcd").stream()
						.filter(item -> !item.isEmpty())
						.map(item -> item.trim())
						.map(item -> item.length())
						.filter(item -> item > 1)
						.forEachOrdered(item -> result.add(select(item)));
					return result.toString();
				}
				static String select(Integer value) { return "boxed:" + value; }
				static String select(int value) { return "primitive:" + value; }
				""",
				"""
				static String text = "field";
				public static String run() {
					List<String> result = new ArrayList<>();
					Arrays.<Object>asList("a", 1, "b").stream()
						.filter(item -> item instanceof String text && !text.isEmpty())
						.forEach(item -> result.add(text + item));
					return result.toString();
				}
				""",
				"""
				static final List<String> trace = new ArrayList<>();
				static List<String> source() {
					trace.add("source");
					return Arrays.asList("a", "", "bbb");
				}
				public static String run() {
					source().stream().filter(item -> {
						trace.add("filter:" + item);
						return !item.isEmpty();
					}).map(item -> {
						trace.add("map:" + item);
						return item.length();
					}).forEach(item -> {
						try {
							if (item == 1) return;
							trace.add("accept:" + item);
						} finally {
							trace.add("finally:" + item);
						}
					});
					trace.add("after");
					return trace.toString();
				}
				""",
				"""
				static String item = "field";
				public static String run() {
					List<String> result = new ArrayList<>();
					Arrays.asList("a", "b").stream().map(item -> item.toUpperCase())
						.forEach(value -> result.add(item + value));
					return result.toString();
				}
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					Arrays.asList(" a ", " ").stream()
						.filter(item -> !(item = item.trim()).isEmpty())
						.forEach(item -> result.add(item));
					return result.toString();
				}
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					Arrays.asList(" a ", "bb").stream().map(String::trim).map(String::length)
						.forEach(value -> result.add(select(value)));
					return result.toString();
				}
				static String select(Integer value) { return "boxed:" + value; }
				static String select(int value) { return "primitive:" + value; }
				""",
				"""
				public static String run() {
					List<List<String>> items = Arrays.asList(Arrays.asList("a"), Arrays.asList("b", "c"));
					List<String> result = new ArrayList<>();
					items.stream().forEach(item -> result.add(item.get(0)));
					return result.toString();
				}
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					int it = 7;
					Arrays.asList(1, 2).stream().forEach(value -> result.add(value.toString()));
					Arrays.asList(3, 4).forEach(value -> result.add(value.toString()));
					return it + result.toString();
				}
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					Arrays.asList("a", "b").stream().filter((Object item) -> select(item))
						.forEach(item -> result.add(item));
					return result.toString();
				}
				static boolean select(Object value) { return true; }
				static boolean select(String value) { return false; }
				""",
				"""
				static List<String> result = new ArrayList<>();
				public static String run() {
					Arrays.asList("a", null, "b").stream().filter(Objects::nonNull)
						.map(StringBuilder::new).forEach(Example::record);
					return result.toString();
				}
				static void record(StringBuilder value) { result.add(value.toString()); }
				""",
				"""
				public static String run() {
					return new Example().process();
				}
				private final List<String> items = Arrays.asList("a", "bb");
				private final List<Integer> result = new ArrayList<>();
				String process() {
					this.items.stream().map(String::length).forEach(this::record);
					return result.toString();
				}
				void record(Integer value) { result.add(value); }
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					Arrays.asList("a", "b").stream().map(item -> (Supplier<String>) () -> item)
						.forEach(item -> result.add(item.get()));
					return result.toString();
				}
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					if (result.isEmpty())
						Arrays.asList("a").stream().map(String::trim).forEach(item -> result.add(item));
					return result.toString();
				}
				""",
				"""
				public static String run() {
					List<String> items = null;
					try {
						items.stream().map(String::trim).forEach(Example::record);
						return "missed null";
					} catch (NullPointerException expected) {
						return "null source";
					}
				}
				static void record(String value) { throw new AssertionError(); }
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					Collections.<String>emptyList().stream().map(String::length)
						.forEach(item -> result.add(item.toString()));
					return result.toString();
				}
				""");
	}

	@ParameterizedTest(name = "{0}: preserves pipeline behavior [{index}]")
	@MethodSource("pipelines")
	void preservesBehavior(String target, String body) throws Exception {
		String original = source(body);
		String converted = context.convert(original, target);
		assertNotEquals(original, converted);
		assertFalse(converted.contains(".stream()"), converted);
		assertTrue(converted.contains("enhanced_for".equals(target) ? "for (" : "while ("), converted);
		assertEquals(execute(original, "before"), execute(converted, "after"), converted);
		assertEquals(converted, context.convert(converted, target), "A second cleanup must be stable");
	}

	@ParameterizedTest
	@ValueSource(strings = { "enhanced_for", "iterator_while" })
	void preservesCommentsAndLambdaLocalReturn(String target) throws Exception {
		String original = source("""
				public static String run() {
					List<String> result = new ArrayList<>();
					Arrays.asList("a", "b").stream().map(item -> {
						// mapper comment
						return item.toUpperCase();
					}).forEach(item -> {
						// consumer comment
						if (item.equals("A")) return;
						result.add(item);
					});
					result.add("after");
					return result.toString();
				}
				""");
		String converted = context.convert(original, target);
		assertNotEquals(original, converted);
		assertTrue(converted.contains("// mapper comment"));
		assertTrue(converted.contains("// consumer comment"));
		assertEquals("[B, after]", execute(converted, "comments"));
	}

	static String commentBody() {
		return """
				public static String run() {
					List<String> result = new ArrayList<>();
					// before pipeline
					Arrays.asList("a", "b").stream().forEach(item -> {
						// before statement
						result.add(item); // beside statement
						// after statement
					});
					Arrays.asList("a", "b").stream().forEach(item -> {
						// empty consumer
					});
					return result.toString();
				}
				""";
	}

	@ParameterizedTest
	@ValueSource(strings = { "enhanced_for", "iterator_while" })
	void preservesAllTerminalBodyComments(String target) throws Exception {
		String converted = context.convert(source(commentBody()), target);
		for (String comment : new String[] { "before pipeline", "before statement", "beside statement", "after statement", "empty consumer" }) {
			assertTrue(converted.contains("// " + comment), converted);
		}
		assertEquals("[a, b]", execute(converted, "body-comments"));
	}

	static Stream<Arguments> unsupported() {
		return Stream.of("enhanced_for", "iterator_while").flatMap(target -> Stream.of(
				"items.parallelStream().map(String::trim).forEach(item -> sink.add(item));",
				"items.stream().parallel().forEach(item -> sink.add(item));",
				"items.stream().sorted().forEach(item -> sink.add(item));",
				"items.stream().map(String::trim).distinct().forEach(item -> sink.add(item));",
				"items.stream().limit(1).forEach(item -> sink.add(item));",
				"items.stream().forEach(sink::add);",
				"items.stream().filter(predicate).forEach(item -> sink.add(item));",
				"items.stream() /* keep pipeline comment */ .filter(item -> true).forEach(item -> sink.add(item));",
				"java.util.stream.Stream.of(\"a\").parallel().forEach(item -> sink.add(item));")
				.map(statement -> Arguments.of(target, statement)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "enhanced_for", "iterator_while" })
	void nestedSourceFormatsUseSeparateRewrites(String target) throws Exception {
		String outer = "iterator_while".equals(target)
				? "for (String prefix : groups) {"
				: "Iterator<String> cursor = groups.iterator(); while (cursor.hasNext()) { Object prefix = cursor.next();";
		String original = source("""
				public static String run() {
					List<String> groups = Arrays.asList("A", "B");
					List<String> words = Arrays.asList(" a ", " b ");
					List<String> result = new ArrayList<>();
					LOOP_HEADER
						words.stream().map(String::trim).forEach(word -> result.add(label(prefix) + word));
					}
					return result.toString();
				}
				static String label(Object value) { return "object:" + value; }
				static String label(String value) { return "string:" + value; }
				""".replace("LOOP_HEADER", outer));
		String first = context.convert(original, target, true);
		assertFalse(first.contains(".stream()"), first);
		String second = context.convert(first, target, true);
		assertNotEquals(first, second, "The enclosing loop is converted on the next pass");
		String expected = execute(original, "nested-original");
		assertEquals(expected, execute(first, "nested-first"));
		assertEquals(expected, execute(second, "nested-second"));
		assertEquals(second, context.convert(second, target, true));
	}

	@ParameterizedTest
	@ValueSource(strings = { "iterator_while" })
	void independentSourceFormatsReserveDifferentIterators(String target) throws Exception {
		String original = source("""
				public static String run() {
					List<String> items = Arrays.asList("a", "b");
					List<String> result = new ArrayList<>();
					for (String item : items) { result.add(item); }
					items.stream().map(String::toUpperCase).forEach(item -> result.add(item));
					return result.toString();
				}
				""");
		String converted = context.convert(original, target, true);
		assertFalse(converted.contains(".stream()"));
		assertFalse(converted.contains("for ("));
		assertEquals("[a, b, A, B]", execute(converted, "independent-sources"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "enhanced_for", "iterator_while" })
	void qualifiesTypesThatClashWithNestedDeclarations(String target) throws Exception {
		String original = source("""
				static class Function { }
				static class Consumer { }
				static class Iterator { }
				static List<Integer> result = new ArrayList<>();
				public static String run() {
					Arrays.asList("a", "bb").stream().map(String::length).forEach(Example::record);
					return result.toString();
				}
				static void record(Integer value) { result.add(value); }
				""");
		String converted = context.convert(original, target);
		assertFalse(converted.contains(".stream()"));
		assertTrue(converted.contains("java.util.function.Function"), converted);
		assertTrue(converted.contains("java.util.function.Consumer"), converted);
		assertEquals("[1, 2]", execute(converted, "type-conflicts"));
	}

	@ParameterizedTest(name = "{0}: unsupported chain remains unchanged [{index}]")
	@MethodSource("unsupported")
	void leavesUnsupportedChainsIntact(String target, String statement) throws CoreException {
		String original = source("""
				void process(List<String> items, List<String> sink, Predicate<String> predicate) {
					STATEMENT
				}
				""".replace("STATEMENT", statement));
		assertEquals(original, context.convert(original, target));
	}

	@ParameterizedTest
	@ValueSource(strings = { "enhanced_for", "iterator_while" })
	void rejectsCustomMethodsWithStandardNames(String target) throws CoreException {
		String original = source("""
				static class Custom extends ArrayList<String> {
					@Override public java.util.stream.Stream<String> stream() {
						return java.util.stream.Stream.of("different");
					}
					@Override public void forEach(Consumer<? super String> action) {
						action.accept("different");
					}
				}
				void process(Custom items, List<String> sink) {
					items.stream().map(String::trim).forEach(item -> sink.add(item));
					items.forEach(item -> sink.add(item));
				}
				""");
		assertEquals(original, context.convert(original, target));
	}

	private static String source(String body) {
		return "package test1;\nimport java.util.*;\nimport java.util.function.*;\npublic class Example {\n" + body + "}\n";
	}

	@ParameterizedTest
	@ValueSource(strings = { "enhanced_for", "iterator_while" })
	void extractedUlrSupportsStreamAndLoopRenderers(String target) throws Exception {
		String original = source("""
				public static String run() {
					List<String> result = new ArrayList<>();
					Arrays.asList("a", "bb").stream().<Number>map(text -> {
						// retain this function boundary and comment
						return text.length();
					}).filter((Object value) -> value != null)
						.forEachOrdered(number -> result.add(label(number)));
					return result.toString();
				}
				static String label(Number value) { return "number:" + value; }
				static String label(Integer value) { return "integer:" + value; }
				""");
		var extracted = context.extract(original);
		var model = extracted.model();
		assertEquals(2, model.getOperations().size());
		MapOp map = (MapOp) model.getOperations().get(0);
		FilterOp filter = (FilterOp) model.getOperations().get(1);
		assertEquals("java.lang.Number", map.targetType());
		assertEquals("java.lang.String", map.function().inputType());
		assertTrue(map.function().requiresInvocation());
		assertEquals("java.lang.Object", filter.function().inputType());
		assertFalse(filter.function().requiresInvocation());
		assertTrue(((ForEachTerminal) model.getTerminal()).ordered());
		String textPipeline = new LoopModelTransformer<>(new StringRenderer()).transform(model);
		assertTrue(textPipeline.contains("// retain this function boundary and comment"));
		CompilationUnit root = (CompilationUnit) extracted.context().statement().getRoot();
		var astRenderer = new ASTStreamRenderer(root.getAST(), ASTRewrite.create(root.getAST()), root, null);
		String astPipeline = new LoopModelTransformer<Expression>(astRenderer).transform(model).toString();
		String expected = execute(original, "ulr-original");
		assertEquals("[number:1, number:2]", expected);
		int start = extracted.context().statement().getStartPosition();
		int end = start + extracted.context().statement().getLength();
		assertEquals(expected, execute(original.substring(0, start) + textPipeline + ";" + original.substring(end), "ulr-text"));
		assertEquals(expected, execute(original.substring(0, start) + astPipeline + ";" + original.substring(end), "ulr-ast"));
		assertEquals(expected, execute(context.convert(original, target), "ulr-loop"));
	}

	static Stream<String> iteratorBodies() {
		return Stream.of(
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					for (long value : Arrays.asList(1, 2, 3)) { result.add(select(value)); }
					return result.toString();
				}
				static String select(long value) { return "long:" + value; }
				static String select(Integer value) { return "boxed:" + value; }
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					List<? extends Number> values = Arrays.asList(1, 2.5);
					for (Number value : values) { result.add(value.toString()); }
					return result.toString();
				}
				""",
				"""
				static class Iterator { }
				public static String run() {
					List<String> result = new ArrayList<>();
					for (List<String> value : Arrays.asList(Arrays.asList("a"), Arrays.asList("b"))) {
						result.addAll(value);
					}
					return result.toString();
				}
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					outer: for (int value : Arrays.asList(1, 2, 3, 4)) {
						// label must still denote the loop
						if (value == 2) continue outer;
						if (value == 4) break outer;
						result.add("v" + value);
					}
					return result.toString();
				}
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					for (final String row[] : Arrays.<String[]>asList(new String[] {"a"}, new String[] {"b"})) {
						result.add(row[0]);
					}
					return result.toString();
				}
				""",
				"""
				public static String run() {
					List<String> result = new ArrayList<>();
					Iterable values = Arrays.asList("a", 1);
					if (values != null) for (Object value : values) result.add(value.toString());
					for (var value : Arrays.asList("b", "c")) { /* empty body comment */ }
					return result.toString();
				}
				""");
	}

	@ParameterizedTest
	@MethodSource("iteratorBodies")
	void enhancedForPreservesTypesLabelsAndScope(String body) throws Exception {
		String original = source(body);
		String converted = context.convert(original, "iterator_while", true);
		assertNotEquals(original, converted);
		assertTrue(converted.contains("while ("), converted);
		if (original.contains("/* empty body comment */")) {
			assertTrue(converted.contains("/* empty body comment */"), converted);
		}
		assertEquals(execute(original, "iterator-original"), execute(converted, "iterator-converted"), converted);
	}

	private String execute(String source, String directory) throws Exception {
		return executeSource(source, temporary.resolve(directory));
	}

	static String executeSource(String source, Path directory) throws Exception {
		Path output = Files.createDirectories(directory);
		Path file = output.resolve("Example.java");
		Files.writeString(file, source, StandardCharsets.UTF_8);
		var compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "The runtime equivalence tests require a full JDK");
		assertEquals(0, compiler.run(null, null, null, "-d", output.toString(), file.toString()), source);
		try (URLClassLoader loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
			return (String) loader.loadClass("test1.Example").getMethod("run").invoke(null);
		}
	}
}
