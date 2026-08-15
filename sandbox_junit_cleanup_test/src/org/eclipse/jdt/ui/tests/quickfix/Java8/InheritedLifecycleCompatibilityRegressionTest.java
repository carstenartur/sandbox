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
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.MultiFileCleanUpLifecycleAssertions;

/** Regression for inherited JUnit 4 lifecycle methods with unannotated overrides. */
public class InheritedLifecycleCompatibilityRegressionTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
	}

	@Test
	public void unannotatedOverridesRetainInheritedLifecycleSemantics() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("LifecycleBase.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.After;
				import org.junit.Before;

				public class LifecycleBase {
					protected String value;

					@Before
					public void setUp() {
						value = "base";
					}

					@After
					public void tearDown() {
						value = null;
					}
				}
				""", false, null);
		ICompilationUnit test= pack.createCompilationUnit("LifecycleOverrideTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Test;

				public class LifecycleOverrideTest extends LifecycleBase {
					@Override
					public void setUp() {
						super.setUp();
						value += "-override";
					}

					@Override
					public void tearDown() {
						value = null;
						super.tearDown();
					}

					@Test
					public void testLifecycle() {
						System.out.println(value);
					}
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(
				new ICompilationUnit[] { base, test }, new String[] {
						"""
						package test;

						import org.junit.jupiter.api.AfterEach;
						import org.junit.jupiter.api.BeforeEach;

						public class LifecycleBase {
							protected String value;

							@BeforeEach
							public void setUp() {
								value = "base";
							}

							@AfterEach
							public void tearDown() {
								value = null;
							}
						}
						""",
						"""
						package test;

						import org.junit.jupiter.api.AfterEach;
						import org.junit.jupiter.api.BeforeEach;
						import org.junit.jupiter.api.Test;

						public class LifecycleOverrideTest extends LifecycleBase {
							@Override
							@BeforeEach
							public void setUp() {
								super.setUp();
								value += "-override";
							}

							@Override
							@AfterEach
							public void tearDown() {
								value = null;
								super.tearDown();
							}

							@Test
							public void testLifecycle() {
								System.out.println(value);
							}
						}
						""" });
	}

	private void enable(String... options) throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		for (String option : options) {
			context.enable(option);
		}
	}
}
