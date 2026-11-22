/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Complete-unit regressions for the JUnit 3 contracts used by the upstream UI tests. */
@SuppressWarnings("nls")
class JUnit3ConsumerContractTest {

	@RegisterExtension
	final EclipseJava17 context= new EclipseJava17();
	private IPackageFragment pack;
	private IPackageFragmentRoot sourceRoot;

	@BeforeEach
	void setup() throws Exception {
		sourceRoot= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		pack= sourceRoot.createPackageFragment("test", false, null);
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
		var options= context.getJavaProject().getOptions(false);
		options.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, "0");
		context.getJavaProject().setOptions(options);
	}

	static Stream<Arguments> legacyConsumers() {
		return Stream.of(
				Arguments.of("TestCase parameter", "consume(this);", "void consume(junit.framework.TestCase value) {}"),
				Arguments.of("parenthesized parameter", "consume((this));", "void consume(junit.framework.TestCase value) {}"),
				Arguments.of("generic TestCase bound", "consume(this);", "<T extends junit.framework.TestCase> void consume(T value) {}"),
				Arguments.of("secondary Test bound", "consume(this);", "<T extends Object & junit.framework.Test> void consume(T value) {}"),
				Arguments.of("lower capture bound",
						"java.util.function.Consumer<? super junit.framework.TestCase> consumer = value -> {}; consumer.accept(this);", ""),
				Arguments.of("generic constructor", "new Contract(this);",
						"static class Contract { <T extends junit.framework.TestCase> Contract(T value) {} }"),
				Arguments.of("JUnit Test parameter", "new junit.framework.TestResult().startTest(this);", ""),
				Arguments.of("JUnit Test constructor", "new junit.extensions.TestDecorator(this);", ""),
				Arguments.of("legacy return type", "identity();", "junit.framework.TestCase identity() { return this; }"));
	}

	@ParameterizedTest(name= "{0}")
	@MethodSource("legacyConsumers")
	void preservesRequiredLegacyInstanceContracts(String description, String body, String helper) throws Exception {
		String original= """
				package test;
				import junit.framework.TestCase;
				public class Example extends TestCase {
					public void testContract() { $body }
					$helper
				}
				""".replace("$body", body).replace("$helper", helper);
		assertEquals(original, applyAndUndo(original, false), description);
	}

	static Stream<String> selfSuiteConsumers() {
		return Stream.of("junit.textui.TestRunner.run(suite());",
				"junit.textui.TestRunner.run(Example.suite());",
				"java.util.function.Supplier<Test> source = Example::suite; junit.textui.TestRunner.run(source.get());");
	}

	@ParameterizedTest
	@MethodSource("selfSuiteConsumers")
	void preservesSelfSuiteStillInvokedByTheTestHarness(String mainBody) throws Exception {
		String original= """
				package test;
				import junit.framework.*;
				public class Example extends TestCase {
					public static Test suite() { return new TestSuite(Example.class); }
					public void testContract() { assertTrue(true); }
					public static void main(String[] args) { $mainBody }
				}
				""".replace("$mainBody", mainBody);
		assertEquals(original, applyAndUndo(original, false));
	}

	static Stream<Arguments> independentConsumers() {
		return Stream.of(
				Arguments.of("Object", "consume(this);", "void consume(Object value) {}"),
				Arguments.of("unbounded generic", "consume(this);", "<T> void consume(T value) {}"),
				Arguments.of("Object lower capture bound",
						"java.util.function.Consumer<? super Object> consumer = value -> {}; consumer.accept(this);", ""),
				Arguments.of("unbounded constructor", "new Contract(this);",
						"static class Contract { <T> Contract(T value) {} }"),
				Arguments.of("own type", "consume(this);", "void consume(Example value) {}"),
				Arguments.of("ordinary receiver", "this.helper();", "void helper() {}"));
	}

	@ParameterizedTest(name= "{0}")
	@MethodSource("independentConsumers")
	void stillMigratesInstancesWithoutALegacyConsumer(String description, String body, String helper) throws Exception {
		String generated= applyAndUndo("""
				package test;
				import junit.framework.TestCase;
				public class Example extends TestCase {
					public void testContract() { $body }
					$helper
				}
				""".replace("$body", body).replace("$helper", helper), true);
		assertFalse(generated.contains("extends TestCase"), description);
	}

	@Test
	void stillMigratesAnUnreferencedSelfSuiteWithWildcardImports() throws Exception {
		String generated= applyAndUndo("""
				package test;
				import junit.framework.*;
				public class Example extends TestCase {
					public static Test suite() { return new TestSuite(Example.class); }
					public void testContract() { assertTrue(true); }
				}
				""", true);
		assertFalse(generated.contains("suite()"), generated);
	}

	/** Exact JDT UI R4_41 source, commit 54bd63e6453a7eb5db0bfc1d60f17e9568095619. */
	@ParameterizedTest
	@ValueSource(strings= { "strict", "best-effort", "compatibility" })
	void preservesOriginalSampleHarnessWithoutAJupiterClasspath(String mode) throws Exception {
		var project= context.getJavaProject();
		project.setRawClasspath(Arrays.stream(project.getRawClasspath())
				.filter(entry -> !JUnitCore.JUNIT5_CONTAINER_PATH.equals(entry.getPath()))
				.toArray(IClasspathEntry[]::new), null);
		assertNull(project.findType("org.junit.jupiter.api.Test"));
		Path resources= Path.of("testresources", "upstream-junit3");
		String original= Files.readString(resources.resolve("SimpleTest.java.txt"), StandardCharsets.UTF_8);
		Properties properties= new Properties();
		try (var reader= Files.newBufferedReader(resources.resolve("junit-strict.properties"), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		Map<String, String> options= new HashMap<>();
		properties.stringPropertyNames().forEach(key -> options.put(key, properties.getProperty(key)));
		options.put("cleanup.junitcleanup_best_effort", Boolean.toString("best-effort".equals(mode)));
		options.put("cleanup.junitcleanup_6_compatibility", Boolean.toString("compatibility".equals(mode)));
		IPackageFragment sample= sourceRoot.createPackageFragment("junit.samples", false, null);
		assertEquals(original, applyAndUndo(sample, "SimpleTest.java", original, false, options));
	}

	private String applyAndUndo(String original, boolean expectMigration) throws Exception {
		return applyAndUndo(pack, "Example.java", original, expectMigration, Map.of(
				MYCleanUpConstants.JUNIT3_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_3_TEST, CleanUpOptions.TRUE));
	}

	private String applyAndUndo(IPackageFragment target, String name, String original, boolean expectMigration,
			Map<String, String> options) throws Exception {
		ICompilationUnit unit= target.createCompilationUnit(name, original, false, null);
		CompilationUnit before= parse(unit);
		assertTrue(Arrays.stream(before.getProblems()).noneMatch(IProblem::isError),
				() -> Arrays.toString(before.getProblems()));
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		JUnitCleanUpCore cleanup= new JUnitCleanUpCore(options);
		try {
			var status= cleanup.checkPreConditions(context.getJavaProject(), new ICompilationUnit[] { unit }, null);
			assertFalse(status.hasFatalError(), status.toString());
			var fix= cleanup.createFix(new CleanUpContext(unit, before));
			if (fix == null) {
				assertFalse(expectMigration, "The safe positive must still migrate");
				diagnostics.assertRestored();
				return unit.getSource();
			}
			var change= fix.createChange(null);
			try {
				change.initializeValidationData(null);
				var undo= change.perform(null);
				assertNotNull(undo);
				try {
					String generated= unit.getSource();
					diagnostics.assertNoNewDiagnostics();
					assertTrue(expectMigration, "A required legacy execution contract must be retained: " + generated);
					CompilationUnit after= parse(unit);
					after.accept(new ASTVisitor() {
						@Override public boolean visit(MarkerAnnotation annotation) {
							assertNotNull(annotation.resolveTypeBinding());
							assertEquals("org.junit.jupiter.api.Test", annotation.resolveTypeBinding().getQualifiedName());
							return true;
						}
					});
					assertNull(cleanup.createFix(new CleanUpContext(unit, after)), "A second pass must remain unchanged");
					return generated;
				} finally {
					var redo= undo.perform(null);
					if (redo != null) redo.dispose();
					undo.dispose();
				}
			} finally {
				change.dispose();
				assertEquals(original, unit.getSource());
				diagnostics.assertRestored();
			}
		} finally {
			cleanup.checkPostConditions(null);
		}
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
