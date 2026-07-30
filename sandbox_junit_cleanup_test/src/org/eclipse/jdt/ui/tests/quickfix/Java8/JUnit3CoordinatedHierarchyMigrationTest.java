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

import org.sandbox.jdt.internal.corext.fix.multifile.JUnitTestTypeInventory;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

import org.eclipse.jdt.ui.tests.quickfix.Java8.JUnitRuntimeTestTree.Snapshot;

/** End-to-end rewrite contract for ordinary closed JUnit 3 hierarchies. */
public class JUnit3CoordinatedHierarchyMigrationTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		// Keep Vintage and Jupiter on one classpath so the configured JDT finder and
		// runtime launch kind are the same before and after the migration.
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void migratesAbstractBaseAndConcreteLeafAtomically() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public abstract class BaseTest extends TestCase {
					@Override
					protected void setUp() {
					}

					public void testInherited() {
						assertEquals("value", 1, 1);
					}
				}
				""", false, null);
		ICompilationUnit concrete= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"""
				package test;

				public class ConcreteTest extends BaseTest {
					public void helper() {
					}

					public void testLocal() {
					}
				}
				""", false, null);

		IType launchTarget= concrete.findPrimaryType();
		assertNotNull(launchTarget);
		JUnitTestTypeInventory before= JUnitTestTypeInventory.capture(context.getJavaProject(), null);
		Snapshot runtimeBefore= JUnitRuntimeTestTree.capture(launchTarget);
		assertTrue(runtimeBefore.successful(), () -> "JUnit 3 baseline failed: " + runtimeBefore.entries()); //$NON-NLS-1$
		enableMigration();
		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { base, concrete }, new String[] {
						"""
						package test;
						import org.junit.jupiter.api.Assertions;
						import org.junit.jupiter.api.BeforeEach;
						import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
						import org.junit.jupiter.api.Order;
						import org.junit.jupiter.api.Test;
						import org.junit.jupiter.api.TestMethodOrder;

						@TestMethodOrder(OrderAnnotation.class)
						public abstract class BaseTest {
							@BeforeEach
							protected void setUp() {
							}

							@Test
							@Order(3)
							public void testInherited() {
								Assertions.assertEquals(1, 1, "value");
							}
						}
						""",
						"""
						package test;

						import org.junit.jupiter.api.Order;
						import org.junit.jupiter.api.Test;

						public class ConcreteTest extends BaseTest {
							public void helper() {
							}

							@Test
							@Order(1)
							public void testLocal() {
							}
						}
						"""
				}, null);
		assertFinderInventoryUnchanged(before);
		assertRuntimeTreeUnchanged(runtimeBefore, launchTarget);
	}

	@Test
	public void migratesThreeLevelHierarchyAndPreservesFinderInventory() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("deep", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package deep;
				import junit.framework.TestCase;

				public abstract class BaseTest extends TestCase {
					public void testFromBase() {
					}
				}
				""", false, null);
		ICompilationUnit middle= pack.createCompilationUnit("IntermediateTest.java", //$NON-NLS-1$
				"""
				package deep;

				public abstract class IntermediateTest extends BaseTest {
					public void testFromMiddle() {
					}
				}
				""", false, null);
		ICompilationUnit concrete= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"""
				package deep;

				public class ConcreteTest extends IntermediateTest {
					public void testFromLeaf() {
					}
				}
				""", false, null);

		IType launchTarget= concrete.findPrimaryType();
		assertNotNull(launchTarget);
		JUnitTestTypeInventory before= JUnitTestTypeInventory.capture(context.getJavaProject(), null);
		Snapshot runtimeBefore= JUnitRuntimeTestTree.capture(launchTarget);
		assertTrue(runtimeBefore.successful(), () -> "JUnit 3 baseline failed: " + runtimeBefore.entries()); //$NON-NLS-1$
		enableMigration();
		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { base, middle, concrete }, new String[] {
						"""
						package deep;
						import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
						import org.junit.jupiter.api.Order;
						import org.junit.jupiter.api.Test;
						import org.junit.jupiter.api.TestMethodOrder;

						@TestMethodOrder(OrderAnnotation.class)
						public abstract class BaseTest {
							@Test
							@Order(5)
							public void testFromBase() {
							}
						}
						""",
						"""
						package deep;

						import org.junit.jupiter.api.Order;
						import org.junit.jupiter.api.Test;

						public abstract class IntermediateTest extends BaseTest {
							@Test
							@Order(3)
							public void testFromMiddle() {
							}
						}
						""",
						"""
						package deep;

						import org.junit.jupiter.api.Order;
						import org.junit.jupiter.api.Test;

						public class ConcreteTest extends IntermediateTest {
							@Test
							@Order(1)
							public void testFromLeaf() {
							}
						}
						"""
				}, null);
		assertFinderInventoryUnchanged(before);
		assertRuntimeTreeUnchanged(runtimeBefore, launchTarget);
	}

	private void enableMigration() throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT3_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_3_TEST);
	}

	private void assertFinderInventoryUnchanged(JUnitTestTypeInventory before) throws CoreException {
		JUnitTestTypeInventory after= JUnitTestTypeInventory.capture(context.getJavaProject(), null);
		assertEquals(before.typeHandles(), after.typeHandles(),
				"The configured JDT JUnit finder must expose the same test types after migration."); //$NON-NLS-1$
	}

	private void assertRuntimeTreeUnchanged(Snapshot before, IType launchTarget) throws CoreException {
		Snapshot after= JUnitRuntimeTestTree.capture(launchTarget);
		assertTrue(after.successful(), () -> "Migrated Jupiter run failed: " + after.entries()); //$NON-NLS-1$
		assertEquals(before.entries(), after.entries(),
				"The same JDT JUnit launch must preserve suite nesting, test identity, order and multiplicity."); //$NON-NLS-1$
	}
}
