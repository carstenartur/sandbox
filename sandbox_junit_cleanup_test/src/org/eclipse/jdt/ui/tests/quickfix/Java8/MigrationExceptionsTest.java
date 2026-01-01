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
	******************************************************************************/
	package org.eclipse.jdt.ui.tests.quickfix.Java8;
	
	import org.eclipse.core.runtime.CoreException;
	import org.eclipse.jdt.core.ICompilationUnit;
	import org.eclipse.jdt.core.IPackageFragment;
	import org.eclipse.jdt.core.IPackageFragmentRoot;
	import org.eclipse.jdt.junit.JUnitCore;
	import org.junit.jupiter.api.BeforeEach;
	import org.junit.jupiter.api.Disabled;
	import org.junit.jupiter.api.Test;
	import org.junit.jupiter.api.extension.RegisterExtension;
	import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
	import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
	import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
	
	/**
	* Tests for migrating JUnit 4 exception handling to JUnit 5.
	* Covers @Test(expected=...) → assertThrows() and ExpectedException rule.
	*/
	public class MigrationExceptionsTest {
	
	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();
	
	IPackageFragmentRoot fRoot;
	
	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}
	
	@Test
	public void migrates_test_expected_to_assertThrows() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				
				public class MyTest {
					@Test(expected = IllegalArgumentException.class)
					public void testException() {
						throw new IllegalArgumentException("Expected");
					}
				}
				""", false, null);
	
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED);
	
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.assertThrows;
				
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testException() {
						assertThrows(IllegalArgumentException.class, () -> {
							throw new IllegalArgumentException("Expected");
						});
					}
				}
				"""
		}, null);
	}
	
	@Test
	public void migrates_test_expected_with_method_call() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				
				public class MyTest {
					@Test(expected = NullPointerException.class)
					public void testNullPointerException() {
						String str = null;
						str.length();
					}
				}
				""", false, null);
	
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST_EXPECTED);
	
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.assertThrows;
				
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testNullPointerException() {
						assertThrows(NullPointerException.class, () -> {
							String str = null;
							str.length();
						});
					}
				}
				"""
		}, null);
	}
	
	@Disabled("temporarily disabled: failing in CI (see PR #320)")
	@Test
	public void migrates_expectedException_rule_basic() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.ExpectedException;
				
				public class MyTest {
					@Rule
					public ExpectedException thrown = ExpectedException.none();
					
					@Test
					public void testException() {
						thrown.expect(IllegalArgumentException.class);
						throw new IllegalArgumentException("Expected");
					}
				}
				""", false, null);
	
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
	
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.assertThrows;
				
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testException() {
						assertThrows(IllegalArgumentException.class, () -> {
						throw new IllegalArgumentException("Expected");
						});
					}
				}
				"""
		}, null);
	}
	
	@Disabled("temporarily disabled: failing in CI (see PR #320)")
	@Test
	public void migrates_expectedException_rule_with_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.ExpectedException;
				
				public class MyTest {
					@Rule
					public ExpectedException thrown = ExpectedException.none();
					
					@Test
					public void testExceptionWithMessage() {
						thrown.expect(IllegalArgumentException.class);
						thrown.expectMessage("Invalid argument");
						doSomethingThatThrows();
					}
					
					private void doSomethingThatThrows() {
						throw new IllegalArgumentException("Invalid argument");
					}
				}
				""", false, null);
	
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
	
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.assertEquals;
				import static org.junit.jupiter.api.Assertions.assertThrows;
				
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testExceptionWithMessage() {
						IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
						doSomethingThatThrows();
						});
						assertEquals("Invalid argument", exception.getMessage());
					}
					
					private void doSomethingThatThrows() {
						throw new IllegalArgumentException("Invalid argument");
					}
				}
				"""
		}, null);
	}
	
	@Disabled("Not yet implemented - ExpectedException with cause")
	@Test
	public void migrates_expectedException_rule_with_cause() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.ExpectedException;
				
				public class MyTest {
					@Rule
					public ExpectedException thrown = ExpectedException.none();
					
					@Test
					public void testExceptionWithCause() {
						thrown.expect(RuntimeException.class);
						thrown.expectCause(org.hamcrest.Matchers.isA(IllegalArgumentException.class));
						throw new RuntimeException(new IllegalArgumentException());
					}
				}
				""", false, null);
	
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
	
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.assertInstanceOf;
				import static org.junit.jupiter.api.Assertions.assertThrows;
				
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testExceptionWithCause() {
						RuntimeException exception = assertThrows(RuntimeException.class, () -> {
						throw new RuntimeException(new IllegalArgumentException());
						});
						assertInstanceOf(IllegalArgumentException.class, exception.getCause());
					}
				}
				"""
		}, null);
	}
	}
