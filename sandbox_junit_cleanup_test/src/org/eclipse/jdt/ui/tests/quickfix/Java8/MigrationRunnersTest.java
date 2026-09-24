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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.JUnitMigrationFixtureClasspath;

/**
 * Tests for migrating @RunWith annotations to JUnit 5 equivalents.
 * Covers Suite, Parameterized, MockitoJUnitRunner, SpringRunner, etc.
 */
public class MigrationRunnersTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = JUnitMigrationFixtureClasspath.createJUnit4And5Root(context, "org.junit.platform.suite.api.Suite");
	}

	private void createRunnerFixtures() throws CoreException {
		fRoot.createPackageFragment("org.mockito", true, null).createCompilationUnit("Mock.java", """
				package org.mockito;
				@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
				@java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
				public @interface Mock {
				}
				""", false, null);
		fRoot.createPackageFragment("org.mockito.junit", true, null).createCompilationUnit("MockitoJUnitRunner.java", """
				package org.mockito.junit;
				public class MockitoJUnitRunner extends org.junit.runner.Runner {
					@Override
					public org.junit.runner.Description getDescription() {
						return org.junit.runner.Description.EMPTY;
					}
					@Override
					public void run(org.junit.runner.notification.RunNotifier notifier) {
					}
				}
				""", false, null);
		fRoot.createPackageFragment("org.mockito.junit.jupiter", true, null).createCompilationUnit("MockitoExtension.java", """
				package org.mockito.junit.jupiter;
				public class MockitoExtension implements org.junit.jupiter.api.extension.Extension {
				}
				""", false, null);
		fRoot.createPackageFragment("org.springframework.beans.factory.annotation", true, null)
				.createCompilationUnit("Autowired.java", """
				package org.springframework.beans.factory.annotation;
				@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
				@java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
				public @interface Autowired {
				}
				""", false, null);
		fRoot.createPackageFragment("org.springframework.test.context.junit4", true, null)
				.createCompilationUnit("SpringRunner.java", """
				package org.springframework.test.context.junit4;
				public class SpringRunner extends org.junit.runner.Runner {
					@Override
					public org.junit.runner.Description getDescription() {
						return org.junit.runner.Description.EMPTY;
					}
					@Override
					public void run(org.junit.runner.notification.RunNotifier notifier) {
					}
				}
				""", false, null);
		fRoot.createPackageFragment("org.springframework.test.context.junit.jupiter", true, null)
				.createCompilationUnit("SpringExtension.java", """
				package org.springframework.test.context.junit.jupiter;
				public class SpringExtension implements org.junit.jupiter.api.extension.Extension {
				}
				""", false, null);
	}

	@Test
	public void migrates_runWith_suite() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTestSuite.java",
				"""
				package test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Suite;
				
				@RunWith(Suite.class)
				@Suite.SuiteClasses({
					TestClass1.class,
					TestClass2.class
				})
				public class MyTestSuite {
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.platform.suite.api.SelectClasses;
				import org.junit.platform.suite.api.Suite;
				
				@Suite
				@SelectClasses({
					TestClass1.class,
					TestClass2.class
				})
				public class MyTestSuite {
				}
				"""
		}, null);
	}

	@Test
	public void migrates_runWith_parameterized() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyParameterizedTest.java",
				"""
				package test;
				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameters;
				import java.util.Arrays;
				import java.util.Collection;
				
				@RunWith(Parameterized.class)
				public class MyParameterizedTest {
					private int input;
					private int expected;
					
					public MyParameterizedTest(int input, int expected) {
						this.input = input;
						this.expected = expected;
					}
					
					@Parameters
					public static Collection<Object[]> data() {
						return Arrays.asList(new Object[][] {
							{1, 2}, {2, 4}, {3, 6}
						});
					}
					
					@Test
					public void testMultiply() {
						System.out.println(expected == input * 2);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED);

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(new ICompilationUnit[] { cu }, new String[] {
"""
package test;
import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.parallel.Execution(value = org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
@org.junit.jupiter.api.TestInstance(value = org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD)
@org.junit.jupiter.api.TestMethodOrder(value = org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.params.provider.MethodSource(value = "jupiterArguments")
@org.junit.jupiter.params.ParameterizedClass(autoCloseArguments = false, name = "{argumentSetName}")
public class MyParameterizedTest {
	private int input;
	private int expected;

	public MyParameterizedTest(int input, int expected) {
		this.input = input;
		this.expected = expected;
	}

	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] {
			{1, 2}, {2, 4}, {3, 6}
		});
	}

	@org.junit.jupiter.api.DisplayName(value = "testMultiply")
	@Order(0)
	@Test
	public void testMultiply() {
		System.out.println(expected == input * 2);
	}

	static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> jupiterArguments() {
		java.lang.Object source = data();
		java.lang.Iterable<?> rows = source instanceof java.lang.Object[][]
				? java.util.Arrays.asList((java.lang.Object[][]) source)
				: (java.lang.Iterable<?>) source;
		java.util.List<org.junit.jupiter.params.provider.Arguments> result = new java.util.ArrayList<>();
		int index = 0;
		for (java.lang.Object row : rows) {
			java.lang.Object[] arguments = (java.lang.Object[]) row;
			java.lang.String name = "[" + java.text.MessageFormat
					.format("{index}".replace("{index}", java.lang.Integer.toString(index++)), arguments) + "]";
			result.add(org.junit.jupiter.params.provider.Arguments.argumentSet(name, arguments));
		}
		return result.stream();
	}
}
"""
		}, null);
	}

	@Test
	public void migrates_runWith_mockito() throws CoreException {
		createRunnerFixtures();
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyMockitoTest.java",
				"""
				package test;
				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.mockito.Mock;
				import org.mockito.junit.MockitoJUnitRunner;
				
				@RunWith(MockitoJUnitRunner.class)
				public class MyMockitoTest {
					@Mock
					private SomeService service;
					
					@Test
					public void testWithMock() {
						// Test code
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.extension.ExtendWith;
				import org.mockito.Mock;
				import org.mockito.junit.jupiter.MockitoExtension;
				
				@ExtendWith(MockitoExtension.class)
				public class MyMockitoTest {
					@Mock
					private SomeService service;
					
					@Test
					public void testWithMock() {
						// Test code
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_runWith_spring() throws CoreException {
		createRunnerFixtures();
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MySpringTest.java",
				"""
				package test;
				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.springframework.test.context.junit4.SpringRunner;
				import org.springframework.beans.factory.annotation.Autowired;
				
				@RunWith(SpringRunner.class)
				public class MySpringTest {
					@Autowired
					private SomeBean bean;
					
					@Test
					public void testWithSpring() {
						// Test code
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.extension.ExtendWith;
				import org.springframework.beans.factory.annotation.Autowired;
				import org.springframework.test.context.junit.jupiter.SpringExtension;
				
				@ExtendWith(SpringExtension.class)
				public class MySpringTest {
					@Autowired
					private SomeBean bean;
					
					@Test
					public void testWithSpring() {
						// Test code
					}
				}
				"""
		}, null);
	}
}