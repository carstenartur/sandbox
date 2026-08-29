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

/** Regression coverage for inherited ExternalResource fixtures without local callbacks. */
public class MultiFileExternalResourceNoOpSubclassTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void leavesCallbackFreeSubclassUnchangedWhileMigratingItsBaseAndRuleField() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("WorkspaceTestSetup.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.rules.ExternalResource;

				public class WorkspaceTestSetup extends ExternalResource {
					private final boolean junit4;

					public WorkspaceTestSetup(boolean junit4) {
						this.junit4 = junit4;
					}

					@Override
					protected void before() throws Throwable {
						System.setProperty("junit4", Boolean.toString(junit4));
					}

					@Override
					protected void after() {
						System.clearProperty("junit4");
					}
				}
				""", false, null);
		ICompilationUnit subtype= pack.createCompilationUnit("JUnit4WorkspaceTestSetup.java", //$NON-NLS-1$
				"""
				package test;

				public class JUnit4WorkspaceTestSetup extends WorkspaceTestSetup {
					public JUnit4WorkspaceTestSetup() {
						super(true);
					}
				}
				""", false, null);
		ICompilationUnit test= pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;

				public class MyTest {
					@Rule
					public JUnit4WorkspaceTestSetup workspace = new JUnit4WorkspaceTestSetup();
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE);

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(new ICompilationUnit[] { base, subtype, test },
				new String[] {
						"""
						package test;
						import org.junit.jupiter.api.extension.AfterEachCallback;
						import org.junit.jupiter.api.extension.BeforeEachCallback;
						import org.junit.jupiter.api.extension.ExtensionContext;

						public class WorkspaceTestSetup implements BeforeEachCallback, AfterEachCallback {
							private final boolean junit4;

							public WorkspaceTestSetup(boolean junit4) {
								this.junit4 = junit4;
							}

							@Override
							public void beforeEach(ExtensionContext context) throws Exception {
								System.setProperty("junit4", Boolean.toString(junit4));
							}

							@Override
							public void afterEach(ExtensionContext context) {
								System.clearProperty("junit4");
							}
						}
						""",
						"""
						package test;

						public class JUnit4WorkspaceTestSetup extends WorkspaceTestSetup {
							public JUnit4WorkspaceTestSetup() {
								super(true);
							}
						}
						""",
						"""
						package test;
						import org.junit.jupiter.api.extension.RegisterExtension;
						import org.junit.jupiter.api.parallel.Isolated;

						@Isolated
						public class MyTest {
							@RegisterExtension
							public JUnit4WorkspaceTestSetup workspace = new JUnit4WorkspaceTestSetup();
						}
						""" }, null);
	}
}
