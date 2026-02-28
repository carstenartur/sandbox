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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for wrong string comparison cleanup.
 */
public class WrongStringComparisonCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	enum WrongStringComparisonPatterns {
		EQUALS_LITERAL_ON_RIGHT("""
				package test1;

				public class E1 {
				    public void method(String str) {
				        boolean b = str == "hello";
				    }
				}
				""",

				"""
				package test1;

				public class E1 {
				    public void method(String str) {
				        boolean b = "hello".equals(str);
				    }
				}
				"""),

		EQUALS_LITERAL_ON_LEFT("""
				package test1;

				public class E1 {
				    public void method(String str) {
				        boolean b = "hello" == str;
				    }
				}
				""",

				"""
				package test1;

				public class E1 {
				    public void method(String str) {
				        boolean b = "hello".equals(str);
				    }
				}
				"""),

		NOT_EQUALS_LITERAL_ON_RIGHT("""
				package test1;

				public class E1 {
				    public void method(String str) {
				        boolean b = str != "hello";
				    }
				}
				""",

				"""
				package test1;

				public class E1 {
				    public void method(String str) {
				        boolean b = !("hello".equals(str));
				    }
				}
				"""),

		NOT_EQUALS_LITERAL_ON_LEFT("""
				package test1;

				public class E1 {
				    public void method(String str) {
				        boolean b = "hello" != str;
				    }
				}
				""",

				"""
				package test1;

				public class E1 {
				    public void method(String str) {
				        boolean b = !("hello".equals(str));
				    }
				}
				""");

		String given;
		String expected;

		WrongStringComparisonPatterns(String given, String expected) {
			this.given = given;
			this.expected = expected;
		}
	}

	@ParameterizedTest
	@EnumSource(WrongStringComparisonPatterns.class)
	public void testWrongStringComparisonParameterized(WrongStringComparisonPatterns test) throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", test.given, false, null);
		context.enable(MYCleanUpConstants.WRONG_STRING_COMPARISON_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
	}

	@Test
	public void testProperEqualsNoChange() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;

				public class E1 {
				    public void method(String str) {
				        boolean a = "hello".equals(str);
				        boolean b = str.equals("world");
				        boolean c = str == null;
				        boolean d = null == str;
				        boolean e = str != null;
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.enable(MYCleanUpConstants.WRONG_STRING_COMPARISON_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testDisabled() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		String given = """
				package test1;

				public class E1 {
				    public void method(String str) {
				        boolean b = str == "hello";
				    }
				}
				""";
		ICompilationUnit cu = pack.createCompilationUnit("E1.java", given, false, null);
		context.disable(MYCleanUpConstants.WRONG_STRING_COMPARISON_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
