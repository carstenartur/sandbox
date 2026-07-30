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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

import org.eclipse.jdt.ui.tests.quickfix.Java8.JUnitRuntimeTestTree.Snapshot;

/** Active migration contract for the first detachable Eclipse JDT Core harness leaf. */
public class JdtCoreLeafMigrationTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void migratesRealPackageNamedSuiteLeafAndPreservesRuntimeTree() throws CoreException {
		ICompilationUnit harness= createHarness();
		IPackageFragment pack= root.createPackageFragment("sample", true, null); //$NON-NLS-1$
		ICompilationUnit test= pack.createCompilationUnit("IrritantSetTest.java", //$NON-NLS-1$
				"""
				package sample;
				import junit.framework.Test;
				import junit.framework.TestSuite;
				import org.eclipse.jdt.core.tests.junit.extension.TestCase;

				public class IrritantSetTest extends TestCase {
					public IrritantSetTest(String name) {
						super(name);
					}

					public static Test suite() {
						TestSuite suite = new TestSuite(IrritantSetTest.class.getPackageName());
						suite.addTest(new TestSuite(IrritantSetTest.class));
						return suite;
					}

					public void testGroup4() {
						assertTrue(true);
					}
				}
				""", false, null);

		IType launchTarget= test.findPrimaryType();
		assertNotNull(launchTarget);
		Snapshot before= JUnitRuntimeTestTree.capture(launchTarget);
		assertTrue(before.successful(), () -> "JDT Core-style JUnit 3 baseline failed: " + before.entries()); //$NON-NLS-1$

		enableMigration();
		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { harness, test }, new String[] {
						"""
						package org.eclipse.jdt.core.tests.junit.extension;
						import junit.framework.Test;
						import junit.framework.TestSuite;

						public class TestCase extends junit.framework.TestCase {
							public TestCase(String name) {
								super(name);
							}

							public static Test buildTestSuite(Class<?> type) {
								return new TestSuite(type);
							}
						}
						""",
						"""
						package sample;

						import org.junit.jupiter.api.Assertions;
						import org.junit.jupiter.api.Test;

						public class IrritantSetTest {
							@Test
							public void testGroup4() {
								Assertions.assertTrue(true);
							}
						}
						"""
				}, null);

		Snapshot after= JUnitRuntimeTestTree.capture(launchTarget);
		assertTrue(after.successful(), () -> "Migrated JDT Core leaf failed: " + after.entries()); //$NON-NLS-1$
		assertEquals(before.entries(), after.entries(),
				"The same JDT launch must preserve the real package-named suite path and test identity."); //$NON-NLS-1$
	}

	@Test
	public void rejectsMultipleTestsBecauseConfigurableHarnessOrderingWouldBeLost() throws CoreException {
		ICompilationUnit harness= createHarness();
		IPackageFragment pack= root.createPackageFragment("sample", true, null); //$NON-NLS-1$
		String source= """
				package sample;
				import junit.framework.Test;
				import org.eclipse.jdt.core.tests.junit.extension.TestCase;

				public class OrderedTests extends TestCase {
					public OrderedTests(String name) {
						super(name);
					}

					public static Test suite() {
						return buildTestSuite(OrderedTests.class);
					}

					public void testA() {
					}

					public void testB() {
					}
				}
				""";
		ICompilationUnit test= pack.createCompilationUnit("OrderedTests.java", source, false, null); //$NON-NLS-1$

		enableMigration();
		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { harness, test }, new String[] { harness.getSource(), source }, null);
	}

	private ICompilationUnit createHarness() throws CoreException {
		IPackageFragment harnessPackage=
				root.createPackageFragment("org.eclipse.jdt.core.tests.junit.extension", true, null); //$NON-NLS-1$
		return harnessPackage.createCompilationUnit("TestCase.java", //$NON-NLS-1$
				"""
				package org.eclipse.jdt.core.tests.junit.extension;
				import junit.framework.Test;
				import junit.framework.TestSuite;

				public class TestCase extends junit.framework.TestCase {
					public TestCase(String name) {
						super(name);
					}

					public static Test buildTestSuite(Class<?> type) {
						return new TestSuite(type);
					}
				}
				""", false, null);
	}

	private void enableMigration() throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT3_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_3_TEST);
	}
}
