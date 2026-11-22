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

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix.JUnitMigrationOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.CompilerDiagnosticAssertions;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.JUnitMigrationFixtureClasspath;

/** Lifecycle overrides must not gain diagnostics when their TestCase base is removed. */
class JUnit3LifecycleDiagnosticTest {

	@RegisterExtension
	final EclipseJava17 context= new EclipseJava17();

	enum Profile { STRICT, BEST_EFFORT, JUNIT6_COMPATIBILITY }

	static Stream<Arguments> lifecycleCases() {
		return Arrays.stream(Profile.values()).flatMap(profile -> Stream.of(
				Arguments.of(profile, "static setup", "setUp", "super.setUp(); changes++;", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(profile, "static teardown", "tearDown", "super.tearDown(); changes++;", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(profile, "only setup base call", "setUp", "super.setUp();", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(profile, "only teardown base call", "tearDown", "super.tearDown();", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(profile, "empty nested block", "setUp", "this.value++; { super.setUp(); }", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(profile, "empty try block", "tearDown", //$NON-NLS-1$ //$NON-NLS-2$
						"try { super.tearDown(); } finally { this.value++; }", false), //$NON-NLS-1$
				Arguments.of(profile, "unbraced callback", "setUp", "if (this.value == 0) super.setUp();", false), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(profile, "checked catch depends on base call", "setUp", //$NON-NLS-1$ //$NON-NLS-2$
						"try { super.setUp(); changes++; } catch (java.io.IOException exception) { this.value++; }", false), //$NON-NLS-1$
				Arguments.of(profile, "lifecycle super method reference", "setUp", //$NON-NLS-1$ //$NON-NLS-2$
						"this.value++; Hook hook= super::setUp; hook.invoke();", false), //$NON-NLS-1$
				Arguments.of(profile, "unrelated method receiver", "setUp", //$NON-NLS-1$ //$NON-NLS-2$
						"super.setUp(); new StringBuilder().append(1);", false), //$NON-NLS-1$
				Arguments.of(profile, "another instance field", "tearDown", //$NON-NLS-1$ //$NON-NLS-2$
						"super.tearDown(); Example other= new Example(); other.value++;", false), //$NON-NLS-1$
				Arguments.of(profile, "anonymous instance", "setUp", //$NON-NLS-1$ //$NON-NLS-2$
						"super.setUp(); new Object() { @Override public String toString() { return this.getClass().getName(); } }.toString();", false), //$NON-NLS-1$
				Arguments.of(profile, "removed assertion receiver", "setUp", //$NON-NLS-1$ //$NON-NLS-2$
						"super.setUp(); this.assertTrue(true);", false), //$NON-NLS-1$
				Arguments.of(profile, "removed receiver subtree", "setUp", //$NON-NLS-1$ //$NON-NLS-2$
						"super.setUp(); this.receiver().assertTrue(true);", false), //$NON-NLS-1$
				Arguments.of(profile, "unrelated receiver in lambda", "setUp", //$NON-NLS-1$ //$NON-NLS-2$
						"super.setUp(); Runnable action= () -> new StringBuilder().append(1); action.run();", false), //$NON-NLS-1$
				Arguments.of(profile, "preexisting empty callback", "setUp", "", true), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(profile, "explicit instance field", "setUp", "super.setUp(); this.value= 1;", true), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(profile, "implicit instance field", "tearDown", "super.tearDown(); value++;", true), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(profile, "implicit instance helper", "setUp", "super.setUp(); initialize();", true), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Arguments.of(profile, "owning instance in lambda", "setUp", //$NON-NLS-1$ //$NON-NLS-2$
						"super.setUp(); Runnable action= () -> this.value++; action.run();", true), //$NON-NLS-1$
				Arguments.of(profile, "retained assertion argument", "setUp", //$NON-NLS-1$ //$NON-NLS-2$
						"super.setUp(); TestCase.assertTrue(this.value >= 0);", true)));
	}

	@ParameterizedTest(name= "{0}: {1}") //$NON-NLS-1$
	@MethodSource("lifecycleCases") //$NON-NLS-1$
	void preservesLifecycleDiagnosticsAndOwningInstance(Profile profile, String description, String callback,
			String body, boolean expectMigration) throws Exception {
		String original= """
				package test;
				import junit.framework.TestCase;
				public class Example extends TestCase {
					private int value;
					private static int changes;
					@Override
					protected void $callback() throws Exception { $body }
					public void testValue() { assertTrue(this.value >= 0); }
					private void initialize() { this.value++; }
					private Example receiver() { return this; }
					private interface Hook { void invoke() throws Exception; }
				}
				""".replace("$callback", callback).replace("$body", body); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= JUnitMigrationFixtureClasspath.createJUnit3And5Root(context)
				.createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Example.java", original, false, null); //$NON-NLS-1$
		var options= Map.copyOf(unit.getJavaProject().getOptions(false));
		var classpath= unit.getJavaProject().getRawClasspath().clone();
		CompilationUnit before= parse(unit);
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		JUnitCleanUpCore cleanup= cleanup(profile);
		try {
			var status= cleanup.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null);
			assertFalse(status.hasError(), status.toString());
			var fix= cleanup.createFix(new CleanUpContext(unit, before));
			if (fix == null) {
				assertFalse(expectMigration, description);
				return;
			}
			var change= fix.createChange(null);
			try {
				change.initializeValidationData(null);
				var undo= change.perform(null);
				assertNotNull(undo);
				try {
					diagnostics.assertNoNewDiagnostics();
					assertTrue(expectMigration, description + " must retain its legacy lifecycle contract"); //$NON-NLS-1$
					CompilationUnit after= parse(unit);
					TypeDeclaration migrated= (TypeDeclaration) after.types().getFirst();
					assertNull(migrated.getSuperclassType(), unit.getSource());
					var migratedCallback= Arrays.stream(migrated.getMethods())
							.filter(method -> callback.equals(method.getName().getIdentifier())).findFirst().orElseThrow();
					assertEquals("org.junit.jupiter.api." + ("setUp".equals(callback) ? "BeforeEach" : "AfterEach"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
							migratedCallback.resolveBinding().getAnnotations()[0].getAnnotationType().getQualifiedName());
					cleanup.checkPostConditions(null);
					JUnitCleanUpCore second= cleanup(profile);
					try {
						assertFalse(second.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null).hasError());
						assertNull(second.createFix(new CleanUpContext(unit, after)), "Migration must be idempotent"); //$NON-NLS-1$
					} finally {
						second.checkPostConditions(null);
					}
				} finally {
					var redo= undo.perform(null);
					if (redo != null) redo.dispose();
					undo.dispose();
				}
			} finally {
				change.dispose();
			}
		} finally {
			cleanup.checkPostConditions(null);
			assertEquals(original, unit.getSource());
			diagnostics.assertRestored();
			assertEquals(options, unit.getJavaProject().getOptions(false));
			assertEquals(Arrays.asList(classpath), Arrays.asList(unit.getJavaProject().getRawClasspath()));
		}
	}

	private static JUnitCleanUpCore cleanup(Profile profile) {
		return new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, "true", //$NON-NLS-1$
				MYCleanUpConstants.JUNIT3_CLEANUP, "true", //$NON-NLS-1$
				MYCleanUpConstants.JUNIT_CLEANUP_3_TEST, "true", //$NON-NLS-1$
				JUnitMigrationOptions.BEST_EFFORT, Boolean.toString(profile == Profile.BEST_EFFORT),
				JUnitMigrationOptions.JUNIT6_COMPATIBILITY, Boolean.toString(profile == Profile.JUNIT6_COMPATIBILITY)));
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		assertTrue(Arrays.stream(root.getProblems()).noneMatch(IProblem::isError),
				() -> Arrays.toString(root.getProblems()));
		return root;
	}
}
