/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
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
 * Tests for optimizing JUnit assertions.
 * Covers transformations like:
 * - assertTrue(a == b) → assertEquals(a, b)
 * - assertTrue(obj == null) → assertNull(obj)
 * - assertTrue(!condition) → assertFalse(condition)
 */
public class AssertOptimizationTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void optimizes_assertTrue_with_equality_check() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testValue() {
						int result = 42;
						Assertions.assertTrue(result == 42);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testValue() {
						int result = 42;
						Assertions.assertEquals(42, result);
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assertTrue_with_null_check() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNull() {
						String str = null;
						Assertions.assertTrue(str == null);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNull() {
						String str = null;
						Assertions.assertNull(str);
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assertTrue_with_not_null_check() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNotNull() {
						String str = "value";
						Assertions.assertTrue(str != null);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNotNull() {
						String str = "value";
						Assertions.assertNotNull(str);
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assertTrue_with_negation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
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
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

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
	public void optimizes_assertTrue_with_equals_call() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testEquals() {
						String expected = "hello";
						String actual = "hello";
						Assertions.assertTrue(actual.equals(expected));
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testEquals() {
						String expected = "hello";
						String actual = "hello";
						Assertions.assertEquals(expected, actual);
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assertFalse_with_equality_check() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNotEqual() {
						int result = 42;
						Assertions.assertFalse(result == 99);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNotEqual() {
						int result = 42;
						Assertions.assertNotEquals(99, result);
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assertions_with_message_parameter() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testWithMessage() {
						int result = 42;
						Assertions.assertTrue(result == 42, "Should be 42");
						Assertions.assertTrue(result != 0, "Should not be zero");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testWithMessage() {
						int result = 42;
						Assertions.assertEquals(42, result, "Should be 42");
						Assertions.assertNotEquals(0, result, "Should not be zero");
					}
				}
				"""
		}, null);
	}

	@Test
	public void optimizes_assertSame_for_object_identity() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testSameInstance() {
						String str1 = "test";
						String str2 = str1;
						Assertions.assertTrue(str1 == str2);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testSameInstance() {
						String str1 = "test";
						String str2 = str1;
						Assertions.assertSame(str2, str1);
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertEquals_with_string_literal_as_second_parameter() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testValue() {
						String result = "hello";
						Assertions.assertEquals(result, "hello");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testValue() {
						String result = "hello";
						Assertions.assertEquals("hello", result);
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertEquals_with_numeric_literal_as_second_parameter() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testCalculation() {
						int result = calculate();
						Assertions.assertEquals(result, 42);
					}
					
					private int calculate() {
						return 42;
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testCalculation() {
						int result = calculate();
						Assertions.assertEquals(42, result);
					}
					
					private int calculate() {
						return 42;
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertEquals_with_constant_as_second_parameter() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private static final int EXPECTED_VALUE = 100;
					
					@Test
					public void testValue() {
						int result = getValue();
						Assertions.assertEquals(result, EXPECTED_VALUE);
					}
					
					private int getValue() {
						return 100;
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private static final int EXPECTED_VALUE = 100;
					
					@Test
					public void testValue() {
						int result = getValue();
						Assertions.assertEquals(EXPECTED_VALUE, result);
					}
					
					private int getValue() {
						return 100;
					}
				}
				"""
		}, null);
	}

	@Test
	public void preserves_message_when_swapping_junit5_style() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testValue() {
						String result = "hello";
						Assertions.assertEquals(result, "hello", "Values should match");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testValue() {
						String result = "hello";
						Assertions.assertEquals("hello", result, "Values should match");
					}
				}
				"""
		}, null);
	}

	@Test
	public void does_not_swap_when_first_is_already_constant() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testValue() {
						String result = "hello";
						Assertions.assertEquals("hello", result);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testValue() {
						String result = "hello";
						Assertions.assertEquals("hello", result);
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertNotEquals_parameters() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNotEqual() {
						String result = "hello";
						Assertions.assertNotEquals(result, "world");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNotEqual() {
						String result = "hello";
						Assertions.assertNotEquals("world", result);
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertEquals_with_delta_parameter() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testDouble() {
						double result = getDouble();
						Assertions.assertEquals(result, 3.14, 0.01);
					}
					
					private double getDouble() {
						return 3.14;
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testDouble() {
						double result = getDouble();
						Assertions.assertEquals(3.14, result, 0.01);
					}
					
					private double getDouble() {
						return 3.14;
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertEquals_with_enum_constant() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					enum Status { ACTIVE, INACTIVE }
					
					@Test
					public void testStatus() {
						Status result = getStatus();
						Assertions.assertEquals(result, Status.ACTIVE);
					}
					
					private Status getStatus() {
						return Status.ACTIVE;
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					enum Status { ACTIVE, INACTIVE }
					
					@Test
					public void testStatus() {
						Status result = getStatus();
						Assertions.assertEquals(Status.ACTIVE, result);
					}
					
					private Status getStatus() {
						return Status.ACTIVE;
					}
				}
				"""
		}, null);
	}
}
