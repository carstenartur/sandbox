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

/** Lifecycle QA for the coordinated JUnit ExternalResource migration. */
public class MultiFileExternalResourceLifecycleTest {

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
	public void completeSelectionMigratesResourceAndRuleUserAtomically() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit resource= pack.createCompilationUnit("SharedResource.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.rules.ExternalResource;

				public class SharedResource extends ExternalResource {
					@Override
					protected void before() throws Throwable {
						System.setProperty("resource", "started");
					}

					@Override
					protected void after() {
						System.clearProperty("resource");
					}
				}
				""", false, null);
		ICompilationUnit test= pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;

				public class MyTest {
					@Rule
					public SharedResource resource = new SharedResource();
				}
				""", false, null);
		ICompilationUnit unrelated= pack.createCompilationUnit("UnrelatedTestSupport.java", //$NON-NLS-1$
				"""
				package test;

				public class UnrelatedTestSupport {
					String value() {
						return "unchanged";
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);

		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(
				new ICompilationUnit[] { resource, test, unrelated },
				new ICompilationUnit[] { resource, test, unrelated },
				new String[] {
						"""
						package test;
						import org.junit.jupiter.api.extension.AfterEachCallback;
						import org.junit.jupiter.api.extension.BeforeEachCallback;
						import org.junit.jupiter.api.extension.ExtensionContext;

						public class SharedResource implements BeforeEachCallback, AfterEachCallback {
							@Override
							public void beforeEach(ExtensionContext context) {
								System.setProperty("resource", "started");
							}

							@Override
							public void afterEach(ExtensionContext context) {
								System.clearProperty("resource");
							}
						}
						""",
						"""
						package test;
						import org.junit.jupiter.api.extension.RegisterExtension;

						public class MyTest {
							@RegisterExtension
							public SharedResource resource = new SharedResource();
						}
						""",
						"""
						package test;

						public class UnrelatedTestSupport {
							String value() {
								return "unchanged";
							}
						}
						""" });
	}
}
