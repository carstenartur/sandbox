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
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.MultiFileCleanUpLifecycleAssertions;

/** Regression coverage for Parameterized migrations split across cleanup runs. */
public class ParameterizedStagedMigrationTest {

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
	public void testOnlyPassCannotDetachMethodsFromParameterizedRunner() throws CoreException {
		ICompilationUnit unit= createParameterizedTest("org.junit.Test"); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void laterParameterizedPassRepairsAlreadyJupiterTestAnnotation() throws CoreException {
		ICompilationUnit unit= createParameterizedTest("org.junit.jupiter.api.Test"); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED);

		MultiFileCleanUpLifecycleAssertions.assertApplyCompileAndUndo(
				new ICompilationUnit[] { unit }, new String[] {
						"""
						package test;

						import java.util.stream.Stream;

						import org.junit.jupiter.params.ParameterizedTest;
						import org.junit.jupiter.params.provider.Arguments;
						import org.junit.jupiter.params.provider.MethodSource;

						public class StagedParameterizedTest {
							@ParameterizedTest
							@MethodSource("data")
							public void verifiesValue(int value) {
								System.out.println(value);
							}

							static Stream<Arguments> data() {
								return Stream.of(Arguments.of(1), Arguments.of(2));
							}
						}
						""" });
	}

	private ICompilationUnit createParameterizedTest(String testAnnotationType) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		return pack.createCompilationUnit("StagedParameterizedTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.Arrays;
				import java.util.Collection;

				import %s;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class StagedParameterizedTest {
					private final int value;

					public StagedParameterizedTest(int value) {
						this.value = value;
					}

					@Parameters
					public static Collection<Object[]> data() {
						return Arrays.asList(new Object[][] { { 1 }, { 2 } });
					}

					@Test
					public void verifiesValue() {
						System.out.println(value);
					}
				}
				""".replace("%s", testAnnotationType), false, null); //$NON-NLS-1$
	}
}
