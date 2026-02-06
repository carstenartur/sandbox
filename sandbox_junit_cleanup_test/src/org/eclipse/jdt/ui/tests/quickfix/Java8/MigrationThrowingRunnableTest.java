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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/**
 * Tests for migrating JUnit 4 ThrowingRunnable to JUnit 5 Executable.
 * Covers ThrowingRunnable → Executable transformations and .run() → .execute() method calls.
 */
public class MigrationThrowingRunnableTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
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

	@Disabled("Currently fails due to missing support for type parameter references in the plugin")
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

	@Disabled("Currently fails due to missing support for type parameter references in the plugin")
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
					}
					
					private static void withNatives(boolean natives, ThrowingRunnable runnable) throws Throwable {
						runnable.run();
					}
				}
				""", false, null);

		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_THROWINGRUNNABLE);

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
					}
					
					private static void withNatives(boolean natives, Executable runnable) throws Throwable {
						runnable.execute();
					}
				}
				"""
		}, null);
	}
}
