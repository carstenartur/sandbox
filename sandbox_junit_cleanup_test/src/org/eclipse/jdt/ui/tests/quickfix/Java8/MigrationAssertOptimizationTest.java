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
 * Tests for Assert migration from JUnit 4 to JUnit 5.
 * 
 * NOTE: Optimization (e.g., assertTrue(!x) → assertFalse(x)) is only applied to 
 * already-migrated JUnit 5 assertions. During migration from JUnit 4, only the 
 * import/class migration is performed. To get optimized assertions, run the 
 * optimization cleanup again after migration.
 * 
 * Tests verify:
 * - Assert → Assertions class migration
 * - Message parameter reordering (first to last position)
 * - Import updates (org.junit.Assert → org.junit.jupiter.api.Assertions)
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
	public void migrates_assertEquals_preserving_parameter_order() throws CoreException {
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

		// Note: Parameter order is preserved during migration. Optimization would swap them
		// but that only works on already-migrated JUnit 5 assertions.
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
						Assertions.assertEquals(getResult(), 42);
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertTrue_preserving_negation() throws CoreException {
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

		// Note: Negation is preserved during migration. Optimization (assertTrue(!x) → assertFalse(x))
		// only works on already-migrated JUnit 5 assertions.
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNegation() {
						boolean condition = false;
						Assertions.assertTrue(!condition);
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertFalse_preserving_negation() throws CoreException {
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

		// Note: Negation is preserved during migration. Optimization (assertFalse(!x) → assertTrue(x))
		// only works on already-migrated JUnit 5 assertions.
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNegation() {
						boolean condition = true;
						Assertions.assertFalse(!condition);
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
						Assertions.assertEquals(getResult(), 42, "Expected value");
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
						Assertions.assertTrue(!condition, "Condition should be false");
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
						Assertions.assertTrue(!false);
						Assertions.assertFalse(!true);
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
						assertTrue(!false);
						assertFalse(!true);
					}
				}
				"""
		}, null);
	}
}
