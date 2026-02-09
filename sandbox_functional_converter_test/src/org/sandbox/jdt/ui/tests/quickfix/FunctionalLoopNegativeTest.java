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
 * Tests that loops with break statements are NOT converted.
 * 
 * <p><b>Pattern:</b> Enhanced for-loop with conditional break statement</p>
 * 
 * <p><b>Why not convertible:</b> Break statements have no direct equivalent in
 * the Stream API. While {@code findFirst()} or {@code anyMatch()} can short-circuit
 * stream processing, they cannot replicate the exact semantics of a break statement
 * that occurs after side effects. Converting this pattern could change behavior if
 * there are side effects before the break.</p>
 * 
 * <p><b>Example that shows the problem:</b></p>
 * <pre>{@code
 * for (Integer item : list) {
 *     System.out.println(item);  // Side effect
 *     if (item > 5) break;       // Break after side effect
 * }
 * }</pre>
 * <p>Cannot be safely converted because the side effect (println) happens before
 * the break condition is checked. Stream operations like {@code takeWhile()} or
 * {@code findFirst()} cannot replicate this order.</p>
 * 
 * <p><b>Semantic equivalence:</b> N/A - no conversion should occur. The loop
 * remains unchanged to preserve its exact control flow semantics.</p>
 */
@Test
void test_Break_Statement_ShouldNotConvert() throws CoreException {
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
 * Tests that loops with throw statements are NOT converted.
 * 
 * <p><b>Pattern:</b> Enhanced for-loop that unconditionally throws an exception</p>
 * 
 * <p><b>Why not convertible:</b> While lambda expressions can throw checked and
 * unchecked exceptions, converting a loop that throws an exception to a stream
 * would change the exception handling context. Additionally, {@code forEach()}
 * and other terminal operations don't declare throws clauses, so checked exceptions
 * would need to be wrapped. This changes the exception semantics and makes the
 * code less readable.</p>
 * 
 * <p><b>Example:</b></p>
 * <pre>{@code
 * for (Integer item : list) {
 *     throw new Exception();  // Unconditional throw
 * }
 * }</pre>
 * <p>Converting this to {@code list.forEach(item -> throw new Exception())} is
 * invalid syntax, and wrapping it in a try-catch changes the exception handling
 * semantics.</p>
 * 
 * <p><b>Semantic equivalence:</b> N/A - no conversion should occur. Exception
 * handling semantics must be preserved exactly as written.</p>
 */
@Test
void test_Throw_Statement_ShouldNotConvert() throws CoreException {
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
 * Tests that loops with labeled continue statements are NOT converted.
 * 
 * <p><b>Pattern:</b> Enhanced for-loop with a labeled continue statement that
 * references an outer label</p>
 * 
 * <p><b>Why not convertible:</b> Labeled continue statements allow control flow
 * to jump to an outer loop iteration, which has no equivalent in the Stream API.
 * The Stream API is designed for simple, linear transformations and cannot
 * replicate complex control flow patterns involving multiple loop levels.</p>
 * 
 * <p><b>Example:</b></p>
 * <pre>{@code
 * label:
 * for (Integer item : list) {
 *     if (item == null) {
 *         continue label;  // Jump to next iteration of labeled loop
 *     }
 *     // ... more processing
 * }
 * }</pre>
 * <p>This control flow pattern cannot be expressed using stream operations. While
 * a simple {@code continue} can be converted to a filter condition, labeled continues
 * that affect outer loop control flow have no stream equivalent.</p>
 * 
 * <p><b>Semantic equivalence:</b> N/A - no conversion should occur. Labeled control
 * flow must be preserved exactly as written.</p>
 */
@Test
void test_Labeled_Continue_ShouldNotConvert() throws CoreException {
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
 * Tests that loops with external variable modification AND side-effects are NOT converted.
 * 
 * <p>
 * V2 cannot safely convert this complex pattern: mixed side-effects with
 * plain assignment accumulation ({@code count = count + 1} is NOT a compound
 * assignment pattern like {@code count += 1}). The loop is left unchanged.
 * </p>
 */
@Test
void test_External_variable_modification_with_side_effects_not_converted() throws CoreException {
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
 * Tests that loops with early return statements that have side effects are not converted.
 * 
 * <p>
 * Verifies the cleanup correctly identifies patterns that cannot be safely converted.
 * </p>
 */
@Test
void test_EarlyReturn_WithSideEffects_ShouldNotConvert() throws CoreException {
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
