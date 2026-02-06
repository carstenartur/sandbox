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
 * Tests for migrating @FixMethodOrder from JUnit 4 to JUnit 5 @TestMethodOrder.
 */
public class MigrationFixMethodOrderTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void migrates_NAME_ASCENDING_to_MethodName() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.FixMethodOrder;
				import org.junit.Test;
				import org.junit.runners.MethodSorters;
				
				@FixMethodOrder(MethodSorters.NAME_ASCENDING)
				public class MyTest {
					@Test
					public void testA() {
						// test code
					}
					
					@Test
					public void testB() {
						// test code
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_FIX_METHOD_ORDER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.MethodOrderer;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.TestMethodOrder;
				
				@TestMethodOrder(MethodOrderer.MethodName.class)
				public class MyTest {
					@Test
					public void testA() {
						// test code
					}
					
					@Test
					public void testB() {
						// test code
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_JVM_to_Random() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.FixMethodOrder;
				import org.junit.Test;
				import org.junit.runners.MethodSorters;
				
				@FixMethodOrder(MethodSorters.JVM)
				public class MyTest {
					@Test
					public void testA() {
						// test code
					}
					
					@Test
					public void testB() {
						// test code
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_FIX_METHOD_ORDER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.MethodOrderer;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.TestMethodOrder;
				
				@TestMethodOrder(MethodOrderer.Random.class)
				public class MyTest {
					@Test
					public void testA() {
						// test code
					}
					
					@Test
					public void testB() {
						// test code
					}
				}
				"""
		}, null);
	}

	@Test
	public void removes_DEFAULT_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.FixMethodOrder;
				import org.junit.Test;
				import org.junit.runners.MethodSorters;
				
				@FixMethodOrder(MethodSorters.DEFAULT)
				public class MyTest {
					@Test
					public void testA() {
						// test code
					}
					
					@Test
					public void testB() {
						// test code
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_FIX_METHOD_ORDER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@Test
					public void testA() {
						// test code
					}
					
					@Test
					public void testB() {
						// test code
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_with_other_junit_cleanup() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
				"""
				package test;
				import org.junit.FixMethodOrder;
				import org.junit.Test;
				import org.junit.runners.MethodSorters;
				
				@FixMethodOrder(MethodSorters.NAME_ASCENDING)
				public class MyTest {
					@Test
					public void testA() {
						// test code
					}
					
					@Test
					public void testB() {
						// test code
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_FIX_METHOD_ORDER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.MethodOrderer;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.TestMethodOrder;
				
				@TestMethodOrder(MethodOrderer.MethodName.class)
				public class MyTest {
					@Test
					public void testA() {
						// test code
					}
					
					@Test
					public void testB() {
						// test code
					}
				}
				"""
		}, null);
	}
}
