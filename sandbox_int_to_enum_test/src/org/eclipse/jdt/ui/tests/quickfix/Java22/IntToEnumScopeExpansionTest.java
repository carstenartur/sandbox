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
import java.util.stream.Collectors;

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

/** Tests candidate-gated project-wide Int-to-Enum analysis. */
public class IntToEnumScopeExpansionTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	public void localOptionDoesNotExpandScope() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit selected= createUnit(pack, "Selected.java"); //$NON-NLS-1$
		createUnit(pack, "Unrelated.java"); //$NON-NLS-1$
		IntToEnumCleanUpCore cleanup= new IntToEnumCleanUpCore(Map.of(
				MYCleanUpConstants.INT_TO_ENUM_CLEANUP, CleanUpOptions.TRUE));

		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null);

		assertTrue(expanded.isEmpty(), "A local Int-to-Enum cleanup must retain the user's target scope");
	}

	@Test
	public void localCleanupStillRunsWithoutProjectPlanner() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit selected= pack.createCompilationUnit("Selected.java", //$NON-NLS-1$
				"""
				package test;

				public class Selected {
					private static final int STATUS_PENDING = 0;
					private static final int STATUS_APPROVED = 1;

					void run() {
						process(STATUS_PENDING);
					}

					private void process(int status) {
						if (status == STATUS_PENDING) {
							System.out.println("pending");
						} else if (status == STATUS_APPROVED) {
							System.out.println("approved");
						}
					}
				}
				""", false, null);
		createUnit(pack, "Unrelated.java"); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.INT_TO_ENUM_CLEANUP);

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(new ICompilationUnit[] { selected },
				new String[] { """
						package test;

						public class Selected {
							private enum Status {
								PENDING, APPROVED
							}

							void run() {
								process(Status.PENDING);
							}

							private void process(Status status) {
								if (status == Status.PENDING) {
									System.out.println("pending");
								} else if (status == Status.APPROVED) {
									System.out.println("approved");
								}
							}
						}
						""" }, null);
	}

	@Test
	public void projectWideOptionWithoutCandidateDoesNotExpandScope() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit selected= createUnit(pack, "Selected.java"); //$NON-NLS-1$
		createUnit(pack, "Unrelated.java"); //$NON-NLS-1$
		IntToEnumCleanUpCore cleanup= projectWideCleanup();

		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null);

		assertTrue(expanded.isEmpty(),
				"An explicit project-wide option must not scan the project without a selected candidate owner");
	}

	@Test
	public void selectedCandidateUsesConservativeProjectFallback() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit selected= pack.createCompilationUnit("Selected.java", //$NON-NLS-1$
				"""
				package test;

				public class Selected {
					static final int STATUS_PENDING = 0;
					static final int STATUS_APPROVED = 1;

					void process(int status) {
						if (status == STATUS_PENDING) {
							System.out.println("pending");
						} else if (status == STATUS_APPROVED) {
							System.out.println("approved");
						}
					}
				}
				""", false, null);
		ICompilationUnit related= createUnit(pack, "Related.java"); //$NON-NLS-1$
		ICompilationUnit unrelated= createUnit(pack, "Unrelated.java"); //$NON-NLS-1$

		Collection<ICompilationUnit> expanded= projectWideCleanup().expandCleanUpScope(
				selected.getJavaProject(), List.of(selected), null);
		Set<String> expandedHandles= expanded.stream().map(ICompilationUnit::getHandleIdentifier)
				.collect(Collectors.toSet());

		assertEquals(Set.of(selected.getHandleIdentifier(), related.getHandleIdentifier(),
				unrelated.getHandleIdentifier()), expandedHandles,
				"A selected coordinated candidate must retain the conservative complete-project fallback");
	}

	private static IntToEnumCleanUpCore projectWideCleanup() {
		return new IntToEnumCleanUpCore(Map.of(
				MYCleanUpConstants.INT_TO_ENUM_CLEANUP, CleanUpOptions.TRUE,
				IntToEnumCleanUpOptions.PROJECT_WIDE, CleanUpOptions.TRUE));
	}

	private static ICompilationUnit createUnit(IPackageFragment pack, String name) throws CoreException {
		String source= "package test;%n%npublic class %s {%n}%n" //$NON-NLS-1$
				.formatted(name.substring(0, name.length() - ".java".length())); //$NON-NLS-1$
		return pack.createCompilationUnit(name, source, false, null);
	}
}
