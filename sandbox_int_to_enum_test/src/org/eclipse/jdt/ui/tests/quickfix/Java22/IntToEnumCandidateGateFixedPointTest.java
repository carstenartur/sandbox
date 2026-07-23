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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.internal.corext.fix.IntToEnumCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.IntToEnumCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Verifies fixed-point state of candidate-gated Int-to-Enum expansion. */
public class IntToEnumCandidateGateFixedPointTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	public void exactClosureIsReturnedOnceAndDoesNotLeakIntoLaterScope() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit candidate= pack.createCompilationUnit("Candidate.java", //$NON-NLS-1$
				"""
				package test;
				public class Candidate {
					static final int STATE_ONE = 1;
					static final int STATE_TWO = 2;
					void consume(int state) {
					}
				}
				""", false, null);
		ICompilationUnit caller= pack.createCompilationUnit("Caller.java", //$NON-NLS-1$
				"""
				package test;
				public class Caller {
					void run(Candidate candidate) {
						candidate.consume(Candidate.STATE_ONE);
					}
				}
				""", false, null);
		ICompilationUnit unrelated= pack.createCompilationUnit("Unrelated.java", //$NON-NLS-1$
				"package test; public class Unrelated {}", false, null); //$NON-NLS-1$
		IntToEnumCleanUpCore cleanup= new IntToEnumCleanUpCore(Map.of(
				MYCleanUpConstants.INT_TO_ENUM_CLEANUP, CleanUpOptions.TRUE,
				IntToEnumCleanUpOptions.PROJECT_WIDE, CleanUpOptions.TRUE));

		Collection<ICompilationUnit> first= cleanup.expandCleanUpScope(candidate.getJavaProject(),
				List.of(candidate), null);
		assertEquals(Set.of(candidate, caller), Set.copyOf(first));

		Collection<ICompilationUnit> fixedPoint= cleanup.expandCleanUpScope(candidate.getJavaProject(), first, null);
		assertTrue(fixedPoint.isEmpty(), "The exact closure must be emitted only once");

		Collection<ICompilationUnit> laterUnrelatedScope= cleanup.expandCleanUpScope(candidate.getJavaProject(),
				List.of(unrelated), null);
		assertTrue(laterUnrelatedScope.isEmpty(), "A completed closure must not leak into a later candidate-free scope");
	}
}
