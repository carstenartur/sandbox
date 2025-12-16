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
 * Tests for migrating JUnit 4 lifecycle annotations to JUnit 5.
 * Covers @Before → @BeforeEach, @After → @AfterEach,
 * @BeforeClass → @BeforeAll, @AfterClass → @AfterAll.
 */
public class MigrationLifecycleTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void migrates_before_to_beforeEach() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Before;
				import org.junit.Test;
				
				public class MyTest {
					@Before
					public void setUp() {
						// Setup code
					}
					
					@Test
					public void testSomething() {
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
					@BeforeEach
					public void setUp() {
						// Setup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_after_to_afterEach() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.After;
				import org.junit.Test;
				
				public class MyTest {
					@After
					public void tearDown() {
						// Cleanup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.AfterEach;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@AfterEach
					public void tearDown() {
						// Cleanup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_beforeClass_to_beforeAll() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.BeforeClass;
				import org.junit.Test;
				
				public class MyTest {
					@BeforeClass
					public static void setUpBeforeClass() throws Exception {
						// Setup before all tests
					}
					
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.BeforeAll;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@BeforeAll
					public static void setUpBeforeClass() throws Exception {
						// Setup before all tests
					}
					
					@Test
					public void testSomething() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_afterClass_to_afterAll() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.AfterClass;
				import org.junit.Test;
				
				public class MyTest {
					@AfterClass
					public static void tearDownAfterClass() throws Exception {
						// Cleanup after all tests
					}
					
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.AfterAll;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@AfterAll
					public static void tearDownAfterClass() throws Exception {
						// Cleanup after all tests
					}
					
					@Test
					public void testSomething() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_all_lifecycle_methods_together() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.After;
				import org.junit.AfterClass;
				import org.junit.Before;
				import org.junit.BeforeClass;
				import org.junit.Test;
				
				public class MyTest {
					@BeforeClass
					public static void setUpBeforeClass() throws Exception {
					}
					
					@AfterClass
					public static void tearDownAfterClass() throws Exception {
					}
					
					@Before
					public void setUp() throws Exception {
					}
					
					@After
					public void tearDown() throws Exception {
					}
					
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.AfterAll;
				import org.junit.jupiter.api.AfterEach;
				import org.junit.jupiter.api.BeforeAll;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@BeforeAll
					public static void setUpBeforeClass() throws Exception {
					}
					
					@AfterAll
					public static void tearDownAfterClass() throws Exception {
					}
					
					@BeforeEach
					public void setUp() throws Exception {
					}
					
					@AfterEach
					public void tearDown() throws Exception {
					}
					
					@Test
					public void testSomething() {
					}
				}
				"""
		}, null);
	}
}
