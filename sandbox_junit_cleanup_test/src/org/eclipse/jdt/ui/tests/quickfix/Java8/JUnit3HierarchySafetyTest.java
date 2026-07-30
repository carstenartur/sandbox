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

/** Regression coverage for the fail-closed JUnit 3 migration boundary. */
public class JUnit3HierarchySafetyTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT3_CONTAINER_PATH);
	}

	@Test
	public void leavesLifecycleOverrideHierarchyUntouched() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("AbstractBase.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public abstract class AbstractBase extends TestCase {
					@Override
					protected void setUp() throws Exception {
					}

					public void testInherited() {
					}
				}
				""", false, null);
		ICompilationUnit child= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"""
				package test;

				public class ConcreteTest extends AbstractBase {
					@Override
					protected void setUp() throws Exception {
						super.setUp();
					}

					public void testLocal() {
					}
				}
				""", false, null);

		enableJUnit3Cleanup();
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { base, child });
	}

	@Test
	public void leavesHierarchyWithCustomConstructorUntouched() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class BaseTest extends TestCase {
					public BaseTest() {
					}

					public void testBase() {
					}
				}
				""", false, null);
		ICompilationUnit child= pack.createCompilationUnit("ChildTest.java", //$NON-NLS-1$
				"""
				package test;

				public class ChildTest extends BaseTest {
					public void testChild() {
					}
				}
				""", false, null);

		enableJUnit3Cleanup();
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { base, child });
	}

	@Test
	public void leavesCustomJUnit3ExecutionHookUntouched() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("CustomExecutionTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class CustomExecutionTest extends TestCase {
					@Override
					protected void runTest() throws Throwable {
					}

					public void testLocal() {
					}
				}
				""", false, null);

		enableJUnit3Cleanup();
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void annotatesOnlyExactJUnit3TestMethods() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("SimpleTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class SimpleTest extends TestCase {
					protected void setUp() {
					}

					public void testSomething() {
					}

					public void shouldRemainAHelper() {
					}

					public void helper() {
					}
				}
				""", false, null);

		enableJUnit3Cleanup();
		context.assertRefactoringResultAsExpectedNormalizingWhitespace(new ICompilationUnit[] { unit },
				new String[] {
						"""
						package test;
						import org.junit.jupiter.api.BeforeEach;
						import org.junit.jupiter.api.Test;

						public class SimpleTest {
							@BeforeEach
							protected void setUp() {
							}

							@Test
							public void testSomething() {
							}

							public void shouldRemainAHelper() {
							}

							public void helper() {
							}
						}
						"""
				}, null);
	}

	private void enableJUnit3Cleanup() throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT3_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_3_TEST);
	}
}
