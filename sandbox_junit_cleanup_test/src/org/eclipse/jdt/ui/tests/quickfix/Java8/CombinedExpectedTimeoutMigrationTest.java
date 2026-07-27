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
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Tests coordinated migration of the two JUnit 4 {@code @Test} attributes. */
public class CombinedExpectedTimeoutMigrationTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void migratesExpectedThenTimeoutAtomically() throws CoreException {
		assertCombinedMigration("expected = IllegalArgumentException.class, timeout = 1000"); //$NON-NLS-1$
	}

	@Test
	public void migratesTimeoutThenExpectedAtomically() throws CoreException {
		assertCombinedMigration("timeout = 1000, expected = IllegalArgumentException.class"); //$NON-NLS-1$
	}

	@Test
	public void refusesPartialMigrationWhenOnlyExpectedIsEnabled() throws CoreException {
		ICompilationUnit unit= createTest("expected = IllegalArgumentException.class, timeout = 1000"); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void refusesPartialMigrationWhenOnlyTimeoutIsEnabled() throws CoreException {
		ICompilationUnit unit= createTest("expected = IllegalArgumentException.class, timeout = 1000"); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	private void assertCombinedMigration(String attributes) throws CoreException {
		ICompilationUnit unit= createTest(attributes);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.assertThrows;

				import java.util.concurrent.TimeUnit;

				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.Timeout;

				public class MyTest {
					@Test
					@Timeout(value = 1, unit = TimeUnit.SECONDS)
					public void testBoth() {
						assertThrows(IllegalArgumentException.class, () -> {
							throw new IllegalArgumentException("Expected");
						});
					}
				}
				""" }, null);
	}

	private ICompilationUnit createTest(String attributes) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		return pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Test;

				public class MyTest {
					@Test(__ATTRIBUTES__)
					public void testBoth() {
						throw new IllegalArgumentException("Expected");
					}
				}
				""".replace("__ATTRIBUTES__", attributes), false, null); //$NON-NLS-1$
	}
}
