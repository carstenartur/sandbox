/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * Eclipse Public License 2.0: https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.junit.JUnitCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.cleanup.HintFileFixCore;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Complete-unit regressions for JDT UI's partially migrated JUnit assertion imports. */
@SuppressWarnings("nls")
class JUnitHintImportSafetyTest {

	@RegisterExtension
	final EclipseJava17 context= new EclipseJava17();
	private IPackageFragment pack;
	private HintFile hint;

	@BeforeEach
	void setup() throws Exception {
		pack= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH)
				.createPackageFragment("test", false, null);
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
		var options= context.getJavaProject().getOptions(false);
		options.put(JavaCore.COMPILER_PB_MAX_PER_UNIT, "0");
		context.getJavaProject().setOptions(options);
		try (var input= getClass().getResourceAsStream("/org/sandbox/jdt/internal/corext/fix/hints/junit5.sandbox-hint")) {
			assertNotNull(input, "Use the actual bundled JUnit migration rules");
			hint= new HintFileParser().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	static Stream<Arguments> remainingMessageFirstCalls() {
		return Stream.of(
				Arguments.of("assertNotEquals", "assertNotEquals(message, left, right)"),
				Arguments.of("assertEquals", "assertEquals(message, left, right)"),
				Arguments.of("assertTrue", "assertTrue(message, condition)"),
				Arguments.of("assertFalse", "assertFalse(message, condition)"),
				Arguments.of("assertNotNull", "assertNotNull(message, value)"),
				Arguments.of("assertNull", "assertNull(message, value)"));
	}

	@ParameterizedTest
	@MethodSource("remainingMessageFirstCalls")
	void retainsStaticImportForUnchangedMessageFirstCall(String member, String call) throws Exception {
		String source= """
				package test;
				import static org.junit.Assert.$member;
				import org.junit.Assert;
				public class Example {
					void run(String message, int left, int right, boolean condition, Object value) {
						$call;
						if (condition) Assert.fail(message);
					}
				}
				""".replace("$member", member).replace("$call", call);
		String generated= applyAndUndo(source, List.of("org.junit.Assert", "org.junit.jupiter.api.Assertions"));
		assertTrue(generated.contains("import static org.junit.Assert." + member + ";"), generated);
		assertFalse(generated.contains("import static org.junit.jupiter.api.Assertions."), generated);
	}

	@Test
	void retainsWildcardImportForUnchangedMessageFirstCall() throws Exception {
		String generated= applyAndUndo("""
				package test;
				import static org.junit.Assert.*;
				import org.junit.Assert;
				public class Example {
					void run(String message, boolean condition) {
						assertTrue(message, condition);
						if (condition) Assert.fail(message);
					}
				}
				""", List.of("org.junit.Assert", "org.junit.jupiter.api.Assertions"));
		assertTrue(generated.contains("import static org.junit.Assert.*;"), generated);
	}

	@Test
	void retainsTypeImportUsedInsideReplacedAssertionArgument() throws Exception {
		String generated= applyAndUndo("""
				package test;
				import java.util.concurrent.TimeUnit;
				import org.junit.Assert;
				public class Example {
					void run(long expected, long seconds) {
						Assert.assertEquals(expected, TimeUnit.SECONDS.toMillis(seconds));
					}
				}
				""", List.of("org.junit.jupiter.api.Assertions"));
		assertTrue(generated.contains("import java.util.concurrent.TimeUnit;"), generated);
	}

	@Test
	void retainsStaticImportUsedInsideReplacedAssertionArgument() throws Exception {
		String generated= applyAndUndo("""
				package test;
				import static java.util.Objects.requireNonNull;
				import org.junit.Assert;
				public class Example {
					void run(Object expected, Object value) {
						Assert.assertEquals(expected, requireNonNull(value));
					}
				}
				""", List.of("org.junit.jupiter.api.Assertions"));
		assertTrue(generated.contains("import static java.util.Objects.requireNonNull;"), generated);
	}

	@Test
	void stillMigratesQualifiedMessageFirstAssertionAndRemovesObsoleteTypeImport() throws Exception {
		String generated= applyAndUndo("""
				package test;
				import org.junit.Assert;
				public class Example {
					void run(String message, int expected, int actual) {
						Assert.assertEquals(message, expected, actual);
					}
				}
				""", List.of("org.junit.jupiter.api.Assertions"));
		assertTrue(generated.contains("assertEquals(expected, actual, message)"), generated);
		assertFalse(generated.contains("import org.junit.Assert;"), generated);
	}

	private String applyAndUndo(String original, List<String> expectedOwners) throws Exception {
		ICompilationUnit unit= pack.createCompilationUnit("Example.java", original, false, null);
		CompilationUnit before= parse(unit);
		assertTrue(Arrays.stream(before.getProblems()).noneMatch(IProblem::isError),
				() -> Arrays.toString(before.getProblems()));
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		var operations= operations(before);
		assertFalse(operations.isEmpty(), "The qualified JUnit assertion must still migrate");
		var fix= new CompilationUnitRewriteOperationsFixCore("JUnit hint import safety", before,
				operations.toArray(CompilationUnitRewriteOperation[]::new));
		var change= fix.createChange(null);
		try {
			change.initializeValidationData(null);
			var undo= change.perform(null);
			assertNotNull(undo);
			try {
				String generated= unit.getSource();
				diagnostics.assertNoNewDiagnostics();
				CompilationUnit after= parse(unit);
				List<String> owners= new ArrayList<>();
				after.accept(new ASTVisitor() {
					@Override public boolean visit(MethodInvocation node) {
						if (node.getName().getIdentifier().startsWith("assert") || "fail".equals(node.getName().getIdentifier())) {
							var binding= node.resolveMethodBinding();
							assertNotNull(binding, node.toString());
							owners.add(binding.getDeclaringClass().getQualifiedName());
						}
						return true;
					}
				});
				assertEquals(expectedOwners, owners, generated);
				assertTrue(operations(after).isEmpty(), "A second pass must remain unchanged");
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
	}

	private LinkedHashSet<CompilationUnitRewriteOperation> operations(CompilationUnit root) {
		var operations= new LinkedHashSet<CompilationUnitRewriteOperation>();
		HintFileFixCore.findOperationsFromHint(root, hint, operations);
		return operations;
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
