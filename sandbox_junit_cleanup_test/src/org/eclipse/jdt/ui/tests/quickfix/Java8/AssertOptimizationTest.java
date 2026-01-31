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
						Assertions.assertEquals(result, 42);
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
						Assertions.assertNotEquals(result, 99);
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
						Assertions.assertEquals(result, 42, "Should be 42");
						Assertions.assertNotEquals(result, 0, "Should not be zero");
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
						Assertions.assertSame(str1, str2);
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertArrayEquals_parameters() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private int[] getArray() {
						return new int[]{4, 5, 6};
					}
					
					@Test
					public void testArrayEquals() {
						Assertions.assertArrayEquals(getArray(), new int[]{1, 2, 3});
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
					private int[] getArray() {
						return new int[]{4, 5, 6};
					}
					
					@Test
					public void testArrayEquals() {
						Assertions.assertArrayEquals(new int[]{1, 2, 3}, getArray());
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertSame_parameters() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private static final String SINGLETON = "singleton";
					
					private String getInstance() {
						return "test";
					}
					
					@Test
					public void testSameInstance() {
						Assertions.assertSame(getInstance(), SINGLETON);
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
					private static final String SINGLETON = "singleton";
					
					private String getInstance() {
						return "test";
					}
					
					@Test
					public void testSameInstance() {
						Assertions.assertSame(SINGLETON, getInstance());
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertNotSame_parameters() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private static final Object EXPECTED = new Object();
					
					private Object getObject() {
						return new Object();
					}
					
					@Test
					public void testNotSame() {
						Assertions.assertNotSame(getObject(), EXPECTED);
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
					private static final Object EXPECTED = new Object();
					
					private Object getObject() {
						return new Object();
					}
					
					@Test
					public void testNotSame() {
						Assertions.assertNotSame(EXPECTED, getObject());
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertIterableEquals_parameters() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import java.util.List;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private List<Integer> getList() {
						return List.of(4, 5, 6);
					}
					
					@Test
					public void testIterableEquals() {
						Assertions.assertIterableEquals(getList(), List.of(1, 2, 3));
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import java.util.List;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private List<Integer> getList() {
						return List.of(4, 5, 6);
					}
					
					@Test
					public void testIterableEquals() {
						Assertions.assertIterableEquals(List.of(1, 2, 3), getList());
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertLinesMatch_parameters() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import java.util.List;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private List<String> getLines() {
						return List.of("line3", "line4");
					}
					
					@Test
					public void testLinesMatch() {
						Assertions.assertLinesMatch(getLines(), List.of("line1", "line2"));
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT_OPTIMIZATION);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import java.util.List;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private List<String> getLines() {
						return List.of("line3", "line4");
					}
					
					@Test
					public void testLinesMatch() {
						Assertions.assertLinesMatch(List.of("line1", "line2"), getLines());
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertEquals_with_constant_field() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private static final int EXPECTED_VALUE = 42;
					
					private int getResult() {
						return 99;
					}
					
					@Test
					public void testValue() {
						Assertions.assertEquals(getResult(), EXPECTED_VALUE);
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
					private static final int EXPECTED_VALUE = 42;
					
					private int getResult() {
						return 99;
					}
					
					@Test
					public void testValue() {
						Assertions.assertEquals(EXPECTED_VALUE, getResult());
					}
				}
				"""
		}, null);
	}

	@Test
	public void swaps_assertArrayEquals_with_getBytes() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					private byte[] getBytes() {
						return new byte[]{1, 2, 3};
					}
					
					@Test
					public void testBytes() {
						Assertions.assertArrayEquals(getBytes(), "test".getBytes());
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
					private byte[] getBytes() {
						return new byte[]{1, 2, 3};
					}
					
					@Test
					public void testBytes() {
						Assertions.assertArrayEquals("test".getBytes(), getBytes());
					}
				}
				"""
		}, null);
	}
}
