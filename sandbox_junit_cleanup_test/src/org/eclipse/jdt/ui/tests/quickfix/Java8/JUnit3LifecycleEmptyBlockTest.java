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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.TryStatement;
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

/** Complete-unit regression for MultiReleaseJarCompilerToolTest's teardown shape. */
class JUnit3LifecycleEmptyBlockTest {

	@RegisterExtension
	final EclipseJava17 context= new EclipseJava17();

	enum Profile { STRICT, BEST_EFFORT, JUNIT6_COMPATIBILITY }
	enum Shape { PLAIN, CATCH, RESOURCE, TWO_SUPER_CALLS, NONEMPTY_FINALLY, COMMENTED_FINALLY, COMMENT_IN_CALL }

	static Stream<Arguments> scenarios() {
		return Arrays.stream(Profile.values()).flatMap(profile ->
				Arrays.stream(Shape.values()).map(shape -> Arguments.of(profile, shape)));
	}

	@ParameterizedTest
	@MethodSource("scenarios") //$NON-NLS-1$
	void migratingLifecycleCallsPreservesBlockDiagnosticsAndScope(Profile profile, Shape shape) throws Exception {
		var pack= JUnitMigrationFixtureClasspath.createJUnit3And5Root(context)
				.createPackageFragment("test", false, null); //$NON-NLS-1$
		// The pinned compiler.tool.tests preferences ignore this warning. The shared
		// diagnostic assertion promotes ignored/info severities equally before/after.
		var options= context.getJavaProject().getOptions(false);
		options.put(JavaCore.COMPILER_PB_UNDOCUMENTED_EMPTY_BLOCK, JavaCore.IGNORE);
		context.getJavaProject().setOptions(options);
		String body= switch (shape) {
			case PLAIN, TWO_SUPER_CALLS -> "try { int count= 1; this.value+= count; }"; //$NON-NLS-1$
			case CATCH -> "try { this.value++; } catch (RuntimeException exception) { this.value+= 2; }"; //$NON-NLS-1$
			case RESOURCE -> "try (var reader= new java.io.StringReader(\"x\")) { this.value+= reader.read(); }"; //$NON-NLS-1$
			case NONEMPTY_FINALLY, COMMENTED_FINALLY, COMMENT_IN_CALL -> "try { this.value++; }"; //$NON-NLS-1$
		};
		String finallyBody= switch (shape) {
			case TWO_SUPER_CALLS -> "super.tearDown(); super.tearDown();"; //$NON-NLS-1$
			case NONEMPTY_FINALLY -> "this.value++; super.tearDown();"; //$NON-NLS-1$
			case COMMENTED_FINALLY -> "super.tearDown(); /* Resource cleanup is complete here. */"; //$NON-NLS-1$
			case COMMENT_IN_CALL -> "super./* Legacy base call */tearDown();"; //$NON-NLS-1$
			default -> "super.tearDown();"; //$NON-NLS-1$
		};
		String original= """
				package test;
				import junit.framework.TestCase;
				public class Example extends TestCase {
					private int value;
					@Override
					protected void setUp() throws Exception {
						super.setUp();
						this.value= 10;
					}
					@Override
					protected void tearDown() throws Exception {
						$try finally { $finally }
						int count= 2;
						this.value+= count;
					}
					public void testValue() { assertEquals(10, this.value); }
				}
				""".replace("$try", body).replace("$finally", finallyBody); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= pack.createCompilationUnit("Example.java", original, false, null); //$NON-NLS-1$
		CompilationUnit before= parse(unit);
		assertTrue(Arrays.stream(before.getProblems()).noneMatch(problem -> problem.isError()),
				() -> Arrays.toString(before.getProblems()));
		var diagnostics= CompilerDiagnosticAssertions.capture(unit);
		JUnitCleanUpCore cleanup= cleanup(profile);
		var status= cleanup.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null);
		assertFalse(status.hasError(), status.toString());
		var fix= cleanup.createFix(new CleanUpContext(unit, before));
		assertNotNull(fix, "A supported JUnit 3 lifecycle must migrate"); //$NON-NLS-1$
		var change= fix.createChange(null);
		try {
			change.initializeValidationData(null);
			var undo= change.perform(null);
			assertNotNull(undo);
			try {
				diagnostics.assertNoNewDiagnostics();
				CompilationUnit after= parse(unit);
				TypeDeclaration migrated= (TypeDeclaration) after.types().getFirst();
				assertNull(migrated.getSuperclassType(), unit.getSource());
				List<String> annotations= Arrays.stream(migrated.getMethods())
						.map(MethodDeclaration::resolveBinding)
						.flatMap(binding -> Arrays.stream(binding.getAnnotations()))
						.map(binding -> binding.getAnnotationType().getQualifiedName()).sorted().toList();
				assertEquals(List.of("org.junit.jupiter.api.AfterEach", "org.junit.jupiter.api.BeforeEach", //$NON-NLS-1$ //$NON-NLS-2$
						"org.junit.jupiter.api.Test"), annotations); //$NON-NLS-1$
				assertFalse(unit.getSource().contains("super."), unit.getSource()); //$NON-NLS-1$
				int[] clauses= new int[3];
				after.accept(new ASTVisitor() {
					@Override public boolean visit(TryStatement statement) {
						clauses[0]+= statement.getFinally() == null ? 0 : 1;
						clauses[1]+= statement.catchClauses().size();
						clauses[2]+= statement.resources().size();
						return true;
					}
				});
				assertEquals(shape == Shape.NONEMPTY_FINALLY || shape == Shape.COMMENTED_FINALLY ? 1 : 0, clauses[0]);
				assertEquals(shape == Shape.CATCH ? 1 : 0, clauses[1]);
				assertEquals(shape == Shape.RESOURCE ? 1 : 0, clauses[2]);
				assertEquals(effects(before), effects(after), "Lifecycle state changes must survive"); //$NON-NLS-1$
				if (shape == Shape.COMMENTED_FINALLY) {
					assertTrue(unit.getSource().contains("/* Resource cleanup is complete here. */")); //$NON-NLS-1$
				}
				cleanup.checkPostConditions(null);
				JUnitCleanUpCore second= cleanup(profile);
				assertFalse(second.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null).hasError());
				assertNull(second.createFix(new CleanUpContext(unit, after)), "Migration must be idempotent"); //$NON-NLS-1$
				second.checkPostConditions(null);
			} finally {
				var redo= undo.perform(null);
				if (redo != null) redo.dispose();
				undo.dispose();
			}
		} finally {
			change.dispose();
			cleanup.checkPostConditions(null);
			assertEquals(original, unit.getSource());
			diagnostics.assertRestored();
			assertEquals(JavaCore.IGNORE, context.getJavaProject().getOption(JavaCore.COMPILER_PB_UNDOCUMENTED_EMPTY_BLOCK, true));
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
		return (CompilationUnit) parser.createAST(null);
	}

	private static List<String> effects(CompilationUnit root) {
		List<String> result= new ArrayList<>();
		root.accept(new ASTVisitor() {
			@Override public boolean visit(Assignment node) {
				result.add(node.toString());
				return true;
			}
			@Override public boolean visit(PostfixExpression node) {
				result.add(node.toString());
				return true;
			}
		});
		return result;
	}
}
