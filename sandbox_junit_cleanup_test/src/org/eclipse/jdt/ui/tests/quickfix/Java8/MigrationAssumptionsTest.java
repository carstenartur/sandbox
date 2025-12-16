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
 * Tests for migrating JUnit 4 assumptions to JUnit 5.
 * Covers Assume.* → Assumptions.* transformations.
 */
public class MigrationAssumptionsTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void migrates_assumeTrue_with_and_without_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assume;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testWithPrecondition() {
						Assume.assumeTrue("Precondition failed", true);
						Assume.assumeTrue(true);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assumptions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testWithPrecondition() {
						Assumptions.assumeTrue(true, "Precondition failed");
						Assumptions.assumeTrue(true);
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assumeFalse_with_and_without_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assume;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testWithPrecondition() {
						Assume.assumeFalse("Precondition not met", false);
						Assume.assumeFalse(false);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assumptions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testWithPrecondition() {
						Assumptions.assumeFalse(false, "Precondition not met");
						Assumptions.assumeFalse(false);
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assumeNotNull() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assume;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testWithPrecondition() {
						Assume.assumeNotNull("Value should not be null", new Object());
						Assume.assumeNotNull(new Object());
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assumptions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testWithPrecondition() {
						Assumptions.assumeNotNull(new Object(), "Value should not be null");
						Assumptions.assumeNotNull(new Object());
					}
				}
				"""
		}, null);
	}

	@Disabled("Produces unused Assumptions import - needs cleanup fix")
	@Test
	public void migrates_assumeThat_with_hamcrest() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.hamcrest.CoreMatchers;
				import org.junit.Assume;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testWithPrecondition() {
						Assume.assumeThat("Value should match condition", 42, CoreMatchers.is(42));
						Assume.assumeThat(42, CoreMatchers.is(42));
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.hamcrest.junit.MatcherAssume.assumeThat;
				
				import org.hamcrest.CoreMatchers;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testWithPrecondition() {
						assumeThat("Value should match condition", 42, CoreMatchers.is(42));
						assumeThat(42, CoreMatchers.is(42));
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_combined_assumptions_in_test() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assume;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testWithMultiplePreconditions() {
						Assume.assumeTrue("System property set", System.getProperty("test.run") != null);
						Assume.assumeNotNull("Value exists", getValue());
						// Test logic here
					}
					
					private Object getValue() {
						return new Object();
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assumptions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testWithMultiplePreconditions() {
						Assumptions.assumeTrue(System.getProperty("test.run") != null, "System property set");
						Assumptions.assumeNotNull(getValue(), "Value exists");
						// Test logic here
					}
					
					private Object getValue() {
						return new Object();
					}
				}
				"""
		}, null);
	}
}
