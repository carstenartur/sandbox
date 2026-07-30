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

import java.util.List;

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

/** Active contracts for the direct Eclipse JDT Core custom-harness slice. */
public class JdtCoreDirectHarnessContractTest {

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
	public void generatedBridgeCompilesAgainstTargetPerformanceBundle() throws CoreException {
		context.addBundleToClasspath("org.eclipse.test.performance"); //$NON-NLS-1$
		ICompilationUnit javaCore= javaCoreStub();
		ICompilationUnit modelManager= javaModelManagerStub();
		ICompilationUnit harness= harnessUsingTargetPerformanceBundle();
		ICompilationUnit direct= unit("target", "TargetPerformanceTests.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package target;
				import org.eclipse.jdt.core.tests.junit.extension.TestCase;
				public class TargetPerformanceTests extends TestCase {
					public TargetPerformanceTests(String name) { super(name); }
					public void testCompiles() {}
				}
				""");

		ICompilationUnit[] units= { javaCore, modelManager, harness, direct };
		context.assertCompiles(units);
		enableMigration();
		RefactoringStatus status= context.migrate(harness, direct);
		assertFalse(status.hasError(), status::toString);
		context.assertCompiles(units);
		assertTrue(harness.getBuffer().getContents().contains(
				"extends org.eclipse.test.performance.PerformanceTestCaseJunit5")); //$NON-NLS-1$
	}

	@Test
	public void preservesFilterOrderMultiplicityIndexerInterruptAndPerformanceCalls() throws CoreException {
		ICompilationUnit abstractPerformance= unit("org.eclipse.test.performance", //$NON-NLS-1$
				"AbstractPerformanceTestCase.java", performanceBaseSource("AbstractPerformanceTestCase")); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit performance3= unit("org.eclipse.test.performance", "PerformanceTestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.test.performance;
				public class PerformanceTestCase extends junit.framework.TestCase {
					protected int starts;
					protected int stops;
					protected int commits;
					protected int assertions;
					public PerformanceTestCase() {}
					public PerformanceTestCase(String name) { super(name); }
					protected void startMeasuring() { this.starts++; }
					protected void stopMeasuring() { this.stops++; }
					protected void commitMeasurements() { this.commits++; }
					protected void assertPerformance() {
						this.assertions++;
						if (this.starts != 1 || this.stops != 1 || this.commits != 1 || this.assertions != 1) {
							throw new AssertionError("performance calls were not preserved");
						}
					}
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
		ICompilationUnit javaCore= javaCoreStub();
		ICompilationUnit modelManager= javaModelManagerStub();
		ICompilationUnit harness= instrumentedHarness();
		ICompilationUnit ordered= unit("direct", "OrderedHarnessTests.java", orderedFamilySource()); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit filtered= unit("direct", "FilteredHarnessTests.java", filteredFamilySource()); //$NON-NLS-1$ //$NON-NLS-2$

		ICompilationUnit[] allUnits= { abstractPerformance, performance3, performance5, javaCore,
				modelManager, harness, ordered, filtered };
		context.assertCompiles(allUnits);
		IType orderedType= ordered.findPrimaryType();
		IType filteredType= filtered.findPrimaryType();
		Snapshot orderedBefore= JUnitRuntimeTestTree.capture(orderedType);
		Snapshot filteredBefore= JUnitRuntimeTestTree.capture(filteredType);
		assertTrue(orderedBefore.successful(), () -> "Ordered JUnit 3 baseline failed: " + orderedBefore.entries()); //$NON-NLS-1$
		assertTrue(filteredBefore.successful(), () -> "Filtered JUnit 3 baseline failed: " + filteredBefore.entries()); //$NON-NLS-1$
		assertEquals(List.of("testZulu", "testMiddle", "testAlpha"), testMethods(orderedBefore)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(List.of("testCase002"), testMethods(filteredBefore)); //$NON-NLS-1$

		enableMigration();
		RefactoringStatus status= context.migrate(harness, ordered, filtered);
		assertFalse(status.hasError(), status::toString);
		context.assertCompiles(allUnits);

		Snapshot orderedAfter= JUnitRuntimeTestTree.capture(orderedType);
		Snapshot filteredAfter= JUnitRuntimeTestTree.capture(filteredType);
		assertTrue(orderedAfter.successful(), () -> "Ordered Jupiter migration failed: " + orderedAfter.entries()); //$NON-NLS-1$
		assertTrue(filteredAfter.successful(), () -> "Filtered Jupiter migration failed: " + filteredAfter.entries()); //$NON-NLS-1$
		assertEquals(orderedBefore.entries(), orderedAfter.entries(),
				"Order, identity, nesting and multiplicity must be unchanged."); //$NON-NLS-1$
		assertEquals(filteredBefore.entries(), filteredAfter.entries(),
				"The configured JDT Core method filter must select the same runtime tree."); //$NON-NLS-1$
		assertEquals(List.of("testZulu", "testMiddle", "testAlpha"), testMethods(orderedAfter)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		assertEquals(List.of("testCase002"), testMethods(filteredAfter)); //$NON-NLS-1$
	}

	private ICompilationUnit javaCoreStub() throws CoreException {
		return unit("org.eclipse.jdt.core", "JavaCore.java", //$NON-NLS-1$ //$NON-NLS-2$
				"""
				package org.eclipse.jdt.core;
				public final class JavaCore {
					private JavaCore() {}
					public static Object getPlugin() { return new Object(); }
				}
				""");
	}

	private ICompilationUnit javaModelManagerStub() throws CoreException {
		return unit("org.eclipse.jdt.internal.core", "JavaModelManager.java", //$NON-NLS-1$ //$NON-NLS-2$
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
	}

	private ICompilationUnit harnessUsingTargetPerformanceBundle() throws CoreException {
		return unit("org.eclipse.jdt.core.tests.junit.extension", "TestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
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
	}

	private ICompilationUnit instrumentedHarness() throws CoreException {
		return unit("org.eclipse.jdt.core.tests.junit.extension", "TestCase.java", //$NON-NLS-1$ //$NON-NLS-2$
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
					protected boolean indexDisabledForTest= true;

					public TestCase(String name) { super(name); }

					public static junit.framework.Test buildTestSuite(Class<?> type) {
						junit.framework.TestSuite suite= new junit.framework.TestSuite(type.getName());
						java.util.List<String> names= java.util.Arrays.stream(type.getDeclaredMethods())
								.filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
								.filter(method -> !java.lang.reflect.Modifier.isStatic(method.getModifiers()))
								.map(java.lang.reflect.Method::getName)
								.filter(name -> name.startsWith(METHOD_PREFIX)).distinct()
								.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
						java.util.List<String> onlyNames= RUN_ONLY_ID == null ? java.util.List.of()
								: names.stream().filter(name -> name.substring(METHOD_PREFIX.length())
										.startsWith(RUN_ONLY_ID)).toList();
						names= onlyNames.isEmpty() ? names.stream().filter(TestCase::selected).collect(
								java.util.stream.Collectors.toCollection(java.util.ArrayList::new))
								: new java.util.ArrayList<>(onlyNames);
						if (ORDERING == ALPHABETICAL_SORT) {
							java.util.Collections.sort(names);
						} else if (ORDERING == ALPHA_REVERSE_SORT) {
							names.sort(java.util.Collections.reverseOrder());
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

					private static boolean selected(String name) {
						if (TESTS_PREFIX == null && TESTS_NAMES == null && TESTS_NUMBERS == null && TESTS_RANGE == null) {
							return true;
						}
						if (TESTS_PREFIX != null && !name.startsWith(TESTS_PREFIX)) {
							return false;
						}
						if (TESTS_NAMES != null && java.util.Arrays.stream(TESTS_NAMES).anyMatch(name::contains)) {
							return true;
						}
						int number= testNumber(name);
						if (TESTS_NUMBERS != null && java.util.Arrays.stream(TESTS_NUMBERS).anyMatch(value -> value == number)) {
							return true;
						}
						if (TESTS_RANGE != null && TESTS_RANGE.length == 2 && number >= 0
								&& (TESTS_RANGE[0] == -1 || number >= TESTS_RANGE[0])
								&& (TESTS_RANGE[1] == -1 || number <= TESTS_RANGE[1])) {
							return true;
						}
						return TESTS_NAMES == null && TESTS_NUMBERS == null && TESTS_RANGE == null;
					}

					private static int testNumber(String name) {
						int index= TESTS_PREFIX == null ? METHOD_PREFIX.length() : TESTS_PREFIX.length();
						while (index < name.length() && !Character.isDigit(name.charAt(index))) index++;
						while (index < name.length() && name.charAt(index) == '0') index++;
						int end= index;
						while (end < name.length() && Character.isDigit(name.charAt(end))) end++;
						return end == index ? -1 : Integer.parseInt(name.substring(index, end));
					}

					@Override
					protected void setUp() throws Exception {
						if (org.eclipse.jdt.core.JavaCore.getPlugin() != null && isIndexDisabledForTest()) disableIndexer();
						super.setUp();
					}

					@Override
					protected void tearDown() throws Exception {
						super.tearDown();
						if (org.eclipse.jdt.core.JavaCore.getPlugin() != null && isIndexDisabledForTest()) enableIndexer();
					}

					@Override
					protected void runTest() throws Throwable {
						try { super.runTest(); } finally { Thread.interrupted(); }
					}

					public boolean isIndexDisabledForTest() { return this.indexDisabledForTest; }
					protected void disableIndexer() {
						while (org.eclipse.jdt.internal.core.JavaModelManager.getJavaModelManager()
								.getIndexManager().isEnabled()) {
							org.eclipse.jdt.internal.core.JavaModelManager.getJavaModelManager()
									.getIndexManager().disable();
						}
					}
					protected void enableIndexer() {
						while (!org.eclipse.jdt.internal.core.JavaModelManager.getJavaModelManager()
								.getIndexManager().isEnabled()) {
							org.eclipse.jdt.internal.core.JavaModelManager.getJavaModelManager()
									.getIndexManager().enable();
						}
					}
				}
				""");
	}

	private static String performanceBaseSource(String typeName) {
		return """
				package org.eclipse.test.performance;
				public class %s {
					protected int starts;
					protected int stops;
					protected int commits;
					protected int assertions;
					protected void startMeasuring() { this.starts++; }
					protected void stopMeasuring() { this.stops++; }
					protected void commitMeasurements() { this.commits++; }
					protected void assertPerformance() {
						this.assertions++;
						if (this.starts != 1 || this.stops != 1 || this.commits != 1 || this.assertions != 1) {
							throw new AssertionError("performance calls were not preserved");
						}
					}
				}
				""".formatted(typeName);
	}

	private static String orderedFamilySource() {
		return """
				package direct;
				import junit.framework.Test;
				import org.eclipse.jdt.core.tests.junit.extension.TestCase;
				public class OrderedHarnessTests extends TestCase {
					static {
						TestCase.ORDERING= TestCase.ALPHA_REVERSE_SORT;
						TestCase.RUN_ONLY_ID= "ONLY_";
						TestCase.TESTS_PREFIX= null;
						TestCase.TESTS_NAMES= null;
						TestCase.TESTS_NUMBERS= null;
						TestCase.TESTS_RANGE= null;
					}
					private final boolean indexEnabledAtConstruction= assertIndexerEnabled();
					public OrderedHarnessTests(String name) { super(name); }
					public static Test suite() { return buildTestSuite(OrderedHarnessTests.class); }
					public void testZulu() {
						assertHarnessState(false);
						startMeasuring();
						stopMeasuring();
						commitMeasurements();
						assertPerformance();
						Thread.currentThread().interrupt();
					}
					public void testMiddle() {
						assertHarnessState(true);
						startMeasuring();
						stopMeasuring();
						commitMeasurements();
						assertPerformance();
					}
					public void testAlpha() {
						assertHarnessState(true);
						startMeasuring();
						stopMeasuring();
						commitMeasurements();
						assertPerformance();
					}
					private static boolean assertIndexerEnabled() {
						if (!org.eclipse.jdt.internal.core.JavaModelManager.getJavaModelManager()
								.getIndexManager().isEnabled()) {
							throw new AssertionError("indexer was not restored before constructing the next test");
						}
						return true;
					}
					private void assertHarnessState(boolean interruptMustBeClear) {
						if (!this.indexEnabledAtConstruction) throw new AssertionError("constructor probe failed");
						if (org.eclipse.jdt.internal.core.JavaModelManager.getJavaModelManager()
								.getIndexManager().isEnabled()) {
							throw new AssertionError("indexer was not disabled during the test");
						}
						if (interruptMustBeClear && Thread.currentThread().isInterrupted()) {
							throw new AssertionError("interrupt status leaked from the previous test");
						}
					}
				}
				""";
	}

	private static String filteredFamilySource() {
		return """
				package direct;
				import junit.framework.Test;
				import org.eclipse.jdt.core.tests.junit.extension.TestCase;
				public class FilteredHarnessTests extends TestCase {
					static {
						TestCase.ORDERING= TestCase.ALPHABETICAL_SORT;
						TestCase.RUN_ONLY_ID= "ONLY_";
						TestCase.TESTS_PREFIX= "testCase";
						TestCase.TESTS_NAMES= null;
						TestCase.TESTS_NUMBERS= new int[] { 2 };
						TestCase.TESTS_RANGE= null;
					}
					public FilteredHarnessTests(String name) { super(name); }
					public static Test suite() { return buildTestSuite(FilteredHarnessTests.class); }
					public void testCase001() {}
					public void testCase002() {}
					public void testOther003() {}
				}
				""";
	}

	private static List<String> testMethods(Snapshot snapshot) {
		return snapshot.entries().stream().filter(entry -> entry.contains(":test:")) //$NON-NLS-1$
				.map(entry -> entry.substring(entry.indexOf('#') + 1, entry.lastIndexOf('='))).toList();
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
