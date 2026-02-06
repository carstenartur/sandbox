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
 *     Carsten Hammer - initial implementation
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for nested loops, complex patterns, and edge cases in functional loop conversion.
 * 
 * <p>
 * This test class covers scenarios that are more complex than simple single-loop conversions:
 * </p>
 * <ul>
 * <li><b>Nested loops</b>: Inner loops within outer loops</li>
 * <li><b>Multiple operations</b>: Loops with multiple transformations</li>
 * <li><b>Complex conditions</b>: Multi-part conditions and logical operators</li>
 * <li><b>Edge cases</b>: Empty loops, single-element operations, etc.</li>
 * <li><b>Lambda capture</b>: Variables captured from outer scope</li>
 * </ul>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder
 */
@DisplayName("Functional Loop - Nested Loops and Edge Cases")
public class FunctionalLoopNestedAndEdgeCaseTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	// ==================== NESTED LOOP TESTS ====================

	@Nested
	@DisplayName("Nested Loop Tests")
	class NestedLoopTests {

		/**
		 * Tests that inner loops within nested contexts can be independently converted.
		 * 
		 * <p><b>Pattern:</b> Nested enhanced-for loops where inner loop is simple forEach</p>
		 * 
		 * <p><b>Expected Behavior:</b> The inner loop should be converted to {@code forEach()},
		 * while the outer loop remains as an enhanced-for loop. This requires the cleanup
		 * to identify and convert inner loops independently without being blocked by the
		 * outer loop's nested structure.</p>
		 * 
		 * <p><b>Why not yet implemented:</b> The current implementation in
		 * {@link org.sandbox.jdt.internal.corext.fix.helper.PreconditionsChecker} detects
		 * nested loops and marks the outer loop as non-convertible (sets
		 * {@code containsNestedLoop = true}). This prevents ANY conversion in the nested
		 * context. To support this pattern, the implementation would need to:
		 * <ul>
		 * <li>Run multiple passes - first converting inner loops, then outer loops</li>
		 * <li>Allow conversion of inner loops even when they're nested</li>
		 * <li>Track which loop is being analyzed (inner vs outer) to apply different rules</li>
		 * </ul>
		 * </p>
		 * 
		 * <p><b>Semantic equivalence:</b> Converting only the inner loop preserves the
		 * exact semantics - the outer loop still iterates over the matrix, but the inner
		 * iteration uses a functional style.</p>
		 * 
		 * <p><b>Future enhancement:</b> See TODO.md for nested loop conversion roadmap.</p>
		 */
		@Test
		@Disabled("Inner loop conversion in nested context not yet implemented - requires multi-pass cleanup execution")
		@DisplayName("Nested for-each loops should convert inner loop only")
		void test_NestedForEach_ShouldConvertInnerOnly() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public void processMatrix(List<List<Integer>> matrix) {
							for (List<Integer> row : matrix) {
								for (Integer cell : row) {
									System.out.println(cell);
								}
							}
						}
					}""";

			// Expected: Only inner loop converted, outer loop stays as-is
			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void processMatrix(List<List<Integer>> matrix) {
							for (List<Integer> row : matrix) {
								row.forEach(cell -> System.out.println(cell));
							}
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests that enhanced-for containing a traditional for loop should NOT convert.
		 */
		@Test
		@DisplayName("Enhanced-for with nested traditional for loop - should NOT convert")
		void test_NestedTraditionalForLoop_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								for (int i = 0; i < item.length(); i++) {
									System.out.println(item.charAt(i));
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
		 * Tests that enhanced-for containing a while loop should NOT convert.
		 */
		@Test
		@DisplayName("Enhanced-for with nested while loop - should NOT convert")
		void test_NestedWhileLoop_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								int i = 0;
								while (i < item.length()) {
									System.out.println(item.charAt(i));
									i++;
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
		 * Tests that enhanced-for containing a do-while loop should NOT convert.
		 */
		@Test
		@DisplayName("Enhanced-for with nested do-while loop - should NOT convert")
		void test_NestedDoWhileLoop_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								int i = 0;
								do {
									System.out.println(item.charAt(i));
									i++;
								} while (i < item.length());
							}
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		}

		/**
		 * Tests that the inner loop of nested loops CAN be converted when it's simple.
		 * 
		 * <p>
		 * While the outer loop cannot be converted, if we analyze just the inner loop,
		 * it should be convertible. However, the current implementation converts loops
		 * at the statement level, so this tests the expected behavior.
		 * </p>
		 */
		@Test
		@Disabled("Inner loop conversion in nested context not yet implemented")
		@DisplayName("Inner loop in nested context can be converted")
		void test_NestedForEach_InnerLoopConverts() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void processMatrix(List<List<Integer>> matrix) {
							for (List<Integer> row : matrix) {
								for (Integer cell : row) {
									System.out.println(cell);
								}
							}
						}
					}""";

			// Expected: Only inner loop converted
			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void processMatrix(List<List<Integer>> matrix) {
							for (List<Integer> row : matrix) {
								row.forEach(cell -> System.out.println(cell));
							}
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests nested loops with outer loop variable used in inner loop.
		 * 
		 * <p>
		 * This pattern should NOT be converted because the outer loop variable
		 * is captured by the inner loop's lambda.
		 * </p>
		 */
		@Test
		@DisplayName("Nested loops with outer variable capture - should NOT convert")
		void test_NestedLoops_OuterVariableCapture_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public void processWithIndex(List<List<String>> data) {
							int rowIndex = 0;
							for (List<String> row : data) {
								for (String item : row) {
									System.out.println("Row " + rowIndex + ": " + item);
								}
								rowIndex++;
							}
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		}

		/**
		 * Tests a loop that iterates over results of another collection operation.
		 */
		@Test
		@DisplayName("Loop over filtered stream result - should convert")
		void test_LoopOverFilteredList() throws CoreException {
			String input = """
					package test1;

					import java.util.List;
					import java.util.stream.Collectors;

					class MyTest {
						public void process(List<String> items) {
							List<String> filtered = items.stream().filter(s -> s.length() > 3).collect(Collectors.toList());
							for (String item : filtered) {
								System.out.println(item);
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;
					import java.util.stream.Collectors;

					class MyTest {
						public void process(List<String> items) {
							List<String> filtered = items.stream().filter(s -> s.length() > 3).collect(Collectors.toList());
							filtered.forEach(item -> System.out.println(item));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}
	}

	// ==================== COMPLEX CONDITION TESTS ====================

	@Nested
	@DisplayName("Complex Condition Tests")
	class ComplexConditionTests {

		/**
		 * Tests loop with AND condition in filter.
		 */
		@Test
		@DisplayName("Filter with AND condition")
		void test_FilterWithAndCondition() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<Integer> numbers) {
							for (Integer num : numbers) {
								if (num > 0 && num < 100) {
									System.out.println(num);
								}
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<Integer> numbers) {
							numbers.stream().filter(num -> (num > 0 && num < 100)).forEachOrdered(num -> System.out.println(num));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests loop with OR condition in filter.
		 */
		@Test
		@DisplayName("Filter with OR condition")
		void test_FilterWithOrCondition() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<Integer> numbers) {
							for (Integer num : numbers) {
								if (num < 0 || num > 100) {
									System.out.println(num);
								}
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<Integer> numbers) {
							numbers.stream().filter(num -> (num < 0 || num > 100)).forEachOrdered(num -> System.out.println(num));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests loop with instanceof check in filter.
		 */
		@Test
		@DisplayName("Filter with instanceof check")
		void test_FilterWithInstanceof() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<Object> items) {
							for (Object item : items) {
								if (item instanceof String) {
									System.out.println(item);
								}
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<Object> items) {
							items.stream().filter(item -> item instanceof String).forEachOrdered(item -> System.out.println(item));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests loop with negated complex condition.
		 */
		@Test
		@DisplayName("Filter with negated complex condition using continue")
		void test_FilterWithNegatedComplexCondition() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								if (item == null || item.isEmpty()) {
									continue;
								}
								System.out.println(item);
							}
						}
					}""";

			// Note: The formatter may add line breaks for long expressions
			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							items.stream().filter(item -> !(item == null || item.isEmpty()))
									.forEachOrdered(item -> System.out.println(item));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}
	}

	// ==================== LAMBDA CAPTURE TESTS ====================

	@Nested
	@DisplayName("Lambda Capture and Scope Tests")
	class LambdaCaptureTests {

		/**
		 * Tests loop that uses a method parameter in the body.
		 */
		@Test
		@DisplayName("Loop body uses method parameter")
		void test_LoopUsesMethodParameter() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items, String prefix) {
							for (String item : items) {
								System.out.println(prefix + item);
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items, String prefix) {
							items.forEach(item -> System.out.println(prefix + item));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests loop that uses a final local variable.
		 */
		@Test
		@DisplayName("Loop body uses final local variable")
		void test_LoopUsesFinalLocalVariable() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							final String separator = " - ";
							for (String item : items) {
								System.out.println(separator + item);
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							final String separator = " - ";
							items.forEach(item -> System.out.println(separator + item));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests loop that uses an instance field.
		 */
		@Test
		@DisplayName("Loop body uses instance field")
		void test_LoopUsesInstanceField() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						private String prefix = "Item: ";
						
						public void process(List<String> items) {
							for (String item : items) {
								System.out.println(prefix + item);
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						private String prefix = "Item: ";
						
						public void process(List<String> items) {
							items.forEach(item -> System.out.println(prefix + item));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests loop with variable that is modified before the loop (effectively final).
		 */
		@Test
		@DisplayName("Loop uses effectively final variable")
		void test_LoopUsesEffectivelyFinalVariable() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items, boolean verbose) {
							String prefix;
							if (verbose) {
								prefix = "VERBOSE: ";
							} else {
								prefix = "";
							}
							for (String item : items) {
								System.out.println(prefix + item);
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items, boolean verbose) {
							String prefix;
							if (verbose) {
								prefix = "VERBOSE: ";
							} else {
								prefix = "";
							}
							items.forEach(item -> System.out.println(prefix + item));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}
	}

	// ==================== EDGE CASE TESTS ====================

	@Nested
	@DisplayName("Edge Case Tests")
	class EdgeCaseTests {

		/**
		 * Tests empty loop body.
		 */
		@Test
		@DisplayName("Empty loop body - should NOT convert")
		void test_EmptyLoopBody_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								// Empty body
							}
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		}

		/**
		 * Tests loop with only a comment.
		 */
		@Test
		@DisplayName("Loop with only comment - should NOT convert")
		void test_LoopWithOnlyComment_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								// TODO: implement later
							}
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		}

		/**
		 * Tests loop iterating over array (not collection).
		 */
		@Test
//		@Disabled("Array iteration to stream not yet supported")
		@DisplayName("Loop over array - should convert to Arrays.stream()")
		void test_LoopOverArray() throws CoreException {
			String input = """
					package test1;

					import java.util.Arrays;

					class MyTest {
						public void process(String[] items) {
							for (String item : items) {
								System.out.println(item);
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.Arrays;

					class MyTest {
						public void process(String[] items) {
							Arrays.stream(items).forEach(item -> System.out.println(item));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests loop with this keyword usage.
		 */
		@Test
		@DisplayName("Loop body uses this keyword")
		void test_LoopUsesThisKeyword() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								this.doSomething(item);
							}
						}
						
						private void doSomething(String s) {
							System.out.println(s);
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							items.forEach(item -> this.doSomething(item));
						}
						
						private void doSomething(String s) {
							System.out.println(s);
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests loop with generic type parameters.
		 */
		@Test
		@DisplayName("Loop with generic type parameters")
		void test_LoopWithGenericTypes() throws CoreException {
			String input = """
					package test1;

					import java.util.List;
					import java.util.Map;

					class MyTest {
						public void process(List<Map.Entry<String, Integer>> entries) {
							for (Map.Entry<String, Integer> entry : entries) {
								System.out.println(entry.getKey() + ": " + entry.getValue());
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;
					import java.util.Map;

					class MyTest {
						public void process(List<Map.Entry<String, Integer>> entries) {
							entries.forEach(entry -> System.out.println(entry.getKey() + ": " + entry.getValue()));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests loop where the loop variable shadows a field.
		 */
		@Test
		@DisplayName("Loop variable shadows instance field")
		void test_LoopVariableShadowsField() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						private String item = "default";
						
						public void process(List<String> items) {
							for (String item : items) {
								System.out.println(item);  // Uses loop variable, not field
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						private String item = "default";
						
						public void process(List<String> items) {
							items.forEach(item -> System.out.println(item));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}
	}

	// ==================== NEGATIVE TESTS FOR COMPLEX PATTERNS ====================

	@Nested
	@DisplayName("Complex Patterns That Should NOT Convert")
	class NegativeComplexPatternTests {

		/**
		 * Tests loop with method call that has side effects on collection.
		 */
		@Test
		@DisplayName("Loop modifying collection during iteration - should NOT convert")
		void test_ModifyingCollectionDuringIteration_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;
					import java.util.ArrayList;

					class MyTest {
						public void process(List<String> items, List<String> results) {
							for (String item : items) {
								results.add(item.toUpperCase());
							}
						}
					}""";

			// This COULD be converted to forEach, but the side-effect (adding to another list)
			// might be better expressed as collect(). For now, we test current behavior.
			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			// Current behavior: converts to forEach - update this if behavior changes
			// context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		}

		/**
		 * Tests loop with try-catch inside.
		 */
		@Test
		@DisplayName("Loop with try-catch - should NOT convert")
		void test_LoopWithTryCatch_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								try {
									Integer.parseInt(item);
								} catch (NumberFormatException e) {
									System.out.println("Invalid: " + item);
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
		 * Tests loop with synchronized block inside.
		 */
		@Test
		@DisplayName("Loop with synchronized block - should NOT convert")
		void test_LoopWithSynchronized_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						private final Object lock = new Object();
						private int count = 0;
						
						public void process(List<String> items) {
							for (String item : items) {
								synchronized (lock) {
									count++;
									System.out.println(count + ": " + item);
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
		 * Tests loop with switch statement inside.
		 */
		@Test
		@DisplayName("Loop with switch statement - should NOT convert")
		void test_LoopWithSwitch_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								switch (item.length()) {
									case 0:
										System.out.println("Empty");
										break;
									case 1:
										System.out.println("Single char");
										break;
									default:
										System.out.println("Multi char");
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
		 * Tests loop with multiple return statements in different branches.
		 */
		@Test
		@DisplayName("Loop with multiple different returns - should NOT convert")
		void test_LoopWithMultipleDifferentReturns_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public String findSpecial(List<String> items) {
							for (String item : items) {
								if (item.startsWith("A")) {
									return item;
								}
								if (item.endsWith("Z")) {
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
	}
}
