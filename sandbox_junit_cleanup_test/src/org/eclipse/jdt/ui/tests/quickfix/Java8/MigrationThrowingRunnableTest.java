/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.JUnitMigrationFixtureClasspath;

/**
 * Tests for migrating JUnit 4 ThrowingRunnable to JUnit 5 Executable.
 * Covers ThrowingRunnable → Executable transformations and .run() → .execute() method calls.
 */
public class MigrationThrowingRunnableTest {

	// Keep the same explicit system library in both fixture initialization steps.
	// Do not switch from rtstubs to a lazily resolved default-JRE container.
	@RegisterExtension
	AbstractEclipseJava context = new AbstractEclipseJava(
			Path.of(System.getProperty("java.home"), "lib", "jrt-fs.jar").toString(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			JavaCore.VERSION_17);

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = JUnitMigrationFixtureClasspath.createJUnit4And5Root(context);
		IJavaProject project = context.getJavaProject();
		for (String type : List.of("java.lang.Object", "java.util.concurrent.atomic.AtomicReference", //$NON-NLS-1$ //$NON-NLS-2$
				"org.junit.function.ThrowingRunnable", "org.junit.jupiter.api.function.Executable")) { //$NON-NLS-1$ //$NON-NLS-2$
			assertNotNull(project.findType(type), type);
		}
	}

	@RepeatedTest(25)
	public void resolvesRuntimeAndBothJUnitApisAfterEverySetup() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("probe", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("RuntimeProbe.java", //$NON-NLS-1$
				"""
				package probe;
				import java.util.concurrent.atomic.AtomicReference;
				import org.junit.function.ThrowingRunnable;
				import org.junit.jupiter.api.function.Executable;

				public class RuntimeProbe {
					public void run() throws Throwable {
						AtomicReference<ThrowingRunnable> legacy = new AtomicReference<>(() -> {});
						legacy.get().run();
						AtomicReference<Executable> target = new AtomicReference<>(() -> {});
						target.get().execute();
					}
				}
				""", false, null);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void migrates_basic_type_replacement() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java",
				"""
				package test;
				import org.junit.function.ThrowingRunnable;
				
				public class Test {
					ThrowingRunnable runnable = () -> {};
				}
				""", false, null);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.function.Executable;
				
				public class Test {
					Executable runnable = () -> {};
				}
				"""
		}, null);
	}

	@Test
	public void migrates_method_call_replacement() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java",
				"""
				package test;
				import org.junit.function.ThrowingRunnable;
				
				public class Test {
					void test(ThrowingRunnable r) throws Throwable {
						r.run();
					}
				}
				""", false, null);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.function.Executable;
				
				public class Test {
					void test(Executable r) throws Throwable {
						r.execute();
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_generic_type_parameter() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java",
				"""
				package test;
				import java.util.concurrent.atomic.AtomicReference;
				import org.junit.function.ThrowingRunnable;
				
				public class Test {
					AtomicReference<ThrowingRunnable> ref = new AtomicReference<>();
					void test() throws Throwable {
						ref.get().run();
					}
				}
				""", false, null);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import java.util.concurrent.atomic.AtomicReference;
				
				import org.junit.jupiter.api.function.Executable;
				
				public class Test {
					AtomicReference<Executable> ref = new AtomicReference<>();
					void test() throws Throwable {
						ref.get().execute();
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_method_parameter() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java",
				"""
				package test;
				import org.junit.function.ThrowingRunnable;
				
				public class Test {
					void withAction(ThrowingRunnable action) throws Throwable {
						action.run();
					}
				}
				""", false, null);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.function.Executable;
				
				public class Test {
					void withAction(Executable action) throws Throwable {
						action.execute();
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_static_final_field() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java",
				"""
				package test;
				import org.junit.function.ThrowingRunnable;
				
				public class Test {
					private static final ThrowingRunnable NOOP_RUNNABLE = () -> {};
					
					public void test() throws Throwable {
						NOOP_RUNNABLE.run();
					}
				}
				""", false, null);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.function.Executable;
				
				public class Test {
					private static final Executable NOOP_RUNNABLE = () -> {};
					
					public void test() throws Throwable {
						NOOP_RUNNABLE.execute();
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_local_variable() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java",
				"""
				package test;
				import org.junit.function.ThrowingRunnable;
				
				public class Test {
					public void test() throws Throwable {
						ThrowingRunnable action = () -> System.out.println("test");
						action.run();
					}
				}
				""", false, null);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.function.Executable;
				
				public class Test {
					public void test() throws Throwable {
						Executable action = () -> System.out.println("test");
						action.execute();
					}
				}
				"""
		}, null);
	}

	@Test
	public void migrates_complete_eclipse_platform_example() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java",
				"""
				package test;
				import java.util.concurrent.atomic.AtomicReference;
				import org.junit.function.ThrowingRunnable;
				
				public class Test {
					private static final ThrowingRunnable NOOP_RUNNABLE = () -> {};
					
					public void test() throws Throwable {
						final AtomicReference<ThrowingRunnable> callback = new AtomicReference<>(NOOP_RUNNABLE);
						callback.get().run();
						withNatives(true, callback.get());
					}
					
					private static void withNatives(boolean natives, ThrowingRunnable runnable) throws Throwable {
						runnable.run();
					}
				}
				""", false, null);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE);

		String before = cu.getSource();
		try {
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import java.util.concurrent.atomic.AtomicReference;
				
				import org.junit.jupiter.api.function.Executable;
				
				public class Test {
					private static final Executable NOOP_RUNNABLE = () -> {};
					
					public void test() throws Throwable {
						final AtomicReference<Executable> callback = new AtomicReference<>(NOOP_RUNNABLE);
						callback.get().execute();
						withNatives(true, callback.get());
					}
					
					private static void withNatives(boolean natives, Executable runnable) throws Throwable {
						runnable.execute();
					}
				}
				"""
		}, null);
		} catch (AssertionError failure) {
			failure.addSuppressed(new AssertionError("Before cleanup:\n" + before //$NON-NLS-1$
					+ "\nAfter cleanup:\n" + cu.getSource() //$NON-NLS-1$
					+ "\nResolved classpath:\n" //$NON-NLS-1$
					+ java.util.Arrays.toString(cu.getJavaProject().getResolvedClasspath(true))));
			throw failure;
		}
	}
}
