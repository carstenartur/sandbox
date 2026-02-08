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

import java.util.Hashtable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.eclipse.jdt.testplugin.TestOptions;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for shift out of range cleanup.
 */
public class ShiftOutOfRangeCleanUpTest {

	@BeforeEach
	protected void setUp() throws Exception {
		Hashtable<String, String> defaultOptions = TestOptions.getDefaultOptions();
		defaultOptions.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, Integer.toString(120));
		JavaCore.setOptions(defaultOptions);
		TestOptions.initializeCodeGenerationOptions();
	}

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	enum ShiftOutOfRangePatterns {
		INT_LEFT_SHIFT_32("""
				package test1;

				public class E1 {
				    public void method() {
				        int x = 1;
				        int result = x << 32;
				    }
				}
				""",

				"""
				package test1;

				public class E1 {
				    public void method() {
				        int x = 1;
				        int result = x << 0;
				    }
				}
				"""),

		INT_RIGHT_SHIFT_33("""
				package test1;

				public class E1 {
				    public void method() {
				        int x = 1024;
				        int result = x >> 33;
				    }
				}
				""",

				"""
				package test1;

				public class E1 {
				    public void method() {
				        int x = 1024;
				        int result = x >> 1;
				    }
				}
				"""),

		INT_UNSIGNED_RIGHT_SHIFT_64("""
				package test1;

				public class E1 {
				    public void method() {
				        int x = -1;
				        int result = x >>> 64;
				    }
				}
				""",

				"""
				package test1;

				public class E1 {
				    public void method() {
				        int x = -1;
				        int result = x >>> 0;
				    }
				}
				"""),

		LONG_LEFT_SHIFT_64("""
				package test1;

				public class E1 {
				    public void method() {
				        long x = 1L;
				        long result = x << 64;
				    }
				}
				""",

				"""
				package test1;

				public class E1 {
				    public void method() {
				        long x = 1L;
				        long result = x << 0;
				    }
				}
				"""),

		LONG_RIGHT_SHIFT_65("""
				package test1;

				public class E1 {
				    public void method() {
				        long x = 1024L;
				        long result = x >> 65;
				    }
				}
				""",

				"""
				package test1;

				public class E1 {
				    public void method() {
				        long x = 1024L;
				        long result = x >> 1;
				    }
				}
				"""),

		INT_NEGATIVE_SHIFT("""
				package test1;

				public class E1 {
				    public void method() {
				        int x = 1;
				        int result = x << -1;
				    }
				}
				""",

				"""
				package test1;

				public class E1 {
				    public void method() {
				        int x = 1;
				        int result = x << 31;
				    }
				}
				""");

		String given;
		String expected;

		ShiftOutOfRangePatterns(String given, String expected) {
			this.given = given;
			this.expected = expected;
		}
	}

	@ParameterizedTest
	@EnumSource(ShiftOutOfRangePatterns.class)
	public void testShiftOutOfRangeParametrized(ShiftOutOfRangePatterns test) throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.SHIFT_OUT_OF_RANGE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
	}

	@Test
	public void testShiftInRangeNoChange() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;

				public class E1 {
				    public void method() {
				        int x = 1;
				        int a = x << 0;
				        int b = x << 16;
				        int c = x << 31;
				        int d = x >> 0;
				        int e = x >> 31;
				        int f = x >>> 0;
				        int g = x >>> 31;
				        long y = 1L;
				        long h = y << 0;
				        long i = y << 32;
				        long j = y << 63;
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.SHIFT_OUT_OF_RANGE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testShiftDisabled() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;

				public class E1 {
				    public void method() {
				        int x = 1;
				        int result = x << 32;
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.disable(MYCleanUpConstants.SHIFT_OUT_OF_RANGE_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
