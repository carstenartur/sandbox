/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Regression test for multiple JUnit rewrites discovered through one holder. */
class JUnitSuiteCombinedRewriteIsolationTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	IPackageFragmentRoot root;

	@BeforeEach
	void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	void migratesSuiteRunnerAndSuiteClassesAlongsideAssertionsAndLifecycle() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import static org.junit.Assert.*;
				import org.junit.Before;
				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Suite;

				@RunWith(Suite.class)
				@Suite.SuiteClasses({MyTest.class})
				public class MyTest {
					@Before
					public void setUp() {
					}

					@Test
					public void testSomething() {
						assertEquals("expected", "actual");
					}
				}
				""", false, null); //$NON-NLS-1$

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] {
				"""
				package test;
				import static org.junit.jupiter.api.Assertions.*;

				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;
				import org.junit.platform.suite.api.SelectClasses;
				import org.junit.platform.suite.api.Suite;

				@Suite
				@SelectClasses({MyTest.class})
				public class MyTest {
					@BeforeEach
					public void setUp() {
					}

					@Test
					public void testSomething() {
						assertEquals("expected", "actual");
					}
				}
				"""
		}, null);
	}
}
