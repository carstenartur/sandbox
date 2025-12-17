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
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

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
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		
		// Add stub classes for Mockito
		IPackageFragment mockitoJunitPack = fRoot.createPackageFragment("org.mockito.junit", false, null);
		mockitoJunitPack.createCompilationUnit("MockitoJUnitRunner.java",
				"""
				package org.mockito.junit;
				public class MockitoJUnitRunner {}
				""", false, null);
		
		IPackageFragment mockitoRunnersPack = fRoot.createPackageFragment("org.mockito.runners", false, null);
		mockitoRunnersPack.createCompilationUnit("MockitoJUnitRunner.java",
				"""
				package org.mockito.runners;
				public class MockitoJUnitRunner {}
				""", false, null);
		
		// Add stub classes for Spring
		IPackageFragment springJunit4Pack = fRoot.createPackageFragment("org.springframework.test.context.junit4", false, null);
		springJunit4Pack.createCompilationUnit("SpringRunner.java",
				"""
				package org.springframework.test.context.junit4;
				public class SpringRunner {}
				""", false, null);
		springJunit4Pack.createCompilationUnit("SpringJUnit4ClassRunner.java",
				"""
				package org.springframework.test.context.junit4;
				public class SpringJUnit4ClassRunner {}
				""", false, null);
	}

	@Disabled("Not yet implemented - @RunWith(Suite.class) migration")
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

	@Disabled("Not yet implemented - Parameterized runner migration")
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
						assertEquals(expected, input * 2);
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.params.ParameterizedTest;
				import org.junit.jupiter.params.provider.MethodSource;
				import java.util.stream.Stream;
				
				public class MyParameterizedTest {
					@ParameterizedTest
					@MethodSource("data")
					public void testMultiply(int input, int expected) {
						assertEquals(expected, input * 2);
					}
					
					static Stream<org.junit.jupiter.params.provider.Arguments> data() {
						return Stream.of(
							org.junit.jupiter.params.provider.Arguments.of(1, 2),
							org.junit.jupiter.params.provider.Arguments.of(2, 4),
							org.junit.jupiter.params.provider.Arguments.of(3, 6)
						);
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_runWith_mockito() throws CoreException {
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
				import org.springframework.test.context.junit.jupiter.SpringExtension;
				import org.springframework.beans.factory.annotation.Autowired;
				
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
