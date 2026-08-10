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
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.MethodKind;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnit3HierarchyMigration.MethodMigration;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Planner contract for JDT-finder-backed JUnit 3 hierarchy migration. */
public class JUnit3HierarchyPlannerTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		// The JUnit 5 container retains Vintage compatibility, allowing the same JDT
		// finder configuration to recognize both the source and target forms.
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void plansOrdinaryClosedHierarchyAndCapturesJdtFinderInventory() throws CoreException {
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
						assertTrue(true);
					}
				}
				""", false, null);
		ICompilationUnit concrete= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"""
				package test;

				public class ConcreteTest extends BaseTest {
					public void testLocal() {
					}
				}
				""", false, null);

		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= JUnitMultiFilePlanner.createCoordinated(
				context.getJavaProject(), new ICompilationUnit[] { base, concrete }, false, true, true, null);

		assertFalse(result.status().hasFatalError());
		assertEquals(1, result.plan().junit3Hierarchies().size());
		JUnit3HierarchyMigration migration= result.plan().junit3Hierarchies().get(0);
		assertEquals("test.BaseTest", migration.rootTypeName()); //$NON-NLS-1$
		assertEquals(2, migration.types().size());
		assertEquals(1, migration.baselineTestTypeHandles().size());
		assertTrue(result.plan().testTypeInventory().typeHandles()
				.containsAll(migration.baselineTestTypeHandles()));
		MethodMigration setUp= migration.types().stream()
				.flatMap(type -> type.methods().stream())
				.filter(method -> method.kind() == MethodKind.BEFORE_EACH)
				.findFirst().orElseThrow();
		assertTrue(setUp.removeOverride(),
				"The immutable plan must retain planning-time java.lang.Override presence"); //$NON-NLS-1$
		assertEquals(MultiFileCandidateOutcome.TRANSFORMED,
				result.diagnostics().candidates().get(0).outcome());
	}


	@Test
	public void rejectsHierarchyWithCollidingJUnit3MethodNameHashes() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("collision", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package collision;
				import junit.framework.TestCase;

				public abstract class BaseTest extends TestCase {
					public void testAa() {
					}

					public void testBB() {
					}
				}
				""", false, null);
		ICompilationUnit concrete= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"""
				package collision;

				public class ConcreteTest extends BaseTest {
				}
				""", false, null);

		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= JUnitMultiFilePlanner.createCoordinated(
				context.getJavaProject(), new ICompilationUnit[] { base, concrete }, false, true, true, null);

		assertTrue(result.plan().junit3Hierarchies().isEmpty());
		assertEquals(MultiFileCandidateOutcome.REJECTED,
				result.diagnostics().candidates().get(0).outcome());
		assertEquals("COLLIDING_JUNIT3_TEST_NAME_HASH", //$NON-NLS-1$
				result.diagnostics().candidates().get(0).reasonCode());
	}

	@Test
	public void leavesStandaloneLeafToLocalFailClosedCleanup() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("standalone", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("SimpleTest.java", //$NON-NLS-1$
				"""
				package standalone;
				import junit.framework.TestCase;

				public class SimpleTest extends TestCase {
					public void testOne() {
					}
				}
				""", false, null);

		JUnitScopeCandidateDetector.SearchSeeds seeds= JUnit3HierarchyScopeDetector.findSearchSeeds(
				context.getJavaProject(), List.of(unit), null);
		assertFalse(seeds.candidateFound());
		assertTrue(seeds.complete());
		assertTrue(seeds.elements().isEmpty());
		assertTrue(seeds.directCompilationUnits().isEmpty());

		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= JUnitMultiFilePlanner.createCoordinated(
				context.getJavaProject(), new ICompilationUnit[] { unit }, false, true, true, null);

		assertFalse(result.status().hasFatalError());
		assertTrue(result.plan().junit3Hierarchies().isEmpty());
		assertTrue(result.diagnostics().candidates().isEmpty());
	}

	@Test
	public void rejectsHierarchyReferencedByCustomSuiteOwner() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public abstract class BaseTest extends TestCase {
					public void testInherited() {
					}
				}
				""", false, null);
		ICompilationUnit concrete= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"""
				package test;

				public class ConcreteTest extends BaseTest {
				}
				""", false, null);
		ICompilationUnit suite= pack.createCompilationUnit("AllTests.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.Test;
				import junit.framework.TestSuite;

				public class AllTests {
					public static Test suite() {
						return new TestSuite(ConcreteTest.class);
					}
				}
				""", false, null);

		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= JUnitMultiFilePlanner.createCoordinated(
				context.getJavaProject(), new ICompilationUnit[] { base, concrete, suite }, false, true, true, null);

		assertTrue(result.plan().junit3Hierarchies().isEmpty());
		assertEquals(MultiFileCandidateOutcome.REJECTED,
				result.diagnostics().candidates().get(0).outcome());
		assertEquals("CUSTOM_JUNIT3_HARNESS", //$NON-NLS-1$
				result.diagnostics().candidates().get(0).reasonCode());
	}
}
