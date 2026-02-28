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
 *
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java22;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for hint-only Phase 3 cleanups (PrintStackTrace, SystemOut,
 * ObsoleteCollection, MissingHashCode, OverridableCallInConstructor).
 *
 * <p>These cleanups detect issues but do not modify code, so we verify
 * that enabling them does not cause unintended code changes.</p>
 */
public class HintOnlyCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	@Test
	public void testPrintStackTraceNoCodeChange() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;

				public class E1 {
				    public void method() {
				        try {
				            int x = 1 / 0;
				        } catch (Exception ex) {
				            ex.printStackTrace();
				        }
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.PRINT_STACKTRACE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testSystemOutNoCodeChange() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;

				public class E1 {
				    public void method() {
				        System.out.println("hello");
				        System.err.println("error");
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.SYSTEM_OUT_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testObsoleteCollectionNoCodeChange() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;

				import java.util.Vector;

				public class E1 {
				    public void method() {
				        Vector<String> v = new Vector<>();
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.OBSOLETE_COLLECTION_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testMissingHashCodeNoCodeChange() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;

				public class E1 {
				    @Override
				    public boolean equals(Object obj) {
				        return this == obj;
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.MISSING_HASHCODE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testOverridableCallInConstructorNoCodeChange() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;

				public class E1 {
				    public E1() {
				        init();
				    }

				    public void init() {
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.OVERRIDABLE_IN_CONSTRUCTOR_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testDisabledNoChange() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;

				public class E1 {
				    public void method() {
				        try {
				            int x = 1 / 0;
				        } catch (Exception ex) {
				            ex.printStackTrace();
				        }
				        System.out.println("hello");
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.disable(MYCleanUpConstants.PRINT_STACKTRACE_CLEANUP);
		context.disable(MYCleanUpConstants.SYSTEM_OUT_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
