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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix.JUnitMigrationOptions;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Tests source-compatible upgrades from JUnit Jupiter 5 to JUnit 6. */
public class MigrationJUnit6CompatibilityTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		// The removed JUnit 5 type must still be available while the cleanup
		// resolves its binding. The generated MethodName form is valid under both
		// the JUnit 5 input container and the JUnit 6 target container.
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void replacesRemovedAlphanumericMethodOrderer() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("OrderedTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.jupiter.api.MethodOrderer;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.TestMethodOrder;

				@TestMethodOrder(MethodOrderer.Alphanumeric.class)
				public class OrderedTest {
					@Test
					void first() {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(JUnitMigrationOptions.JUNIT6_COMPATIBILITY);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] {
				"""
				package test;

				import org.junit.jupiter.api.MethodOrderer;
				import org.junit.jupiter.api.Test;
				import org.junit.jupiter.api.TestMethodOrder;

				@TestMethodOrder(MethodOrderer.MethodName.class)
				public class OrderedTest {
					@Test
					void first() {
					}
				}
				"""
		}, null);
	}

	@Test
	public void qualifiesReplacementWhenSimpleNamesConflict() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("ConflictingNames.java", //$NON-NLS-1$
				"""
				package test;

				@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.Alphanumeric.class)
				public class ConflictingNames {
					static class MethodOrderer {
					}

					@interface TestMethodOrder {
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(JUnitMigrationOptions.JUNIT6_COMPATIBILITY);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { unit }, new String[] {
				"""
				package test;

				@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.MethodName.class)
				public class ConflictingNames {
					static class MethodOrderer {
					}

					@interface TestMethodOrder {
					}
				}
				"""
		}, null);
	}

	@Test
	public void leavesUserDefinedAlphanumericTypeUntouched() throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("CustomOrderTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.jupiter.api.MethodOrderer;
				import org.junit.jupiter.api.MethodOrdererContext;
				import org.junit.jupiter.api.TestMethodOrder;

				@TestMethodOrder(CustomOrderTest.Alphanumeric.class)
				public class CustomOrderTest {
					static class Alphanumeric implements MethodOrderer {
						@Override
						public void orderMethods(MethodOrdererContext context) {
						}
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(JUnitMigrationOptions.JUNIT6_COMPATIBILITY);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}
}
