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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

/**
 * Method Reuse Cleanup Tests
 * 
 * Tests for the method reusability finder cleanup.
 */
public class MethodReuseCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();

	enum MethodReuseScenarios {
		// Test case 1: Simple inline sequence with different variable names
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
		
		// Test case 2: Inline sequence with method calls as expressions
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
		
		// Test case 3: Multiple variable mapping
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
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("Test.java", test.given, false, null);
		context.enable(MYCleanUpConstants.METHOD_REUSE_INLINE_SEQUENCES);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
	}
}
