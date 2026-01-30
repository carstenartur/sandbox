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
 * Tests for migrating advanced @RunWith annotations to JUnit 5 equivalents.
 * Covers Enclosed, Theories, and Categories runners.
 */
public class MigrationRunnersAdvancedTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void migrates_runWith_enclosed_to_nested() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("EnclosedTest.java",
				"""
				package test;
				import org.junit.Test;
				import org.junit.experimental.runners.Enclosed;
				import org.junit.runner.RunWith;
				
				@RunWith(Enclosed.class)
				public class EnclosedTest {
				    
				    public static class WhenConditionA {
				        @Test
				        public void shouldDoSomething() {
				            // test
				        }
				    }
				    
				    public static class WhenConditionB {
				        @Test
				        public void shouldDoSomethingElse() {
				            // test
				        }
				    }
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Nested;
				import org.junit.jupiter.api.Test;
				
				public class EnclosedTest {
				    
				    @Nested
				    class WhenConditionA {
				        @Test
				        void shouldDoSomething() {
				            // test
				        }
				    }
				    
				    @Nested
				    class WhenConditionB {
				        @Test
				        void shouldDoSomethingElse() {
				            // test
				        }
				    }
				}
				"""
		}, null);
	}

	@Test
	public void migrates_runWith_theories_to_parameterizedTest() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("TheoriesTest.java",
				"""
				package test;
				import org.junit.experimental.theories.DataPoints;
				import org.junit.experimental.theories.Theories;
				import org.junit.experimental.theories.Theory;
				import org.junit.runner.RunWith;
				import static org.junit.Assert.assertTrue;
				
				@RunWith(Theories.class)
				public class TheoriesTest {
				    
				    @DataPoints
				    public static int[] values = {1, 2, 3, 4, 5};
				    
				    @Theory
				    public void testPositiveNumbers(int value) {
				        assertTrue(value > 0);
				    }
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.assertTrue;
				
				import org.junit.jupiter.params.ParameterizedTest;
				import org.junit.jupiter.params.provider.ValueSource;
				
				public class TheoriesTest {
				    
				    @ParameterizedTest
				    @ValueSource(ints = {1, 2, 3, 4, 5})
				    void testPositiveNumbers(int value) {
				        assertTrue(value > 0);
				    }
				}
				"""
		}, null);
	}

	@Test
	public void migrates_runWith_categories_to_suite_with_tags() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		
		// Create marker interfaces for categories
		pack.createCompilationUnit("FastTests.java",
				"""
				package test;
				public interface FastTests {}
				""", false, null);
		
		pack.createCompilationUnit("SlowTests.java",
				"""
				package test;
				public interface SlowTests {}
				""", false, null);
		
		ICompilationUnit cu = pack.createCompilationUnit("FastTestSuite.java",
				"""
				package test;
				import org.junit.experimental.categories.Categories;
				import org.junit.experimental.categories.Categories.IncludeCategory;
				import org.junit.experimental.categories.Categories.ExcludeCategory;
				import org.junit.runner.RunWith;
				import org.junit.runners.Suite.SuiteClasses;
				
				@RunWith(Categories.class)
				@IncludeCategory(FastTests.class)
				@ExcludeCategory(SlowTests.class)
				@SuiteClasses({TestA.class, TestB.class})
				public class FastTestSuite {
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.platform.suite.api.ExcludeTags;
				import org.junit.platform.suite.api.IncludeTags;
				import org.junit.platform.suite.api.SelectClasses;
				import org.junit.platform.suite.api.Suite;
				
				@Suite
				@IncludeTags("FastTests")
				@ExcludeTags("SlowTests")
				@SelectClasses({TestA.class, TestB.class})
				public class FastTestSuite {
				}
				"""
		}, null);
	}
}
