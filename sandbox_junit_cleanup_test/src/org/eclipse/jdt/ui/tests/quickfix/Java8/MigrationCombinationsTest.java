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
 * Tests for complex migration scenarios combining multiple JUnit features.
 * These tests verify that combinations of migrations work correctly together.
 */
public class MigrationCombinationsTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void migrates_full_test_class_with_all_features() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import static org.junit.Assert.*;
				import org.junit.After;
				import org.junit.AfterClass;
				import org.junit.Before;
				import org.junit.BeforeClass;
				import org.junit.Ignore;
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
					
					@Ignore
					@Test
					public void ignoredTest() {
						fail("Not yet implemented");
					}
					
					@Test
					public void testWithAssertions() {
						assertEquals(42, 42);
						assertTrue(true);
						assertFalse(false);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.*;
				
				import org.junit.jupiter.api.AfterAll;
				import org.junit.jupiter.api.AfterEach;
				import org.junit.jupiter.api.BeforeAll;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Disabled;
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
					
					@Disabled
					@Test
					public void ignoredTest() {
						fail("Not yet implemented");
					}
					
					@Test
					public void testWithAssertions() {
						assertEquals(42, 42);
						assertTrue(true);
						assertFalse(false);
					}
				}
				"""
		}, null);
	}

//	@Disabled("Not yet implemented - TemporaryFolder rule migration")
	@Test
	public void migrates_test_with_temporaryFolder_and_testName() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import java.io.File;
				import java.io.IOException;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;
				import org.junit.rules.TestName;
				
				public class MyTest {
					@Rule
					public TemporaryFolder tempFolder = new TemporaryFolder();
					
					@Rule
					public TestName testName = new TestName();
					
					@Test
					public void testWithBothRules() throws IOException {
						System.out.println("Test name: " + testName.getMethodName());
						File newFile = tempFolder.newFile("myfile.txt");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
package test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

public class MyTest {
	@TempDir
	Path tempFolder;

	private String testName;

	@BeforeEach
	void init(TestInfo testInfo) {
		this.testName = testInfo.getDisplayName();
	}

	@Test
	public void testWithBothRules() throws IOException {
		System.out.println("Test name: " + testName);
		File newFile = Files.createFile(tempFolder.resolve("myfile.txt")).toFile();
	}
}
				"""
		}, null);
	}

	@Test
	public void migrates_suite_with_assertions_and_lifecycle() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import static org.junit.Assert.*;
				import org.junit.Before;
				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Suite;
				
				@RunWith(Suite.class)
				@Suite.SuiteClasses({MyTest.class})
				public class MyTest {
					@Before
					public void setUp() {
					}
					
					@Test
					public void testSomething() {
						assertEquals("expected", "actual");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.*;
				
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;
				import org.junit.platform.suite.api.SelectClasses;
				import org.junit.platform.suite.api.Suite;
				
				@Suite
				@SelectClasses({MyTest.class})
				public class MyTest {
					@BeforeEach
					public void setUp() {
					}
					
					@Test
					public void testSomething() {
						assertEquals("expected", "actual");
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_test_with_multiple_rules() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import java.io.File;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;
				import org.junit.rules.TestName;
				import org.junit.rules.Timeout;
				
				public class MyTest {
					@Rule
					public TemporaryFolder tempFolder = new TemporaryFolder();
					
					@Rule
					public TestName testName = new TestName();
					
					@Rule
					public Timeout timeout = Timeout.seconds(10);
					
					@Test
					public void testWithMultipleRules() throws Exception {
						File file = tempFolder.newFile(testName.getMethodName() + ".txt");
						// Test code
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import java.io.File;
				import java.nio.file.Files;
				import java.nio.file.Path;
				import java.util.concurrent.TimeUnit;
				
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.TestInfo;
				import org.junit.jupiter.api.Timeout;
				import org.junit.jupiter.api.io.TempDir;
				
				@Timeout(value = 10, unit = TimeUnit.SECONDS)
				public class MyTest {
					@TempDir
					Path tempFolder;

					private String testName;

					@BeforeEach
					void init(TestInfo testInfo) {
						this.testName = testInfo.getDisplayName();
					}
					
					@Test
					public void testWithMultipleRules() throws Exception {
						File file = Files.createFile(tempFolder.resolve(testName + ".txt")).toFile();
						// Test code
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_full_test_class_with_rules_and_lifecycle() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import static org.junit.Assert.*;
				import org.junit.After;
				import org.junit.Before;
				import org.junit.Ignore;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TestName;
				
				public class MyTest {
					@Rule
					public TestName testName = new TestName();
					
					@Before
					public void setUp() {
						System.out.println("Setting up: " + testName.getMethodName());
					}
					
					@After
					public void tearDown() {
						System.out.println("Tearing down: " + testName.getMethodName());
					}
					
					@Test
					public void testWithAssertions() {
						assertEquals(42, 42);
						assertTrue(true);
					}
					
					@Ignore("Not ready")
					@Test
					public void testIgnored() {
						fail("Should not run");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.*;

				import org.junit.jupiter.api.AfterEach;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Disabled;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.TestInfo;
				
				public class MyTest {
					private String testName;

					@BeforeEach
					void init(TestInfo testInfo) {
						this.testName = testInfo.getDisplayName();
					}
					
					@BeforeEach
					public void setUp() {
						System.out.println("Setting up: " + testName);
					}
					
					@AfterEach
					public void tearDown() {
						System.out.println("Tearing down: " + testName);
					}
					
					@Test
					public void testWithAssertions() {
						assertEquals(42, 42);
						assertTrue(true);
					}
					
					@Disabled("Not ready")
					@Test
					public void testIgnored() {
						fail("Should not run");
					}
				}
				"""
		}, null);
	}
}
