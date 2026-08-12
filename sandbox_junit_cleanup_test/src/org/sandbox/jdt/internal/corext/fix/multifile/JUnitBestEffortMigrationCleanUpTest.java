/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix.JUnitMigrationOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** End-to-end cleanup policy tests for explicit non-atomic JUnit migration. */
public class JUnitBestEffortMigrationCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED);
	}

	@Test
	public void strictModeQuarantinesTheCompleteUnsupportedCompilationUnit() throws CoreException {
		ICompilationUnit unit= createSource();

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void bestEffortModeKeepsSafeChangesAndAddsActionableTodoScaffold() throws CoreException {
		context.enable(JUnitMigrationOptions.BEST_EFFORT);
		ICompilationUnit unit= createSource();

		String expected= """
				package test;

				import static org.junit.jupiter.api.Assertions.assertEquals;

				import java.util.Arrays;
				import java.util.List;

				import org.junit.jupiter.api.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameter;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class FieldInjectionTest {
					@Parameter
					public int value;

					@Parameters
					public static List<Object[]> data() {
						return Arrays.asList(new Object[][] { { 1 } });
					}

					@Test
					public void verifiesValue() {
						assertEquals(1, value);
					}

					/**
					 * @todo Sandbox JUnit migration gap parameterized:test.FieldInjectionTest (PARAMETERIZED_FIELD_INJECTION): @Parameterized.Parameter field injection is not represented by the constructor-based local rewrite. Manual completion: Replace field injection or the custom provider with explicit Jupiter method arguments/Arguments sources, then remove the Parameterized runner and constructor coupling.
					 */
					private static void sandboxJUnitMigrationTodoParameterizedFieldInjection() {
						throw new UnsupportedOperationException("Manual JUnit migration required: PARAMETERIZED_FIELD_INJECTION");
					}
				}
				"""; //$NON-NLS-1$

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { unit }, new String[] { expected }, null);
	}

	private ICompilationUnit createSource() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		return pack.createCompilationUnit("FieldInjectionTest.java", """
				package test;

				import static org.junit.Assert.assertEquals;

				import java.util.Arrays;
				import java.util.List;

				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameter;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class FieldInjectionTest {
					@Parameter
					public int value;

					@Parameters
					public static List<Object[]> data() {
						return Arrays.asList(new Object[][] { { 1 } });
					}

					@Test
					public void verifiesValue() {
						assertEquals(1, value);
					}
				}
				""", false, null); //$NON-NLS-1$
	}
}
