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
 * Tests for Assert optimization during JUnit 4→5 migration.
 * Tests combinations of migration + optimization transformations like:
 * - assertEquals(actualValue, expectedValue) → assertEquals(expectedValue, actualValue)
 * - assertTrue(!condition) → assertFalse(condition)
 * - assertFalse(!condition) → assertTrue(condition)
 */
public class MigrationAssertOptimizationTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void swaps_assertEquals_parameters_when_actual_first() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					private int getResult() {
						return 99;
					}
					
					@Test
					public void testValue() {
						Assert.assertEquals(getResult(), 42);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private int getResult() {
						return 99;
					}
					
					@Test
					public void testValue() {
						Assertions.assertEquals(42, getResult());
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assertTrue_with_negation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testNegation() {
						boolean condition = false;
						Assert.assertTrue(!condition);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNegation() {
						boolean condition = false;
						Assertions.assertFalse(condition);
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assertFalse_with_negation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testNegation() {
						boolean condition = true;
						Assert.assertFalse(!condition);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNegation() {
						boolean condition = true;
						Assertions.assertTrue(condition);
					}
				}
				"""
		}, null);
	}

	@Test
	public void handles_assertEquals_with_message_parameter_order() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					private int getResult() {
						return 99;
					}
					
					@Test
					public void testWithMessage() {
						Assert.assertEquals("Expected value", getResult(), 42);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private int getResult() {
						return 99;
					}
					
					@Test
					public void testWithMessage() {
						Assertions.assertEquals(42, getResult(), "Expected value");
					}
				}
				"""
		}, null);
	}

	@Test
	public void handles_assertTrue_negation_with_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testNegationWithMessage() {
						boolean condition = false;
						Assert.assertTrue("Condition should be false", !condition);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNegationWithMessage() {
						boolean condition = false;
						Assertions.assertFalse(condition, "Condition should be false");
					}
				}
				"""
		}, null);
	}

	@Test
	public void handles_multiple_assertions_in_one_method() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testMultiple() {
						Assert.assertTrue(!false);
						Assert.assertFalse(!true);
						Assert.assertEquals("message", 42, 42);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testMultiple() {
						Assertions.assertFalse(false);
						Assertions.assertTrue(true);
						Assertions.assertEquals(42, 42, "message");
					}
				}
				"""
		}, null);
	}

	@Test
	public void handles_wildcard_static_imports() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import static org.junit.Assert.*;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testWithWildcard() {
						assertTrue(!false);
						assertFalse(!true);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.*;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testWithWildcard() {
						assertFalse(false);
						assertTrue(true);
					}
				}
				"""
		}, null);
	}
}
