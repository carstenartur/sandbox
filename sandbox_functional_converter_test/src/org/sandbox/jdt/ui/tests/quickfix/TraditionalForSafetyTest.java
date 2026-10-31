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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.text.correction.AssistContext;
import org.eclipse.jdt.internal.ui.text.correction.proposals.FixCorrectionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.ltk.core.refactoring.Change;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix.helper.LoopTargetFormat;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.LoopConversionQuickAssistProcessor;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Compiles and executes the real cleanup output, including observable condition checks. */
@SuppressWarnings("nls")
class TraditionalForSafetyTest {

	@RegisterExtension
	final ConversionContext context = new ConversionContext();

	@TempDir
	Path temporary;

	static final class ConversionContext extends AbstractEclipseJava {
		ConversionContext() {
			// Numeric admission needs real constant values; rtstubs defines MAX_VALUE as zero.
			super(Path.of(System.getProperty("java.home"), "lib", "jrt-fs.jar").toString(), JavaCore.VERSION_22);
		}

		ICompilationUnit unit(String source) throws CoreException {
			ICompilationUnit unit = getSourceFolder().createPackageFragment("test1", false, null)
					.createCompilationUnit("Example.java", source, true, null);
			assertNoCompilationError(unit);
			return unit;
		}

		String convert(String source) throws CoreException {
			ICompilationUnit unit = unit(source);
			performRefactoring(new ICompilationUnit[] { unit }, null);
			assertNoCompilationError(unit);
			return unit.getSource();
		}
	}

	@BeforeEach
	void selectClassicForConversion() throws CoreException {
		context.disable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.disable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2);
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_ENABLED);
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_CLASSIC_FOR);
		context.disable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR);
		context.disable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE);
		context.disable(MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM);
		context.set(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, "stream");
	}

	static Stream<Arguments> unsafeBounds() {
		return Stream.of(
				Arguments.of("inclusive maximum wraps before the second body call", """
						public static String run() {
							List<Integer> seen = new ArrayList<>();
							try {
								for (int i = Integer.MAX_VALUE; i <= Integer.MAX_VALUE; i++) {
									seen.add(i);
									if (seen.size() == 2) throw new IllegalStateException("stop");
								}
							} catch (IllegalStateException expected) { return seen + ":stop"; }
							return seen + ":normal";
						}
						""", "[2147483647, -2147483648]:stop"),
				Arguments.of("runtime inclusive maximum", """
						public static String run() { return visit(Integer.MAX_VALUE); }
						static String visit(int end) {
							List<Integer> seen = new ArrayList<>();
							try {
								for (int i = Integer.MAX_VALUE; i <= end; i++) {
									seen.add(i);
									if (seen.size() == 1) throw new IllegalStateException("stop");
								}
							} catch (IllegalStateException expected) { return seen + ":stop"; }
							return seen + ":normal";
						}
						""", "[2147483647]:stop"),
				Arguments.of("method bound is evaluated before every iteration and once after", """
						static final List<String> trace = new ArrayList<>();
						static int end() { trace.add("bound"); return 2; }
						public static String run() {
							for (int i = 0; i < end(); i++) trace.add(Integer.toString(i));
							return trace.toString();
						}
						""", "[bound, 0, bound, 1, bound]"),
				Arguments.of("last condition can throw", """
						static int checks;
						static int end() {
							if (++checks == 3) throw new IllegalStateException("last check");
							return 2;
						}
						public static String run() {
							List<Integer> seen = new ArrayList<>();
							try {
								for (int i = 0; i < end(); i++) seen.add(i);
							} catch (IllegalStateException expected) { return seen + ":" + checks; }
							return "normal:" + checks;
						}
						""", "[0, 1]:3"),
				Arguments.of("mutable field bound", """
						static int end = 3;
						public static String run() {
							List<Integer> seen = new ArrayList<>();
							for (int i = 0; i < end; i++) { seen.add(i); end--; }
							return seen.toString();
						}
						""", "[0, 1]"),
				Arguments.of("mutable array element bound", """
						public static String run() {
							int[] end = { 3 };
							List<Integer> seen = new ArrayList<>();
							for (int i = 0; i < end[0]; i++) { seen.add(i); end[0]--; }
							return seen.toString();
						}
						""", "[0, 1]"),
				Arguments.of("collection size changes in the body", """
						public static String run() {
							List<Integer> items = new ArrayList<>(Arrays.asList(1, 2, 3));
							List<Integer> seen = new ArrayList<>();
							for (int i = 0; i < items.size(); i++) seen.add(items.remove(0));
							return seen.toString();
						}
						""", "[1, 2]"),
				Arguments.of("mutable local bound", """
						public static String run() {
							int end = 3;
							List<Integer> seen = new ArrayList<>();
							for (int i = 0; i < end; i++) { seen.add(i); end--; }
							return seen.toString();
						}
						""", "[0, 1]"),
				Arguments.of("counter-dependent bound", """
						public static String run() {
							List<Integer> seen = new ArrayList<>();
							for (int i = 0; i < 3 - i; i++) seen.add(i);
							return seen.toString();
						}
						""", "[0, 1]"),
				Arguments.of("body changes the loop counter", """
						public static String run() {
							List<Integer> seen = new ArrayList<>();
							for (int i = 0; i < 4; i++) { seen.add(i); i++; }
							return seen.toString();
						}
						""", "[0, 2]"),
				Arguments.of("body writes a captured local", """
						public static String run() {
							int sum = 0;
							for (int i = 0; i < 4; i++) sum += i;
							return Integer.toString(sum);
						}
						""", "6"),
				Arguments.of("widened condition permits counter wrap", """
						public static String run() {
							List<Integer> seen = new ArrayList<>();
							try {
								for (int i = Integer.MAX_VALUE; i < 2147483648L; i++) {
									seen.add(i);
									if (seen.size() == 1) throw new IllegalStateException("stop");
								}
							} catch (IllegalStateException expected) { return seen + ":stop"; }
							return seen + ":normal";
						}
						""", "[2147483647]:stop"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("unsafeBounds")
	void unsafeLoopPreservesJavaSemantics(String reason, String body, String expected) throws Exception {
		String original = source(body);
		assertEquals(expected, execute(original, "original"), reason);
		String converted = context.convert(original);
		assertEquals(expected, execute(converted, "converted"), reason + "\n" + converted);
		assertEquals(original, converted, "No finite range has been proved for " + reason);
		ICompilationUnit unit = context.unit(original);
		assertFalse(new LoopConversionQuickAssistProcessor().hasAssists(invocation(unit, original)), reason);
	}

	static Stream<Arguments> safeBounds() {
		return Stream.of(
				Arguments.of("0", "< 3", "", "[0, 1, 2]"),
				Arguments.of("0", "<= 2", "", "[0, 1, 2]"),
				Arguments.of("3", "< 2", "", "[]"),
				Arguments.of("Integer.MAX_VALUE - 1", "< Integer.MAX_VALUE", "", "[2147483646]"),
				Arguments.of("Integer.MAX_VALUE - 2", "<= Integer.MAX_VALUE - 1", "", "[2147483645, 2147483646]"),
				Arguments.of("Integer.MIN_VALUE", "<= Integer.MIN_VALUE", "", "[-2147483648]"),
				Arguments.of("0", "< end", "int end = 3;", "[0, 1, 2]"),
				Arguments.of("0", "<= end", "short end = 2;", "[0, 1, 2]"),
				Arguments.of("0", "< end + 1", "int end = 2;", "[0, 1, 2]"),
				Arguments.of("0", "< values.length", "int[] values = { 4, 5, 6 };", "[0, 1, 2]"));
	}

	@ParameterizedTest
	@MethodSource("safeBounds")
	void provenFiniteRangesStillConvertAndExecute(String start, String condition, String setup, String expected) throws Exception {
		String original = source("public static String run() { " + setup
				+ " List<Integer> seen = new ArrayList<>(); for (int i = " + start + "; i " + condition
				+ "; i++) seen.add(i); return seen.toString(); }");
		String converted = context.convert(original);
		assertNotEquals(original, converted);
		assertTrue(converted.contains("IntStream.range("), converted);
		assertEquals(expected, execute(original, "original"));
		assertEquals(expected, execute(converted, "converted"));
		assertEquals(converted, context.convert(converted), "Repeated cleanup is stable");
	}

	@Test
	void initializerRunsOnceBeforeTheFirstCondition() throws Exception {
		String original = source("""
				static final List<String> trace = new ArrayList<>();
				static int start(int from, int ignored) { trace.add("start"); return from; }
				public static String run() {
					for (int i = start(1, 7); i < 3; i++) trace.add(Integer.toString(i));
					return trace.toString();
				}
				""");
		String converted = assertDoesNotThrow(() -> context.convert(original));
		assertNotEquals(original, converted);
		assertEquals("[start, 1, 2]", execute(converted, "converted"));
	}

	@Test
	void previewPreservesBodyCommentsAndUndoRestoresTheOriginal() throws Exception {
		String original = source("""
				public static String run() {
					List<Integer> seen = new ArrayList<>();
					// retain the loop's leading comment
					for (int i = 0; i < 3; i++) {
						// retain the body comment
						seen.add(i); /* retain the trailing comment */
					}
					return seen.toString();
				}
				""");
		ICompilationUnit unit = context.unit(original);
		unit.becomeWorkingCopy(null);
		try {
			AssistContext invocation = invocation(unit, original);
			var proposal = (FixCorrectionProposal) Arrays.stream(new LoopConversionQuickAssistProcessor()
					.getAssists(invocation, new IProblemLocation[0]))
					.filter(item -> item.getDisplayString().equals(LoopConversionQuickAssistProcessor.label(LoopTargetFormat.STREAM)))
					.findFirst().orElseThrow();
			String preview = proposal.getPreviewContent();
			assertEquals(original, unit.getSource(), "Preview must not alter the source buffer");
			assertTrue(preview.contains("// retain the loop's leading comment"), preview);
			assertTrue(preview.contains("// retain the body comment"), preview);
			assertTrue(preview.contains("/* retain the trailing comment */"), preview);
			assertEquals("[0, 1, 2]", execute(preview, "preview"));
			var change = proposal.getTextChange();
			change.initializeValidationData(null);
			assertFalse(change.isValid(null).hasFatalError());
			Change undo = change.perform(null);
			try {
				assertEquals(preview, unit.getSource());
				assertNotNull(undo);
				undo.initializeValidationData(null);
				Change redo = undo.perform(null);
				if (redo != null) redo.dispose();
				assertEquals(original, unit.getSource());
			} finally {
				change.dispose();
				if (undo != null) undo.dispose();
			}
		} finally {
			unit.discardWorkingCopy();
		}
	}

	@Test
	void headerCommentsRemainInPlaceWhenNoCorrespondingStreamLocationExists() throws Exception {
		String original = source("""
				public static String run() {
					List<Integer> seen = new ArrayList<>();
					for (int /* counter documentation */ i = 0; i < 3; i++) seen.add(i);
					return seen.toString();
				}
				""");
		assertEquals(original, context.convert(original));
	}

	@Test
	void unusedObjectParameterDoesNotCallToString() throws Exception {
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR);
		String original = source("""
				static class Value {
					@Override public String toString() { throw new IllegalStateException("unwanted toString"); }
				}
				public static String run() {
					List<String> seen = new ArrayList<>();
					for (Object value : Arrays.asList(new Value(), new Value())) seen.add("body");
					return seen.toString();
				}
				""");
		String converted = context.convert(original);
		assertNotEquals(original, converted);
		assertEquals("[body, body]", execute(original, "original"));
		assertEquals("[body, body]", execute(converted, "converted"));
	}

	private static final String CHECKED_METHOD = """
			static void checked(int i) throws java.io.IOException {
				if (i == 1) throw new java.io.IOException("checked");
			}
			""";

	private static final String CHECKED_RESOURCE = """
			static class Resource implements AutoCloseable {
				@Override public void close() throws java.io.IOException { throw new java.io.IOException("close"); }
			}
			""";

	static Stream<Arguments> escapingCheckedExceptions() {
		return Stream.of(
				Arguments.of("Thread.sleep(0);", "", "[0, 1]:normal"),
				Arguments.of("checked(i);", CHECKED_METHOD, "[0]:caught"),
				Arguments.of("super.clone();", "", "[]:caught"),
				Arguments.of("if (i == 1) throw new java.io.IOException();", "", "[0]:caught"),
				Arguments.of("new Checked(i);", """
						static class Checked {
							Checked(int i) throws java.io.IOException { if (i == 1) throw new java.io.IOException(); }
						}
						""", "[0]:caught"),
				Arguments.of("try (Resource resource = new Resource()) { seen.add(7); }", CHECKED_RESOURCE, "[7]:caught"),
				Arguments.of("try (var resource = new Resource()) { seen.add(7); }", CHECKED_RESOURCE, "[7]:caught"),
				Arguments.of("Resource resource = new Resource(); try (resource) { seen.add(7); }", CHECKED_RESOURCE, "[7]:caught"));
	}

	@ParameterizedTest
	@MethodSource("escapingCheckedExceptions")
	void checkedExceptionsStayInTheirOriginalMethodContext(String statement, String members, String expected) throws Exception {
		String original = exceptionSource(statement, members);
		assertEquals(expected, execute(original, "original"));
		String converted = context.convert(original);
		assertEquals(original, converted, "IntConsumer cannot propagate checked exceptions from " + statement);
		assertEquals(expected, execute(converted, "converted"));
		assertFalse(new LoopConversionQuickAssistProcessor().hasAssists(invocation(context.unit(original), original)));
	}

	static Stream<Arguments> handledExceptions() {
		return Stream.of(
				Arguments.of("try { checked(i); } catch (java.io.IOException | IllegalArgumentException expected) { seen.add(-1); }",
						CHECKED_METHOD, "[0, -1, 1]:normal"),
				Arguments.of("try (var resource = new Resource()) { seen.add(7); } catch (java.io.IOException expected) { seen.add(-1); }",
						CHECKED_RESOURCE, "[7, -1, 0, 7, -1, 1]:normal"),
				Arguments.of("if (i == 1) throw new IllegalStateException();", "", "[0]:caught"),
				Arguments.of("if (i == 1) throw new AssertionError();", "", "[0]:caught"));
	}

	@ParameterizedTest
	@MethodSource("handledExceptions")
	void caughtAndUncheckedExceptionsStillConvert(String statement, String members, String expected) throws Exception {
		String original = exceptionSource(statement, members);
		String converted = context.convert(original);
		assertNotEquals(original, converted);
		assertEquals(expected, execute(original, "original"));
		assertEquals(expected, execute(converted, "converted"));
	}

	@Test
	void checkedInitializerRemainsOutsideTheLambda() throws Exception {
		String original = source("""
				static int start() throws java.io.IOException { throw new java.io.IOException("start"); }
				public static String run() {
					List<Integer> seen = new ArrayList<>();
					try {
						for (int i = start(); i < 3; i++) seen.add(i);
					} catch (java.io.IOException expected) { return seen + ":start"; }
					return "normal";
				}
				""");
		String converted = context.convert(original);
		assertNotEquals(original, converted);
		assertEquals("[]:start", execute(original, "original"));
		assertEquals("[]:start", execute(converted, "converted"));
	}

	static Stream<Arguments> streamFactoryNames() {
		return Stream.of(
				Arguments.of("int IntStream = 0;", "", true),
				Arguments.of("", "static final Object IntStream = new Object();", true),
				Arguments.of("class IntStream {}", "", true),
				Arguments.of("int java = 0;", "", true),
				Arguments.of("int IntStream = 0; int java = 0;", "", false),
				Arguments.of("int java = 0;", "static class IntStream {}", false),
				Arguments.of("int IntStream = 0;", "static class java {}", false));
	}

	@ParameterizedTest
	@MethodSource("streamFactoryNames")
	void streamFactoryMustResolveToTheJdkType(String locals, String members, boolean convertible) throws Exception {
		String original = source("public static String run() { " + locals + "\n"
				+ "List<Integer> seen = new ArrayList<>();\n"
				+ "for (int i = 0; i < 2; i++) seen.add(i);\nreturn seen.toString(); }\n" + members);
		assertEquals("[0, 1]", execute(original, "original"));
		String converted = context.convert(original);
		if (convertible) {
			assertNotEquals(original, converted);
		} else {
			assertEquals(original, converted, "Both the simple type and its package qualifier are obscured");
			assertFalse(new LoopConversionQuickAssistProcessor().hasAssists(invocation(context.unit(original), original)));
		}
		assertEquals("[0, 1]", execute(converted, "converted"));
	}

	private static String exceptionSource(String statement, String members) {
		return source("public static String run() { return new Example().visit(); }\n"
				+ "private String visit() { List<Integer> seen = new ArrayList<>(); try {\n"
				+ "for (int i = 0; i < 2; i++) { " + statement + " seen.add(i); }\n"
				+ "} catch (Throwable expected) { return seen + \":caught\"; } return seen + \":normal\"; }\n" + members);
	}

	private static String source(String body) {
		return "package test1;\nimport java.util.*;\npublic class Example {\n" + body + "\n}\n";
	}

	private static AssistContext invocation(ICompilationUnit unit, String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		AssistContext invocation = new AssistContext(unit, source.indexOf("for (int"), 0);
		invocation.setASTRoot((CompilationUnit) parser.createAST(null));
		return invocation;
	}

	private String execute(String source, String directory) throws Exception {
		return StreamChainToLoopTest.executeSource(source, temporary.resolve(directory));
	}
}
