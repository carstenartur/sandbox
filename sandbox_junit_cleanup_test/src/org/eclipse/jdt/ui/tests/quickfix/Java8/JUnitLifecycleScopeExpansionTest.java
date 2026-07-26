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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.internal.corext.fix.multifile.JUnitLifecycleScopeCandidateDetector;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitLifecycleScopeCandidateDetector.SearchSeeds;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Scope and fail-closed tests for inherited JUnit 4 lifecycle migration. */
public class JUnitLifecycleScopeExpansionTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	@Test
	public void selectedSubclassAddsLifecycleBaseClassAndReachesFixedPoint() throws CoreException {
		IPackageFragmentRoot root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Before;

				public class BaseTest {
					@Before
					public void prepareBase() {
					}
				}
				""", false, null);
		ICompilationUnit child= pack.createCompilationUnit("ChildTest.java", //$NON-NLS-1$
				"""
				package test;

				public class ChildTest extends BaseTest {
				}
				""", false, null);
		ICompilationUnit unrelated= pack.createCompilationUnit("UnrelatedTest.java", //$NON-NLS-1$
				"""
				package test;

				public class UnrelatedTest {
				}
				""", false, null);

		JUnitCleanUpCore cleanup= lifecycleCleanup();
		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(child.getJavaProject(), List.of(child), null);
		Set<String> expandedHandles= handles(expanded);

		assertTrue(expandedHandles.contains(base.getHandleIdentifier()));
		assertTrue(expandedHandles.contains(child.getHandleIdentifier()));
		assertFalse(expandedHandles.contains(unrelated.getHandleIdentifier()));
		assertTrue(cleanup.expandCleanUpScope(child.getJavaProject(), List.of(child, base), null).isEmpty(),
				"Lifecycle hierarchy expansion must reach a fixed point"); //$NON-NLS-1$
	}

	@Test
	public void selectedImplementorAddsInterfaceDefaultLifecycleContract() throws CoreException {
		IPackageFragmentRoot root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit contract= pack.createCompilationUnit("LifecycleContract.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Before;

				public interface LifecycleContract {
					@Before
					default void prepareContract() {
					}
				}
				""", false, null);
		ICompilationUnit implementation= pack.createCompilationUnit("ContractTest.java", //$NON-NLS-1$
				"""
				package test;

				public class ContractTest implements LifecycleContract {
				}
				""", false, null);

		Collection<ICompilationUnit> expanded= lifecycleCleanup().expandCleanUpScope(
				implementation.getJavaProject(), List.of(implementation), null);

		assertTrue(handles(expanded).containsAll(Set.of(
				contract.getHandleIdentifier(), implementation.getHandleIdentifier())));
	}

	@Test
	public void unresolvedLifecycleAnnotationRequestsConservativeFallback() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unresolved= pack.createCompilationUnit("UnresolvedTest.java", //$NON-NLS-1$
				"""
				package test;

				public class UnresolvedTest {
					@Before
					public void prepare() {
					}
				}
				""", false, null);

		SearchSeeds seeds= JUnitLifecycleScopeCandidateDetector.findSearchSeeds(
				unresolved.getJavaProject(), List.of(unresolved), null);

		assertTrue(seeds.candidateFound());
		assertFalse(seeds.complete());
	}

	@Test
	public void incompleteSelectionDoesNotPartiallyRewriteLifecycleAnnotation() throws CoreException {
		IPackageFragmentRoot root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Before;

				public class BaseTest {
					@Before
					public void prepareBase() {
					}
				}
				""", false, null);
		pack.createCompilationUnit("ChildTest.java", //$NON-NLS-1$
				"""
				package test;

				public class ChildTest extends BaseTest {
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { base });
	}

	@Test
	public void detectorReturnsSourceHierarchyElements() throws CoreException {
		IPackageFragmentRoot root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= pack.createCompilationUnit("BaseTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.After;
				public class BaseTest {
					@After public void release() {
					}
				}
				""", false, null);
		ICompilationUnit child= pack.createCompilationUnit("ChildTest.java", //$NON-NLS-1$
				"""
				package test;
				public class ChildTest extends BaseTest {
				}
				""", false, null);

		SearchSeeds seeds= JUnitLifecycleScopeCandidateDetector.findSearchSeeds(
				child.getJavaProject(), List.of(child), null);
		Set<String> declarationUnits= seeds.elements().stream()
				.map(element -> element.getAncestor(IJavaElement.COMPILATION_UNIT))
				.filter(ICompilationUnit.class::isInstance)
				.map(ICompilationUnit.class::cast)
				.map(ICompilationUnit::getHandleIdentifier)
				.collect(Collectors.toSet());

		assertTrue(seeds.candidateFound());
		assertTrue(seeds.complete());
		assertEquals(Set.of(base.getHandleIdentifier(), child.getHandleIdentifier()), declarationUnits);
	}

	private static JUnitCleanUpCore lifecycleCleanup() {
		return new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS, CleanUpOptions.TRUE));
	}

	private static Set<String> handles(Collection<ICompilationUnit> units) {
		return units.stream().map(ICompilationUnit::getHandleIdentifier).collect(Collectors.toSet());
	}
}
