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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.junit.JUnitCore;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

import org.eclipse.jdt.ui.tests.quickfix.Java8.JUnitRuntimeTestTree.Snapshot;

/** End-to-end contract for the first direct Eclipse JDT Core harness slice. */
public class JdtCoreDirectHarnessMigrationTest {

	private static final class HarnessEclipseJava extends AbstractEclipseJava {
		HarnessEclipseJava() {
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
	HarnessEclipseJava context= new HarnessEclipseJava();

	private IPackageFragmentRoot root;

	@BeforeEach
	public void setup() throws CoreException {
		root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@Test
	public void migratesDirectNamedTestFamilyAndPreservesRuntimeTree() throws CoreException {
		ICompilationUnit abstractPerformance= unit("org.eclipse.test.performance", //$NON-NLS-1$
				"AbstractPerformanceTestCase.java", //$NON-NLS-1$
				"""
				package org.eclipse.test.performance;
				public abstract class AbstractPerformanceTestCase {
					protected void startMeasuring() {}
					protected void stopMeasuring() {}
					protected void commitMeasurements() {}
					protected void assertPerformance() {}
				}
				""");
		ICompilationUnit performance3= unit("org.eclipse.test.performance", "PerformanceTestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.test.performance;
				public class PerformanceTestCase extends junit.framework.TestCase {
					public PerformanceTestCase() {}
					public PerformanceTestCase(String name) { super(name); }
				}
				""");
		ICompilationUnit performance5= unit("org.eclipse.test.performance", //$NON-NLS-1$
				"PerformanceTestCaseJunit5.java", //$NON-NLS-1$
				"""
				package org.eclipse.test.performance;
				public class PerformanceTestCaseJunit5 extends AbstractPerformanceTestCase {
					@org.junit.jupiter.api.BeforeEach
					public void setUp(org.junit.jupiter.api.TestInfo testInfo) throws Exception {}
					@org.junit.jupiter.api.AfterEach
					public void tearDown() throws Exception {}
				}
				""");
		ICompilationUnit javaCore= unit("org.eclipse.jdt.core", "JavaCore.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.core;
				public final class JavaCore {
					private JavaCore() {}
					public static Object getPlugin() { return new Object(); }
				}
				""");
		ICompilationUnit modelManager= unit("org.eclipse.jdt.internal.core", "JavaModelManager.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.internal.core;
				public final class JavaModelManager {
					private static final JavaModelManager INSTANCE= new JavaModelManager();
					private final IndexManager indexManager= new IndexManager();
					private JavaModelManager() {}
					public static JavaModelManager getJavaModelManager() { return INSTANCE; }
					public IndexManager getIndexManager() { return this.indexManager; }
					public static final class IndexManager {
						private boolean enabled= true;
						public boolean isEnabled() { return this.enabled; }
						public void disable() { this.enabled= false; }
						public void enable() { this.enabled= true; }
					}
				}
				""");
		ICompilationUnit harness= unit("org.eclipse.jdt.core.tests.junit.extension", "TestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.core.tests.junit.extension;
				public abstract class TestCase extends org.eclipse.test.performance.PerformanceTestCase {
					public static final int NO_ORDER= 0;
					public static final int ALPHABETICAL_SORT= 1;
					public static final int ALPHA_REVERSE_SORT= 2;
					public static final int BYTECODE_DECLARATION_ORDER= 3;
					public static long ORDERING= ALPHABETICAL_SORT;
					public static final String METHOD_PREFIX= "test";
					public static String RUN_ONLY_ID;
					public static String TESTS_PREFIX;
					public static String[] TESTS_NAMES;
					public static int[] TESTS_NUMBERS;
					public static int[] TESTS_RANGE;
					private static final int MAX_GC= 5;
					private static final int TIME_GC= 1;
					private static final int DELTA_GC= 0;
					private static java.io.File MEM_LOG_FILE;
					private static Class<?> CURRENT_CLASS;
					private static String CURRENT_CLASS_NAME;
					private static String STORE_MEMORY;
					private static boolean ALL_TESTS_LOG;
					private static boolean RUN_GC;

					public TestCase(String name) { super(name); }
					public static junit.framework.Test buildTestSuite(Class<?> type) {
						return new junit.framework.TestSuite(type.asSubclass(junit.framework.TestCase.class));
					}
				}
				""");
		ICompilationUnit direct= unit("direct", "DirectTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package direct;
				import junit.framework.Test;
				import org.eclipse.jdt.core.tests.junit.extension.TestCase;
				public class DirectTests extends TestCase {
					public DirectTests(String name) { super(name); }
					public static Test suite() { return buildTestSuite(DirectTests.class); }
					public void testOne() { assertEquals("message", 1, 1); }
				}
				""");

		ICompilationUnit[] allUnits= { abstractPerformance, performance3, performance5, javaCore,
				modelManager, harness, direct };
		context.assertCompiles(allUnits);
		IType launchTarget= direct.findPrimaryType();
		assertNotNull(launchTarget);
		Snapshot before= JUnitRuntimeTestTree.capture(launchTarget);
		assertTrue(before.successful(), () -> "JUnit 3 harness baseline failed: " + before.entries()); //$NON-NLS-1$

		enableMigration();
		RefactoringStatus status= context.migrate(harness, direct);
		assertFalse(status.hasError(), status::toString);
		context.assertCompiles(allUnits);

		String harnessSource= harness.getBuffer().getContents();
		String directSource= direct.getBuffer().getContents();
		assertTrue(harnessSource.contains("class Jupiter extends org.eclipse.test.performance.PerformanceTestCaseJunit5")); //$NON-NLS-1$
		assertTrue(harnessSource.contains("JdtCoreFilterCondition")); //$NON-NLS-1$
		assertTrue(harnessSource.contains("JdtCoreMethodOrderer")); //$NON-NLS-1$
		assertTrue(directSource.contains("extends TestCase.Jupiter")); //$NON-NLS-1$
		assertTrue(directSource.contains("@Test")); //$NON-NLS-1$
		assertTrue(directSource.contains("Assertions.assertEquals(1, 1, \"message\")")); //$NON-NLS-1$
		assertFalse(directSource.contains("DirectTests(String name)")); //$NON-NLS-1$
		assertFalse(directSource.contains("static Test suite()")); //$NON-NLS-1$

		Snapshot after= JUnitRuntimeTestTree.capture(launchTarget);
		assertTrue(after.successful(), () -> "Migrated JDT Core Jupiter run failed: " + after.entries()); //$NON-NLS-1$
		assertEquals(before.entries(), after.entries(),
				"The same JDT launch must preserve direct harness test identity, nesting and multiplicity."); //$NON-NLS-1$
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
