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
 * Tests for detecting and fixing "lost" JUnit 3 tests that were not properly migrated.
 * Tests the LostTestFinderJUnitPlugin functionality.
 */
public class LostTestFinderTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void finds_and_fixes_lost_test_methods() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("CalculatorTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Test;
				import static org.junit.jupiter.api.Assertions.*;
				
				public class CalculatorTest {
					@Test
					public void testAddition() {
						assertEquals(2, 1 + 1);
					}
					
					public void testEdgeCase() {
						assertEquals(0, 0);
					}
					
					public void testSpecialCase() {
						assertTrue(true);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				import static org.junit.jupiter.api.Assertions.*;
				
				public class CalculatorTest {
					@Test
					public void testAddition() {
						assertEquals(2, 1 + 1);
					}
					
					@Test
					public void testEdgeCase() {
						assertEquals(0, 0);
					}
					
					@Test
					public void testSpecialCase() {
						assertTrue(true);
					}
				}
				"""
		}, null);
	}

	@Test
	public void does_not_modify_class_without_test_methods() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("Calculator.java",
				"""
				package test;
				
				public class Calculator {
					public void testSomething() {
						// Not a test class, just a regular method
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void does_not_modify_methods_with_before_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.BeforeEach;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					@BeforeEach
					public void testSetup() {
						// Intentionally named with test prefix
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void does_not_modify_methods_with_after_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.AfterEach;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					@AfterEach
					public void testCleanup() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void does_not_modify_methods_with_ignore_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.Disabled;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					@Disabled
					public void testNotYetImplemented() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void does_not_modify_non_public_methods() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					private void testHelper() {
						// Private helper method
					}
					
					protected void testProtected() {
						// Protected helper method
					}
					
					void testPackagePrivate() {
						// Package-private helper method
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void does_not_modify_non_void_methods() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					public int testReturnsInt() {
						return 0;
					}
					
					public String testReturnsString() {
						return "test";
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void does_not_modify_methods_with_parameters() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					public void testWithParameter(String param) {
						// Has parameter, not a test
					}
					
					public void testWithMultipleParameters(int a, int b) {
						// Has parameters, not a test
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void does_not_modify_helper_methods() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					public void setupCalculator() {
						// Helper method, doesn't start with "test"
					}
					
					public void verifyResults() {
						// Another helper method
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void uses_junit4_test_annotation_when_junit4_imported() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					public void testLost() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					@Test
					public void testLost() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void detects_lost_tests_with_inherited_test_methods() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		
		// Create base test class with @Test methods
		pack.createCompilationUnit("BaseTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class BaseTest {
					@Test
					public void testInBase() {
					}
				}
				""", false, null);
		
		// Create subclass with lost test methods
		ICompilationUnit cu = pack.createCompilationUnit("SubTest.java",
				"""
				package test;
				
				public class SubTest extends BaseTest {
					public void testInSub() {
						// Lost test method
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				
				import org.junit.jupiter.api.Test;
				
				public class SubTest extends BaseTest {
					@Test
					public void testInSub() {
						// Lost test method
					}
				}
				"""
		}, null);
	}

	@Test
	public void handles_multiple_lost_tests_in_one_class() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MathTest.java",
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class MathTest {
					@Test
					public void testAdd() {
					}
					
					public void testSubtract() {
					}
					
					public void testMultiply() {
					}
					
					public void testDivide() {
					}
					
					public void helper() {
						// Not a test
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class MathTest {
					@Test
					public void testAdd() {
					}
					
					@Test
					public void testSubtract() {
					}
					
					@Test
					public void testMultiply() {
					}
					
					@Test
					public void testDivide() {
					}
					
					public void helper() {
						// Not a test
					}
				}
				"""
		}, null);
	}

	@Test
	public void works_with_junit4_before_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				import org.junit.Before;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					@Before
					public void testSetup() {
						// Should not add @Test
					}
					
					public void testLost() {
						// Should add @Test
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.Test;
				import org.junit.Before;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					@Before
					public void testSetup() {
						// Should not add @Test
					}
					
					@Test
					public void testLost() {
						// Should add @Test
					}
				}
				"""
		}, null);
	}

	@Test
	public void uses_junit4_test_annotation_when_wildcard_import_present() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.*;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					public void testLost() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_LOST_TESTS);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.*;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
					
					@Test
					public void testLost() {
					}
				}
				"""
		}, null);
	}
}
