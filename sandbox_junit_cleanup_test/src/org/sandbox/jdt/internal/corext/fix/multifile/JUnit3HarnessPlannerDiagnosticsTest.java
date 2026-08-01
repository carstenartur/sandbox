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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Planner integration for precise unsupported JUnit 3 harness diagnostics. */
class JUnit3HarnessPlannerDiagnosticsTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	void reportsNamedTestConstruction() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("namedconstruction", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package namedconstruction;
				import junit.framework.TestCase;

				public abstract class BaseTest extends TestCase {
					protected BaseTest(String name) {
						super(name);
					}

					public void testInherited() {
					}
				}
				""", false, null); //$NON-NLS-1$
		ICompilationUnit concrete= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"""
				package namedconstruction;

				public class ConcreteTest extends BaseTest {
					public ConcreteTest(String name) {
						super(name);
					}
				}
				""", false, null); //$NON-NLS-1$

		assertReason(plan(base, concrete), "NAMED_JUNIT3_TEST_CONSTRUCTION"); //$NON-NLS-1$
	}

	@Test
	void reportsCustomSuiteBuilder() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("suitebuilder", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package suitebuilder;
				import junit.framework.Test;
				import junit.framework.TestCase;
				import junit.framework.TestSuite;

				public abstract class BaseTest extends TestCase {
					public static Test suite() {
						return new TestSuite(ConcreteTest.class);
					}

					public void testInherited() {
					}
				}
				""", false, null); //$NON-NLS-1$
		ICompilationUnit concrete= pack.createCompilationUnit("ConcreteTest.java", //$NON-NLS-1$
				"""
				package suitebuilder;

				public class ConcreteTest extends BaseTest {
				}
				""", false, null); //$NON-NLS-1$

		assertReason(plan(base, concrete), "CUSTOM_JUNIT3_SUITE_BUILDER"); //$NON-NLS-1$
	}

	private MultiFileCleanUpPlanResult<JUnitMigrationPlan> plan(
			ICompilationUnit... units) throws CoreException {
		return JUnitMultiFilePlanner.createCoordinated(context.getJavaProject(),
				units, false, true, true, null);
	}

	private static void assertReason(
			MultiFileCleanUpPlanResult<JUnitMigrationPlan> result,
			String reasonCode) {
		assertFalse(result.status().hasFatalError());
		assertEquals(1, result.diagnostics().candidates().size());
		MultiFileCandidateDiagnostic diagnostic=
				result.diagnostics().candidates().get(0);
		assertEquals(MultiFileCandidateOutcome.REJECTED,
				diagnostic.outcome());
		assertEquals(reasonCode, diagnostic.reasonCode());
		assertFalse(diagnostic.message().isBlank());
	}
}
