/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.triggerpattern.cleanup.NlsAwareCleanUpFix;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.JUnitMigrationFixtureClasspath;

/** Legacy assertion evaluation and primitive overloads from the pinned tests. */
class JUnit3BooleanAssertionTest {

	@RegisterExtension
	final EclipseJava17 context= new EclipseJava17();

	@TempDir
	Path temporary;

	static Stream<Arguments> literalExpectations() {
		return Stream.of(
				Arguments.of(true, true, "", ""), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of(true, false, "", "junit.framework.TestCase."), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of(false, false, "", "junit.framework.Assert."), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of(false, true, "", ""), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of(true, true, "\"marker\", ", ""), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of(true, false, "\"marker\", ", "junit.framework.Assert."), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of(false, false, "\"mark\" + \"er\", ", ""), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of(false, true, "(String) \"marker\", ", "")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@MethodSource("literalExpectations") //$NON-NLS-1$
	void primitiveBooleanEqualityPreservesDiagnosticsAndExecution(boolean expected, boolean actual,
			String message, String qualifier) throws Exception {
		String original= source(qualifier + "assertEquals(" + message + expected + ", this.next());", //$NON-NLS-1$ //$NON-NLS-2$
				actual, ""); //$NON-NLS-1$
		ICompilationUnit unit= unit(original);
		var originalBinding= assertion(parse(unit), "assertEquals").resolveMethodBinding(); //$NON-NLS-1$
		assertTrue(Set.of("junit.framework.Assert", "junit.framework.TestCase") //$NON-NLS-1$ //$NON-NLS-2$
				.contains(originalBinding.getDeclaringClass().getQualifiedName()));
		assertEquals(message.isEmpty() ? List.of("boolean", "boolean") //$NON-NLS-1$ //$NON-NLS-2$
				: List.of("java.lang.String", "boolean", "boolean"), parameterTypes(originalBinding)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Outcome before= execute(original, unit);
		String generated= applyAndUndo(unit, true, expected ? "assertTrue" : "assertFalse", //$NON-NLS-1$ //$NON-NLS-2$
				"org.junit.jupiter.api.Assertions", message.isEmpty() //$NON-NLS-1$
						? List.of("boolean") : List.of("boolean", "java.lang.String")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Outcome after= execute(generated, unit);
		assertEquals(expected != actual, before.failed());
		assertEquals(before.failed(), after.failed());
		assertEquals(1, before.calls());
		assertEquals(before.calls(), after.calls());
		assertEquals("actual;", before.events()); //$NON-NLS-1$
		assertEquals(before.events(), after.events());
		if (before.failed() && !message.isEmpty()) {
			assertTrue(before.message().contains("marker"), before.message()); //$NON-NLS-1$
			assertTrue(after.message().contains("marker"), after.message()); //$NON-NLS-1$
		}
	}

	static Stream<Arguments> evaluationSensitiveCalls() {
		return Stream.of(
				Arguments.of("assertEquals(this.expected(), this.next());", "expected;actual;"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertEquals(this.message(), true, this.next());", "message;actual;"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertEquals(this.events, true, this.next());", "actual;"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("assertEquals(null, true, this.next());", "actual;"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.receiver().assertEquals(true, this.next());", "receiver;actual;")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@MethodSource("evaluationSensitiveCalls") //$NON-NLS-1$
	void migratesPrimitiveCallsWithoutChangingEvaluation(String call, String events) throws Exception {
		String original= source(call, true, ""); //$NON-NLS-1$
		ICompilationUnit unit= unit(original);
		Outcome before= execute(original, unit);
		boolean message= call.contains("message()") || call.contains("this.events") || call.contains("null,"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String generated= applyAndUndo(unit, true, "assertTrue", "org.junit.jupiter.api.Assertions", //$NON-NLS-1$ //$NON-NLS-2$
				message ? List.of("boolean", "java.lang.String") : List.of("boolean")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertFalse(before.failed(), before.message());
		assertEquals(events, before.events());
		assertEquals(1, before.calls());
		assertEquals(before, execute(generated, unit));
	}

	static Stream<Arguments> dynamicMessages() {
		return Stream.of(
				Arguments.of("assertTrue(this.message(), this.next());", "message;actual;", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.receiver().assertTrue(this.message(), this.next());", "receiver;message;actual;", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("if (this.result) assertTrue(this.message(), this.next());", "message;actual;", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("if (!this.result) assertTrue(this.message(), this.next());", "", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("String junitMessage= \"keep\"; assertTrue(this.message(), this.next()); this.events += junitMessage;", "message;actual;keep", false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.throwingReceiver().assertTrue(this.message(), this.next());", "receiver;", true), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.nullReceiver().assertTrue(this.message(), this.next());", "receiver;message;actual;", false)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@MethodSource("dynamicMessages") //$NON-NLS-1$
	void migratesMessagesAndReceiversAtTheirOriginalEvaluationPoint(String call, String events, boolean failed)
			throws Exception {
		String original= source(call, true, """
				private junit.framework.TestCase throwingReceiver() {
					this.events += "receiver;";
					throw new AssertionError("receiver");
				}
				private junit.framework.TestCase nullReceiver() {
					this.events += "receiver;";
					return null;
				}
				""");
		ICompilationUnit unit= unit(original);
		Outcome before= execute(original, unit);
		String generated= applyAndUndo(unit, true, "assertTrue", "org.junit.jupiter.api.Assertions", //$NON-NLS-1$ //$NON-NLS-2$
				List.of("boolean", "java.lang.String")); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(events, before.events());
		assertEquals(failed, before.failed());
		assertEquals(before, execute(generated, unit));
	}

	@ParameterizedTest
	@ValueSource(strings= { "assertTrue(this.events, !this.next());", "assertEquals(this.events, false, this.next());" }) //$NON-NLS-1$ //$NON-NLS-2$
	void snapshotsMutableMessagesBeforeActualEvaluation(String call) throws Exception {
		String original= source("this.events= \"before;\"; " + call, true, ""); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= unit(original);
		Outcome before= execute(original, unit);
		String generated= applyAndUndo(unit, true, call.startsWith("assertEquals") ? "assertFalse" : "assertTrue", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"org.junit.jupiter.api.Assertions", List.of("boolean", "java.lang.String")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Outcome after= execute(generated, unit);
		assertTrue(before.failed());
		assertTrue(after.failed());
		assertEquals(before.events(), after.events());
		assertEquals(before.calls(), after.calls());
		assertTrue(before.message().startsWith("before;"), before.message()); //$NON-NLS-1$
		assertTrue(after.message().startsWith("before;"), after.message()); //$NON-NLS-1$
		assertFalse(after.message().contains("before;actual;"), after.message()); //$NON-NLS-1$
	}

	@ParameterizedTest
	@ValueSource(strings= {
			"assertEquals(this.expected() || this.next(), !this.next());", //$NON-NLS-1$
			"assertEquals(!this.next(), this.expected() ? this.next() : false);" }) //$NON-NLS-1$
	void preservesBooleanOperandGroupingAndShortCircuitEvaluation(String call) throws Exception {
		String original= source(call, true, ""); //$NON-NLS-1$
		ICompilationUnit unit= unit(original);
		Outcome before= execute(original, unit);
		String generated= applyAndUndo(unit, true, "assertTrue", "org.junit.jupiter.api.Assertions", List.of("boolean")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Outcome after= execute(generated, unit);
		assertTrue(before.failed());
		assertEquals(before.failed(), after.failed());
		assertEquals(before.events(), after.events());
		assertEquals(before.calls(), after.calls());
	}

	@ParameterizedTest
	@ValueSource(strings= { "message", "receiver", "both", "comment", "constant" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
	void preservesNlsTagsWhenAnEvaluationMovesToAPrecedingStatement(String kind) throws Exception {
		var originalOptions= context.getJavaProject().getOptions(false);
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		try {
			String call= switch (kind) {
			case "message" -> "assertTrue(\"prefix \" + this.message(), this.next()); //$NON-NLS-1$\n"; //$NON-NLS-1$ //$NON-NLS-2$
			case "comment" -> "assertTrue(/*why*/ \"prefix \" + this.message(), this.next()); //$NON-NLS-1$\n"; //$NON-NLS-1$ //$NON-NLS-2$
			case "constant" -> "assertEquals(\"message\", \"value\", \"value\"); //$NON-NLS-1$\n"; //$NON-NLS-1$ //$NON-NLS-2$
			case "receiver" -> "this.receiver(\"receiver;\").assertTrue(this.next()); //$NON-NLS-1$\n"; //$NON-NLS-1$ //$NON-NLS-2$
			default -> "this.receiver(\"r\").assertTrue(\"m\" + this.message(), \"a\".equals(\"a\") && this.next()); //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ keep this note\n"; //$NON-NLS-1$
			};
			String original= source(call, true, """
					private junit.framework.TestCase receiver(String marker) {
						this.events += marker;
						return null;
					}
					""");
			ICompilationUnit unit= unit(original);
			Outcome before= execute(original, unit);
			String method= "constant".equals(kind) ? "assertEquals" : "assertTrue"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			String generated= applyAndUndo(unit, true, method, "org.junit.jupiter.api.Assertions", null); //$NON-NLS-1$
			assertEquals(before, execute(generated, unit));
			assertEquals(taggedLiterals(original), taggedLiterals(generated), generated);
			if ("comment".equals(kind)) { //$NON-NLS-1$
				assertTrue(generated.contains("/*why*/ \"prefix \""), generated); //$NON-NLS-1$
				assertEquals(generated.indexOf("/*why*/"), generated.lastIndexOf("/*why*/")); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if ("both".equals(kind)) assertTrue(generated.contains("keep this note"), generated); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			context.getJavaProject().setOptions(originalOptions);
		}
	}

	static List<String> taggedLiterals(String source) throws Exception {
		return Arrays.stream(NLSScanner.scan(source)).flatMap(line -> Arrays.stream(line.getElements()))
				.filter(NLSElement::hasTag).map(NLSElement::getValue).sorted().toList();
	}

	static Stream<Arguments> assertionReceivers() {
		return Stream.of(
				Arguments.of("this.receiver().assertTrue(this.next());", "receiver;actual;", 1, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.receiver().assertFalse(!this.next());", "receiver;actual;", 1, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.receiver().assertNull(this.next() ? null : this);", "receiver;actual;", 1, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.receiver().assertNotNull(this.next() ? this : null);", "receiver;actual;", 1, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.receiver().assertSame(this, this.next() ? this : null);", "receiver;actual;", 1, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.receiver().assertNotSame(this, this.next() ? new Object() : this);", "receiver;actual;", 1, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.receiver().assertEquals(1, this.next() ? 1 : 0);", "receiver;actual;", 1, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.receiver().assertEquals(Boolean.TRUE, this.nextBoxed());", "receiver;actual;", 1, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.receiver().fail(this.message());", "receiver;message;", 0, true), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("(this.next() ? this.receiver() : null).assertTrue(this.next());", "actual;receiver;actual;", 2, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("((junit.framework.TestCase) this.receiver()).assertTrue(this.next());", "receiver;actual;", 1, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("this.throwingReceiver().assertTrue(this.next());", "receiver;", 0, true), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("junit.framework.TestCase receiver= this.receiver(); receiver.assertTrue(this.next());", "receiver;actual;", 1, false), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("new Receiver(this).assertTrue(this.next());", "receiver;actual;", 1, false)); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@MethodSource("assertionReceivers") //$NON-NLS-1$
	void preservesReceiverEvaluationDuringMigration(String call, String events, int calls, boolean failed) throws Exception {
		String original= source(call, true, """
				private junit.framework.TestCase throwingReceiver() {
					this.events += "receiver;";
					throw new AssertionError("receiver");
				}
				private static class Receiver extends junit.framework.TestCase {
					private Receiver(Example owner) { owner.events += "receiver;"; }
				}
				""");
		ICompilationUnit unit= unit(original);
		List<MethodInvocation> legacyAssertions= new ArrayList<>();
		parse(unit).accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				var binding= invocation.resolveMethodBinding();
				if (binding != null && !binding.isRecovered() && Set.of("junit.framework.Assert", "junit.framework.TestCase") //$NON-NLS-1$ //$NON-NLS-2$
						.contains(binding.getDeclaringClass().getQualifiedName())) {
					legacyAssertions.add(invocation);
				}
				return true;
			}
		});
		assertEquals(1, legacyAssertions.size(), original);
		Outcome before= execute(original, unit);
		assertEquals(failed, before.failed());
		assertEquals(events, before.events());
		assertEquals(calls, before.calls());
		MethodInvocation assertion= legacyAssertions.getFirst();
		if (assertion.getExpression() instanceof MethodInvocation) {
			String generated= applyAndUndo(unit, true, assertion.getName().getIdentifier(),
					"org.junit.jupiter.api.Assertions", null); //$NON-NLS-1$
			assertEquals(before, execute(generated, unit));
		} else {
			assertEquals(original, applyAndUndo(unit, false, null, null, null));
		}
		assertEquals(before, execute(unit.getSource(), unit));
	}

	static Stream<String> safeInstanceQualifiers() {
		return Stream.of("this.", "Example.this."); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@MethodSource("safeInstanceQualifiers") //$NON-NLS-1$
	void migratesSafeInstanceQualifiers(String qualifier) throws Exception {
		String original= source(qualifier + "assertTrue(this.next());", true, ""); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= unit(original);
		Outcome before= execute(original, unit);
		String generated= applyAndUndo(unit, true, "assertTrue", "org.junit.jupiter.api.Assertions", List.of("boolean")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(before, execute(generated, unit));
	}

	@Test
	void preservesCustomAssertionReceiverEvaluation() throws Exception {
		String original= source("this.customReceiver().assertTrue(this.next());", false, //$NON-NLS-1$
				"""
				private Example customReceiver() {
					this.events += "receiver;";
					return this;
				}
				/**
				 * Checks this fixture's custom assertion contract.
				 * @param condition the condition to check
				 */
				public static void assertTrue(boolean condition) {
					if (!condition) throw new AssertionError("custom");
				}
				""");
		ICompilationUnit unit= unit(original);
		Outcome before= execute(original, unit);
		assertTrue(before.failed());
		assertEquals("receiver;actual;", before.events()); //$NON-NLS-1$
		assertEquals(1, before.calls());
		String generated= applyAndUndo(unit, true, "assertTrue", "test.Example", List.of("boolean")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(before, execute(generated, unit));
	}

	static Stream<Arguments> otherOverloads() {
		return Stream.of(
				Arguments.of("assertEquals(Boolean.TRUE, this.nextBoxed());", "", //$NON-NLS-1$ //$NON-NLS-2$
						"org.junit.jupiter.api.Assertions", List.of("java.lang.Object", "java.lang.Object")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of("assertEquals(true, this.next());", """
						/**
						 * Checks this fixture's custom equality contract.
						 * @param expected the expected value
						 * @param actual the actual value
						 */
						public static void assertEquals(boolean expected, boolean actual) {
							if (expected != actual) throw new AssertionError("custom");
						}
						""", "test.Example", List.of("boolean", "boolean"))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	@ParameterizedTest
	@MethodSource("otherOverloads") //$NON-NLS-1$
	void retainsObjectAndCustomOverloadSemantics(String call, String extra, String owner,
			List<String> parameters) throws Exception {
		String original= source(call, false, extra);
		ICompilationUnit unit= unit(original);
		Outcome before= execute(original, unit);
		String generated= applyAndUndo(unit, true, "assertEquals", owner, parameters); //$NON-NLS-1$
		Outcome after= execute(generated, unit);
		assertTrue(before.failed());
		assertEquals(before.failed(), after.failed());
		assertEquals(1, before.calls());
		assertEquals(before.calls(), after.calls());
		assertEquals(before.events(), after.events());
	}

	static Stream<Arguments> customAssertionDocumentation() {
		return Stream.of(
				Arguments.of("", "boolean", "assertEquals(true, this.next());", true), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of("/** Checks equality. */", "boolean", "assertEquals(true, this.next());", true), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of("", "Boolean", "assertEquals(Boolean.TRUE, this.nextBoxed());", false)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@ParameterizedTest
	@MethodSource("customAssertionDocumentation") //$NON-NLS-1$
	void preservesCustomAssertionDocumentationContracts(String documentation, String parameterType,
			String call, boolean hidesLegacyMethod) throws Exception {
		verifyCustomAssertionDocumentation(documentation, parameterType, call, hidesLegacyMethod);
	}

	@ParameterizedTest
	@ValueSource(strings= { "@throws", "@exception" }) //$NON-NLS-1$ //$NON-NLS-2$
	void preservesCustomAssertionTagOrderContract(String exceptionTag) throws Exception {
		String documentation= """
				/**
				 * Checks equality.
				 * $exceptionTag RuntimeException if evaluation fails
				 * @param expected the expected value
				 * @param actual the actual value
				 */
				""".replace("$exceptionTag", exceptionTag); //$NON-NLS-1$
		verifyCustomAssertionDocumentation(documentation, "boolean", "assertEquals(true, this.next());", true); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void verifyCustomAssertionDocumentation(String documentation, String parameterType,
			String call, boolean hidesLegacyMethod) throws Exception {
		String declaration= """
				$documentation
				public static void assertEquals($type expected, $type actual) {
					if (expected != actual) throw new AssertionError("custom");
				}
				""".replace("$documentation", documentation).replace("$type", parameterType).stripLeading(); //$NON-NLS-1$ //$NON-NLS-2$
		String original= source(call, false, declaration);
		ICompilationUnit unit= unit(original);
		var binding= assertion(parse(unit), "assertEquals").resolveMethodBinding(); //$NON-NLS-1$
		assertEquals("test.Example", binding.getDeclaringClass().getQualifiedName()); //$NON-NLS-1$
		assertTrue(Modifier.isStatic(binding.getModifiers()));
		var legacyAssert= binding.getDeclaringClass().getSuperclass().getSuperclass();
		assertEquals("junit.framework.Assert", legacyAssert.getQualifiedName()); //$NON-NLS-1$
		assertFalse(legacyAssert.isRecovered());
		var inherited= Arrays.stream(legacyAssert.getDeclaredMethods())
				.filter(method -> "assertEquals".equals(method.getName()) //$NON-NLS-1$
						&& parameterTypes(method).equals(List.of("boolean", "boolean"))) //$NON-NLS-1$ //$NON-NLS-2$
				.findFirst().orElseThrow();
		assertFalse(inherited.isRecovered());
		assertTrue(Modifier.isStatic(inherited.getModifiers()));
		assertEquals(hidesLegacyMethod, binding.isSubsignature(inherited));
		assertFalse(binding.overrides(inherited), "Static hiding is not an instance override"); //$NON-NLS-1$
		Outcome before= execute(original, unit);
		assertTrue(before.failed());
		assertEquals("custom", before.message()); //$NON-NLS-1$
		assertEquals("actual;", before.events()); //$NON-NLS-1$
		assertEquals(1, before.calls());
		String generated= applyAndUndo(unit, !hidesLegacyMethod, "assertEquals", "test.Example", parameterTypes(binding)); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(before, execute(generated, unit));
	}

	private ICompilationUnit unit(String source) throws Exception {
		return JUnitMigrationFixtureClasspath.createJUnit3And5Root(context)
				.createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Example.java", source, false, null); //$NON-NLS-1$
	}

	private String applyAndUndo(ICompilationUnit unit, boolean expectMigration, String method,
			String owner, List<String> parameters) throws Exception {
		String original= unit.getSource();
		CompilationUnit before= parse(unit);
		var options= context.getJavaProject().getOptions(false);
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		var operations= operations(before);
		if (!expectMigration && operations.isEmpty()) {
			diagnostics.assertRestored();
			assertEquals(options, context.getJavaProject().getOptions(false));
			return unit.getSource();
		}
		assertFalse(operations.isEmpty(), original);
		Outcome originalExecution= expectMigration ? null : execute(original, unit);
		var fix= new NlsAwareCleanUpFix("Migrate JUnit 3", before, //$NON-NLS-1$
				operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
		var change= fix.createChange(null);
		try {
			change.initializeValidationData(null);
			var undo= change.perform(new NullProgressMonitor());
			assertNotNull(undo);
			try {
				if (!expectMigration) {
					assertAll("Unproven assertion evaluation must retain the original class", //$NON-NLS-1$
							() -> assertEquals(originalExecution, execute(unit.getSource(), unit)),
							diagnostics::assertNoNewDiagnostics,
							() -> assertEquals(original, unit.getSource()));
				}
				diagnostics.assertNoNewDiagnostics();
				CompilationUnit after= parse(unit);
				MethodInvocation invocation= assertion(after, method);
				var binding= invocation.resolveMethodBinding();
				assertEquals(owner, binding.getDeclaringClass().getQualifiedName());
				if (parameters != null) assertEquals(parameters, parameterTypes(binding));
				assertTrue(operations(after).isEmpty(), "Migration must be idempotent"); //$NON-NLS-1$
				return unit.getSource();
			} finally {
				var redo= undo.perform(new NullProgressMonitor());
				if (redo != null) redo.dispose();
				undo.dispose();
			}
		} finally {
			change.dispose();
			assertEquals(original, unit.getSource());
			diagnostics.assertRestored();
			assertEquals(options, context.getJavaProject().getOptions(false));
		}
	}

	private static LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange> operations(CompilationUnit root) {
		var operations= new LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange>();
		JUnitCleanUpFixCore.TEST3.findOperations(root, operations, new HashSet<>());
		return operations;
	}

	private static MethodInvocation assertion(CompilationUnit root, String method) {
		List<MethodInvocation> invocations= new ArrayList<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				if (method.equals(invocation.getName().getIdentifier())) invocations.add(invocation);
				return true;
			}
		});
		assertEquals(1, invocations.size());
		var result= invocations.getFirst();
		assertNotNull(result.resolveMethodBinding());
		assertFalse(result.resolveMethodBinding().isRecovered());
		return result;
	}

	private static List<String> parameterTypes(org.eclipse.jdt.core.dom.IMethodBinding binding) {
		return Arrays.stream(binding.getParameterTypes()).map(type -> type.getQualifiedName()).toList();
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		assertTrue(Arrays.stream(root.getProblems()).noneMatch(problem -> problem.isError()),
				() -> Arrays.toString(root.getProblems()));
		return root;
	}

	private Outcome execute(String source, ICompilationUnit unit) throws Exception {
		Path directory= Files.createTempDirectory(temporary, "execution-"); //$NON-NLS-1$
		Path file= Files.writeString(directory.resolve("Example.java"), source, StandardCharsets.UTF_8); //$NON-NLS-1$
		var jars= Arrays.stream(unit.getJavaProject().getResolvedClasspath(true))
				.filter(entry -> entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY)
				.map(entry -> entry.getPath().toFile()).filter(File::isFile).distinct().toList();
		String classpath= jars.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
		var compiler= ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler);
		var output= new ByteArrayOutputStream();
		assertEquals(0, compiler.run(null, output, output, "--release", //$NON-NLS-1$
				unit.getJavaProject().getOption(JavaCore.COMPILER_SOURCE, true), "-encoding", "UTF-8", //$NON-NLS-1$ //$NON-NLS-2$
				"-cp", classpath, "-d", directory.toString(), file.toString()), //$NON-NLS-1$ //$NON-NLS-2$
				output.toString(StandardCharsets.UTF_8));
		var urls= new ArrayList<java.net.URL>();
		urls.add(directory.toUri().toURL());
		for (File jar : jars) urls.add(jar.toURI().toURL());
		try (var loader= new URLClassLoader(urls.toArray(java.net.URL[]::new), null)) {
			Class<?> type= loader.loadClass("test.Example"); //$NON-NLS-1$
			Object instance= type.getConstructor().newInstance();
			Throwable failure= null;
			try {
				type.getMethod("testValue").invoke(instance); //$NON-NLS-1$
			} catch (InvocationTargetException exception) {
				failure= exception.getCause();
				assertTrue(failure instanceof AssertionError, failure.toString());
			}
			return new Outcome(failure != null, failure == null ? "" : failure.getMessage(), //$NON-NLS-1$
					(String) type.getField("events").get(instance), type.getField("calls").getInt(instance)); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private record Outcome(boolean failed, String message, String events, int calls) {
	}

	private static String source(String assertion, boolean actual, String extra) {
		return """
				package test;
				import junit.framework.TestCase;
				public class Example extends TestCase {
					public String events= "";
					public int calls;
					private boolean result= $actual;
					public void testValue() { $assertion }
					private boolean next() { this.events += "actual;"; this.calls++; return this.result; }
					private Boolean nextBoxed() { return Boolean.valueOf(this.next()); }
					private boolean expected() { this.events += "expected;"; return true; }
					private String message() { this.events += "message;"; return "marker"; }
					private junit.framework.TestCase receiver() {
						this.events += "receiver;";
						return new junit.framework.TestCase() { };
					}
					$extra
				}
				""".replace("$actual", Boolean.toString(actual)).replace("$assertion", assertion) //$NON-NLS-1$ //$NON-NLS-2$
				.replace("$extra", extra); //$NON-NLS-1$
	}
}
