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
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMigrationPlan;
import org.sandbox.jdt.internal.corext.fix.multifile.IntEnumMultiFilePlanner;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Diagnostics QA for coordinated integer-state migration planning. */
public class MultiFileIntToEnumDiagnosticsTest {

	private static final String METHOD_PREFIX_MARKER= "__METHOD_PREFIX__"; //$NON-NLS-1$
	private static final String ARGUMENT_MARKER= "__ARGUMENT__"; //$NON-NLS-1$

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	public void reportsSuccessfulClosedMigration() throws CoreException {
		ICompilationUnit[] units= createCandidate("void", "OrderProcessor.STATUS_PENDING"); //$NON-NLS-1$ //$NON-NLS-2$

		MultiFileCleanUpPlanResult<IntEnumMigrationPlan> result= plan(units);

		assertFalse(result.status().hasFatalError());
		assertEquals(1, result.plan().candidates().size());
		assertEquals(1, result.diagnostics().candidates().size());
		assertEquals(MultiFileCandidateOutcome.TRANSFORMED,
				result.diagnostics().candidates().get(0).outcome());
	}

	@Test
	public void reportsUnknownOrArbitraryIntegerArgument() throws CoreException {
		ICompilationUnit[] units= createCandidate("void", "7"); //$NON-NLS-1$ //$NON-NLS-2$

		MultiFileCleanUpPlanResult<IntEnumMigrationPlan> result= plan(units);

		assertEquals(0, result.plan().candidates().size());
		assertRejected(result, "ARBITRARY_INTEGER_ARGUMENT"); //$NON-NLS-1$
	}

	@Test
	public void reportsUnsupportedPublicApiVisibility() throws CoreException {
		ICompilationUnit[] units= createCandidate("public void", "OrderProcessor.STATUS_PENDING"); //$NON-NLS-1$ //$NON-NLS-2$

		MultiFileCleanUpPlanResult<IntEnumMigrationPlan> result= plan(units);

		assertEquals(0, result.plan().candidates().size());
		assertRejected(result, "UNSUPPORTED_API_VISIBILITY"); //$NON-NLS-1$
	}

	@Test
	public void reportsConstantUsedAsPersistedNumericIdentity() throws CoreException {
		ICompilationUnit[] base= createCandidate("void", "OrderProcessor.STATUS_PENDING"); //$NON-NLS-1$ //$NON-NLS-2$
		IPackageFragment pack= (IPackageFragment) base[0].getParent();
		ICompilationUnit persistence= pack.createCompilationUnit("StoredOrder.java", //$NON-NLS-1$
				"""
				package test;

				public class StoredOrder {
					// Models a database, serialization, preference or wire value.
					int persistedStatus = OrderProcessor.STATUS_PENDING;
				}
				""", false, null);
		ICompilationUnit[] units= Arrays.copyOf(base, 3);
		units[2]= persistence;

		MultiFileCleanUpPlanResult<IntEnumMigrationPlan> result= plan(units);

		assertEquals(0, result.plan().candidates().size());
		assertRejected(result, "UNSUPPORTED_CONSTANT_REFERENCE"); //$NON-NLS-1$
	}

	@Test
	public void reportsStateUsedAsBitMaskOrExternalNumericValue() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit processor= pack.createCompilationUnit("OrderProcessor.java", //$NON-NLS-1$
				"""
				package test;

				public class OrderProcessor {
					static final int STATUS_PENDING = 0;
					static final int STATUS_APPROVED = 1;

					void process(int status) {
						if (status == STATUS_PENDING) {
							System.out.println("pending");
						} else if (status == STATUS_APPROVED) {
							System.out.println("approved");
						}
						int externalValue = status | 0x10;
						System.out.println(externalValue);
					}
				}
				""", false, null);
		ICompilationUnit client= pack.createCompilationUnit("OrderClient.java", //$NON-NLS-1$
				"""
				package test;

				public class OrderClient {
					void run(OrderProcessor processor) {
						processor.process(OrderProcessor.STATUS_PENDING);
					}
				}
				""", false, null);

		MultiFileCleanUpPlanResult<IntEnumMigrationPlan> result= plan(processor, client);

		assertEquals(0, result.plan().candidates().size());
		assertRejected(result, "UNSUPPORTED_STATE_REFERENCE"); //$NON-NLS-1$
	}

	private MultiFileCleanUpPlanResult<IntEnumMigrationPlan> plan(ICompilationUnit... units) throws CoreException {
		return IntEnumMultiFilePlanner.create(context.getJavaProject(), units, true, null);
	}

	private ICompilationUnit[] createCandidate(String methodPrefix, String argument) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit processor= pack.createCompilationUnit("OrderProcessor.java", //$NON-NLS-1$
				"""
				package test;

				public class OrderProcessor {
					static final int STATUS_PENDING = 0;
					static final int STATUS_APPROVED = 1;

					__METHOD_PREFIX__ process(int status) {
						if (status == STATUS_PENDING) {
							System.out.println("pending");
						} else if (status == STATUS_APPROVED) {
							System.out.println("approved");
						}
					}
				}
				""".replace(METHOD_PREFIX_MARKER, methodPrefix), false, null);
		ICompilationUnit client= pack.createCompilationUnit("OrderClient.java", //$NON-NLS-1$
				"""
				package test;

				public class OrderClient {
					void run(OrderProcessor processor) {
						processor.process(__ARGUMENT__);
					}
				}
				""".replace(ARGUMENT_MARKER, argument), false, null);
		return new ICompilationUnit[] { processor, client };
	}

	private static void assertRejected(MultiFileCleanUpPlanResult<IntEnumMigrationPlan> result,
			String reasonCode) {
		assertEquals(1, result.diagnostics().candidates().size());
		MultiFileCandidateDiagnostic diagnostic= result.diagnostics().candidates().get(0);
		assertEquals(MultiFileCandidateOutcome.REJECTED, diagnostic.outcome());
		assertEquals(reasonCode, diagnostic.reasonCode());
		assertEquals(2, diagnostic.relatedCompilationUnitHandles().size());
	}
}
