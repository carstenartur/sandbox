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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/**
 * Tests for migrating JUnit 4 Rules to JUnit 5 Extensions.
 * Covers TemporaryFolder, TestName, ExternalResource, Timeout, etc.
 */
public class MigrationRulesToExtensionsTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@ParameterizedTest
	@EnumSource(RuleCases.class)
	public void migrates_junit4_rules_to_junit5_extensions(RuleCases testCase) throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", testCase.given, true, null);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { testCase.expected }, null);
	}

	@Test
	public void migrates_temporaryFolder_rule() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import java.io.File;
				import java.io.IOException;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;
				
				public class MyTest {
					@Rule
					public TemporaryFolder tempFolder = new TemporaryFolder();
					
					@Test
					public void testWithTempFile() throws IOException {
						File newFile = tempFolder.newFile("myfile.txt");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import java.io.File;
				import java.io.IOException;
				
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.io.TempDir;
				
				public class MyTest {
					@TempDir
					Path tempFolder;
					
					@Test
					public void testWithTempFile() throws IOException {
						File newFile = tempFolder.resolve("myfile.txt").toFile();
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_testName_rule() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TestName;
				
				public class MyTest {
					@Rule
					public TestName testName = new TestName();
					
					@Test
					public void testSomething() {
						System.out.println("Test name: " + testName.getMethodName());
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.TestInfo;
				
				public class MyTest {
					private String testName;
					
					@BeforeEach
					void init(TestInfo testInfo) {
						this.testName = testInfo.getDisplayName();
					}
					
					@Test
					public void testSomething() {
						System.out.println("Test name: " + testName);
					}
				}
				"""
		}, null);
	}

	@Disabled("Not yet implemented - Timeout rule migration")
	@Test
	public void migrates_timeout_rule() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.Timeout;
				import java.util.concurrent.TimeUnit;
				
				public class MyTest {
					@Rule
					public Timeout globalTimeout = new Timeout(1000);
					
					@Test
					public void testSomething() {
						// Test code
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.Timeout;
				import java.util.concurrent.TimeUnit;
				
				public class MyTest {
					@Test
					@Timeout(value = 1000, unit = TimeUnit.MILLISECONDS)
					public void testSomething() {
						// Test code
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_externalResource_anonymous_class() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.ExternalResource;
				
				public class MyTest {
					@Rule
					public ExternalResource resource = new ExternalResource() {
						@Override
						protected void before() throws Throwable {
							System.out.println("Setup");
						}
						
						@Override
						protected void after() {
							System.out.println("Teardown");
						}
					};
					
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.extension.ExtensionContext;
				import org.junit.jupiter.api.extension.RegisterExtension;
				
				public class MyTest {
					@RegisterExtension
					public Resource_abc123 resource = new Resource_abc123();
					
					@Test
					public void testSomething() {
					}
					
					class Resource_abc123 implements org.junit.jupiter.api.extension.BeforeEachCallback,
							org.junit.jupiter.api.extension.AfterEachCallback {
						public void beforeEach(ExtensionContext context) {
							System.out.println("Setup");
						}
						
						public void afterEach(ExtensionContext context) {
							System.out.println("Teardown");
						}
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_classRule_to_static_extension() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.ClassRule;
				import org.junit.Test;
				import org.junit.rules.ExternalResource;
				
				public class MyTest {
					@ClassRule
					public static ExternalResource resource = new ExternalResource() {
						@Override
						protected void before() throws Throwable {
							System.out.println("Class setup");
						}
						
						@Override
						protected void after() {
							System.out.println("Class teardown");
						}
					};
					
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.extension.ExtensionContext;
				import org.junit.jupiter.api.extension.RegisterExtension;
				
				public class MyTest {
					@RegisterExtension
					public static Resource_abc123 resource = new Resource_abc123();
					
					@Test
					public void testSomething() {
					}
					
					static class Resource_abc123 implements org.junit.jupiter.api.extension.BeforeAllCallback,
							org.junit.jupiter.api.extension.AfterAllCallback {
						public void beforeAll(ExtensionContext context) {
							System.out.println("Class setup");
						}
						
						public void afterAll(ExtensionContext context) {
							System.out.println("Class teardown");
						}
					}
				}
				"""
		}, null);
	}

	enum RuleCases {
		TemporaryFolderBasic(
				"""
				package test;
				import java.io.File;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;
				
				public class MyTest {
					@Rule
					public TemporaryFolder folder = new TemporaryFolder();
					
					@Test
					public void test() throws Exception {
						File file = folder.newFile("test.txt");
					}
				}
				""",
				"""
				package test;
				import java.io.File;
				
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.io.TempDir;
				
				public class MyTest {
					@TempDir
					Path folder;
					
					@Test
					public void test() throws Exception {
						File file = folder.resolve("test.txt").toFile();
					}
				}
				""");

		final String given;
		final String expected;

		RuleCases(String given, String expected) {
			this.given = given;
			this.expected = expected;
		}
	}
}
