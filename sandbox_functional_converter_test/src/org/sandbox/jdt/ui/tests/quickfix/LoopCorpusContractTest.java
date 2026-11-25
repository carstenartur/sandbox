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
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Native regressions reduced from the complete R4_41 Core/UI cleanup corpus. */
class LoopCorpusContractTest {

	@RegisterExtension
	final ConversionContext context= new ConversionContext();

	@TempDir
	Path temporary;

	static Stream<Arguments> contracts() {
		return Stream.of(
				Arguments.of("conditional collector retains preceding additions", "[plain.color, plain.bold, plain.italic, plain.strike, plain.underline, semantic.color, semantic.bold, semantic.italic, semantic.strike, semantic.underline, semantic.enable]", "", """
						public static String run() {
							ArrayList<String> overlayKeys = new ArrayList<>();
							for (CharSequence item : List.<CharSequence>of("plain", new StringBuilder("semantic"))) {
								overlayKeys.add(item + ".color");
								overlayKeys.add(item + ".bold");
								overlayKeys.add(item + ".italic");
								overlayKeys.add(item + ".strike");
								overlayKeys.add(item + ".underline");
								if (item instanceof StringBuilder)
									overlayKeys.add(item + ".enable");
							}
							return overlayKeys.toString();
						}
						"""),
				Arguments.of("mapped collector retains preceding additions", "[first.color, first.bold, first.italic, first.strike, first.underline, second.color, second.bold, second.italic, second.strike, second.underline]", "", """
						public static String run() {
							String[][] colors = { { "First", "first" }, { "Second", "second" } };
							ArrayList<String> overlayKeys = new ArrayList<>();
							for (String[] color : colors) {
								String colorKey = color[1];
								overlayKeys.add(colorKey + ".color");
								overlayKeys.add(colorKey + ".bold");
								overlayKeys.add(colorKey + ".italic");
								overlayKeys.add(colorKey + ".strike");
								overlayKeys.add(colorKey + ".underline");
							}
							return overlayKeys.toString();
						}
						"""),
				Arguments.of("mapped collector reads the current destination", "[0:a, 1:b]", "", """
						public static String run() {
							ArrayList<String> overlayKeys = new ArrayList<>();
							for (String item : List.of("a", "b")) {
								String colorKey = overlayKeys.size() + ":" + item;
								overlayKeys.add(colorKey);
							}
							return overlayKeys.toString();
						}
						"""),
				Arguments.of("commented collector", "[a, bb]", "", """
						public static String run() {
							List<String> result = new ArrayList<>();
							for (String item : List.of("", "a", "bb")) {
								// Keep the nonempty entries.
								if (!item.isEmpty()) result.add(item);
							}
							return result.toString();
						}
						"""),
				Arguments.of("array initializer map", "true", "", """
						public static String run() { return Boolean.toString(contains()); }
						static boolean contains() {
							for (String item : List.of("a", "b")) {
								String[] singleton = { item };
								if (singleton[0].equals("b")) return true;
							}
							return false;
						}
						"""),
				Arguments.of("array initializer collector", "[[key, a], [key, b]]", "", """
						public static String run() {
							List<String> result = new ArrayList<>();
							for (String item : List.of("a", "b")) {
								String[] pair = { "key", item };
								result.add(Arrays.toString(pair));
							}
							return result.toString();
						}
						"""),
				Arguments.of("nested lambda target type", "[a, b]", "", """
						public static String run() {
							List<String> result = new ArrayList<>();
							for (String item : List.of("a", "b")) {
								Runnable callback = () -> result.add(item);
								callback.run();
							}
							return result.toString();
						}
						"""),
				Arguments.of("anonymous diamond target type", "[!a, !b]", "import java.util.function.Function;\n", """
						public static String run() {
							List<String> result = new ArrayList<>();
							for (String item : List.of("a", "b")) {
								Function<String, String> mapper = new Function<>() {
									@Override public String apply(String prefix) { return prefix + item; }
								};
								result.add(mapper.apply("!"));
							}
							return result.toString();
						}
						"""),
				Arguments.of("imported lambda target type", "[a, b]", "import java.util.function.Consumer;\n", """
						public static String run() {
							List<String> result = new ArrayList<>();
							for (String item : List.of("a", "b")) {
								Consumer<String> callback = suffix -> result.add(item + suffix);
								callback.accept("");
							}
							return result.toString();
						}
						"""),
				Arguments.of("collector observes previous additions", "[a, b]", "", """
						public static String run() {
							ArrayList<String> result = new ArrayList<>();
							for (String item : List.of("a", "a", "b")) {
								if (!result.contains(item)) result.add(item);
							}
							return result.toString();
						}
						"""),
				Arguments.of("self-observing match retains useful conversion", "true", "", """
						public static String run() { return Boolean.toString(filled()); }
						static boolean filled() {
							List<String> result = new ArrayList<>();
							for (String item : List.of("a", "b")) {
								if (!result.contains(item)) result.add(item);
								if (result.size() == 2) return true;
							}
							return false;
						}
						"""),
				Arguments.of("qualified lambda type does not retain a namesake import", "x", "import java.util.function.Consumer;\n", """
						static final StringBuilder seen = new StringBuilder();
						static class Sink { interface Consumer { void accept(); } }
						static List<java.util.function.Consumer<String>> consumers() {
							return List.of(value -> seen.append(value));
						}
						public static String run() {
							for (Consumer<String> consumer : consumers()) {
								Sink.Consumer callback = () -> consumer.accept("x");
								callback.accept();
							}
							return seen.toString();
						}
						"""),
				Arguments.of("nested continue", "[a, bb]", "", """
						public static String run() {
							List<String> result = new ArrayList<>();
							for (String item : List.of("", "a", "bb")) {
								if (item.length() < 2) {
									if (item.isEmpty()) continue;
								}
								result.add(item);
							}
							return result.toString();
						}
						"""),
				Arguments.of("primitive element unboxed once", "2040", "", """
						public static String run() {
							StringBuilder result = new StringBuilder();
							for (int value : List.of(Integer.valueOf(1), Integer.valueOf(2))) {
								result.append(value + value).append(value - value);
							}
							return result.toString();
						}
						"""),
				Arguments.of("negated boolean match", "true", "", """
						public static String run() { return Boolean.toString(changed()); }
						static boolean changed() {
							Set<String> remaining = new HashSet<>(List.of("a", "b"));
							for (String item : List.of("a", "a")) {
								// Preserve the primitive flag and its comment.
								boolean found = remaining.remove(item);
								if (!found) return true;
							}
							return false;
						}
						"""),
				Arguments.of("primitive widening preserves overload", "long:3", "", """
						static String label(long value) { return "long:" + value; }
						static String label(Integer value) { return "boxed:" + value; }
						public static String run() {
							StringBuilder result = new StringBuilder();
							for (long value : List.of(Integer.valueOf(3))) result.append(label(value));
							return result.toString();
						}
						"""),
				Arguments.of("long to double preserves widening", "double:9.007199254740992E15", "", """
						static String label(double value) { return "double:" + value; }
						static String label(Long value) { return "boxed:" + value; }
						public static String run() {
							StringBuilder result = new StringBuilder();
							for (double value : List.of(Long.valueOf(9007199254740993L))) result.append(label(value));
							return result.toString();
						}
						"""),
				Arguments.of("character to int preserves widening", "65", "", """
						public static String run() {
							StringBuilder result = new StringBuilder();
							for (int value : List.of(Character.valueOf('A'))) result.append(value);
							return result.toString();
						}
						"""),
				Arguments.of("unused primitive element still checks null", "null", "", """
						public static String run() {
							StringBuilder result = new StringBuilder();
							try {
								for (int ignored : Arrays.asList((Integer) null)) result.append("entered");
							} catch (NullPointerException expected) {
								result.append("null");
							}
							return result.toString();
						}
						"""),
				Arguments.of("primitive iterable retains stream source import", "12", "", """
						public static String run() {
							Iterable<Integer> values = List.of(Integer.valueOf(1), Integer.valueOf(2));
							StringBuilder result = new StringBuilder();
							for (int value : values) result.append(value);
							return result.toString();
						}
						"""),
				Arguments.of("counted primitive element still checks null", "null", "", """
						public static String run() {
							try {
								long count = 0L;
								for (long ignored : Arrays.asList((Long) null)) count++;
								return Long.toString(count);
							} catch (NullPointerException expected) {
								return "null";
							}
						}
						"""),
				Arguments.of("primitive iterable avoids stream source name collision", "12", "", """
						static class StreamSupport { }
						public static String run() {
							Iterable<Integer> values = List.of(Integer.valueOf(1), Integer.valueOf(2));
							StringBuilder result = new StringBuilder();
							for (int value : values) result.append(value);
							return result.toString();
						}
						"""),
				Arguments.of("reference iterable collect fallback needs no stream import", "[a, b]", "", """
						static void fill(Iterable<String> values, List<String> result) {
							for (String value : values) result.add(value);
						}
						public static String run() {
							List<String> result = new ArrayList<>();
							fill(List.of("a", "b"), result);
							return result.toString();
						}
						"""),
				Arguments.of("compared boolean match", "true", "", """
						public static String run() { return Boolean.toString(contains()); }
						static boolean contains() {
							for (String item : List.of("", "a")) {
								if (!item.isEmpty()) {
									boolean found = item.startsWith("a");
									if (found == true) return true;
								}
							}
							return false;
						}
						"""),
				Arguments.of("iterator element import", "ab", "import java.util.Map.Entry;\n", """
						public static String run() {
							Map<String, String> values = new LinkedHashMap<>();
							values.put("a", "A");
							values.put("b", "B");
							StringBuilder result = new StringBuilder();
							Iterator<Map.Entry<String, String>> iterator = values.entrySet().iterator();
							while (iterator.hasNext()) {
								Entry<String, String> entry = iterator.next();
								result.append(entry.getKey());
							}
							return result.toString();
						}
						"""));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("contracts")
	void preservesDiagnosticsExecutionAndUndo(String name, String expected, String imports, String body) throws Exception {
		String original= "package test1;\nimport java.util.*;\n" + imports + "public class Example {\n" + body + "}\n";
		assertEquals(expected, StreamChainToLoopTest.executeSource(original, temporary.resolve("before")), name);
		String converted= context.convert(original);
		assertNotEquals(original, converted, "The useful loop conversion must remain available: " + name);
		original.lines().map(String::strip).filter(line -> line.startsWith("//"))
				.forEach(comment -> assertTrue(converted.contains(comment), converted));
		assertEquals(expected, StreamChainToLoopTest.executeSource(converted, temporary.resolve("after")), converted);
	}

	static Stream<Arguments> collectorBindingControls() {
		return Stream.of(
				Arguments.of("same-spelled field", "Shadow.overlayKeys.get(0) + item"),
				Arguments.of("shadowed local in an anonymous method", """
						new Shadow() {
							String map(String value) {
								List<String> overlayKeys = List.of("prefix:");
								return overlayKeys.get(0) + value;
							}
						}.map(item)
						"""));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("collectorBindingControls")
	void distinctBindingsKeepCollectionConversion(String name, String initializer) throws Exception {
		String original= """
				package test1;
				import java.util.*;
				public class Example {
					static class Shadow { static final List<String> overlayKeys = List.of("prefix:"); }
					public static String run() {
						ArrayList<String> overlayKeys = new ArrayList<>();
						for (String item : List.of("a", "b")) {
							String value = MAPPED_VALUE;
							overlayKeys.add(value);
						}
						return overlayKeys.toString();
					}
				}
				""".replace("MAPPED_VALUE", initializer);
		String expected= "[prefix:a, prefix:b]";
		assertEquals(expected, StreamChainToLoopTest.executeSource(original, temporary.resolve("before")), name);
		String converted= context.convert(original);
		assertTrue(converted.contains(".collect("), "The independent destination must remain a collector: " + converted);
		assertEquals(expected, StreamChainToLoopTest.executeSource(converted, temporary.resolve("after")), converted);
	}

	@Test
	void preservesExistingIdentityComparisonMarkerOnMatch() throws CoreException {
		context.getJavaProject().setOption(CompilerOptions.OPTION_ReportUninternedIdentityComparison, JavaCore.ENABLED);
		context.getSourceFolder().createPackageFragment("org.eclipse.jdt.internal.compiler.lookup", false, null)
				.createCompilationUnit("TypeBinding.java", "package org.eclipse.jdt.internal.compiler.lookup; public class TypeBinding {}", false, null);
		String original= """
				package test1;
				import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
				public class Example {
					public boolean contains(TypeBinding[] values, TypeBinding wanted) {
						for (TypeBinding value : values) {
							if (value == wanted) { //$IDENTITY-COMPARISON$
								return true;
							}
						}
						return false;
					}
				}
				""";
		String converted= context.convert(original);
		assertNotEquals(original, converted);
		assertTrue(converted.contains("$IDENTITY-COMPARISON$"), converted);
	}

	private static final class ConversionContext extends AbstractEclipseJava {
		ConversionContext() {
			super("testresources/rtstubs_22.jar", JavaCore.VERSION_22);
		}

		String convert(String source) throws CoreException {
			ICompilationUnit unit= getSourceFolder().createPackageFragment("test1", false, null)
					.createCompilationUnit("Example.java", source, true, null);
			enable(MYCleanUpConstants.LOOP_CONVERSION_ENABLED);
			set(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, "stream");
			enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR);
			enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE);
			assertNoCompilationError(unit);
			var diagnostics= CompilerDiagnosticAssertions.capture(unit);
			performRefactoring(new ICompilationUnit[] { unit }, null);
			String converted= unit.getSource();
			RefactoringCore.getUndoManager().performUndo(null, null);
			assertEquals(source, unit.getSource(), "Undo must restore the exact compilation unit");
			diagnostics.assertRestored();
			return converted;
		}
	}
}
