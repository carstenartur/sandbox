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

import java.util.EnumSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupport.Analysis;
import org.sandbox.jdt.internal.corext.fix.multifile.JUnitBestEffortSupport.Gap;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Regression tests for explicit non-atomic JUnit migration evidence. */
public class JUnitBestEffortSupportTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void reportsParameterizedFieldInjectionWithActionableRemediation() throws CoreException {
		ICompilationUnit unit= compilationUnit("fieldinjection", "FieldInjectionTest", """
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
					public void verifiesValue() {
					}
				}
				"""); //$NON-NLS-1$

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.PARAMETERIZED), unit);

		assertEquals(1, analysis.gaps().size());
		Gap gap= analysis.gaps().get(0);
		assertEquals("PARAMETERIZED_FIELD_INJECTION", gap.reasonCode()); //$NON-NLS-1$
		assertTrue(gap.explanation().contains("field injection")); //$NON-NLS-1$
		assertTrue(gap.remediation().contains("Jupiter method arguments")); //$NON-NLS-1$
		assertTrue(analysis.toJson("{\"scope\":{}}") //$NON-NLS-1$
				.contains("\"manualCompletionRequired\":true")); //$NON-NLS-1$
	}

	@Test
	public void reportsCustomRunnerWithoutGuessingAnExtension() throws CoreException {
		ICompilationUnit runner= compilationUnit("customrunner", "ProjectRunner", """
				import org.junit.runner.Description;
				import org.junit.runner.Runner;
				import org.junit.runner.notification.RunNotifier;

				public class ProjectRunner extends Runner {
					@Override
					public Description getDescription() {
						return Description.EMPTY;
					}

					@Override
					public void run(RunNotifier notifier) {
					}
				}
				"""); //$NON-NLS-1$
		ICompilationUnit test= compilationUnit("customrunner", "RunnerTest", """
				import org.junit.Test;
				import org.junit.runner.RunWith;

				@RunWith(ProjectRunner.class)
				public class RunnerTest {
					@Test
					public void testSomething() {
					}
				}
				"""); //$NON-NLS-1$

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.RUNWITH), runner, test);

		assertEquals(1, analysis.gaps().size());
		Gap gap= analysis.gaps().get(0);
		assertEquals("CUSTOM_JUNIT4_RUNNER", gap.reasonCode()); //$NON-NLS-1$
		assertTrue(gap.explanation().contains("customrunner.ProjectRunner")); //$NON-NLS-1$
		assertTrue(gap.remediation().contains("Jupiter extensions")); //$NON-NLS-1$
	}

	@Test
	public void mixedRuleLifecycleDefersOnlyCoordinatedExternalResourcePlanning() throws CoreException {
		ICompilationUnit resource= compilationUnit("mixedlifecycle", "SharedResource", """
				import org.junit.rules.ExternalResource;

				public class SharedResource extends ExternalResource {
					@Override
					protected void before() {
					}
				}
				"""); //$NON-NLS-1$
		ICompilationUnit instanceUse= compilationUnit("mixedlifecycle", "InstanceUseTest", """
				import org.junit.Rule;
				import org.junit.Test;

				public class InstanceUseTest {
					@Rule
					public SharedResource resource = new SharedResource();

					@Test
					public void instanceTest() {
					}
				}
				"""); //$NON-NLS-1$
		ICompilationUnit classUse= compilationUnit("mixedlifecycle", "ClassUseTest", """
				import org.junit.ClassRule;
				import org.junit.Test;

				public class ClassUseTest {
					@ClassRule
					public static SharedResource resource = new SharedResource();

					@Test
					public void classTest() {
					}
				}
				"""); //$NON-NLS-1$

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.RULEEXTERNALRESOURCE,
				JUnitCleanUpFixCore.EXTERNALRESOURCE), resource, instanceUse, classUse);

		assertTrue(analysis.disableCoordinatedExternalResource());
		assertEquals(1, analysis.gaps().size());
		assertEquals("MIXED_RULE_LIFECYCLE", analysis.gaps().get(0).reasonCode()); //$NON-NLS-1$
		assertTrue(analysis.gaps().get(0).remediation().contains("separate instance and class extensions")); //$NON-NLS-1$
	}

	@Test
	public void supportedParameterizedContractProducesNoManualGap() throws CoreException {
		ICompilationUnit unit= compilationUnit("supported", "SupportedTest", """
				import java.util.Arrays;
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
						return Arrays.asList(new Object[][] { { 1 } });
					}

					@Test
					public void testValue() {
					}
				}
				"""); //$NON-NLS-1$

		Analysis analysis= analyze(EnumSet.of(JUnitCleanUpFixCore.PARAMETERIZED), unit);

		assertTrue(analysis.gaps().isEmpty());
		assertFalse(analysis.disableCoordinatedExternalResource());
		assertTrue(analysis.toJson("").contains("\"manualCompletionRequired\":false")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private Analysis analyze(EnumSet<JUnitCleanUpFixCore> fixes, ICompilationUnit... units) {
		return JUnitBestEffortSupport.analyze(context.getJavaProject(), units, fixes, null);
	}

	private ICompilationUnit compilationUnit(String packageName, String typeName, String body)
			throws CoreException {
		IPackageFragment pack= root.createPackageFragment(packageName, true, null);
		return pack.createCompilationUnit(typeName + ".java", //$NON-NLS-1$
				"package " + packageName + ";\n\n" + body, false, null); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
