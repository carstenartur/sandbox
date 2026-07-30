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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Compiles the generated compatibility bridge against the actual repository target bundles. */
public class JdtCoreTargetBridgeCompilationTest {

	private static final class TargetEclipseJava extends AbstractEclipseJava {
		TargetEclipseJava() {
			super("testresources/rtstubs_17.jar", JavaCore.VERSION_17); //$NON-NLS-1$
		}

		RefactoringStatus migrate(ICompilationUnit... units) throws CoreException {
			return performRefactoring(units, null);
		}

		void assertCompiles(ICompilationUnit... units) {
			for (ICompilationUnit unit : units) {
				assertNoCompilationError(unit);
			}
		}
	}

	@RegisterExtension
	TargetEclipseJava context= new TargetEclipseJava();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
		context.addBundleToClasspath("org.eclipse.test.performance"); //$NON-NLS-1$
		context.addBundleToClasspath("org.eclipse.jdt.core"); //$NON-NLS-1$
	}

	@Test
	public void compilesGeneratedBridgeAgainstActualJdtAndPerformanceApis() throws CoreException {
		ICompilationUnit harness= unit("org.eclipse.jdt.core.tests.junit.extension", "TestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.core.tests.junit.extension;
				public abstract class TestCase extends org.eclipse.test.performance.PerformanceTestCase {
					public static final int NO_ORDER= 0;
					public static final int ALPHABETICAL_SORT= 1;
					public static final int ALPHA_REVERSE_SORT= 2;
					public static final int BYTECODE_DECLARATION_ORDER= 5;
					public static long ORDERING= ALPHABETICAL_SORT;
					public static final String METHOD_PREFIX= "test";
					public static String RUN_ONLY_ID= "ONLY_";
					public static String TESTS_PREFIX;
					public static String[] TESTS_NAMES;
					public static int[] TESTS_NUMBERS;
					public static int[] TESTS_RANGE;
					private static final int MAX_GC= 1;
					private static final int TIME_GC= 1;
					private static java.io.File MEM_LOG_FILE;
					private static Class<?> CURRENT_CLASS;
					private static String CURRENT_CLASS_NAME;
					private static String STORE_MEMORY;
					private static boolean ALL_TESTS_LOG;
					private static boolean RUN_GC;
					public TestCase(String name) { super(name); }
				}
				""");
		ICompilationUnit direct= unit("target", "TargetBundleTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package target;
				public class TargetBundleTests
						extends org.eclipse.jdt.core.tests.junit.extension.TestCase {
					public TargetBundleTests(String name) { super(name); }
					public void testCompiles() {
						startMeasuring();
						stopMeasuring();
						commitMeasurements();
						assertPerformance();
					}
				}
				""");

		context.assertCompiles(harness, direct);
		enableMigration();
		RefactoringStatus status= context.migrate(harness, direct);
		assertFalse(status.hasError(), status::toString);
		context.assertCompiles(harness, direct);
		assertTrue(harness.getBuffer().getContents().contains(
				"extends org.eclipse.test.performance.PerformanceTestCaseJunit5")); //$NON-NLS-1$
	}

	private ICompilationUnit unit(String packageName, String fileName, String source) throws CoreException {
		IPackageFragment fragment= root.createPackageFragment(packageName, true, null);
		return fragment.createCompilationUnit(fileName, source, false, null);
	}

	private void enableMigration() throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT3_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_3_TEST);
	}
}
