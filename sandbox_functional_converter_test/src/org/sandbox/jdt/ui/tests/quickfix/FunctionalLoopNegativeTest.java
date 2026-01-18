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
import org.junit.jupiter.api.DisplayName;
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


					public void test(List<Integer> ls) {
						for(Integer l : ls)
						{
							if(l!=null)
							{
								break;
							}
						}
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


					public void test(List<Integer> ls) throws Exception {
						for(Integer l : ls)
						{
							throw new Exception();
						}
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
						return false;
					}
					}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
}

/**
 * Tests that loops with external variable modification AND side-effects ARE converted.
 * 
 * <p>
 * When a loop has both side-effects (like println) and a reducer pattern (like count++),
 * the conversion preserves both behaviors by using map operations for side-effects
 * followed by the reduce operation.
 * </p>
 */
@Test
void test_External_variable_modification_with_side_effects_converts() throws CoreException {
String input = """
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

String expected = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processWithExternalModification(List<String> items) {
					int count = 0;
					count = items.stream().map(item -> {
						System.out.println(item);
						return item;
					}).map(item -> 1).reduce(count, Integer::sum);
					System.out.println(count);
				}
					}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
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

/**
 * Tests that loops with multiple different return values should NOT convert.
 * 
 * <p>
 * When different branches return different values (not just the loop variable),
 * the pattern cannot be safely converted to a stream operation because the
 * transformations differ between branches.
 * </p>
 */
@Test
@DisplayName("Loop returning different values in different branches - should NOT convert")
void test_MultipleDifferentReturnValues_ShouldNotConvert() throws CoreException {
String sourceCode = """
			package test1;
			import java.util.List;
			class MyTest {
				public String find(List<String> items) {
					for (String item : items) {
						if (item.startsWith("A")) {
							return item;
						}
						if (item.startsWith("B")) {
							return item.toUpperCase();
						}
					}
					return null;
				}
			}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
}

/**
 * Tests that loops modifying external variable (not accumulator pattern) should NOT convert.
 * 
 * <p>
 * When a loop assigns to an external variable without using the previous value
 * (not an accumulator pattern like count++), this is a side effect that changes
 * semantic behavior and should not be converted.
 * </p>
 */
@Test
@DisplayName("Loop modifying external variable (not accumulator pattern) - should NOT convert")
void test_ExternalVariableModification_ShouldNotConvert() throws CoreException {
String sourceCode = """
			package test1;
			import java.util.List;
			class MyTest {
				public void process(List<String> items) {
					String lastItem = null;
					for (String item : items) {
						lastItem = item;  // Assignment, not accumulation
						System.out.println(item);
					}
					System.out.println("Last: " + lastItem);
				}
			}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
}

/**
 * Tests that loops with both break and continue should NOT convert.
 * 
 * <p>
 * When a loop contains both break and continue statements, the control flow
 * is complex and cannot be safely converted to stream operations.
 * </p>
 */
@Test
@DisplayName("Loop with both break and continue - should NOT convert")
void test_BreakAndContinue_ShouldNotConvert() throws CoreException {
String sourceCode = """
			package test1;
			import java.util.List;
			class MyTest {
				public void process(List<String> items) {
					for (String item : items) {
						if (item == null) {
							continue;
						}
						if (item.isEmpty()) {
							break;
						}
						System.out.println(item);
					}
				}
			}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
}

}
