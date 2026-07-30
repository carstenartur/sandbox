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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** Proves seeded JDT Core ordering against the same before/after JDT launch. */
public class JdtCoreSeededOrderingMigrationTest {

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
	public void preservesSeededShuffleFromReflectionOrder() throws CoreException {
		ICompilationUnit abstractPerformance= unit("org.eclipse.test.performance", //$NON-NLS-1$
				"AbstractPerformanceTestCase.java", //$NON-NLS-1$
				"""
				package org.eclipse.test.performance;
				public class AbstractPerformanceTestCase {
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
					public static Object getPlugin() { return null; }
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
					public static junit.framework.Test buildTestSuite(Class<?> type) {
						junit.framework.TestSuite suite= new junit.framework.TestSuite(type.getName());
						java.util.List<String> names= java.util.Arrays.stream(type.getDeclaredMethods())
								.filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
								.filter(method -> !java.lang.reflect.Modifier.isStatic(method.getModifiers()))
								.map(java.lang.reflect.Method::getName)
								.filter(name -> name.startsWith(METHOD_PREFIX)).distinct()
								.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
						if (ORDERING == ALPHABETICAL_SORT) {
							java.util.Collections.sort(names);
						} else if (ORDERING == ALPHA_REVERSE_SORT) {
							names.sort(java.util.Collections.reverseOrder());
						} else if (ORDERING != NO_ORDER && ORDERING != BYTECODE_DECLARATION_ORDER) {
							java.util.Collections.shuffle(names, new java.util.Random(ORDERING));
						}
						try {
							java.lang.reflect.Constructor<?> constructor= type.getConstructor(String.class);
							for (String name : names) {
								suite.addTest((junit.framework.Test) constructor.newInstance(name));
							}
						} catch (ReflectiveOperationException exception) {
							throw new IllegalStateException(exception);
						}
						return suite;
					}
				}
				""");
		ICompilationUnit family= unit("direct", "SeededHarnessTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package direct;
				import junit.framework.Test;
				import org.eclipse.jdt.core.tests.junit.extension.TestCase;
				public class SeededHarnessTests extends TestCase {
					static {
						TestCase.ORDERING= 8675309L;
						TestCase.RUN_ONLY_ID= "ONLY_";
						TestCase.TESTS_PREFIX= null;
						TestCase.TESTS_NAMES= null;
						TestCase.TESTS_NUMBERS= null;
						TestCase.TESTS_RANGE= null;
					}
					public SeededHarnessTests(String name) { super(name); }
					public static Test suite() { return buildTestSuite(SeededHarnessTests.class); }
					public void testKilo() {}
					public void testAlpha() {}
					public void testZulu() {}
					public void testBravo() {}
					public void testMike() {}
				}
				""");

		ICompilationUnit[] allUnits= { abstractPerformance, performance3, performance5, javaCore,
				modelManager, harness, family };
		context.assertCompiles(allUnits);
		IType familyType= family.findPrimaryType();
		Snapshot before= JUnitRuntimeTestTree.capture(familyType);
		assertTrue(before.successful(), () -> "Seeded JUnit 3 baseline failed: " + before.entries()); //$NON-NLS-1$
		assertEquals(5, testCount(before));

		context.enable(MYCleanUpConstants.JUNIT3_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_3_TEST);
		RefactoringStatus status= context.migrate(harness, family);
		assertFalse(status.hasError(), status::toString);
		context.assertCompiles(allUnits);
		assertTrue(family.getBuffer().getContents().contains("extends TestCase.Jupiter")); //$NON-NLS-1$

		Snapshot after= JUnitRuntimeTestTree.capture(familyType);
		assertTrue(after.successful(), () -> "Seeded Jupiter migration failed: " + after.entries()); //$NON-NLS-1$
		assertEquals(before.entries(), after.entries(),
				"The seeded shuffle must use the same reflected starting order before and after migration."); //$NON-NLS-1$
		assertEquals(5, testCount(after));
	}

	private static long testCount(Snapshot snapshot) {
		return snapshot.entries().stream().filter(entry -> entry.contains(":test:")).count(); //$NON-NLS-1$
	}

	private ICompilationUnit unit(String packageName, String fileName, String source) throws CoreException {
		IPackageFragment fragment= root.createPackageFragment(packageName, true, null);
		return fragment.createCompilationUnit(fileName, source, false, null);
	}
}
