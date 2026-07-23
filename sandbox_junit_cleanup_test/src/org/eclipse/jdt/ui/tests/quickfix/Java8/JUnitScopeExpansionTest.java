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
import java.util.Set;
import java.util.stream.Collectors;

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

/** Tests candidate-gated scope expansion for coordinated JUnit migration. */
public class JUnitScopeExpansionTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void localOnlyOptionDoesNotExpandScope() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit selected= createUnit(pack, "SelectedTest.java"); //$NON-NLS-1$
		createUnit(pack, "UnrelatedTest.java"); //$NON-NLS-1$
		JUnitCleanUpCore cleanup= new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT, CleanUpOptions.TRUE));

		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(selected.getJavaProject(),
				List.of(selected), null);

		assertTrue(expanded.isEmpty(), "A local assertion migration must retain the user's target scope");
	}

	@Test
	public void externalResourceOptionWithoutCandidateDoesNotExpandScope() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit selected= createUnit(pack, "SelectedTest.java"); //$NON-NLS-1$
		createUnit(pack, "UnrelatedTest.java"); //$NON-NLS-1$

		Collection<ICompilationUnit> expanded= externalResourceCleanup().expandCleanUpScope(
				selected.getJavaProject(), List.of(selected), null);

		assertTrue(expanded.isEmpty(),
				"The coordinated option must not scan the project without a resource or rule candidate in scope");
	}

	@Test
	public void selectedExternalResourceFindsOnlyRequiredRuleUser() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit selected= pack.createCompilationUnit("SharedResource.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.rules.ExternalResource;

				public class SharedResource extends ExternalResource {
					@Override
					protected void before() {
					}
				}
				""", false, null);
		ICompilationUnit related= pack.createCompilationUnit("SelectedTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Rule;

				public class SelectedTest {
					@Rule
					public SharedResource resource = new SharedResource();
				}
				""", false, null);
		ICompilationUnit unrelated= createUnit(pack, "UnrelatedTest.java"); //$NON-NLS-1$

		Collection<ICompilationUnit> expanded= externalResourceCleanup().expandCleanUpScope(
				selected.getJavaProject(), List.of(selected), null);
		Set<String> expandedHandles= expanded.stream().map(ICompilationUnit::getHandleIdentifier)
				.collect(Collectors.toSet());

		assertEquals(Set.of(selected.getHandleIdentifier(), related.getHandleIdentifier()), expandedHandles,
				"The exact closure must add the Rule user without broadening to unrelated source");
		assertTrue(!expanded.contains(unrelated));
	}

	private static JUnitCleanUpCore externalResourceCleanup() {
		return new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE, CleanUpOptions.TRUE));
	}

	private static ICompilationUnit createUnit(IPackageFragment pack, String name) throws CoreException {
		String source= "package test;%n%npublic class %s {%n}%n" //$NON-NLS-1$
				.formatted(name.substring(0, name.length() - ".java".length())); //$NON-NLS-1$
		return pack.createCompilationUnit(name, source, false, null);
	}
}
