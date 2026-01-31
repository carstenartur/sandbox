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
 * Tests for migrating @Category to @Tag.
 */
public class MigrationCategoryTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void migrates_single_category_on_method() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				import org.junit.experimental.categories.Category;
				
				public class MyTest {
					@Category(FastTests.class)
					@Test
					public void fastTest() {
						// test code
					}
				}
				
				interface FastTests {
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Tag;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Tag("FastTests")
					@Test
					public void fastTest() {
						// test code
					}
				}
				
				interface FastTests {
				}
				"""
		}, null);
	}

	@Test
	public void migrates_single_category_on_class() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				import org.junit.experimental.categories.Category;
				
				@Category(IntegrationTests.class)
				public class MyTest {
					@Test
					public void integrationTest() {
						// test code
					}
				}
				
				interface IntegrationTests {
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Tag;
				import org.junit.jupiter.api.Test;
				
				@Tag("IntegrationTests")
				public class MyTest {
					@Test
					public void integrationTest() {
						// test code
					}
				}
				
				interface IntegrationTests {
				}
				"""
		}, null);
	}

	@Test
	public void migrates_multiple_categories() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				import org.junit.experimental.categories.Category;
				
				public class MyTest {
					@Category({FastTests.class, UnitTests.class})
					@Test
					public void multiCategoryTest() {
						// test code
					}
				}
				
				interface FastTests {
				}
				
				interface UnitTests {
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Tag;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Tag("FastTests")
					@Tag("UnitTests")
					@Test
					public void multiCategoryTest() {
						// test code
					}
				}
				
				interface FastTests {
				}
				
				interface UnitTests {
				}
				"""
		}, null);
	}

	@Test
	public void migrates_mixed_categories_and_tests() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				import org.junit.experimental.categories.Category;
				
				public class MyTest {
					@Category(FastTests.class)
					@Test
					public void fastTest() {
						// test code
					}
					
					@Category(SlowTests.class)
					@Test
					public void slowTest() {
						// test code
					}
					
					@Test
					public void normalTest() {
						// test code
					}
				}
				
				interface FastTests {
				}
				
				interface SlowTests {
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Tag;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Tag("FastTests")
					@Test
					public void fastTest() {
						// test code
					}
					
					@Tag("SlowTests")
					@Test
					public void slowTest() {
						// test code
					}
					
					@Test
					public void normalTest() {
						// test code
					}
				}
				
				interface FastTests {
				}
				
				interface SlowTests {
				}
				"""
		}, null);
	}

	@Test
	public void migrates_category_on_both_class_and_method() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				import org.junit.experimental.categories.Category;
				
				@Category(IntegrationTests.class)
				public class MyTest {
					@Category(FastTests.class)
					@Test
					public void fastIntegrationTest() {
						// test code
					}
					
					@Test
					public void normalIntegrationTest() {
						// test code
					}
				}
				
				interface FastTests {
				}
				
				interface IntegrationTests {
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_CATEGORY);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Tag;
				import org.junit.jupiter.api.Test;
				
				@Tag("IntegrationTests")
				public class MyTest {
					@Tag("FastTests")
					@Test
					public void fastIntegrationTest() {
						// test code
					}
					
					@Test
					public void normalIntegrationTest() {
						// test code
					}
				}
				
				interface FastTests {
				}
				
				interface IntegrationTests {
				}
				"""
		}, null);
	}
}
