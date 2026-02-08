/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for threading cleanup using TriggerPattern hints.
 *
 * <p>These tests verify that the cleanup correctly transforms threading
 * anti-patterns by comparing input (given) code with expected output code.</p>
 *
 * <p>Inspired by NetBeans' Tiny.java threading hints.</p>
 *
 * @since 1.2.5
 */
public class ThreadingCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	// ========== Thread.run() → Thread.start() Tests ==========

	/**
	 * Tests transformation of {@code thread.run()} to {@code thread.start()}.
	 */
	@Test
	public void testThreadRunToStart() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method() {
			        Thread thread = new Thread(() -> System.out.println("Hello"));
			        thread.run();
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method() {
			        Thread thread = new Thread(() -> System.out.println("Hello"));
			        thread.start();
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_THREADING_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests transformation of {@code myThread.run()} to {@code myThread.start()}
	 * when the thread is a field.
	 */
	@Test
	public void testThreadRunToStartOnField() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    private Thread myThread = new Thread();

			    void method() {
			        myThread.run();
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    private Thread myThread = new Thread();

			    void method() {
			        myThread.start();
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_THREADING_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that code without Thread.run() is not modified.
	 */
	@Test
	public void testNoChangeWhenAlreadyCorrect() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method() {
			        Thread thread = new Thread(() -> System.out.println("Hello"));
			        thread.start();
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_THREADING_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that cleanup is disabled when option is not enabled.
	 */
	@Test
	public void testNoChangeWhenDisabled() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method() {
			        Thread thread = new Thread(() -> System.out.println("Hello"));
			        thread.run();
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		// Note: NOT enabling the cleanup option
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests transformation with inline Thread creation: {@code new Thread(r).run()} → {@code new Thread(r).start()}.
	 */
	@Test
	public void testInlineThreadCreationRunToStart() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
			package test1;

			public class E1 {
			    void method() {
			        new Thread(() -> System.out.println("Hello")).run();
			    }
			}
			""";

		String expected = """
			package test1;

			public class E1 {
			    void method() {
			        new Thread(() -> System.out.println("Hello")).start();
			    }
			}
			""";

		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.TRIGGERPATTERN_THREADING_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
