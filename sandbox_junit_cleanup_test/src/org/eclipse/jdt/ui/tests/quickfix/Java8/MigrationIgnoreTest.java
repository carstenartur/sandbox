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
 * Tests for migrating @Ignore to @Disabled.
 */
public class MigrationIgnoreTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void migrates_ignore_without_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Ignore;
				import org.junit.Test;
				
				public class MyTest {
					@Ignore
					@Test
					public void ignoredTest() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Disabled;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Disabled
					@Test
					public void ignoredTest() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_ignore_with_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Ignore;
				import org.junit.Test;
				
				public class MyTest {
					@Ignore("Not yet implemented")
					@Test
					public void ignoredTestWithMessage() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Disabled;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Disabled("Not yet implemented")
					@Test
					public void ignoredTestWithMessage() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_multiple_ignored_tests() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Ignore;
				import org.junit.Test;
				
				public class MyTest {
					@Ignore
					@Test
					public void ignoredTest1() {
					}
					
					@Ignore("Temporarily disabled")
					@Test
					public void ignoredTest2() {
					}
					
					@Test
					public void activeTest() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Disabled;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Disabled
					@Test
					public void ignoredTest1() {
					}
					
					@Disabled("Temporarily disabled")
					@Test
					public void ignoredTest2() {
					}
					
					@Test
					public void activeTest() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_ignore_with_explicit_value_attribute() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.Ignore;
				import org.junit.Test;
				
				public class MyTest {
					@Ignore(value = "explicit value attribute")
					@Test
					public void ignoredTestWithExplicitValue() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Disabled;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Disabled("explicit value attribute")
					@Test
					public void ignoredTestWithExplicitValue() {
					}
				}
				"""
		}, null);
	}
}
