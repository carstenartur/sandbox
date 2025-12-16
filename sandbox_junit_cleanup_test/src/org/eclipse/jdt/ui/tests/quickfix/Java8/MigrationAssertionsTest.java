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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/**
 * Tests for migrating JUnit 4 assertions to JUnit 5.
 * Covers Assert.* → Assertions.* transformations.
 */
public class MigrationAssertionsTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@ParameterizedTest
	@EnumSource(AssertionCases.class)
	public void migrates_junit4_assertions_to_junit5(AssertionCases testCase) throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", testCase.given, true, null);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { testCase.expected }, null);
	}

	@Test
	public void migrates_assertEquals_with_message_parameter_order() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testEquals() {
						Assert.assertEquals("Values should match", 42, 42);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testEquals() {
						Assertions.assertEquals(42, 42, "Values should match");
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertEquals_without_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testEquals() {
						Assert.assertEquals(42, 42);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testEquals() {
						Assertions.assertEquals(42, 42);
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertArrayEquals() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testArrays() {
						int[] expected = {1, 2, 3};
						int[] actual = {1, 2, 3};
						Assert.assertArrayEquals("Arrays should match", expected, actual);
						Assert.assertArrayEquals(expected, actual);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testArrays() {
						int[] expected = {1, 2, 3};
						int[] actual = {1, 2, 3};
						Assertions.assertArrayEquals(expected, actual, "Arrays should match");
						Assertions.assertArrayEquals(expected, actual);
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertTrue_and_assertFalse() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testBooleans() {
						Assert.assertTrue("Should be true", true);
						Assert.assertTrue(true);
						Assert.assertFalse("Should be false", false);
						Assert.assertFalse(false);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testBooleans() {
						Assertions.assertTrue(true, "Should be true");
						Assertions.assertTrue(true);
						Assertions.assertFalse(false, "Should be false");
						Assertions.assertFalse(false);
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertNull_and_assertNotNull() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testNull() {
						Assert.assertNull("Should be null", null);
						Assert.assertNull(null);
						Assert.assertNotNull("Should not be null", new Object());
						Assert.assertNotNull(new Object());
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNull() {
						Assertions.assertNull(null, "Should be null");
						Assertions.assertNull(null);
						Assertions.assertNotNull(new Object(), "Should not be null");
						Assertions.assertNotNull(new Object());
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertSame_and_assertNotSame() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testIdentity() {
						Object o = "test";
						Assert.assertSame("Objects should be same", o, o);
						Assert.assertSame(o, o);
						Assert.assertNotSame("Objects should not be same", "test1", "test2");
						Assert.assertNotSame("test1", "test2");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testIdentity() {
						Object o = "test";
						Assertions.assertSame(o, o, "Objects should be same");
						Assertions.assertSame(o, o);
						Assertions.assertNotSame("test1", "test2", "Objects should not be same");
						Assertions.assertNotSame("test1", "test2");
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_fail_with_and_without_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testFail() {
						Assert.fail("This should fail");
						Assert.fail();
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testFail() {
						Assertions.fail("This should fail");
						Assertions.fail();
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertEquals_with_delta_for_floating_point() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testFloatingPoint() {
						Assert.assertEquals("Floating point equality", 0.1 + 0.2, 0.3, 0.0001);
						Assert.assertEquals(0.1 + 0.2, 0.3, 0.0001);
						Assert.assertNotEquals("Floating point inequality", 0.1 + 0.2, 0.4, 0.0001);
						Assert.assertNotEquals(0.1 + 0.2, 0.4, 0.0001);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testFloatingPoint() {
						Assertions.assertEquals(0.1 + 0.2, 0.3, 0.0001, "Floating point equality");
						Assertions.assertEquals(0.1 + 0.2, 0.3, 0.0001);
						Assertions.assertNotEquals(0.1 + 0.2, 0.4, 0.0001, "Floating point inequality");
						Assertions.assertNotEquals(0.1 + 0.2, 0.4, 0.0001);
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_static_import_wildcard() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import static org.junit.Assert.*;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testStatic() {
						assertEquals("expected", "actual");
						assertTrue(true);
						assertFalse(false);
						fail("Not implemented");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.*;
				
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testStatic() {
						assertEquals("expected", "actual");
						assertTrue(true);
						assertFalse(false);
						fail("Not implemented");
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_static_import_explicit() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import static org.junit.Assert.assertEquals;
				import static org.junit.Assert.assertTrue;
				import static org.junit.Assert.fail;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testStatic() {
						assertEquals("expected", "actual");
						assertTrue(true);
						fail("Not implemented");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.assertEquals;
				import static org.junit.jupiter.api.Assertions.assertTrue;
				import static org.junit.jupiter.api.Assertions.fail;
				
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testStatic() {
						assertEquals("expected", "actual");
						assertTrue(true);
						fail("Not implemented");
					}
				}
				"""
		}, null);
	}

	enum AssertionCases {
		BasicAssertions(
				"""
				package test;
				import org.junit.Assert;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void test() {
						Assert.assertEquals(42, 42);
						Assert.assertNotEquals(42, 43);
						Assert.assertTrue(true);
						Assert.assertFalse(false);
					}
				}
				""",
				"""
				package test;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void test() {
						Assertions.assertEquals(42, 42);
						Assertions.assertNotEquals(42, 43);
						Assertions.assertTrue(true);
						Assertions.assertFalse(false);
					}
				}
				""");

		final String given;
		final String expected;

		AssertionCases(String given, String expected) {
			this.given = given;
			this.expected = expected;
		}
	}
}
