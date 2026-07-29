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

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Fail-closed contracts for JUnit 4 parameterized-test migration. */
public class ParameterizedMigrationContractTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
	}

	@Test
	public void leavesRunnerUntouchedWithoutLocalParametersProvider() throws CoreException {
		assertNoChange("MissingProviderTest.java", //$NON-NLS-1$
				"""
				package test;

				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;

				@RunWith(Parameterized.class)
				public class MissingProviderTest {
					private final int value;

					public MissingProviderTest(int value) {
						this.value = value;
					}

					@Test
					public void verifiesValue() {
						System.out.println(value);
					}
				}
				""");
	}

	@Test
	public void leavesRunnerUntouchedWithMultipleConstructors() throws CoreException {
		assertNoChange("MultipleConstructorsTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.Arrays;
				import java.util.Collection;

				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class MultipleConstructorsTest {
					private final int value;

					public MultipleConstructorsTest(int value) {
						this.value = value;
					}

					public MultipleConstructorsTest(String value) {
						this(Integer.parseInt(value));
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
				""");
	}

	@Test
	public void leavesFieldInjectionUntouched() throws CoreException {
		assertNoChange("FieldInjectionTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.Arrays;
				import java.util.Collection;

				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameter;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class FieldInjectionTest {
					@Parameter
					public int value;

					@Parameters
					public static Collection<Object[]> data() {
						return Arrays.asList(new Object[][] { { 1 }, { 2 } });
					}

					@Test
					public void verifiesValue() {
						System.out.println(value);
					}
				}
				""");
	}

	private void assertNoChange(String fileName, String source) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit(fileName, source, false, null);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}
}
