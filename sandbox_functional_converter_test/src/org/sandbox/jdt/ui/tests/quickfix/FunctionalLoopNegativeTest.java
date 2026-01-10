/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer and others.
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
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Negative tests for functional loop conversion.
 * 
 * <p>
 * This test class contains test cases that should NOT be converted to
 * functional streams. These tests verify that the cleanup correctly identifies
 * patterns that cannot be safely converted due to:
 * </p>
 * <ul>
 * <li>Break statements</li>
 * <li>Throw statements</li>
 * <li>Labeled continue statements</li>
 * <li>External variable modifications</li>
 * <li>Early returns with side effects (non-pattern)</li>
 * <li>Other unsafe transformations</li>
 * </ul>
 * 
 * <p>
 * Each test verifies that the source code remains unchanged after applying the
 * cleanup.
 * </p>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.PreconditionsChecker
 */
public class FunctionalLoopNegativeTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

/**
 * Tests that loops with Break statement (should NOT convert) are not converted.
 * 
 * <p>
 * Verifies the cleanup correctly identifies patterns that cannot be safely converted.
 * </p>
 */
@Test
void test_Break_statement_should_NOT_convert() throws CoreException {
String sourceCode = """
	package test1;

	import java.util.Arrays;
	import java.util.List;

	class MyTest {

		public static void main(String[] args) {
			new MyTest().test(Arrays.asList(1, 2, 3,7));
		}


		public Boolean test(List<Integer> ls) {
			Integer i=0;
			for(Integer l : ls)
			{
				if(l!=null)
				{
					break;
				}

			}
			System.out.println(i);
			return true;


		}
		private void foo(Object o, int i)
		{

		}
	}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
}

/**
 * Tests that loops with Throw statement (should NOT convert) are not converted.
 * 
 * <p>
 * Verifies the cleanup correctly identifies patterns that cannot be safely converted.
 * </p>
 */
@Test
void test_Throw_statement_should_NOT_convert() throws CoreException {
String sourceCode = """
	package test1;

	import java.util.Arrays;
	import java.util.List;

	class MyTest {

		public static void main(String[] args) throws Exception {
			new MyTest().test(Arrays.asList(1, 2, 3,7));
		}


		public Boolean test(List<Integer> ls) throws Exception {
			Integer i=0;

			for(Integer l : ls)
			{
				throw new Exception();

			}
			System.out.println(i);
			return false;


		}
		private void foo(Object o, int i) throws Exception
		{

		}
	}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
}

/**
 * Tests that loops with Labeled continue (should NOT convert) are not converted.
 * 
 * <p>
 * Verifies the cleanup correctly identifies patterns that cannot be safely converted.
 * </p>
 */
@Test
void test_Labeled_continue_should_NOT_convert() throws CoreException {
String sourceCode = """
	package test1;

	import java.util.Arrays;
	import java.util.List;

	class MyTest {

		public static void main(String[] args) {
			new MyTest().test(Arrays.asList(1, 2, 3,7));
		}


		public Boolean test(List<Integer> ls) {
			Integer i=0;
			label:
			for(Integer l : ls)
			{
				if(l==null)
				{
					continue label;
				}
				if(l.toString()==null)
					return true;

			}
			System.out.println(i);
			return false;


		}
		private void foo(Object o, int i)
		{

		}
	}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
}

/**
 * Tests that loops with External variable modification (should NOT convert) are not converted.
 * 
 * <p>
 * Verifies the cleanup correctly identifies patterns that cannot be safely converted.
 * </p>
 */
@Test
void test_External_variable_modification_should_NOT_convert() throws CoreException {
String sourceCode = """
	package test1;

	import java.util.List;

	class MyTest {
		public void processWithExternalModification(List<String> items) {
			int count = 0;
			for (String item : items) {
				System.out.println(item);
				count = count + 1;  // Assignment to external variable
			}
			System.out.println(count);
		}
	}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
}

/**
 * Tests that loops with Early return with side effects (should NOT convert) are not converted.
 * 
 * <p>
 * Verifies the cleanup correctly identifies patterns that cannot be safely converted.
 * </p>
 */
@Test
void test_Early_return_with_side_effects_should_NOT_convert() throws CoreException {
String sourceCode = """
	package test1;
	import java.util.List;
	class MyTest {
		public void test(List<Integer> ls) throws Exception {
			for(Integer l : ls) {
				return ;
			}
		}
	}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
}

}
