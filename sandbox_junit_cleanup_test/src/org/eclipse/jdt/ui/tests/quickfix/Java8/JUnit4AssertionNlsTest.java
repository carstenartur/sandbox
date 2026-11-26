/*
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
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

import javax.tools.ToolProvider;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;

import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.triggerpattern.cleanup.NlsAwareCleanUpFix;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.JUnitMigrationFixtureClasspath;

/** Preserves the association of NLS tags when a JUnit 4 message moves last. */
public class JUnit4AssertionNlsTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	@TempDir
	Path temporary;

	@ParameterizedTest
	@ValueSource(strings= { "multiline", "indices", "single-line", "crlf", "boolean", "delta", "multiline-message", "partial", "repeated" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
	void movesMessagesWithoutChangingTaggedLiteralsOrDiagnostics(String shape) throws Exception {
		// This option enables both NonExternalizedStringLiteral and UnnecessaryNLSTag.
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		String call= switch (shape) {
			case "indices" -> """
					assertEquals("right " + "fatal message", //$NON-NLS-1$ //$NON-NLS-2$ preserve this note
							Integer.parseInt("1"), actual); //$NON-NLS-1$
					""";
			case "single-line" -> "assertEquals(\"right fatal message\", Integer.parseInt(\"1\"), actual); //$NON-NLS-1$\n"; //$NON-NLS-1$ //$NON-NLS-2$
			case "partial" -> "assertEquals(\"right fatal message\", \"1\", Integer.toString(actual)); //$NON-NLS-2$\n"; //$NON-NLS-1$ //$NON-NLS-2$
			case "repeated" -> "assertEquals(\"right fatal message\" + \"\" + \"\", 1, actual); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$\n"; //$NON-NLS-1$ //$NON-NLS-2$
			case "multiline-message" -> """
					assertEquals("right " + //$NON-NLS-1$ first part
							"fatal message", //$NON-NLS-1$ second part
							1, actual);
					""";
			case "boolean" -> "assertTrue(\"right fatal message\", //$NON-NLS-1$\n actual == 1);\n"; //$NON-NLS-1$ //$NON-NLS-2$
			case "delta" -> "assertEquals(\"right fatal message\", //$NON-NLS-1$\n 1.0, (double) actual, 0.1);\n"; //$NON-NLS-1$ //$NON-NLS-2$
			default -> """
					assertEquals("right fatal message", //$NON-NLS-1$
							1,
							actual);
					""";
		};
		String original= """
				package test;
				import static org.junit.Assert.*;
				public class Example {
					public static void check(int actual) {
						$call
					}
				}
				""".replace("$call", call); //$NON-NLS-1$
		if ("crlf".equals(shape)) original= original.replace("\n", "\r\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ICompilationUnit unit= unit(original);
		assertBehavior(original, unit);
		String generated= applyAndUndo(unit);
		if ("indices".equals(shape)) assertTrue(generated.contains("preserve this note"), generated); //$NON-NLS-1$ //$NON-NLS-2$
		if ("multiline-message".equals(shape)) { //$NON-NLS-1$
			assertTrue(generated.contains("first part"), generated); //$NON-NLS-1$
			assertTrue(generated.contains("second part"), generated); //$NON-NLS-1$
		}
		assertBehavior(generated, unit);
	}

	@ParameterizedTest
	@ValueSource(strings= { "if", "lambda" }) //$NON-NLS-1$ //$NON-NLS-2$
	void capturesTheMessageOnlyWhenItsBranchOrLambdaExecutes(String shape) throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_LOCAL_VARIABLE_HIDING, JavaCore.WARNING);
		String call= """
				assertEquals("right fatal message" + (++calls == 1 ? "" : "wrong order"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						1, actual + ++calls - 2);
				""";
		String body= "if".equals(shape) ? "if (actual > 0) " + call //$NON-NLS-1$ //$NON-NLS-2$
				: "Runnable task = () -> " + call + "\nif (actual > 0) task.run();"; //$NON-NLS-1$ //$NON-NLS-2$
		String original= """
				package test;
				import static org.junit.Assert.assertEquals;
				class Parent { protected static int junitMessage; }
				public class Example extends Parent {
					public static int calls;
					public static void check(int actual) {
						int junitMessage2 = actual;
						java.util.function.IntUnaryOperator identity = junitMessage3 -> junitMessage3 + junitMessage2;
						if (identity.applyAsInt(0) != actual) throw new AssertionError();
						$body
					}
				}
				""".replace("$body", body); //$NON-NLS-1$
		ICompilationUnit unit= unit(original);
		assertBehavior(original, unit, true);
		assertBehavior(applyAndUndo(unit), unit, true);
	}

	@Test
	void retainsForUpdateCallsAndTheirImportsWhileMigratingSupportedPeers() throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.WARNING);
		String original= """
				package test;
				import static org.junit.Assert.assertEquals;
				import static org.junit.Assert.assertTrue;
				public class Example {
					public static void check(int actual) {
						for (int i = 0; i < 1; assertEquals("update message", 1, ++i)) { } //$NON-NLS-1$
						assertTrue("right fatal message", actual == 1); //$NON-NLS-1$
					}
				}
				""";
		ICompilationUnit unit= unit(original);
		assertBehavior(original, unit);
		assertBehavior(applyAndUndo(unit), unit);
	}

	@ParameterizedTest
	@ValueSource(strings= { "shared-line", "duplicate-tag" }) //$NON-NLS-1$ //$NON-NLS-2$
	void retainsCallsWhoseNlsCommentsCannotBeSplitFromTheirPhysicalLine(String shape) throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		String retained= "shared-line".equals(shape) //$NON-NLS-1$
				? "String other = \"other\"; assertEquals(\"retained message\", 1, other.length() - 4); //$NON-NLS-1$ //$NON-NLS-2$" //$NON-NLS-1$
				: "assertEquals(\"retained message\", 1, 1); //$NON-NLS-1$ //$NON-NLS-1$"; //$NON-NLS-1$
		String original= """
				package test;
				import static org.junit.Assert.assertEquals;
				public class Example {
					public static void check(int actual) {
						$retained
						assertEquals("right fatal message", 1, actual); //$NON-NLS-1$
					}
				}
				""".replace("$retained", retained); //$NON-NLS-1$
		ICompilationUnit unit= unit(original);
		assertBehavior(original, unit);
		String generated= applyAndUndo(unit);
		assertTrue(generated.contains(retained), generated);
		assertBehavior(generated, unit);
	}

	@ParameterizedTest
	@ValueSource(strings= { "assert-qualified", "assert-fqn", "assert-hamcrest", "assert-static-hamcrest", "assert-static-explicit-hamcrest", "assume-qualified", "assume-fqn", "assume-not-null", "assume-static-not-null" }) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
	void preservesQualifiedMixedCallsWithoutAddingUnusedImports(String shape) throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.WARNING);
		boolean assumptions= shape.startsWith("assume"); //$NON-NLS-1$
		String type= assumptions ? "Assume" : "Assert"; //$NON-NLS-1$ //$NON-NLS-2$
		String method= assumptions ? "assumeTrue" : "assertTrue"; //$NON-NLS-1$ //$NON-NLS-2$
		String qualifier= shape.endsWith("fqn") ? "org.junit." + type : type; //$NON-NLS-1$ //$NON-NLS-2$
		String imports= shape.endsWith("fqn") ? "" : "import org.junit." + type + ";"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		String retained= "assume-not-null".equals(shape) ? "Assume.assumeNotNull(new Object());" : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if ("assume-static-not-null".equals(shape)) { //$NON-NLS-1$
			qualifier= ""; //$NON-NLS-1$
			imports= "import static org.junit.Assume.assumeTrue;\nimport static org.junit.Assume.assumeNotNull;"; //$NON-NLS-1$
			retained= "assumeNotNull(new Object());"; //$NON-NLS-1$
		}
		if (shape.startsWith("assert-static")) { //$NON-NLS-1$
			qualifier= ""; //$NON-NLS-1$
			imports= "assert-static-hamcrest".equals(shape) ? "import static org.junit.Assert.*;" //$NON-NLS-1$ //$NON-NLS-2$
					: "import static org.junit.Assert.assertTrue;\nimport static org.junit.Assert.assertThat;"; //$NON-NLS-1$
		}
		String peer= shape.endsWith("hamcrest") //$NON-NLS-1$
				? (qualifier.isEmpty() ? "" : qualifier + ".") + "assertThat(\"right fatal message\", actual, org.hamcrest.CoreMatchers.is(1));" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				: (qualifier.isEmpty() ? "" : qualifier + ".") + method + "(\"right fatal message\", actual == 1);"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String original= """
				package test;
				$imports
				public class Example {
					public static void check(int actual) {
						for (int i = 0; i < 1; $qualifier.$method("update message", ++i == 1)) { } //$NON-NLS-1$
						$retained
						$peer //$NON-NLS-1$
					}
				}
				""".replace("$imports", imports).replace("$qualifier.", qualifier.isEmpty() ? "" : qualifier + ".") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				.replace("$method", method).replace("$retained", retained).replace("$peer", peer); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ICompilationUnit unit= unit(original);
		assertBehavior(original, unit, false, assumptions);
		String generated= applyAndUndo(unit, assumptions ? JUnitCleanUpFixCore.ASSUME : JUnitCleanUpFixCore.ASSERT);
		assertBehavior(generated, unit, false, assumptions);
	}

	@Test
	void retainsAnEvaluatedReceiverWithoutDroppingItsSideEffect() throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		String original= """
				package test;
				import org.junit.Assert;
				public class Example {
					public static int calls;
					public static Assert receiver() { calls++; return null; }
					public static void check(int actual) {
						calls = 0;
						for (int i = 0; i < 1; Assert.assertTrue("update message", ++i == 1)) { } //$NON-NLS-1$
						receiver().assertTrue("receiver message", true); //$NON-NLS-1$
						if (calls != 1) throw new AssertionError();
						Assert.assertTrue("right fatal message", actual == 1); //$NON-NLS-1$
					}
				}
				""";
		ICompilationUnit unit= unit(original);
		assertBehavior(original, unit);
		assertBehavior(applyAndUndo(unit), unit);
	}

	@Test
	void evaluatesANonliteralMessageBeforeItsTaggedValue() throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		String original= """
				package test;
				import static org.junit.Assert.assertEquals;
				public class Example {
					public static boolean ready;
					public static int calls;
					public static String message() { calls++; ready = true; return "right fatal message"; }
					public static String value(int actual) { calls++; return ready && actual == 1 ? "ok" : "wrong"; }
					public static void check(int actual) {
						ready = false;
						calls = 0;
						try {
							assertEquals(message(), "ok", value(actual)); //$NON-NLS-1$
						} finally {
							if (calls != 2) throw new AssertionError();
						}
					}
				}
				""";
		ICompilationUnit unit= unit(original);
		assertBehavior(original, unit);
		assertBehavior(applyAndUndo(unit), unit);
	}

	@Test
	void retainsAQualifierCollisionWhileMigratingAnIndependentMethod() throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_NON_NLS_STRING_LITERAL, JavaCore.WARNING);
		String original= """
				package test;
				import static org.junit.Assert.assertTrue;
				public class Example {
					public static void check(int org) {
						independent();
						for (int i = 0; i < 1; assertTrue("update message", ++i == 1)) { } //$NON-NLS-1$
						assertTrue("right fatal message", org == 1); //$NON-NLS-1$
					}
					public static void independent() { assertTrue("independent", true); } //$NON-NLS-1$
				}
				""";
		ICompilationUnit unit= unit(original);
		assertBehavior(original, unit);
		assertBehavior(applyAndUndo(unit), unit);
	}

	private ICompilationUnit unit(String source) throws Exception {
		return JUnitMigrationFixtureClasspath.createJUnit4And5Root(context)
				.createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Example.java", source, false, null); //$NON-NLS-1$
	}

	private String applyAndUndo(ICompilationUnit unit) throws Exception {
		return applyAndUndo(unit, JUnitCleanUpFixCore.ASSERT);
	}

	private String applyAndUndo(ICompilationUnit unit, JUnitCleanUpFixCore fixCore) throws Exception {
		String original= unit.getSource();
		var options= context.getJavaProject().getOptions(false);
		var classpath= unit.getJavaProject().getRawClasspath();
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		CompilationUnit root= parse(unit);
		var operations= new LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange>();
		fixCore.findOperations(root, operations, new HashSet<>());
		assertFalse(operations.isEmpty());
		var fix= new NlsAwareCleanUpFix("Migrate assertions", root, //$NON-NLS-1$
				operations.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new));
		var change= fix.createChange(null);
		try {
			change.initializeValidationData(null);
			var undo= change.perform(new NullProgressMonitor());
			assertNotNull(undo);
			try {
				String generated= unit.getSource();
				diagnostics.assertNoNewDiagnostics();
				assertEquals(taggedLiterals(original), taggedLiterals(generated), generated);
				parse(unit).accept(new ASTVisitor() {
					@Override
					public boolean visit(MethodInvocation invocation) {
						String name= invocation.getName().getIdentifier();
						if (name.startsWith("assert") || name.startsWith("assume")) { //$NON-NLS-1$ //$NON-NLS-2$
							var binding= invocation.resolveMethodBinding();
							assertNotNull(binding);
							assertFalse(binding.isRecovered());
							boolean retained= invocation.getParent() instanceof ForStatement || "assumeNotNull".equals(name) //$NON-NLS-1$
									|| invocation.getExpression() instanceof MethodInvocation || hasOrgParameter(invocation)
									|| invocation.arguments().getFirst() instanceof StringLiteral literal && "retained message".equals(literal.getLiteralValue()); //$NON-NLS-1$
							String owner= name.startsWith("assume") //$NON-NLS-1$
									? (retained ? "org.junit.Assume" : "org.junit.jupiter.api.Assumptions") //$NON-NLS-1$ //$NON-NLS-2$
									: (retained ? "org.junit.Assert" : "org.junit.jupiter.api.Assertions"); //$NON-NLS-1$ //$NON-NLS-2$
							if ("assertThat".equals(name)) owner= "org.hamcrest.MatcherAssert"; //$NON-NLS-1$ //$NON-NLS-2$
							assertEquals(owner, binding.getDeclaringClass().getQualifiedName());
						}
						return true;
					}
				});
				var second= new LinkedHashSet<CompilationUnitRewriteOperationWithSourceRange>();
				CompilationUnit repeatedRoot= parse(unit);
				fixCore.findOperations(repeatedRoot, second, new HashSet<>());
				if (!second.isEmpty()) {
					var repeated= new NlsAwareCleanUpFix("Repeat migration", repeatedRoot, //$NON-NLS-1$
							second.toArray(CompilationUnitRewriteOperationWithSourceRange[]::new)).createOptionalChange(null);
					try {
						assertNull(repeated, "Migration must be idempotent"); //$NON-NLS-1$
					} finally {
						if (repeated != null) repeated.dispose();
					}
				}
				return generated;
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
			assertEquals(Arrays.asList(classpath), Arrays.asList(unit.getJavaProject().getRawClasspath()));
		}
	}

	private static boolean hasOrgParameter(MethodInvocation invocation) {
		MethodDeclaration method= ASTNodes.getParent(invocation, MethodDeclaration.class);
		if (method != null) {
			for (Object parameter : method.parameters()) {
				if (parameter instanceof SingleVariableDeclaration declaration && "org".equals(declaration.getName().getIdentifier())) return true; //$NON-NLS-1$
			}
		}
		return false;
	}

	private static List<String> taggedLiterals(String source) throws Exception {
		return Arrays.stream(NLSScanner.scan(source)).flatMap(line -> Arrays.stream(line.getElements()))
				.filter(NLSElement::hasTag).map(NLSElement::getValue).sorted().toList();
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

	private void assertBehavior(String source, ICompilationUnit unit) throws Exception {
		assertBehavior(source, unit, false);
	}

	private void assertBehavior(String source, ICompilationUnit unit, boolean observesCalls) throws Exception {
		assertBehavior(source, unit, observesCalls, false);
	}

	private void assertBehavior(String source, ICompilationUnit unit, boolean observesCalls, boolean assumptions) throws Exception {
		Path directory= Files.createTempDirectory(temporary, "execution-"); //$NON-NLS-1$
		Path file= Files.writeString(directory.resolve("Example.java"), source, StandardCharsets.UTF_8); //$NON-NLS-1$
		var jars= Arrays.stream(unit.getJavaProject().getResolvedClasspath(true))
				.filter(entry -> entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY)
				.map(entry -> entry.getPath().toFile()).filter(File::isFile).distinct().toList();
		String classpath= jars.stream().map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
		var compiler= ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler);
		var output= new ByteArrayOutputStream();
		assertEquals(0, compiler.run(null, output, output, "--release", "17", "-encoding", "UTF-8", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				"-cp", classpath, "-d", directory.toString(), file.toString()), output.toString(StandardCharsets.UTF_8)); //$NON-NLS-1$ //$NON-NLS-2$
		var urls= new ArrayList<URL>();
		urls.add(directory.toUri().toURL());
		for (File jar : jars) urls.add(jar.toURI().toURL());
		try (var loader= new URLClassLoader(urls.toArray(URL[]::new), null)) {
			Class<?> type= loader.loadClass("test.Example"); //$NON-NLS-1$
			var method= type.getMethod("check", int.class); //$NON-NLS-1$
			if (observesCalls) {
				method.invoke(null, 0);
				assertEquals(0, type.getField("calls").getInt(null)); //$NON-NLS-1$
			}
			method.invoke(null, 1);
			if (observesCalls) {
				assertEquals(2, type.getField("calls").getInt(null)); //$NON-NLS-1$
				type.getField("calls").setInt(null, 0); //$NON-NLS-1$
			}
			Throwable failure= null;
			try {
				method.invoke(null, 2);
			} catch (InvocationTargetException exception) {
				failure= exception.getCause();
			}
			if (assumptions) {
				assertNotNull(failure);
				assertTrue(Set.of("org.junit.AssumptionViolatedException", "org.opentest4j.TestAbortedException").contains(failure.getClass().getName()), failure.toString()); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				assertTrue(failure instanceof AssertionError, String.valueOf(failure));
			}
			assertTrue(failure.getMessage().contains("right fatal message"), failure.toString()); //$NON-NLS-1$
			assertFalse(failure.getMessage().contains("wrong order"), failure.toString()); //$NON-NLS-1$
			if (observesCalls) assertEquals(2, type.getField("calls").getInt(null)); //$NON-NLS-1$
		}
	}
}
