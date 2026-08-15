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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.JUnitCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Verifies that migrated suite metadata remains usable by later cleanup passes. */
public class JUnitStagedSuiteScopeExpansionTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		AbstractEclipseJava.addToClasspath(context.getJavaProject(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
	}

	@Test
	public void selectClassesStillAddsReferencedLegacyTestsForAnotherOption() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit first= legacyTest(pack, "FirstTest.java"); //$NON-NLS-1$
		ICompilationUnit second= legacyTest(pack, "SecondTest.java"); //$NON-NLS-1$
		ICompilationUnit unrelated= legacyTest(pack, "UnrelatedTest.java"); //$NON-NLS-1$
		ICompilationUnit suite= pack.createCompilationUnit("MigratedSuite.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.platform.suite.api.SelectClasses;
				import org.junit.platform.suite.api.Suite;

				@Suite
				@SelectClasses({ FirstTest.class, SecondTest.class })
				public class MigratedSuite {
				}
				""", false, null);

		JUnitCleanUpCore cleanup= testAnnotationCleanup();
		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(suite.getJavaProject(),
				List.of(suite), null);

		assertEquals(Set.of(first.getHandleIdentifier(), second.getHandleIdentifier()), handles(expanded));
		assertFalse(expanded.contains(unrelated));
		assertTrue(cleanup.expandCleanUpScope(suite.getJavaProject(), List.of(suite, first, second), null).isEmpty(),
				"Post-migration suite membership must reach the same fixed point as SuiteClasses");
	}

	private static ICompilationUnit legacyTest(IPackageFragment pack, String name) throws CoreException {
		String typeName= name.substring(0, name.length() - ".java".length()); //$NON-NLS-1$
		return pack.createCompilationUnit(name,
				"""
				package test;

				import org.junit.Test;

				public class %s {
					@Test
					public void testSomething() {
					}
				}
				""".replace("%s", typeName), false, null); //$NON-NLS-1$
	}

	private static JUnitCleanUpCore testAnnotationCleanup() {
		return new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, CleanUpOptions.TRUE));
	}

	private static Set<String> handles(Collection<ICompilationUnit> units) {
		return units.stream().map(ICompilationUnit::getHandleIdentifier).collect(Collectors.toSet());
	}
}
