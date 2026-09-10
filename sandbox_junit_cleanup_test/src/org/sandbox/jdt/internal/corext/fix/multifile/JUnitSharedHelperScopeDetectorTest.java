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

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.junit.JUnitCore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.sandbox.jdt.internal.corext.fix.multifile.JUnitScopeCandidateDetector.SearchSeeds;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Closed-source assertion/assumption helper discovery for issue #1367. */
public class JUnitSharedHelperScopeDetectorTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();
	private IPackageFragment pack;

	@BeforeEach
	public void setup() throws CoreException {
		pack= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH)
				.createPackageFragment("helpers", true, null); //$NON-NLS-1$
	}

	@Test
	public void callerSelectionDiscoversPackageStaticAssertionHelper() throws CoreException {
		ICompilationUnit helper= unit("Checks", """
				final class Checks {
					static void equal(String message, int expected, int actual) {
						org.junit.Assert.assertEquals(message, expected, actual);
					}
				}
				""");
		ICompilationUnit caller= unit("SampleTest", """
				public class SampleTest {
					void test() { Checks.equal("value", 1, 1); }
				}
				""");

		SearchSeeds seeds= JUnitSharedHelperScopeDetector.findSearchSeeds(context.getJavaProject(),
				List.of(caller), true, false, new NullProgressMonitor());

		assertTrue(seeds.candidateFound());
		assertTrue(seeds.complete());
		assertEquals(1, seeds.elements().size());
		assertEquals("equal", seeds.elements().get(0).getElementName()); //$NON-NLS-1$
		assertEquals(List.of(helper.getPrimary()), seeds.directCompilationUnits());
	}

	@Test
	public void selectedPrivateHelperIsAClosureSeedWithoutACaller() throws CoreException {
		ICompilationUnit unit= unit("PrivateChecks", """
				public class PrivateChecks {
					private void truth(boolean value) {
						org.junit.Assert.assertTrue("expected true", value);
					}
				}
				""");

		SearchSeeds seeds= JUnitSharedHelperScopeDetector.findSearchSeeds(context.getJavaProject(),
				List.of(unit), true, false, null);

		assertTrue(seeds.candidateFound());
		assertEquals(1, seeds.elements().size());
		assertEquals(List.of(unit.getPrimary()), seeds.directCompilationUnits());
	}

	@Test
	public void assertionAndAssumptionOptionsRemainIndependent() throws CoreException {
		ICompilationUnit unit= unit("Assumptions", """
				final class Assumptions {
					static void available(boolean value) {
						org.junit.Assume.assumeTrue("required", value);
					}
				}
				""");

		SearchSeeds assertionOnly= JUnitSharedHelperScopeDetector.findSearchSeeds(context.getJavaProject(),
				List.of(unit), true, false, null);
		SearchSeeds assumptionOnly= JUnitSharedHelperScopeDetector.findSearchSeeds(context.getJavaProject(),
				List.of(unit), false, true, null);

		assertFalse(assertionOnly.candidateFound());
		assertTrue(assumptionOnly.candidateFound());
		assertEquals("available", assumptionOnly.elements().get(0).getElementName()); //$NON-NLS-1$
	}

	@Test
	public void publicProtectedAndOverridablePackageInstanceHelpersStayOutsideAutomaticScope()
			throws CoreException {
		ICompilationUnit unit= unit("ExposedChecks", """
				public class ExposedChecks {
					public static void publicCheck(boolean value) { org.junit.Assert.assertTrue(value); }
					protected static void protectedCheck(boolean value) { org.junit.Assert.assertTrue(value); }
					void virtualCheck(boolean value) { org.junit.Assert.assertTrue(value); }
				}
				""");

		SearchSeeds seeds= JUnitSharedHelperScopeDetector.findSearchSeeds(context.getJavaProject(),
				List.of(unit), true, false, null);

		assertFalse(seeds.candidateFound());
		assertTrue(seeds.elements().isEmpty());
		assertTrue(seeds.directCompilationUnits().isEmpty());
	}

	@Test
	public void helperWithAdditionalBehaviorIsNotFlattenedIntoDirectWrapperContract() throws CoreException {
		ICompilationUnit unit= unit("CompoundChecks", """
				final class CompoundChecks {
					static void equal(int expected, int actual) {
						System.out.println("checking");
						org.junit.Assert.assertEquals(expected, actual);
					}
				}
				""");

		SearchSeeds seeds= JUnitSharedHelperScopeDetector.findSearchSeeds(context.getJavaProject(),
				List.of(unit), true, false, null);

		assertFalse(seeds.candidateFound());
	}

	private ICompilationUnit unit(String typeName, String body) throws CoreException {
		return pack.createCompilationUnit(typeName + ".java", //$NON-NLS-1$
				"package helpers;\n" + body, true, null); //$NON-NLS-1$
	}
}
