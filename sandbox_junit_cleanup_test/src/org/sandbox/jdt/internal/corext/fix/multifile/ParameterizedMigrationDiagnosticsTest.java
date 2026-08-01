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
package org.sandbox.jdt.internal.corext.fix.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateDiagnostic;
import org.sandbox.jdt.cleanup.multifile.MultiFileCandidateOutcome;
import org.sandbox.jdt.cleanup.multifile.MultiFileCleanUpPlanResult;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitMultiFilePlanner.PlanningOptions;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Structured diagnostic contract for fail-closed Parameterized candidates. */
public class ParameterizedMigrationDiagnosticsTest {

	private static final PlanningOptions PARAMETERIZED_DIAGNOSTICS=
			new PlanningOptions(false, false, true);

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void reportsInheritedOrExternalProviderContractInIncompleteScope()
			throws CoreException {
		ICompilationUnit unit= compilationUnit("missingprovider", "MissingProviderTest", """ //$NON-NLS-1$ //$NON-NLS-2$
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
					public void testValue() {
					}
				}
				""");

		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result=
				JUnitMultiFilePlanner.createCoordinated(context.getJavaProject(),
						new ICompilationUnit[] { unit }, PARAMETERIZED_DIAGNOSTICS,
						false, null);

		assertFalse(result.diagnostics().scope().complete());
		assertReason(result, "PARAMETERIZED_PROVIDER_NOT_LOCAL"); //$NON-NLS-1$
	}

	@Test
	public void reportsMultipleLocalProviders() throws CoreException {
		ICompilationUnit unit= compilationUnit("multipleproviders", "MultipleProvidersTest", """ //$NON-NLS-1$ //$NON-NLS-2$
				import java.util.List;
				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class MultipleProvidersTest {
					private final int value;

					public MultipleProvidersTest(int value) {
						this.value = value;
					}

					@Parameters
					public static List<Object[]> first() {
						return List.<Object[]>of(new Object[] { 1 });
					}

					@Parameters
					public static List<Object[]> second() {
						return List.<Object[]>of(new Object[] { 2 });
					}

					@Test
					public void testValue() {
					}
				}
				""");

		assertReason(planClosed(unit),
				"PARAMETERIZED_MULTIPLE_LOCAL_PROVIDERS"); //$NON-NLS-1$
	}

	@Test
	public void reportsFieldInjection() throws CoreException {
		ICompilationUnit unit= compilationUnit("fieldinjection", "FieldInjectionTest", """ //$NON-NLS-1$ //$NON-NLS-2$
				import java.util.List;
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
					public static List<Object[]> data() {
						return List.<Object[]>of(new Object[] { 1 });
					}

					@Test
					public void testValue() {
					}
				}
				""");

		assertReason(planClosed(unit), "PARAMETERIZED_FIELD_INJECTION"); //$NON-NLS-1$
	}

	@Test
	public void recognizesExplicitRunWithValueSyntax() throws CoreException {
		ICompilationUnit unit= compilationUnit("explicitrunner", "ExplicitRunnerTest", """ //$NON-NLS-1$ //$NON-NLS-2$
				import java.util.List;
				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameter;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(value = Parameterized.class)
				public class ExplicitRunnerTest {
					@Parameter
					public int value;

					@Parameters
					public static List<Object[]> data() {
						return List.<Object[]>of(new Object[] { 1 });
					}

					@Test
					public void testValue() {
					}
				}
				""");

		assertReason(planClosed(unit), "PARAMETERIZED_FIELD_INJECTION"); //$NON-NLS-1$
	}

	@Test
	public void supportedLocalContractHasNoRejectionDiagnostic()
			throws CoreException {
		ICompilationUnit unit= compilationUnit("supported", "SupportedTest", """ //$NON-NLS-1$ //$NON-NLS-2$
				import java.util.List;
				import org.junit.Test;
				import org.junit.runner.RunWith;
				import org.junit.runners.Parameterized;
				import org.junit.runners.Parameterized.Parameters;

				@RunWith(Parameterized.class)
				public class SupportedTest {
					private final int value;

					public SupportedTest(int value) {
						this.value = value;
					}

					@Parameters
					public static List<Object[]> data() {
						return List.<Object[]>of(new Object[] { 1 });
					}

					@Test
					public void testValue() {
					}
				}
				""");

		MultiFileCleanUpPlanResult<JUnitMigrationPlan> result= planClosed(unit);

		assertFalse(result.status().hasFatalError());
		assertTrue(result.diagnostics().candidates().isEmpty());
	}

	private MultiFileCleanUpPlanResult<JUnitMigrationPlan> planClosed(
			ICompilationUnit unit) throws CoreException {
		return JUnitMultiFilePlanner.createCoordinated(context.getJavaProject(),
				new ICompilationUnit[] { unit }, PARAMETERIZED_DIAGNOSTICS, true,
				null);
	}

	private ICompilationUnit compilationUnit(String packageName,
			String typeName, String body) throws CoreException {
		IPackageFragment pack= root.createPackageFragment(packageName, true, null);
		return pack.createCompilationUnit(typeName + ".java", //$NON-NLS-1$
				"package " + packageName + ";\n\n" + body, false, null); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static void assertReason(
			MultiFileCleanUpPlanResult<JUnitMigrationPlan> result,
			String reasonCode) {
		assertFalse(result.status().hasFatalError());
		assertEquals(1, result.diagnostics().candidates().size());
		MultiFileCandidateDiagnostic diagnostic=
				result.diagnostics().candidates().get(0);
		assertEquals(MultiFileCandidateOutcome.REJECTED,
				diagnostic.outcome());
		assertEquals(reasonCode, diagnostic.reasonCode());
		assertTrue(diagnostic.message().contains("left unchanged")); //$NON-NLS-1$
	}
}
