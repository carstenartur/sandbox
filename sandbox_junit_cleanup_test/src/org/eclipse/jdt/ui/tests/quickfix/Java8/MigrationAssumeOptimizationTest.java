/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/**
 * Tests for Assume optimization during JUnit 4→5 migration.
 * Tests combinations of migration + optimization transformations like:
 * - assumeTrue(!condition) → assumeFalse(condition)
 * - assumeFalse(!condition) → assumeTrue(condition)
 */
public class MigrationAssumeOptimizationTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void optimizes_assumeTrue_with_negation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assume;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testAssumption() {
						boolean condition = false;
						Assume.assumeTrue(!condition);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assumptions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testAssumption() {
						boolean condition = false;
						Assumptions.assumeTrue(!condition);
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assumeFalse_with_negation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assume;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testAssumption() {
						boolean condition = true;
						Assume.assumeFalse(!condition);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assumptions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testAssumption() {
						boolean condition = true;
						Assumptions.assumeFalse(!condition);
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assumeTrue_with_negation_and_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assume;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testAssumption() {
						boolean isWindows = false;
						Assume.assumeTrue("Test should run on non-Windows", !isWindows);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assumptions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testAssumption() {
						boolean isWindows = false;
						Assumptions.assumeTrue(!isWindows, "Test should run on non-Windows");
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assumeFalse_with_negation_and_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assume;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testAssumption() {
						boolean condition = true;
						Assume.assumeFalse("Condition should be true", !condition);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assumptions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testAssumption() {
						boolean condition = true;
						Assumptions.assumeFalse(!condition, "Condition should be true");
					}
				}
				"""
		}, null);
	}

	@Test
	public void handles_multiple_assumptions_with_negation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assume;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testMultipleAssumptions() {
						Assume.assumeTrue(!false);
						Assume.assumeFalse(!true);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assumptions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testMultipleAssumptions() {
						Assumptions.assumeTrue(!false);
						Assumptions.assumeFalse(!true);
					}
				}
				"""
		}, null);
	}
}
