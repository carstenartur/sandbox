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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Verifies fixed-point state of candidate-gated JUnit expansion. */
public class JUnitCandidateGateFixedPointTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void fallbackIsReturnedOnceAndDoesNotLeakIntoLaterScope() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit candidate= pack.createCompilationUnit("SharedResource.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.rules.ExternalResource;
				public class SharedResource extends ExternalResource {
				}
				""", false, null);
		ICompilationUnit unrelated= pack.createCompilationUnit("UnrelatedTest.java", //$NON-NLS-1$
				"package test; public class UnrelatedTest {}", false, null); //$NON-NLS-1$
		JUnitCleanUpCore cleanup= new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, CleanUpOptions.TRUE));

		Collection<ICompilationUnit> first= cleanup.expandCleanUpScope(candidate.getJavaProject(),
				List.of(candidate), null);
		assertEquals(2, first.size());
		assertTrue(first.contains(unrelated));

		Collection<ICompilationUnit> fixedPoint= cleanup.expandCleanUpScope(candidate.getJavaProject(), first, null);
		assertTrue(fixedPoint.isEmpty(), "The complete-project fallback must be emitted only once");

		Collection<ICompilationUnit> laterUnrelatedScope= cleanup.expandCleanUpScope(candidate.getJavaProject(),
				List.of(unrelated), null);
		assertTrue(laterUnrelatedScope.isEmpty(), "A completed fallback must not leak into a later candidate-free scope");
	}
}
