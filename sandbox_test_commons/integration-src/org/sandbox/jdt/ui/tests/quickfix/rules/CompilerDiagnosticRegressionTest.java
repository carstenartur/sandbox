/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;

import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;

import org.eclipse.jdt.ui.cleanup.ICleanUp;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/** Characterizes the default before/after compiler-diagnostic guard. */
class CompilerDiagnosticRegressionTest {

	@RegisterExtension
	final DiagnosticHarness context= new DiagnosticHarness();

	@Test
	void rejectsANewWarning() throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.WARNING);
		String before= source("int value= 1; System.out.println(value);"); //$NON-NLS-1$
		String after= source("int value= 1;"); //$NON-NLS-1$
		ICompilationUnit unit= createUnit(before);
		context.output(after);

		AssertionError failure= assertThrows(AssertionError.class, () ->
				context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { after }, null));

		assertTrue(failure.getMessage().contains("WARNING"), failure.getMessage()); //$NON-NLS-1$
	}

	@Test
	void whitespaceNormalizingAssertionAlsoRejectsANewWarning() throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.WARNING);
		String before= source("int value= 1; System.out.println(value);"); //$NON-NLS-1$
		String after= source("int value= 1;"); //$NON-NLS-1$
		ICompilationUnit unit= createUnit(before);
		context.output(after);

		assertThrows(AssertionError.class, () ->
				context.assertRefactoringResultAsExpectedNormalizingWhitespace(
						new ICompilationUnit[] { unit }, new String[] { after }, null));
	}

	@Test
	void directExecutionChecksUnusedLocalsEvenWithLenientProjectOptions() throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.IGNORE);
		ICompilationUnit unit= createUnit(source("int value= 1; System.out.println(value);"));
		context.output(source("int value= 1;"));
		var failure= assertThrows(AssertionError.class, () -> context.performRefactoring(new ICompilationUnit[] { unit }, null));
		assertTrue(failure.getMessage().contains("WARNING"), failure.getMessage());
		assertEquals(JavaCore.IGNORE, context.getJavaProject().getOption(JavaCore.COMPILER_PB_UNUSED_LOCAL, true));
	}

	@Test
	void rejectsANewCompilerError() throws Exception {
		String before= source("int value= 1; System.out.println(value);"); //$NON-NLS-1$
		String after= source("int value= missing; System.out.println(value);"); //$NON-NLS-1$
		ICompilationUnit unit= createUnit(before);
		context.output(after);

		AssertionError failure= assertThrows(AssertionError.class, () ->
				context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { after }, null));

		assertTrue(failure.getMessage().contains("ERROR"), failure.getMessage()); //$NON-NLS-1$
	}

	@Test
	void rejectsAnAdditionalOccurrenceOfTheSameError() throws Exception {
		String before= source("missing();"); //$NON-NLS-1$
		String after= source("missing(); missing();"); //$NON-NLS-1$
		ICompilationUnit unit= createUnit(before);
		context.output(after);

		AssertionError failure= assertThrows(AssertionError.class, () ->
				context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { after }, null));

		assertTrue(failure.getMessage().contains("before=1 after=2"), failure.getMessage()); //$NON-NLS-1$
	}

	@Test
	void changingTheDiagnosticArgumentsIsARegression() throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.WARNING);
		String before= source("int first= 1;"); //$NON-NLS-1$
		String after= source("int second= 1;"); //$NON-NLS-1$
		ICompilationUnit unit= createUnit(before);
		context.output(after);

		assertThrows(AssertionError.class, () ->
				context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { after }, null));
	}

	@Test
	void diagnosticsAreComparedPerCompilationUnit() throws Exception {
		String broken= source("First", "missing();"); //$NON-NLS-1$ //$NON-NLS-2$
		String clean= source("Second", "System.out.println(1);"); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit first= createUnit("First.java", broken); //$NON-NLS-1$
		ICompilationUnit second= createUnit("Second.java", clean); //$NON-NLS-1$
		String fixedFirst= source("First", "System.out.println(1);");
		String brokenSecond= source("Second", "missing();");
		context.output(fixedFirst, brokenSecond);

		AssertionError failure= assertThrows(AssertionError.class, () ->
				context.assertRefactoringResultAsExpected(
						new ICompilationUnit[] { first, second }, new String[] { fixedFirst, brokenSecond }, null));

		assertTrue(failure.getMessage().contains("Second.java"), failure.getMessage()); //$NON-NLS-1$
	}

	@Test
	void existingStaticSuggestionSurvivesAChangeOfParameterType() throws Exception {
		String before= "package test; class Sample { int run(int input) { return input; } }"; //$NON-NLS-1$
		String after= before.replace("int run(int", "long run(long"); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= createUnit(before);
		context.output(after);
		assertDoesNotThrow(() -> context.performRefactoring(new ICompilationUnit[] { unit }, null));
	}

	@Test
	void movingAStaticSuggestionBetweenOverloadsIsARegression() throws Exception {
		String before= "package test; class Sample { int value; int run(int x) { return x; } long run(long x) { return value + x; } }"; //$NON-NLS-1$
		String after= before.replace("return x;", "return value + x;").replace("long x) { return value + x;", "long x) { return x;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		ICompilationUnit unit= createUnit(before);
		context.output(after);
		AssertionError failure= assertThrows(AssertionError.class,
				() -> context.performRefactoring(new ICompilationUnit[] { unit }, null));
		assertTrue(failure.getMessage().contains("WARNING"), failure.getMessage()); //$NON-NLS-1$
	}

	@Test
	void undoMustRestoreTheOriginalWarningInEachUnit() throws Exception {
		String warning= source("int result= 1;"); //$NON-NLS-1$
		String clean= source("int result= 1; System.out.println(result);"); //$NON-NLS-1$
		ICompilationUnit unit= createUnit(warning);
		var before= CompilerDiagnosticAssertions.capture(unit);
		unit.getBuffer().setContents(clean);
		before.assertNoNewDiagnostics();
		assertThrows(AssertionError.class, before::assertRestored);
		unit.getBuffer().setContents(warning);
		before.assertRestored();
	}

	@Test
	void lineMovementOfTheSameExistingErrorIsAllowed() throws Exception {
		String before= """
				package test;
				class Sample {
					void run() {
						missing();
					}
				}
				""";
		String after= """
				package test;
				class Sample {

					void run() {
						missing();
					}
				}
				""";
		ICompilationUnit unit= createUnit(before);
		context.output(after);

		assertDoesNotThrow(() ->
				context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { after }, null));
	}

	@Test
	void importInsertionPreservesAnExistingUnassignedResourceWarning() throws Exception {
		String original= resourceSource();
		String shifted= original.replace("import java.lang.ref.WeakReference;", //$NON-NLS-1$
				"import java.lang.ref.WeakReference;\nimport java.util.zip.ZipFile;") //$NON-NLS-1$
				.replace("WeakReference<java.util.zip.ZipFile>", "WeakReference<ZipFile>"); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit unit= createUnit(original);
		assertResourceWarnings(unit, 2);
		var options= context.getJavaProject().getOptions(false);
		var baseline= CompilerDiagnosticAssertions.capture(unit);
		try {
			unit.getBuffer().setContents(shifted);
			assertResourceWarnings(unit, 2);
			assertDoesNotThrow(() -> {
				baseline.assertNoNewDiagnostics();
			});
		} finally {
			unit.getBuffer().setContents(original);
			baseline.assertRestored();
			assertEquals(options, context.getJavaProject().getOptions(false));
		}
	}

	@Test
	void anAdditionalUnassignedResourceWarningIsRejected() throws Exception {
		String original= resourceSource();
		String additional= original.replace("return !equal;", """
				if (second.get() != null) {
					return equal;
				}
				return !equal;
				"""); //$NON-NLS-1$
		assertResourceChangeRejected(original, additional, 4);
	}

	@Test
	void anotherUnassignedResourceWithTheSameWarningCountIsRejected() throws Exception {
		String original= resourceSource();
		assertResourceChangeRejected(original, original.replace("first.get()", "second.get()"), 2); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void movingAnUnassignedResourceWarningBetweenIdenticalExpressionsIsRejected() throws Exception {
		String leaking= """
				if (first.get() != null) {
					return;
				}
				""";
		String closed= """
				try (java.util.zip.ZipFile file= first.get()) {
					System.out.println(file.size());
				}
				""";
		String original= resourceLocations(leaking, closed);
		assertResourceChangeRejected(original, resourceLocations(closed, leaking), 2);
	}

	private void assertResourceChangeRejected(String original, String changed, long expectedWarnings) throws Exception {
		ICompilationUnit unit= createUnit(original);
		assertResourceWarnings(unit, 2);
		var baseline= CompilerDiagnosticAssertions.capture(unit);
		try {
			unit.getBuffer().setContents(changed);
			assertResourceWarnings(unit, expectedWarnings);
			AssertionError failure= assertThrows(AssertionError.class, baseline::assertNoNewDiagnostics);
			assertTrue(failure.getMessage().contains("id=" + IProblem.PotentiallyUnclosedCloseable), failure.getMessage()); //$NON-NLS-1$
		} finally {
			unit.getBuffer().setContents(original);
			baseline.assertRestored();
		}
	}

	private static void assertResourceWarnings(ICompilationUnit unit, long expected) {
		var problems= CompilerDiagnosticAssertions.captureCompilerDiagnostics(new ICompilationUnit[] { unit }).get(unit);
		assertTrue(problems.keySet().stream().noneMatch(CompilerDiagnosticAssertions.CompilerProblemKey::error), problems.toString());
		long count= problems.entrySet().stream()
				.filter(entry -> entry.getKey().id() == IProblem.PotentiallyUnclosedCloseable)
				.mapToLong(Map.Entry::getValue).sum();
		assertEquals(expected, count, problems.toString());
	}

	private static String resourceSource() {
		return """
				package test;
				import java.lang.ref.WeakReference;
				class Sample {
					static boolean run(WeakReference<java.util.zip.ZipFile> first, WeakReference<java.util.zip.ZipFile> second) {
						boolean equal= first == second;
						if (first.get() != null) {
							return equal;
						}
						return !equal;
					}
				}
				""";
	}

	private static String resourceLocations(String firstBlock, String secondBlock) {
		return """
				package test;
				import java.lang.ref.WeakReference;
				class Sample {
					static void run(WeakReference<java.util.zip.ZipFile> first, boolean chooseFirst) throws Exception {
						if (chooseFirst) {
							FIRST_BLOCK
						} else {
							SECOND_BLOCK
						}
					}
				}
				""".replace("FIRST_BLOCK", firstBlock).replace("SECOND_BLOCK", secondBlock); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void removingAnExistingWarningIsAllowed() throws Exception {
		context.getJavaProject().setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL, JavaCore.WARNING);
		String before= source("int value= 1;"); //$NON-NLS-1$
		String after= source("int value= 1; System.out.println(value);"); //$NON-NLS-1$
		ICompilationUnit unit= createUnit(before);
		context.output(after);

		assertDoesNotThrow(() ->
				context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] { after }, null));
	}

	private ICompilationUnit createUnit(String source) throws CoreException {
		return createUnit("Sample.java", source); //$NON-NLS-1$
	}

	private ICompilationUnit createUnit(String name, String source) throws CoreException {
		return context.getSourceFolder().createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit(name, source, true, null);
	}

	private static String source(String statement) {
		return source("Sample", statement); //$NON-NLS-1$
	}

	private static String source(String typeName, String statement) {
		return """
				package test;
				class TYPE {
					void run() {
						STATEMENT
					}
				}
				""".replace("TYPE", typeName).replace("STATEMENT", statement); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static final class DiagnosticHarness extends AbstractEclipseJava {
		private String[] output= new String[0];

		DiagnosticHarness() {
			super("testresources/rtstubs_22.jar", JavaCore.VERSION_22); //$NON-NLS-1$
		}

		void output(String... source) {
			output= source.clone();
		}

		@Override
		protected RefactoringStatus runRefactoring(CleanUpRefactoring ref, ICompilationUnit[] units,
				ICleanUp[] cleanUps, Set<String> expectedGroups) throws CoreException {
			if (units.length != output.length) {
				throw new CoreException(org.eclipse.core.runtime.Status.error("Missing simulated refactoring output")); //$NON-NLS-1$
			}
			for (int index= 0; index < units.length; index++) {
				units[index].getBuffer().setContents(output[index]);
			}
			return new RefactoringStatus();
		}
	}
}
