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
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpDiagnostics;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.cleanup.multifile.MultiFilePlanningMetrics;
import org.sandbox.jdt.cleanup.multifile.MultiFileScopeDiagnostic;
import org.sandbox.jdt.cleanup.multifile.SelectedCompilationUnitPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumCandidate;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMigrationPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumPackageVisibilityPolicy;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Package-resolution regression tests for nested enum owners. */
public class IntEnumPackageVisibilityPolicyTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	public void acceptsSamePackageCallerForNestedOwnerType() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit owner= pack.createCompilationUnit("Outer.java", //$NON-NLS-1$
				"package test; public class Outer { static class OrderProcessor {} }", false, null); //$NON-NLS-1$
		ICompilationUnit caller= pack.createCompilationUnit("OrderClient.java", //$NON-NLS-1$
				"package test; public class OrderClient {}", false, null); //$NON-NLS-1$
		String ownerHandle= owner.getHandleIdentifier();
		String callerHandle= caller.getHandleIdentifier();
		IntEnumCandidate candidate= new IntEnumCandidate(ownerHandle, "owner-key", //$NON-NLS-1$
				"test.Outer.OrderProcessor", "method-key", 0, "STATUS_", "Status", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				List.of(), Map.of(), Map.of(ownerHandle, Integer.valueOf(0), callerHandle, Integer.valueOf(1)));
		SelectedCompilationUnitPlan scope= SelectedCompilationUnitPlan.of(context.getJavaProject(),
				new ICompilationUnit[] { owner, caller });
		MultiFileCleanUpDiagnostics diagnostics= new MultiFileCleanUpDiagnostics("int-to-enum", //$NON-NLS-1$
				new MultiFileScopeDiagnostic(List.of(ownerHandle, callerHandle), List.of(),
						"CLOSED_SOURCE_SCOPE", "Complete", true), //$NON-NLS-1$ //$NON-NLS-2$
				List.of(MultiFileCandidateDiagnostic.transformed("candidate", ownerHandle, //$NON-NLS-1$
						"Migrates nested enum Status.", List.of(ownerHandle, callerHandle)))); //$NON-NLS-1$
		MultiFileCleanUpPlanResult<IntEnumMigrationPlan> result= MultiFileCleanUpPlanResult.success(
				new IntEnumMigrationPlan(scope, List.of(candidate)), new RefactoringStatus(),
				MultiFilePlanningMetrics.empty(), diagnostics);

		MultiFileCleanUpPlanResult<IntEnumMigrationPlan> filtered=
				IntEnumPackageVisibilityPolicy.enforce(result, new ICompilationUnit[] { owner, caller });

		assertEquals(List.of(candidate), filtered.plan().candidates());
		assertEquals("TRANSFORMED", filtered.diagnostics().candidates().get(0).outcome().name()); //$NON-NLS-1$
	}
}
