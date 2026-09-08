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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Exercises the actual cleanup, compilation of both versions and runtime behavior. */
class StreamChainToLoopTest {

	@RegisterExtension
	final ConversionContext context = new ConversionContext();

	@TempDir
	Path temporary;

	static final class ConversionContext extends AbstractEclipseJava {
		ConversionContext() {
			super("testresources/rtstubs_22.jar", JavaCore.VERSION_22);
		}

		String convert(String source, String target) throws CoreException {
			ICompilationUnit unit = getSourceFolder().createPackageFragment("test1", false, null)
					.createCompilationUnit("Example.java", source, true, null);
			enable(MYCleanUpConstants.LOOP_CONVERSION_ENABLED);
			set(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, target);
			enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM);
			assertNoCompilationError(unit);
			performRefactoring(new ICompilationUnit[] { unit }, null);
			assertNoCompilationError(unit);
			return unit.getSource();
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
					return result.toString();
				}
				""";
	}

	@ParameterizedTest
	@ValueSource(strings = { "enhanced_for", "iterator_while" })
	void preservesAllTerminalBodyComments(String target) throws Exception {
		String converted = context.convert(source(commentBody()), target);
		for (String comment : new String[] { "before pipeline", "before statement", "beside statement", "after statement" }) {
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
				"items.stream().mapToInt(String::length).forEach(item -> sink.add(String.valueOf(item)));",
				"items.stream().forEach(sink::add);",
				"items.stream().filter(predicate).forEach(item -> sink.add(item));",
				"items.stream() /* keep pipeline comment */ .filter(item -> true).forEach(item -> sink.add(item));",
				"java.util.stream.Stream.of(\"a\").forEach(item -> sink.add(item));")
				.map(statement -> Arguments.of(target, statement)));
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
					%s
				}
				""".formatted(statement));
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

	private String execute(String source, String directory) throws Exception {
		Path output = Files.createDirectories(temporary.resolve(directory));
		Path file = output.resolve("Example.java");
		Files.writeString(file, source);
		var compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "The runtime equivalence tests require a full JDK");
		assertEquals(0, compiler.run(null, null, null, "-d", output.toString(), file.toString()), source);
		try (URLClassLoader loader = new URLClassLoader(new URL[] { output.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
			return (String) loader.loadClass("test1.Example").getMethod("run").invoke(null);
		}
	}
}
