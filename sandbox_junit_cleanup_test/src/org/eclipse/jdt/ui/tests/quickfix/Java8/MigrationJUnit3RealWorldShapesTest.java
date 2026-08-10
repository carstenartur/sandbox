/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
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

/** Regression coverage derived from JDT Core's R4_40 APT tests. */
public class MigrationJUnit3RealWorldShapesTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void migratesDelegatingNameConstructorSelfSuiteAndSuiteAggregator() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("org.eclipse.jdt.apt.tests", true, null); //$NON-NLS-1$
		ICompilationUnit factoryPathTests= pack.createCompilationUnit("FactoryPathTests.java", //$NON-NLS-1$
				"""
				package org.eclipse.jdt.apt.tests;

				import junit.framework.Test;
				import junit.framework.TestCase;
				import junit.framework.TestSuite;

				public class FactoryPathTests extends TestCase {
					private String state;

					public FactoryPathTests(String name) {
						super(name);
					}

					public static Test suite() {
						return new TestSuite(FactoryPathTests.class);
					}

					@Override
					protected void setUp() throws Exception {
						super.setUp();
						state= "ready";
					}

					public void testState() {
						assertEquals("state", "ready", state);
					}
				}
				""", false, null); //$NON-NLS-1$
		ICompilationUnit testAll= pack.createCompilationUnit("TestAll.java", //$NON-NLS-1$
				"""
				package org.eclipse.jdt.apt.tests;

				import junit.framework.Test;
				import junit.framework.TestCase;
				import junit.framework.TestSuite;

				public class TestAll extends TestCase {
					static {
						System.setProperty("modules", "java.base");
					}

					public TestAll(String testName) {
						super(testName);
					}

					public static Test suite() {
						TestSuite suite= new TestSuite();
						suite.addTest(FactoryPathTests.suite());
						return suite;
					}
				}
				""", false, null); //$NON-NLS-1$

		enableRealWorldJUnit3Migration();

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { factoryPathTests, testAll }, new String[] {
						"""
						package org.eclipse.jdt.apt.tests;

						import org.junit.jupiter.api.Assertions;
						import org.junit.jupiter.api.BeforeEach;
						import org.junit.jupiter.api.Test;

						public class FactoryPathTests {
							private String state;

							@BeforeEach
							protected void setUp() throws Exception {
								state= "ready";
							}

							@Test
							public void testState() {
								Assertions.assertEquals("ready", state, "state");
							}
						}
						""", //$NON-NLS-1$
						"""
						package org.eclipse.jdt.apt.tests;

						import org.junit.platform.suite.api.BeforeSuite;
						import org.junit.platform.suite.api.SelectClasses;
						import org.junit.platform.suite.api.Suite;

						@Suite
						@SelectClasses(FactoryPathTests.class)
						public class TestAll {
							@BeforeSuite
							static void beforeSuite() {
								System.setProperty("modules", "java.base");
							}
						}
						""" //$NON-NLS-1$
				}, null);
	}

	@Test
	public void rejectsConstructorWithUserStateInsteadOfDeletingIt() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("unsafe", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("StatefulConstructionTest.java", //$NON-NLS-1$
				"""
				package unsafe;

				import junit.framework.TestCase;

				public class StatefulConstructionTest extends TestCase {
					private final String configured;

					public StatefulConstructionTest(String name) {
						super(name);
						configured= name;
					}

					public void testConfigured() {
						assertNotNull(configured);
					}
				}
				""", false, null); //$NON-NLS-1$

		enableRealWorldJUnit3Migration();

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void rejectsCollidingJUnit3MethodNameHashesInsteadOfGuessingTieOrder() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("unsafe", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("CollidingOrderTest.java", //$NON-NLS-1$
				"""
				package unsafe;

				import junit.framework.TestCase;

				public class CollidingOrderTest extends TestCase {
					public void testAa() {
					}

					public void testBB() {
					}
				}
				""", false, null); //$NON-NLS-1$

		enableRealWorldJUnit3Migration();

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void rejectsMultipleSuiteInitializersInsteadOfReorderingThem() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("unsafe", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("MultipleInitializers.java", //$NON-NLS-1$
				"""
				package unsafe;

				import junit.framework.Test;
				import junit.framework.TestSuite;

				public class MultipleInitializers {
					static {
						System.setProperty("first", "true");
					}

					static {
						System.setProperty("second", "true");
					}

					public static Test suite() {
						return new TestSuite(StatefulConstructionTest.class);
					}
				}
				""", false, null); //$NON-NLS-1$

		enableRealWorldJUnit3Migration();

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	private void enableRealWorldJUnit3Migration() throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT3_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_3_TEST);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);
	}
}
