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
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETIMEOUT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEERRORCOLLECTOR);
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
				import java.nio.file.Files;
				import java.nio.file.Path;
				
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.io.TempDir;
				
				public class MyTest {
					@TempDir
					Path tempFolder;
					@Test
					public void testWithTempFile() throws IOException {
						File newFile = Files.createFile(tempFolder.resolve("myfile.txt")).toFile();
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

		// Note: JUnit 4's @Rule Timeout applies to all test methods in the class,
		// so the correct JUnit 5 equivalent is a class-level @Timeout annotation
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import java.util.concurrent.TimeUnit;
				
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.Timeout;
				
				@Timeout(value = 1000, unit = TimeUnit.MILLISECONDS)
				public class MyTest {
					@Test
					public void testSomething() {
						// Test code
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_errorCollector_multiple_test_methods() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.ErrorCollector;
				import static org.hamcrest.CoreMatchers.equalTo;
				
				public class MyTest {
					@Rule
					public ErrorCollector collector = new ErrorCollector();
					
					@Test
					public void test1() {
						collector.checkThat("a", equalTo("a"));
						collector.checkThat("b", equalTo("b"));
					}
					
					@Test
					public void test2() {
						collector.checkThat("x", equalTo("x"));
					}
					
					@Test
					public void test3() {
						// This test doesn't use collector
						System.out.println("No errors");
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEERRORCOLLECTOR);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
package test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

public class MyTest {
	@Test
	public void test1() {
		assertAll(() -> assertThat("a", equalTo("a")), () -> assertThat("b", equalTo("b")));
	}

	@Test
	public void test2() {
		assertAll(() -> assertThat("x", equalTo("x")));
	}

	@Test
	public void test3() {
		// This test doesn't use collector
		System.out.println("No errors");
	}
}
				"""
		}, null);
	}

	@Disabled("Anonymous ExternalResource migration generates hash-based class names - covered by JUnitMigrationCleanUpTest.testJUnitCleanupSelectedCase with JUnitCleanupCases.RuleAnonymousExternalResource")
	@Test
	public void migrates_externalResource_anonymous_class() throws CoreException {
		// This test is disabled because the cleanup generates hash-based class names
		// for anonymous ExternalResource instances (e.g., Er_5b8b4).
		// The exact hash depends on the variable name and is tested in
		// JUnitMigrationCleanUpTest.testJUnitCleanupSelectedCase using JUnitCleanupCases.RuleAnonymousExternalResource
	}

	@Disabled("ClassRule migration generates hash-based class names - covered by JUnitMigrationCleanUpTest.testJUnitCleanupSelectedCase with JUnitCleanupCases.RuleNestedExternalResource")
	@Test
	public void migrates_classRule_to_static_extension() throws CoreException {
		// This test is disabled because the cleanup generates hash-based class names
		// for anonymous ExternalResource instances in ClassRule scenarios.
		// The exact hash depends on the variable name and is tested in
		// JUnitMigrationCleanUpTest.testJUnitCleanupSelectedCase using JUnitCleanupCases.RuleNestedExternalResource
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
				import java.nio.file.Files;
				import java.nio.file.Path;
				
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.io.TempDir;
				
				public class MyTest {
					@TempDir
					Path folder;
					@Test
					public void test() throws Exception {
						File file = Files.createFile(folder.resolve("test.txt")).toFile();
					}
				}
				"""),
		TemporaryFolderWithNewFolder(
				"""
				package test;
				import java.io.File;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;
				
				public class MyTest {
					@Rule
					public TemporaryFolder tempFolder = new TemporaryFolder();
					
					@Test
					public void test() throws Exception {
						File dir = tempFolder.newFolder("subdir");
					}
				}
				""",
				"""
				package test;
				import java.io.File;
				import java.nio.file.Files;
				import java.nio.file.Path;
				
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.io.TempDir;
				
				public class MyTest {
					@TempDir
					Path tempFolder;
					@Test
					public void test() throws Exception {
						File dir = Files.createDirectories(tempFolder.resolve("subdir")).toFile();
					}
				}
				"""),
		TemporaryFolderWithGetRoot(
				"""
				package test;
				import java.io.File;
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.TemporaryFolder;
				
				public class MyTest {
					@Rule
					public TemporaryFolder tmpDir = new TemporaryFolder();
					
					@Test
					public void test() {
						File root = tmpDir.getRoot();
					}
				}
				""",
				"""
				package test;
				import java.io.File;
				import java.nio.file.Path;
				
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.io.TempDir;
				
				public class MyTest {
					@TempDir
					Path tmpDir;
					@Test
					public void test() {
						File root = tmpDir.toFile();
					}
				}
				"""),
		TemporaryFolderWithNoArgNewFile(
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
						File file = folder.newFile();
					}
				}
				""",
				"""
				package test;
				import java.io.File;
				import java.nio.file.Files;
				import java.nio.file.Path;
				
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.io.TempDir;
				
				public class MyTest {
					@TempDir
					Path folder;
					@Test
					public void test() throws Exception {
						File file = Files.createTempFile(folder, "", null).toFile();
					}
				}
				"""),
		TemporaryFolderWithNoArgNewFolder(
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
						File dir = folder.newFolder();
					}
				}
				""",
				"""
				package test;
				import java.io.File;
				import java.nio.file.Files;
				import java.nio.file.Path;
				
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.io.TempDir;
				
				public class MyTest {
					@TempDir
					Path folder;
					@Test
					public void test() throws Exception {
						File dir = Files.createTempDirectory(folder, "").toFile();
					}
				}
				"""),
	TimeoutSeconds(
			"""
			package test;
			import org.junit.Rule;
			import org.junit.Test;
			import org.junit.rules.Timeout;
			
			public class MyTest {
				@Rule
				public Timeout globalTimeout = Timeout.seconds(10);
				
				@Test
				public void test1() {
					// test code
				}
				
				@Test
				public void test2() {
					// test code
				}
			}
			""",
			"""
			package test;
			import java.util.concurrent.TimeUnit;
			
			import org.junit.jupiter.api.Test;
			import org.junit.jupiter.api.Timeout;
			
			@Timeout(value = 10, unit = TimeUnit.SECONDS)
			public class MyTest {
				@Test
				public void test1() {
					// test code
				}
				
				@Test
				public void test2() {
					// test code
				}
			}
			"""),
	TimeoutMillis(
			"""
			package test;
			import org.junit.Rule;
			import org.junit.Test;
			import org.junit.rules.Timeout;
			
			public class MyTest {
				@Rule
				public Timeout timeout = Timeout.millis(5000);
				
				@Test
				public void testMethod() {
					// test code
				}
			}
			""",
			"""
			package test;
			import java.util.concurrent.TimeUnit;
			
			import org.junit.jupiter.api.Test;
			import org.junit.jupiter.api.Timeout;
			
			@Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
			public class MyTest {
				@Test
				public void testMethod() {
					// test code
				}
			}
			"""),
	TimeoutConstructorMillis(
			"""
			package test;
			import org.junit.Rule;
			import org.junit.Test;
			import org.junit.rules.Timeout;
			
			public class MyTest {
				@Rule
				public Timeout timeout = new Timeout(1000);
				
				@Test
				public void testSomething() {
					// test
				}
			}
			""",
			"""
			package test;
			import java.util.concurrent.TimeUnit;
			
			import org.junit.jupiter.api.Test;
			import org.junit.jupiter.api.Timeout;
			
			@Timeout(value = 1000, unit = TimeUnit.MILLISECONDS)
			public class MyTest {
				@Test
				public void testSomething() {
					// test
				}
			}
			"""),
	TimeoutConstructorWithUnit(
			"""
			package test;
			import java.util.concurrent.TimeUnit;
			import org.junit.Rule;
			import org.junit.Test;
			import org.junit.rules.Timeout;
			
			public class MyTest {
				@Rule
				public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);
				
				@Test
				public void longRunningTest() {
					// test code
				}
			}
			""",
			"""
			package test;
			import java.util.concurrent.TimeUnit;
			
			import org.junit.jupiter.api.Test;
			import org.junit.jupiter.api.Timeout;
			
			@Timeout(value = 30, unit = TimeUnit.SECONDS)
			public class MyTest {
				@Test
				public void longRunningTest() {
					// test code
				}
			}
			"""),
	ErrorCollectorBasic(
			"""
			package test;
			import org.junit.Rule;
			import org.junit.Test;
			import org.junit.rules.ErrorCollector;
			import static org.hamcrest.CoreMatchers.equalTo;
			
			public class MyTest {
				@Rule
				public ErrorCollector collector = new ErrorCollector();
				
				@Test
				public void testMultipleErrors() {
					collector.checkThat("value1", equalTo("expected1"));
					collector.checkThat("value2", equalTo("expected2"));
				}
			}
			""",
			"""
package test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

public class MyTest {
	@Test
	public void testMultipleErrors() {
		assertAll(() -> assertThat("value1", equalTo("expected1")), () -> assertThat("value2", equalTo("expected2")));
	}
}
			"""),
	ErrorCollectorWithAddError(
			"""
			package test;
			import org.junit.Rule;
			import org.junit.Test;
			import org.junit.rules.ErrorCollector;
			
			public class MyTest {
				@Rule
				public ErrorCollector collector = new ErrorCollector();
				
				@Test
				public void testWithAddError() {
					collector.addError(new Throwable("error message"));
				}
			}
			""",
			"""
package test;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

public class MyTest {
	@Test
	public void testWithAddError() {
		assertAll(() -> {
			throw new Throwable("error message");
		});
	}
}
			"""),
	ErrorCollectorMixed(
			"""
			package test;
			import org.junit.Rule;
			import org.junit.Test;
			import org.junit.rules.ErrorCollector;
			import static org.hamcrest.CoreMatchers.equalTo;
			
			public class MyTest {
				@Rule
				public ErrorCollector collector = new ErrorCollector();
				
				@Test
				public void testMixed() {
					collector.checkThat("value1", equalTo("expected1"));
					collector.addError(new AssertionError("Failed check"));
					collector.checkThat("value2", equalTo("expected2"));
				}
			}
			""",
			"""
package test;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

public class MyTest {
	@Test
	public void testMixed() {
		assertAll(() -> assertThat("value1", equalTo("expected1")), () -> {
			throw new AssertionError("Failed check");
		}, () -> assertThat("value2", equalTo("expected2")));
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
