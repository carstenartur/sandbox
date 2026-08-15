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
import org.sandbox.jdt.ui.tests.quickfix.rules.MultiFileCleanUpLifecycleAssertions;

/** Regression coverage for lifecycle migrations split across multiple cleanup runs. */
public class JUnitStagedLifecycleMigrationTest {

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
	public void selectedLegacyBaseAddsEverySourceOverride() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= legacyBase(pack);
		ICompilationUnit child= overridingChild(pack);
		ICompilationUnit unrelated= pack.createCompilationUnit("Unrelated.java", //$NON-NLS-1$
				"""
				package test;

				public class Unrelated {
				}
				""", false, null);

		JUnitCleanUpCore cleanup= lifecycleCleanup();
		Collection<ICompilationUnit> expanded= cleanup.expandCleanUpScope(base.getJavaProject(),
				List.of(base), null);

		assertEquals(Set.of(base.getHandleIdentifier(), child.getHandleIdentifier()), handles(expanded));
		assertFalse(expanded.contains(unrelated));
		assertTrue(cleanup.expandCleanUpScope(base.getJavaProject(), List.of(base, child), null).isEmpty(),
				"Lifecycle hierarchy expansion must reach a fixed point");
	}

	@Test
	public void selectedMigratedBaseStillAddsLegacyOverride() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= migratedBase(pack);
		ICompilationUnit child= overridingChild(pack);

		Collection<ICompilationUnit> expanded= lifecycleCleanup().expandCleanUpScope(
				base.getJavaProject(), List.of(base), null);

		assertEquals(Set.of(base.getHandleIdentifier(), child.getHandleIdentifier()), handles(expanded));
	}

	@Test
	public void laterPassMigratesOverrideOfAlreadyMigratedBase() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit base= migratedBase(pack);
		ICompilationUnit child= overridingChild(pack);

		enableLifecycleMigration();

		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(
				new ICompilationUnit[] { base, child }, new String[] {
						"""
						package test;

						import org.junit.jupiter.api.AfterEach;
						import org.junit.jupiter.api.BeforeEach;

						public class LifecycleBase {
							@BeforeEach
							public void setUp() {
							}

							@AfterEach
							public void tearDown() {
							}
						}
						""",
						"""
						package test;

						import org.junit.jupiter.api.AfterEach;
						import org.junit.jupiter.api.BeforeEach;
						import org.junit.jupiter.api.Test;

						public class LifecycleChildTest extends LifecycleBase {
							@Override
							@BeforeEach
							public void setUp() {
								super.setUp();
							}

							@Override
							@AfterEach
							public void tearDown() {
								super.tearDown();
							}

							@Test
							public void testLifecycle() {
							}
						}
						""" });
	}

	private static ICompilationUnit legacyBase(IPackageFragment pack) throws CoreException {
		return pack.createCompilationUnit("LifecycleBase.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.After;
				import org.junit.Before;

				public class LifecycleBase {
					@Before
					public void setUp() {
					}

					@After
					public void tearDown() {
					}
				}
				""", false, null);
	}

	private static ICompilationUnit migratedBase(IPackageFragment pack) throws CoreException {
		return pack.createCompilationUnit("LifecycleBase.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.jupiter.api.AfterEach;
				import org.junit.jupiter.api.BeforeEach;

				public class LifecycleBase {
					@BeforeEach
					public void setUp() {
					}

					@AfterEach
					public void tearDown() {
					}
				}
				""", false, null);
	}

	private static ICompilationUnit overridingChild(IPackageFragment pack) throws CoreException {
		return pack.createCompilationUnit("LifecycleChildTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Test;

				public class LifecycleChildTest extends LifecycleBase {
					@Override
					public void setUp() {
						super.setUp();
					}

					@Override
					public void tearDown() {
						super.tearDown();
					}

					@Test
					public void testLifecycle() {
					}
				}
				""", false, null);
	}

	private static JUnitCleanUpCore lifecycleCleanup() {
		return new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_TEST, CleanUpOptions.TRUE));
	}

	private void enableLifecycleMigration() throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
	}

	private static Set<String> handles(Collection<ICompilationUnit> units) {
		return units.stream().map(ICompilationUnit::getHandleIdentifier).collect(Collectors.toSet());
	}
}
