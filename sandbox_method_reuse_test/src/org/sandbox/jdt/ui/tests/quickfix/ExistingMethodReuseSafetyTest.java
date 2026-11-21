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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jface.text.Document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.sandbox.jdt.internal.corext.fix.MethodReuseCleanUpFixCore;

/** Exercises the real reuse operations on complete, binding-resolved units without a workbench. */
public class ExistingMethodReuseSafetyTest {

	static Stream<Arguments> unsafeMatches() {
		return Stream.of(
				Arguments.of("different method bindings", """
					public final class Example {
						public int width(String value) { return value.indexOf('x'); }
						public int height(String value) { return value.lastIndexOf('x'); }
					}
					"""),
				Arguments.of("different field bindings and arbitrary expressions", """
					public final class Example {
						private String name = "name";
						private String description = "description";
						public String name() { return name; }
						public String description() { return description; }
						public String computed() { return name.trim(); }
						public int length() { return name.length(); }
					}
					"""),
				Arguments.of("unavailable nested receiver", """
					public final class Example {
						public String value(String text) { return text.trim(); }
						public static class Nested {
							public String value(String text) { return text.trim(); }
						}
					}
					"""),
				Arguments.of("instance method in static context", """
					public final class Example {
						public String trim(String text) { return text.trim(); }
						public static void print(String text) {
							String result = text.trim();
							System.out.println(result);
						}
					}
					"""),
				Arguments.of("unmapped unused parameter", """
					public final class Example {
						public int increment(int unused, int input) { return input + 1; }
						public void print(int value) {
							int result = value + 1;
							System.out.println(result);
						}
					}
					"""),
				Arguments.of("incompatible declared return type", """
					public final class Example {
						public Object trim(String text) { return text.trim(); }
						public void print(String text) {
							String result = text.trim();
							System.out.println(result);
						}
					}
					"""),
				Arguments.of("new checked exception", """
					public final class Example {
						public String trim(String text) throws java.io.IOException { return text.trim(); }
						public void print(String text) {
							String result = text.trim();
							System.out.println(result);
						}
					}
					"""),
				Arguments.of("local escapes the removed sequence", """
					public final class Example {
						public void print(String input) {
							String local = input.trim();
							System.out.println(local);
						}
						public void copy(String value) {
							String result = value.trim();
							System.out.println(result);
							System.out.println(result.length());
						}
					}
					"""),
				Arguments.of("last local declaration escapes a void sequence", """
					public final class Example {
						public void unused(String input) { String local = input.trim(); }
						public void copy(String value) {
							String result = value.trim();
							System.out.println(result);
						}
					}
					"""),
				Arguments.of("caller input is assigned", """
					public final class Example {
						public void print(int input) { input++; System.out.println(input); }
						public void copy(int value) {
							value++;
							System.out.println(value);
							System.out.println(value + 1);
						}
					}
					"""),
				Arguments.of("repeated parameter cannot collapse different calls", """
					public final class Example {
						public int twice(int input) { return input + input; }
						public void print() {
							int result = Integer.parseInt("1") + Integer.parseInt("2");
							System.out.println(result);
						}
					}
					"""),
				Arguments.of("overload binding differs despite identical name", """
					public final class Example {
						public int length(String input) { return input.indexOf(1); }
						public void print(String input) {
							int result = input.indexOf("1");
							System.out.println(result);
						}
					}
					"""),
				Arguments.of("parameter types cannot change overload resolution", """
					public final class Example {
						public Object identity(Object input) { return input; }
						public void print(String input) {
							String result = input;
							System.out.println(result);
						}
					}
					"""),
				Arguments.of("overridable helper would introduce dynamic dispatch", """
					public class Example {
						public String trim(String text) { return text.trim(); }
						public void print(String text) {
							String result = text.trim();
							System.out.println(result);
						}
					}
					class Child extends Example {
						@Override public String trim(String text) { return "changed"; }
					}
					"""),
				Arguments.of("nonreturning method cannot become a plain expression", """
					public final class Example {
						public int fail() { throw new IllegalStateException(); }
						public int alsoFail() { throw new IllegalStateException(); }
					}
					"""),
				Arguments.of("conditional return would exit only the callee", """
					public final class Example {
						public void print(String text) { if (text == null) return; System.out.println(text); }
						public void copy(String text) {
							if (text == null) return;
							System.out.println(text);
							System.out.println(text.length());
						}
					}
					"""));
	}

	@ParameterizedTest(name= "{0}")
	@MethodSource("unsafeMatches")
	void rejectsSemanticallyDifferentOrUnavailableCalls(String reason, String source) throws Exception {
		assertEquals(source, rewrite(source), reason);
	}

	@ParameterizedTest
	@ValueSource(strings= { "(value)++", "++(value)", "((value)) = 3" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	void preservesParenthesizedWritesToCallerInputs(String write) throws Exception {
		String source= """
				public final class Example {
					public void print(int value) { %1$s; System.out.println(value); }
					public void copy(int value) {
						%1$s;
						System.out.println(value);
						System.out.println(value + 1);
					}
				}
				""".formatted(write);
		assertEquals(source, rewrite(source));
	}

	@Test
	void preservesPrimitiveReturnAndKeepsTheReusedBodyToAvoidMutualRecursion() throws Exception {
		String source= """
				public final class Example {
					public int first(int value) { int result = value + 1; return result * 2; }
					public int second(int input) { int result = input + 1; return result * 2; }
				}
				""";
		assertSource("""
				public final class Example {
					public int first(int value) { int result = value + 1; return result * 2; }
					public int second(int input) { return first(input); }
				}
				""", rewrite(source));
	}

	@Test
	void preservesDeclarationModifiersAndFragmentArrayDimensions() throws Exception {
		String source= """
				public final class Example {
					public static String[] split(String input) { return input.split(","); }
					public static String[] copy(String input) {
						final String result[] = input.split(",");
						return result;
					}
				}
				""";
		assertSource("""
				public final class Example {
					public static String[] split(String input) { return input.split(","); }
					public static String[] copy(String input) {
						final String result[] = split(input);
						return result;
					}
				}
				""", rewrite(source));
	}

	@Test
	void retainsSafeLocalRenamingAndTheBoundReceiver() throws Exception {
		String source= """
				public final class Example {
					private String suffix = "!";
					public String format(String text) { return text.trim() + suffix; }
					public void print(String input) {
						String result = input.trim() + suffix;
						System.out.println(result);
					}
				}
				""";
		assertSource("""
				public final class Example {
					private String suffix = "!";
					public String format(String text) { return text.trim() + suffix; }
					public void print(String input) {
						String result = format(input);
						System.out.println(result);
					}
				}
				""", rewrite(source));
	}

	@Test
	void retainsMultipleLocalMappingsAndConstantArguments() throws Exception {
		String source= """
				public final class Example {
					public int calculate(int x, int y) { int temp = x + y; return temp * 2; }
					public void compute(int a, int b) {
						int result = a + b;
						int finalValue = result * 2;
						System.out.println(finalValue);
					}
					public String format(String first, String last) { return first.trim() + last.trim(); }
					public void print() {
						String name = "first".trim() + "last".trim();
						System.out.println(name);
					}
				}
				""";
		String expected= source.replace("int result = a + b;\n\t\tint finalValue = result * 2;", //$NON-NLS-1$
				"int finalValue = calculate(a, b);") //$NON-NLS-1$
				.replace("\"first\".trim() + \"last\".trim()", "format(\"first\", \"last\")"); //$NON-NLS-1$ //$NON-NLS-2$
		assertSource(expected, rewrite(source));
	}

	private static String rewrite(String source) throws Exception {
		Map<String, String> options= new HashMap<>(JavaCore.getOptions());
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		options.replaceAll((key, severity) -> key.startsWith(JavaCore.PLUGIN_ID + ".compiler.problem.") //$NON-NLS-1$
				&& (JavaCore.IGNORE.equals(severity) || JavaCore.INFO.equals(severity)) ? JavaCore.WARNING : severity);
		CompilationUnit before= parse(source, options);
		assertFalse(Arrays.stream(before.getProblems()).anyMatch(IProblem::isError),
				() -> Arrays.toString(before.getProblems()));
		var operations= new LinkedHashSet<CompilationUnitRewriteOperation>();
		MethodReuseCleanUpFixCore.INLINE_SEQUENCES.findOperations(before, operations, new HashSet<>());
		CompilationUnitRewrite rewrite= new CompilationUnitRewrite(null, before);
		for (CompilationUnitRewriteOperation operation : operations) {
			operation.rewriteAST(rewrite, new LinkedProposalModelCore());
		}
		Document document= new Document(source);
		rewrite.getASTRewrite().rewriteAST(document, options).apply(document);
		String result= document.get();
		Map<String, Long> baseline= diagnostics(before);
		diagnostics(parse(result, options)).forEach((problem, count) -> assertTrue(
				count <= baseline.getOrDefault(problem, 0L), () -> "New diagnostic " + problem + "\n" + result)); //$NON-NLS-1$ //$NON-NLS-2$
		return result;
	}

	private static CompilationUnit parse(String source, Map<String, String> options) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(source.toCharArray());
		parser.setUnitName("Example.java"); //$NON-NLS-1$
		parser.setEnvironment(null, null, null, true);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}

	private static Map<String, Long> diagnostics(CompilationUnit unit) {
		return Arrays.stream(unit.getProblems()).filter(problem -> problem.isError() || problem.isWarning())
				.collect(Collectors.groupingBy(problem -> problem.isError() + ":" + problem.getID() //$NON-NLS-1$
						+ Arrays.toString(problem.getArguments()), Collectors.counting()));
	}

	private static void assertSource(String expected, String actual) {
		assertEquals(expected.replaceAll("\\s+", ""), actual.replaceAll("\\s+", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}
}
