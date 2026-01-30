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
 * Tests for V2 TriggerPattern-based JUnit migration plugins.
 * The V2 plugins use declarative @RewriteRule annotations with TriggerPattern framework
 * for simplified implementation. These tests verify that the V2 implementations
 * produce the same correct migrations as the original plugins.
 * 
 * Tested V2 Plugins:
 * - BeforeJUnitPluginV2
 * - AfterJUnitPluginV2
 * - TestJUnitPluginV2
 * - BeforeClassJUnitPluginV2
 * - AfterClassJUnitPluginV2
 * - IgnoreJUnitPluginV2
 */
public class TriggerPatternPluginTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void beforeJUnitPluginV2_migrates_before_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Before;
				import org.junit.Test;
				
				public class MyTest {
					@Before
					public void setUp() {
						// Setup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@BeforeEach
					public void setUp() {
						// Setup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void afterJUnitPluginV2_migrates_after_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.After;
				import org.junit.Test;
				
				public class MyTest {
					@After
					public void tearDown() {
						// Cleanup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.AfterEach;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@AfterEach
					public void tearDown() {
						// Cleanup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void testJUnitPluginV2_migrates_test_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
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

	@Test
	public void beforeClassJUnitPluginV2_migrates_beforeClass_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.BeforeClass;
				import org.junit.Test;
				
				public class MyTest {
					@BeforeClass
					public static void setUpBeforeClass() {
						// Setup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.BeforeAll;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@BeforeAll
					public static void setUpBeforeClass() {
						// Setup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void afterClassJUnitPluginV2_migrates_afterClass_annotation() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.AfterClass;
				import org.junit.Test;
				
				public class MyTest {
					@AfterClass
					public static void tearDownAfterClass() {
						// Cleanup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.AfterAll;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@AfterAll
					public static void tearDownAfterClass() {
						// Cleanup code
					}
					
					@Test
					public void testSomething() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void ignoreJUnitPluginV2_preserves_reason() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Ignore;
				import org.junit.Test;
				
				public class MyTest {
					@Ignore("Not ready yet")
					@Test
					public void testIgnored() {
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
					@Disabled("Not ready yet")
					@Test
					public void testIgnored() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void ignoreJUnitPluginV2_migrates_without_reason() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Ignore;
				import org.junit.Test;
				
				public class MyTest {
					@Ignore
					@Test
					public void testIgnored() {
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
					public void testIgnored() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void v2_plugins_work_together_in_complex_class() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.After;
				import org.junit.AfterClass;
				import org.junit.Before;
				import org.junit.BeforeClass;
				import org.junit.Ignore;
				import org.junit.Test;
				
				public class MyTest {
					@BeforeClass
					public static void setUpClass() {
					}
					
					@AfterClass
					public static void tearDownClass() {
					}
					
					@Before
					public void setUp() {
					}
					
					@After
					public void tearDown() {
					}
					
					@Test
					public void testActive() {
					}
					
					@Ignore("Not ready")
					@Test
					public void testIgnored() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.AfterAll;
				import org.junit.jupiter.api.AfterEach;
				import org.junit.jupiter.api.BeforeAll;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Disabled;
				import org.junit.jupiter.api.Test;
				
				public class MyTest {
					@BeforeAll
					public static void setUpClass() {
					}
					
					@AfterAll
					public static void tearDownClass() {
					}
					
					@BeforeEach
					public void setUp() {
					}
					
					@AfterEach
					public void tearDown() {
					}
					
					@Test
					public void testActive() {
					}
					
					@Disabled("Not ready")
					@Test
					public void testIgnored() {
					}
				}
				"""
		}, null);
	}
}
