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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMigrationPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Diagnostics QA for coordinated JUnit ExternalResource planning. */
public class MultiFileExternalResourceDiagnosticsTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void reportsMixedRuleLifecycleWithTypeAndAffectedUnits() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit resource= createResource(pack);
		ICompilationUnit instanceUser= pack.createCompilationUnit("InstanceRuleTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;

				public class InstanceRuleTest {
					@Rule
					public SharedResource resource = new SharedResource();
				}
				""", false, null);
		ICompilationUnit classUser= pack.createCompilationUnit("ClassRuleTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.ClassRule;

				public class ClassRuleTest {
					@ClassRule
					public static SharedResource resource = new SharedResource();
				}
				""", false, null);

		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= JUnitMultiFilePlanner.create(
				context.getJavaProject(), new ICompilationUnit[] { resource, instanceUser, classUser }, true, true, null);

		assertTrue(result.status().hasFatalError());
		assertEquals(1, result.diagnostics().candidates().size());
		MultiFileCandidateDiagnostic diagnostic= result.diagnostics().candidates().get(0);
		assertEquals(MultiFileCandidateOutcome.REJECTED, diagnostic.outcome());
		assertEquals("MIXED_RULE_LIFECYCLE", diagnostic.reasonCode()); //$NON-NLS-1$
		assertTrue(diagnostic.message().contains("test.SharedResource")); //$NON-NLS-1$
		assertTrue(diagnostic.message().contains("@Rule")); //$NON-NLS-1$
		assertTrue(diagnostic.message().contains("@ClassRule")); //$NON-NLS-1$
		assertEquals(3, diagnostic.relatedCompilationUnitHandles().size());
	}

	@Test
	public void reportsSuccessfulResourceMigrationDeterministically() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit resource= createResource(pack);
		ICompilationUnit user= pack.createCompilationUnit("RuleTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;

				public class RuleTest {
					@Rule
					public SharedResource resource = new SharedResource();
				}
				""", false, null);

		ICompilationUnit[] units= { resource, user };
		MultiFileCleanUpPlanResult<JUnitMigrationPlan> first= JUnitMultiFilePlanner.create(
				context.getJavaProject(), units, true, true, null);
		MultiFileCleanUpPlanResult<JUnitMigrationPlan> second= JUnitMultiFilePlanner.create(
				context.getJavaProject(), units, true, true, null);

		assertFalse(first.status().hasFatalError());
		assertEquals(1, first.plan().migrations().size());
		assertEquals(MultiFileCandidateOutcome.TRANSFORMED,
				first.diagnostics().candidates().get(0).outcome());
		assertEquals(first.diagnostics().toJson(), second.diagnostics().toJson());
	}

	private static ICompilationUnit createResource(IPackageFragment pack) throws CoreException {
		return pack.createCompilationUnit("SharedResource.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.rules.ExternalResource;

				public class SharedResource extends ExternalResource {
					@Override
					protected void before() {
					}

					@Override
					protected void after() {
					}
				}
				""", false, null);
	}
}
