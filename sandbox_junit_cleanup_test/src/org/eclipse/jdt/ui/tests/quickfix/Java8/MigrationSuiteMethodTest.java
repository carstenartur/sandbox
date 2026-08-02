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
 * Migration of JUnit 3 {@code suite()} aggregators, the dominant pattern of the
 * {@code AllTests} classes in {@code org.eclipse.jdt.ui.tests*}.
 */
public class MigrationSuiteMethodTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void migratesAccumulatingAggregator() throws CoreException {
		IPackageFragment pack= fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		createTestClasses(pack);
		ICompilationUnit cu= pack.createCompilationUnit("AllTests.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.Test;
				import junit.framework.TestSuite;

				public class AllTests {
					public static Test suite() {
						TestSuite suite= new TestSuite("all");
						suite.addTestSuite(FooTest.class);
						suite.addTest(new TestSuite(BarTest.class));
						return suite;
					}
				}
				""", false, null); //$NON-NLS-1$

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.platform.suite.api.SelectClasses;
				import org.junit.platform.suite.api.Suite;

				@Suite
				@SelectClasses({ FooTest.class, BarTest.class })
				public class AllTests {
				}
				""" }, null); //$NON-NLS-1$
	}

	@Test
	public void keepsDynamicAggregatorUntouched() throws CoreException {
		IPackageFragment pack= fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		createTestClasses(pack);
		ICompilationUnit cu= pack.createCompilationUnit("AllTests.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.Test;
				import junit.framework.TestSuite;

				public class AllTests {
					public static Test suite() {
						TestSuite suite= new TestSuite("all");
						if (Boolean.getBoolean("slow")) {
							suite.addTestSuite(FooTest.class);
						}
						return suite;
					}
				}
				""", false, null); //$NON-NLS-1$

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void keepsDecoratedAggregatorUntouched() throws CoreException {
		IPackageFragment pack= fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		createTestClasses(pack);
		pack.createCompilationUnit("ProjectTestSetup.java", //$NON-NLS-1$
				"""
				package test;
				import junit.extensions.TestSetup;
				import junit.framework.Test;

				public class ProjectTestSetup extends TestSetup {
					public ProjectTestSetup(Test test) {
						super(test);
					}
				}
				""", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("AllTests.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.Test;
				import junit.framework.TestSuite;

				public class AllTests {
					public static Test suite() {
						TestSuite suite= new TestSuite("all");
						suite.addTestSuite(FooTest.class);
						return new ProjectTestSetup(suite);
					}
				}
				""", false, null); //$NON-NLS-1$

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	private static void createTestClasses(IPackageFragment pack) throws CoreException {
		pack.createCompilationUnit("FooTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class FooTest extends TestCase {
					public void testFoo() {
					}
				}
				""", false, null); //$NON-NLS-1$
		pack.createCompilationUnit("BarTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class BarTest extends TestCase {
					public void testBar() {
					}
				}
				""", false, null); //$NON-NLS-1$
	}
}
