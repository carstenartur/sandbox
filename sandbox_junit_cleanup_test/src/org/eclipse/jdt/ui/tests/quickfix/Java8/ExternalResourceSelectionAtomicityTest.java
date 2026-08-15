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

/** Ensures that both sides of a named ExternalResource migration remain atomic. */
public class ExternalResourceSelectionAtomicityTest {

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
	public void declarationOptionStillFindsEveryRuleUser() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit resource= resource(pack);
		ICompilationUnit ruleUser= ruleUser(pack);
		ICompilationUnit unrelated= pack.createCompilationUnit("Unrelated.java", //$NON-NLS-1$
				"""
				package test;

				public class Unrelated {
				}
				""", false, null);

		Collection<ICompilationUnit> expanded= declarationCleanup().expandCleanUpScope(
				resource.getJavaProject(), List.of(resource), null);

		assertEquals(Set.of(resource.getHandleIdentifier(), ruleUser.getHandleIdentifier()), handles(expanded));
		assertFalse(expanded.contains(unrelated));
	}

	@Test
	public void declarationOptionMigratesDeclarationAndRuleFieldTogether() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit resource= resource(pack);
		ICompilationUnit ruleUser= ruleUser(pack);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE);

		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { resource, ruleUser }, new String[] {
						"""
						package test;
						import org.junit.jupiter.api.extension.AfterEachCallback;
						import org.junit.jupiter.api.extension.BeforeEachCallback;
						import org.junit.jupiter.api.extension.ExtensionContext;

						public class SharedResource implements BeforeEachCallback, AfterEachCallback {
							@Override
							public void beforeEach(ExtensionContext context) throws Exception {
								System.setProperty("resource", "started");
							}

							@Override
							public void afterEach(ExtensionContext context) {
								System.clearProperty("resource");
							}
						}
						""",
						"""
						package test;
						import org.junit.jupiter.api.extension.RegisterExtension;

						public class RuleUserTest {
							@RegisterExtension
							public SharedResource resource = new SharedResource();
						}
						""" }, null);
	}

	private static ICompilationUnit resource(IPackageFragment pack) throws CoreException {
		return pack.createCompilationUnit("SharedResource.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.rules.ExternalResource;

				public class SharedResource extends ExternalResource {
					@Override
					protected void before() throws Throwable {
						System.setProperty("resource", "started");
					}

					@Override
					protected void after() {
						System.clearProperty("resource");
					}
				}
				""", false, null);
	}

	private static ICompilationUnit ruleUser(IPackageFragment pack) throws CoreException {
		return pack.createCompilationUnit("RuleUserTest.java", //$NON-NLS-1$
				"""
				package test;
				import org.junit.Rule;

				public class RuleUserTest {
					@Rule
					public SharedResource resource = new SharedResource();
				}
				""", false, null);
	}

	private static JUnitCleanUpCore declarationCleanup() {
		return new JUnitCleanUpCore(Map.of(
				MYCleanUpConstants.JUNIT_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE, CleanUpOptions.TRUE));
	}

	private static Set<String> handles(Collection<ICompilationUnit> units) {
		return units.stream().map(ICompilationUnit::getHandleIdentifier).collect(Collectors.toSet());
	}
}
