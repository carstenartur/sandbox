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
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.JUnitMigrationFixtureClasspath;

/** Primitive overloads from the pinned compiler annotation-processing tests. */
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
	void preservesCallsWithoutAProvenPrimitiveRewrite(String call, String events) throws Exception {
		String original= source(call, true, ""); //$NON-NLS-1$
		ICompilationUnit unit= unit(original);
		assertEquals(original, applyAndUndo(unit, false, null, null, null));
		Outcome outcome= execute(original, unit);
		assertFalse(outcome.failed(), outcome.message());
		assertEquals(events, outcome.events());
		assertEquals(1, outcome.calls());
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
		if (!expectMigration) {
			assertTrue(operations.isEmpty(), original);
			diagnostics.assertRestored();
			assertEquals(options, context.getJavaProject().getOptions(false));
			return unit.getSource();
		}
		assertFalse(operations.isEmpty(), original);
		var fix= new CompilationUnitRewriteOperationsFixCore("Migrate JUnit 3", before, //$NON-NLS-1$
				operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
		var change= fix.createChange(null);
		try {
			change.initializeValidationData(null);
			var undo= change.perform(new NullProgressMonitor());
			assertNotNull(undo);
			try {
				diagnostics.assertNoNewDiagnostics();
				CompilationUnit after= parse(unit);
				MethodInvocation invocation= assertion(after, method);
				var binding= invocation.resolveMethodBinding();
				assertEquals(owner, binding.getDeclaringClass().getQualifiedName());
				assertEquals(parameters, parameterTypes(binding));
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
