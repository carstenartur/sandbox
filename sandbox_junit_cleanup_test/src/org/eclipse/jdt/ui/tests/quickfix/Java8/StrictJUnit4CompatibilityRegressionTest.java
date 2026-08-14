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

/** Regressions derived from the strict Eclipse JDT UI corpus run. */
public class StrictJUnit4CompatibilityRegressionTest {

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
	public void unusedTestNameDoesNotBlockInheritedBeforeMigration() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("SourceTestCase.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Before;
				import org.junit.Rule;
				import org.junit.rules.TestName;

				public class SourceTestCase {
					@Rule
					public TestName tn = new TestName();

					protected String value;

					@Before
					public void setUp() {
						value = "ready";
					}
				}
				""", false, null);
		ICompilationUnit test= pack.createCompilationUnit("InheritedFixtureTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Test;

				public class InheritedFixtureTest extends SourceTestCase {
					@Test
					public void testFixture() {
						System.out.println(value);
					}
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);

		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(
				new ICompilationUnit[] { base, test }, new String[] {
						"""
						package test;

						import org.junit.jupiter.api.BeforeEach;
						import org.junit.jupiter.api.TestInfo;

						public class SourceTestCase {
							public String tn;

							@BeforeEach
							void initializeTnFromTestInfo(TestInfo testInfo) {
								this.tn = testInfo.getTestMethod().orElseThrow().getName();
							}

							protected String value;

							@BeforeEach
							public void setUp() {
								value = "ready";
							}
						}
						""",
						"""
						package test;

						import org.junit.jupiter.api.Test;

						public class InheritedFixtureTest extends SourceTestCase {
							@Test
							public void testFixture() {
								System.out.println(value);
							}
						}
						""" });
	}

	@Test
	public void blockedParameterizedConsumerKeepsSharedResourceValidForJUnit4AndJupiter()
			throws CoreException {
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
		ICompilationUnit migrated= pack.createCompilationUnit("JupiterCandidate.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Rule;
				import org.junit.Test;

				public class JupiterCandidate {
					@Rule
					public SharedResource resource = new SharedResource();

					@Test
					public void testResource() {
						System.out.println(System.getProperty("resource"));
					}
				}
				""", false, null);
		ICompilationUnit blocked= pack.createCompilationUnit("LegacyParameterizedTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class LegacyParameterizedTest {
					@Parameters
					public static Object[][] data() {
						return new Object[2][0];
					}

					@Rule
					public SharedResource resource = new SharedResource();

					@Test
					public void testResource() {
						System.out.println(System.getProperty("resource"));
					}
				}
				""", false, null);

		enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED);

		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(
				new ICompilationUnit[] { resource, migrated, blocked }, new String[] {
						"""
						package test;

						import org.junit.jupiter.api.extension.AfterEachCallback;
						import org.junit.jupiter.api.extension.BeforeEachCallback;
						import org.junit.jupiter.api.extension.ExtensionContext;
						import org.junit.rules.ExternalResource;

						public class SharedResource extends ExternalResource implements BeforeEachCallback, AfterEachCallback {
							@Override
							protected void before() throws Throwable {
								System.setProperty("resource", "started");
							}

							@Override
							protected void after() {
								System.clearProperty("resource");
							}

							@Override
							public void beforeEach(ExtensionContext context) throws Exception {
								try {
									before();
								} catch (Exception exception) {
									throw exception;
								} catch (Error error) {
									throw error;
								} catch (Throwable throwable) {
									throw new RuntimeException(throwable);
								}
							}

							@Override
							public void afterEach(ExtensionContext context) {
								after();
							}
						}
						""",
						"""
						package test;

						import org.junit.jupiter.api.Test;
						import org.junit.jupiter.api.extension.RegisterExtension;

						public class JupiterCandidate {
							@RegisterExtension
							public SharedResource resource = new SharedResource();

							@Test
							public void testResource() {
								System.out.println(System.getProperty("resource"));
							}
						}
						""",
						"""
						package test;

						import org.junit.Rule;
						import org.junit.Test;
						import org.junit.runner.RunWith;
						import org.junit.runners.Parameterized;
						import org.junit.runners.Parameterized.Parameters;

						@RunWith(Parameterized.class)
						public class LegacyParameterizedTest {
							@Parameters
							public static Object[][] data() {
								return new Object[2][0];
							}

							@Rule
							public SharedResource resource = new SharedResource();

							@Test
							public void testResource() {
								System.out.println(System.getProperty("resource"));
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
