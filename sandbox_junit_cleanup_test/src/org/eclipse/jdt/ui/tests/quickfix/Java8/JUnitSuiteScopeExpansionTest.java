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

/** Scope-expansion tests for JUnit 4 suite migrations. */
public class JUnitSuiteScopeExpansionTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void selectedSuiteAddsOnlyReferencedSourceTests() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit first= testClass(pack, "FirstTest.java"); //$NON-NLS-1$
		ICompilationUnit second= testClass(pack, "SecondTest.java"); //$NON-NLS-1$
		ICompilationUnit unrelated= testClass(pack, "UnrelatedTest.java"); //$NON-NLS-1$
		ICompilationUnit suite= pack.createCompilationUnit("AllTests.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.runner.RunWith;
				import org.junit.runners.Suite;

				@RunWith(Suite.class)
				@Suite.SuiteClasses({ FirstTest.class, SecondTest.class })
				public class AllTests {
				}
				""", false, null);

		JUnitCleanUpCore cleanup= suiteCleanup();
		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(suite.getJavaProject(),
				List.of(suite), null);

		assertEquals(Set.of(first.getHandleIdentifier(), second.getHandleIdentifier()), handles(expanded));
		assertTrue(!expanded.contains(unrelated));
		assertTrue(cleanup.expandCleanUpScope(suite.getJavaProject(), List.of(suite, first, second), null).isEmpty(),
				"Scope expansion must reach a fixed point once all suite members are present");
	}

	@Test
	public void singleSuiteClassLiteralAddsItsSourceTest() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit referenced= testClass(pack, "OnlyTest.java"); //$NON-NLS-1$
		ICompilationUnit suite= pack.createCompilationUnit("OnlySuite.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.runner.RunWith;
				import org.junit.runners.Suite;

				@RunWith(Suite.class)
				@Suite.SuiteClasses(OnlyTest.class)
				public class OnlySuite {
				}
				""", false, null);

		Collection<ICompilationUnit> expanded= suiteCleanup().expandCleanUpScope(suite.getJavaProject(),
				List.of(suite), null);

		assertEquals(Set.of(referenced.getHandleIdentifier()), handles(expanded));
	}

	@Test
	public void runWithWithoutSuiteClassesDoesNotBroadenScope() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		testClass(pack, "UnrelatedTest.java"); //$NON-NLS-1$
		ICompilationUnit runner= pack.createCompilationUnit("CustomRunnerTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.runner.RunWith;
				import org.junit.runners.JUnit4;

				@RunWith(JUnit4.class)
				public class CustomRunnerTest {
				}
				""", false, null);

		Collection<ICompilationUnit> expanded= suiteCleanup().expandCleanUpScope(runner.getJavaProject(),
				List.of(runner), null);

		assertTrue(expanded.isEmpty(), "An ordinary runner annotation has no forward source closure");
	}

	private static JUnitCleanUpCore suiteCleanup() {
		return new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH, CleanUpOptions.TRUE));
	}

	private static ICompilationUnit testClass(IPackageFragment pack, String name) throws CoreException {
		String typeName= name.substring(0, name.length() - ".java".length()); //$NON-NLS-1$
		return pack.createCompilationUnit(name,
				"package test;%n%npublic class %s {%n}%n".formatted(typeName), false, null); //$NON-NLS-1$
	}

	private static Set<String> handles(Collection<ICompilationUnit> units) {
		return units.stream().map(ICompilationUnit::getHandleIdentifier).collect(Collectors.toSet());
	}
}
