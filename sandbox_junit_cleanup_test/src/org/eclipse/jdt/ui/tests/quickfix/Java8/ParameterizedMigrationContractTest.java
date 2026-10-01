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

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.sandbox.jdt.ui.tests.quickfix.rules.JUnitMigrationFixtureClasspath;

/** Fail-closed contracts for JUnit 4 parameterized-test migration. */
public class ParameterizedMigrationContractTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= JUnitMigrationFixtureClasspath.createJUnit4And5Root(context);
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
	public void migratesFieldInjectionWhenParameterizedClassIsAvailable() throws CoreException {
		assertMigration("FieldInjectionTest.java", //$NON-NLS-1$
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
				""",
				"""
				package test;

				import java.util.Arrays;
				import java.util.Collection;

				import org.junit.jupiter.api.Order;
				import org.junit.jupiter.api.Test;

				@org.junit.jupiter.api.parallel.Execution(value = org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
				@org.junit.jupiter.api.TestInstance(value = org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD)
				@org.junit.jupiter.api.TestMethodOrder(value = org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
				@org.junit.jupiter.params.provider.MethodSource(value = "jupiterArguments")
				@org.junit.jupiter.params.ParameterizedClass(autoCloseArguments = false, name = "{argumentSetName}")
				public class FieldInjectionTest {
					@org.junit.jupiter.params.Parameter(value = 0)
					public int value;

					public static Collection<Object[]> data() {
						return Arrays.asList(new Object[][] { { 1 }, { 2 } });
					}

					@org.junit.jupiter.api.DisplayName(value = "verifiesValue")
					@Order(0)
					@Test
					public void verifiesValue() {
						System.out.println(value);
					}

					static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> jupiterArguments() {
						java.lang.Object source = data();
						java.lang.Iterable<?> rows = source instanceof java.lang.Object[][]
								? java.util.Arrays.asList((java.lang.Object[][]) source)
								: (java.lang.Iterable<?>) source;
						java.util.List<org.junit.jupiter.params.provider.Arguments> result = new java.util.ArrayList<>();
						int index = 0;
						for (java.lang.Object row : rows) {
							java.lang.Object[] arguments = (java.lang.Object[]) row;
							java.lang.String name = "[" + java.text.MessageFormat
									.format("{index}".replace("{index}", java.lang.Integer.toString(index++)), arguments) + "]";
							result.add(org.junit.jupiter.params.provider.Arguments.argumentSet(name, arguments));
						}
						return result.stream();
					}
				}
				""");
	}

	@Test
	public void migratesExplicitRunWithValueSyntax() throws CoreException {
		assertMigration("ExplicitRunnerTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.Arrays;
				import java.util.Collection;

				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(value = Parameterized.class)
				public class ExplicitRunnerTest {
					private final int value;

					public ExplicitRunnerTest(int value) {
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
				""",
				"""
				package test;

				import java.util.Arrays;
				import java.util.Collection;

				import org.junit.jupiter.api.Order;
				import org.junit.jupiter.api.Test;

				@org.junit.jupiter.api.parallel.Execution(value = org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD)
				@org.junit.jupiter.api.TestInstance(value = org.junit.jupiter.api.TestInstance.Lifecycle.PER_METHOD)
				@org.junit.jupiter.api.TestMethodOrder(value = org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
				@org.junit.jupiter.params.provider.MethodSource(value = "jupiterArguments")
				@org.junit.jupiter.params.ParameterizedClass(autoCloseArguments = false, name = "{argumentSetName}")
				public class ExplicitRunnerTest {
					private final int value;

					public ExplicitRunnerTest(int value) {
						this.value = value;
					}

					public static Collection<Object[]> data() {
						return Arrays.asList(new Object[][] { { 1 }, { 2 } });
					}

					@org.junit.jupiter.api.DisplayName(value = "verifiesValue")
					@Order(0)
					@Test
					public void verifiesValue() {
						System.out.println(value);
					}

					static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> jupiterArguments() {
						java.lang.Object source = data();
						java.lang.Iterable<?> rows = source instanceof java.lang.Object[][]
								? java.util.Arrays.asList((java.lang.Object[][]) source)
								: (java.lang.Iterable<?>) source;
						java.util.List<org.junit.jupiter.params.provider.Arguments> result = new java.util.ArrayList<>();
						int index = 0;
						for (java.lang.Object row : rows) {
							java.lang.Object[] arguments = (java.lang.Object[]) row;
							java.lang.String name = "[" + java.text.MessageFormat
									.format("{index}".replace("{index}", java.lang.Integer.toString(index++)), arguments) + "]";
							result.add(org.junit.jupiter.params.provider.Arguments.argumentSet(name, arguments));
						}
						return result.stream();
					}
				}
				""");
	}

	@Test
	public void leavesUnsupportedProviderBodyUntouched() throws CoreException {
		assertNoChange("UnsupportedProviderTest.java", //$NON-NLS-1$
				"""
				package test;

				import java.util.List;

				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class UnsupportedProviderTest {
					private final int value;

					public UnsupportedProviderTest(int value) {
						this.value = value;
					}

					@Parameters
					public static List<Object[]> data() {
						return List.<Object[]>of(new Object[] { 1 });
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

	private void assertMigration(String fileName, String source, String expected) throws CoreException {
		IPackageFragment pack= root.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit(fileName, source, false, null);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_PARAMETERIZED);
		context.assertRefactoringResultAsExpectedNormalizingWhitespace(
				new ICompilationUnit[] { unit }, new String[] { expected }, null);
	}
}
