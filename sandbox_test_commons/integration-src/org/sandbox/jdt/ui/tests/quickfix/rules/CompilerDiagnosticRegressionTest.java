/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix.rules;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;

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
		return context.getSourceFolder().createPackageFragment("test", false, null) //$NON-NLS-1$
				.createCompilationUnit("Sample.java", source, true, null); //$NON-NLS-1$
	}

	private static String source(String statement) {
		return """
				package test;
				class Sample {
					void run() {
						STATEMENT
					}
				}
				""".replace("STATEMENT", statement); //$NON-NLS-1$
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
		protected RefactoringStatus performRefactoring(CleanUpRefactoring ref, ICompilationUnit[] units,
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
