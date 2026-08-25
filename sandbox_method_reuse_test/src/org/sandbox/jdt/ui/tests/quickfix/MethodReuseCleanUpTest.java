/*******************************************************************************
 * Copyright (c) 2025, 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.MethodReuseCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

/** Tests for local inline-sequence and coordinated method-reuse cleanup paths. */
public class MethodReuseCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();

	enum MethodReuseScenarios {
		SIMPLE_INLINE_SEQUENCE("""
			package test1;
			public class Test {
				public String formatName(String first, String last) {
					return first.trim() + " " + last.trim();
				}

				public void printUser(String firstName, String lastName) {
					String name = firstName.trim() + " " + lastName.trim();
					System.out.println(name);
				}
			}""",
			"""
			package test1;
			public class Test {
				public String formatName(String first, String last) {
					return first.trim() + " " + last.trim();
				}

				public void printUser(String firstName, String lastName) {
					String name = formatName(firstName, lastName);
					System.out.println(name);
				}
			}"""),

		INLINE_WITH_METHOD_CALLS("""
			package test1;
			public class Test {
				public String combine(String a, String b) {
					return a.toLowerCase() + b.toUpperCase();
				}

				public void process(User u) {
					String result = u.getFirst().toLowerCase() + u.getLast().toUpperCase();
					System.out.println(result);
				}

				class User {
					String getFirst() { return ""; }
					String getLast() { return ""; }
				}
			}""",
			"""
			package test1;
			public class Test {
				public String combine(String a, String b) {
					return a.toLowerCase() + b.toUpperCase();
				}

				public void process(User u) {
					String result = combine(u.getFirst(), u.getLast());
					System.out.println(result);
				}

				class User {
					String getFirst() { return ""; }
					String getLast() { return ""; }
				}
			}"""),

		MULTIPLE_VARIABLE_MAPPING("""
			package test1;
			public class Test {
				public int calculate(int x, int y) {
					int temp = x + y;
					return temp * 2;
				}

				public void compute(int a, int b) {
					int result = a + b;
					int finalValue = result * 2;
					System.out.println(finalValue);
				}
			}""",
			"""
			package test1;
			public class Test {
				public int calculate(int x, int y) {
					int temp = x + y;
					return temp * 2;
				}

				public void compute(int a, int b) {
					int finalValue = calculate(a, b);
					System.out.println(finalValue);
				}
			}""");

		String given;
		String expected;

		MethodReuseScenarios(String given, String expected) {
			this.given= given;
			this.expected= expected;
		}
	}

	@ParameterizedTest
	@EnumSource(MethodReuseScenarios.class)
	public void testMethodReuse(MethodReuseScenarios test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("Test.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.METHOD_REUSE_INLINE_SEQUENCES);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
	}

	@Test
	void delegatesExactDuplicateImplementationFromAutomaticallyExpandedScope() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		pack.createCompilationUnit("CanonicalNames.java", """ //$NON-NLS-1$
			package test1;

			public final class CanonicalNames {
				private CanonicalNames() {
				}

				public static String normalize(String value) {
					return value.trim();
				}
			}
			""", false, null);
		ICompilationUnit duplicate= pack.createCompilationUnit("Names.java", """ //$NON-NLS-1$
			package test1;

			public final class Names {
				private Names() {
				}

				public static String clean(String input) {
					return input.trim();
				}
			}
			""", false, null);
		context.enable(MYCleanUpConstants.METHOD_REUSE_CLEANUP);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { duplicate }, new String[] { """
			package test1;

			public final class Names {
				private Names() {
				}

				public static String clean(String input) {
					return CanonicalNames.normalize(input);
				}
			}
			""" }, null);
	}

	@Test
	void rejectsStateCapturingImplementations() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		pack.createCompilationUnit("CanonicalNames.java", """ //$NON-NLS-1$
			package test1;

			public final class CanonicalNames {
				private static final String SUFFIX = "!";

				public static String normalize(String value) {
					return value.trim() + SUFFIX;
				}
			}
			""", false, null);
		ICompilationUnit duplicate= pack.createCompilationUnit("Names.java", """ //$NON-NLS-1$
			package test1;

			public final class Names {
				private static final String SUFFIX = "!";

				public static String clean(String input) {
					return input.trim() + SUFFIX;
				}
			}
			""", false, null);
		context.enable(MYCleanUpConstants.METHOD_REUSE_CLEANUP);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { duplicate });
	}

	@Test
	void previewShowsTheActualDelegationShape() {
		MethodReuseCleanUpCore cleanup= new MethodReuseCleanUpCore(Map.of(
				MYCleanUpConstants.METHOD_REUSE_CLEANUP, CleanUpOptions.TRUE,
				MYCleanUpConstants.METHOD_REUSE_INLINE_SEQUENCES, CleanUpOptions.FALSE));

		assertTrue(cleanup.getPreview().contains("return CanonicalNames.normalize(input);")); //$NON-NLS-1$
	}
}
