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
 * Tests for edge cases in JUnit 4→5 migration.
 * Covers special scenarios like:
 * - Combined @Test parameters (expected + timeout)
 * - Comments preservation
 * - @Ignore with value attribute
 * - Wildcard static imports
 * - Multiple assertions in one method
 */
public class MigrationEdgeCasesTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void handles_combined_test_expected_and_timeout() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Test;
				
				public class MyTest {
					@Test(expected = IllegalArgumentException.class, timeout = 1000)
					public void testBoth() {
						throw new IllegalArgumentException("Expected");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_TIMEOUT);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
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
				"""
		}, null);
	}

	@Test
	public void preserves_comments_during_annotation_migration() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Before;
				import org.junit.Test;
				
				public class MyTest {
					// Setup method comment
					@Before
					public void setUp() {
						// Setup code
					}
					
					// Test method comment
					@Test
					public void testSomething() {
						// Test code
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					// Setup method comment
					@BeforeEach
					public void setUp() {
						// Setup code
					}
					
					// Test method comment
					@Test
					public void testSomething() {
						// Test code
					}
				}
				"""
		}, null);
	}

	@Test
	public void handles_ignore_with_value_attribute() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Ignore;
				import org.junit.Test;
				
				public class MyTest {
					@Ignore(value = "Not yet implemented")
					@Test
					public void testIgnored() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Disabled;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Disabled("Not yet implemented")
					@Test
					public void testIgnored() {
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
					public void testAssertions() {
						assertEquals(42, 42);
						assertTrue(true);
						assertFalse(false);
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
					public void testAssertions() {
						assertEquals(42, 42);
						assertTrue(true);
						assertFalse(false);
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
						Assert.assertEquals("First", 1, 1);
						Assert.assertEquals("Second", 2, 2);
						Assert.assertTrue("Third", true);
						Assert.assertFalse("Fourth", false);
						Assert.assertNull("Fifth", null);
						Assert.assertNotNull("Sixth", "value");
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
					public void testMultiple() {
						Assertions.assertEquals(1, 1, "First");
						Assertions.assertEquals(2, 2, "Second");
						Assertions.assertTrue(true, "Third");
						Assertions.assertFalse(false, "Fourth");
						Assertions.assertNull(null, "Fifth");
						Assertions.assertNotNull("value", "Sixth");
					}
				}
				"""
		}, null);
	}

	@Test
	public void handles_test_with_empty_body() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void emptyTest() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void emptyTest() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void handles_mixed_junit4_and_junit3_patterns() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Before;
				import org.junit.Test;
				import static org.junit.Assert.*;
				
				public class MyTest {
					@Before
					public void setUp() {
					}
					
					@Test
					public void testWithAnnotation() {
						assertEquals(1, 1);
					}
					
					public void testWithoutAnnotation() {
						assertEquals(2, 2);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.*;
				
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@BeforeEach
					public void setUp() {
					}
					
					@Test
					public void testWithAnnotation() {
						assertEquals(1, 1);
					}
					
					public void testWithoutAnnotation() {
						assertEquals(2, 2);
					}
				}
				"""
		}, null);
	}
}
