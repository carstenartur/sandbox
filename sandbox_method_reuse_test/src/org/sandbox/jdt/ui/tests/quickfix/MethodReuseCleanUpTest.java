/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer.
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
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava11;

/**
 * Method Reuse Cleanup Tests
 * 
 * Tests for the method reusability finder cleanup.
 */
public class MethodReuseCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava11();

	enum MethodReuseScenarios {
		// TODO: Add test scenarios
		// Example test structure:
		// SIMPLE_DUPLICATE("""
		//     package test1;
		//     class Test {
		//         void method1() { int x = 0; x++; System.out.println(x); }
		//         void method2() { int y = 0; y++; System.out.println(y); }
		//     }""",
		//     """
		//     package test1;
		//     class Test {
		//         // Marker should be created for similar methods
		//         void method1() { int x = 0; x++; System.out.println(x); }
		//         void method2() { int y = 0; y++; System.out.println(y); }
		//     }""");

		String given;
		String expected;

		MethodReuseScenarios(String given, String expected) {
			this.given= given;
			this.expected= expected;
		}
	}

	@Disabled("Not yet implemented")
	@ParameterizedTest
	@EnumSource(MethodReuseScenarios.class)
	public void testMethodReuse(MethodReuseScenarios test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("Test.java", test.given, false, null);
		context.enable(MYCleanUpConstants.METHOD_REUSE_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
	}
}
