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
 * Tests for migrating @Test annotation from JUnit 4 to JUnit 5.
 * Covers basic @Test migration and timeout parameter.
 */
public class MigrationTestAnnotationTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void migrates_test_annotation_basic() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testSomething() {
					}
				}
				"""
		}, null);
	}

	@Disabled("Not yet implemented - @Test(timeout) migration")
	@Test
	public void migrates_test_timeout_to_timeout_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				
				public class MyTest {
					@Test(timeout = 1000)
					public void testWithTimeout() {
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
					public void testWithTimeout() {
						// Test code
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_multiple_test_methods() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Test;
				
				public class MyTest {
					@Test
					public void test1() {
					}
					
					@Test
					public void test2() {
					}
					
					@Test
					public void test3() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void test1() {
					}
					
					@Test
					public void test2() {
					}
					
					@Test
					public void test3() {
					}
				}
				"""
		}, null);
	}
}
