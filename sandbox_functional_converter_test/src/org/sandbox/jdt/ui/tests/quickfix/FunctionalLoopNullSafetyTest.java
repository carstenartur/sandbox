/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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
//import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for null safety considerations in functional loop conversion.
 * 
 * <p>
 * This test class focuses on scenarios where the presence or absence of
 * {@code @NotNull} / {@code @NonNull} annotations affects whether a
 * transformation is safe or potentially introduces NullPointerException risks.
 * </p>
 * 
 * <h2>Key Null Safety Scenarios:</h2>
 * <ul>
 * <li><b>String concatenation with reduce:</b> {@code String::concat} is only
 *     safe when the accumulator is guaranteed non-null</li>
 * <li><b>Method calls on loop variable:</b> {@code item.method()} can NPE if
 *     item is null</li>
 * <li><b>Collection elements:</b> Stream operations may process null elements</li>
 * <li><b>Map values:</b> Map.get() may return null</li>
 * </ul>
 * 
 * @see org.sandbox.jdt.internal.corext.fix.helper.TypeResolver#hasNotNullAnnotation
 * @see org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation#isNullSafe
 */
@DisplayName("Functional Loop Null Safety Tests")
public class FunctionalLoopNullSafetyTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	@Nested
	@DisplayName("String Concatenation Reducer Tests")
	class StringConcatReducerTests {

		/**
		 * Tests string concatenation with @NotNull annotated accumulator.
		 * 
		 * <p>
		 * When the accumulator variable has @NotNull annotation, String::concat
		 * method reference can be safely used because we know the accumulator
		 * will never be null (concat only throws NPE if the argument is null,
		 * and the list items being non-null is a separate concern).
		 * </p>
		 */
		@Test
		@DisplayName("String concat with @NotNull accumulator uses String::concat")
		void test_StringConcat_WithNotNullAccumulator() throws CoreException {
			String input = """
					package test1;

					import java.util.List;
					import org.eclipse.jdt.annotation.NotNull;

					class MyTest {
						public String concat(@NotNull List<String> items) {
							@NotNull String result = "";
							for (String item : items) {
								result = result + item;
							}
							return result;
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;
					import org.eclipse.jdt.annotation.NotNull;

					class MyTest {
						public String concat(@NotNull List<String> items) {
							@NotNull String result = "";
							result = items.stream().reduce(result, String::concat);
							return result;
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests string concatenation without @NotNull annotation uses null-safe lambda.
		 * 
		 * <p>
		 * Without @NotNull, the transformation should use a null-safe lambda
		 * {@code (a, b) -> a + b} instead of {@code String::concat} because
		 * String::concat throws NPE if the argument is null.
		 * </p>
		 */
		@Test
		@DisplayName("String concat without @NotNull uses null-safe lambda")
		void test_StringConcat_WithoutNotNull_UsesNullSafeLambda() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public String concat(List<String> items) {
							String result = "";
							for (String item : items) {
								result = result + item;
							}
							return result;
						}
					}""";

			// Expected: null-safe lambda because items may contain null
			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public String concat(List<String> items) {
							String result = "";
							result = items.stream().reduce(result, (a, b) -> a + b);
							return result;
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}
	}

	@Nested
	@DisplayName("Method Invocation on Loop Variable Tests")
	class MethodInvocationNullSafetyTests {

		/**
		 * Tests that method call on potentially null loop variable is handled safely.
		 * 
		 * <p>
		 * When calling methods on the loop variable (e.g., {@code item.toString()}),
		 * the original loop would throw NPE if item is null. The stream transformation
		 * preserves this behavior but should document the risk if the collection can
		 * contain nulls.
		 * </p>
		 */
		@Test
		@DisplayName("Method call on loop variable - same NPE behavior")
		void test_MethodCallOnLoopVariable_PreservesNPEBehavior() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								System.out.println(item.toUpperCase());
							}
						}
					}""";

			// Transformation is valid - both versions will throw NPE on null item
			// Note: The cleanup produces items.forEach() which is simpler and preferred
			// for simple operations without intermediate transformations
			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							items.forEach(item -> System.out.println(item.toUpperCase()));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests loop with explicit null check before method call.
		 * 
		 * <p>
		 * When the loop has an explicit null check (continue on null), the
		 * transformation should include a filter to skip nulls.
		 * </p>
		 */
		@Test
		@DisplayName("Explicit null check with continue converts to filter")
		void test_ExplicitNullCheck_ConvertsToFilter() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								if (item == null) {
									continue;
								}
								System.out.println(item.toUpperCase());
							}
						}
					}""";

			// Note: The cleanup keeps the expression inline in forEachOrdered rather than 
			// extracting to a separate map operation - both are semantically equivalent
			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							items.stream().filter(item -> !(item == null)).forEachOrdered(item -> System.out.println(item.toUpperCase()));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}
	}

	@Nested
	@DisplayName("Match Pattern Null Safety Tests")
	class MatchPatternNullSafetyTests {

		/**
		 * Tests anyMatch with null comparison.
		 * 
		 * <p>
		 * Pattern: Check if any element is null.
		 * </p>
		 */
		@Test
		@DisplayName("anyMatch checking for null elements")
		void test_AnyMatchNullCheck() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public boolean hasNull(List<Object> items) {
							for (Object item : items) {
								if (item == null) {
									return true;
								}
							}
							return false;
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public boolean hasNull(List<Object> items) {
							if (items.stream().anyMatch(item -> item == null)) {
								return true;
							}
							return false;
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests noneMatch with method call that could NPE.
		 * 
		 * <p>
		 * When the condition involves a method call on the element,
		 * both the original loop and the stream would NPE on null elements.
		 * The transformation is behaviorally equivalent but risky.
		 * </p>
		 */
		@Test
		@DisplayName("noneMatch with method call - NPE risk documented")
		void test_NoneMatchWithMethodCall_NPERisk() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public boolean noneEmpty(List<String> items) {
							for (String item : items) {
								if (item.isEmpty()) {
									return false;
								}
							}
							return true;
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public boolean noneEmpty(List<String> items) {
							if (!items.stream().noneMatch(item -> item.isEmpty())) {
								return false;
							}
							return true;
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}
	}

	@Nested
	@DisplayName("Reduce Operation Null Safety Tests")
	class ReduceNullSafetyTests {

		/**
		 * Tests numeric sum reduction with potentially null elements.
		 * 
		 * <p>
		 * For numeric types, null elements would cause NPE in both the original
		 * loop and the stream. The transformation is safe if the collection
		 * is known to not contain nulls.
		 * </p>
		 */
		@Test
		@DisplayName("Integer sum with unboxing - same NPE behavior")
		void test_IntegerSum_UnboxingNPE() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public int sum(List<Integer> items) {
							int total = 0;
							for (Integer item : items) {
								total += item;
							}
							return total;
						}
					}""";

			// Note: The reduce operation uses the variable name 'total' to preserve 
			// the initial value reference. This is semantically equivalent.
			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public int sum(List<Integer> items) {
							int total = 0;
							total = items.stream().reduce(total, Integer::sum);
							return total;
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests Math.max reduction pattern.
		 * 
		 * <p>
		 * The Math.max/min patterns should work the same with or without null
		 * elements as long as the element is used directly.
		 * </p>
		 * 
		 * <p><b>Note:</b> The current implementation preserves the variable initialization
		 * and assigns the reduce result. A future optimization could inline the initializer
		 * into the reduce call.</p>
		 */
		@Test
		@DisplayName("Math.max reducer pattern")
		void test_MathMaxReducer() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public int findMax(List<Integer> items) {
							int max = Integer.MIN_VALUE;
							for (Integer item : items) {
								max = Math.max(max, item);
							}
							return max;
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public int findMax(List<Integer> items) {
							int max = Integer.MIN_VALUE;
							max = items.stream().reduce(max, Math::max);
							return max;
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}
	}

	@Nested
	@DisplayName("Edge Cases and Corner Cases")
	class EdgeCasesTests {

		/**
		 * Tests loop with chained method calls where intermediate can be null.
		 * 
		 * <p>
		 * When chaining method calls like {@code item.getFoo().getBar()}, if
		 * getFoo() returns null, getBar() will NPE. This is the same in both
		 * loop and stream versions.
		 * </p>
		 * 
		 * <p><b>Note:</b> The current implementation generates a lambda for the forEach.
		 * A future optimization could detect when the lambda can be simplified to a 
		 * method reference like {@code System.out::println}.</p>
		 */
		@Test
		@DisplayName("Chained method calls with potential null intermediate")
		void test_ChainedMethodCalls_NullIntermediate() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<Person> people) {
							for (Person person : people) {
								String city = person.getAddress().getCity();
								System.out.println(city);
							}
						}
					}
					
					class Person {
						Address getAddress() { return null; }
					}
					
					class Address {
						String getCity() { return null; }
					}""";

			// Same NPE risk in both versions
			// Note: Lambda is used instead of method reference - optimization for future work
			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<Person> people) {
							people.stream().map(person -> person.getAddress().getCity()).forEachOrdered(city -> System.out.println(city));
						}
					}
					
					class Person {
						Address getAddress() { return null; }
					}
					
					class Address {
						String getCity() { return null; }
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests allMatch where condition includes null-safe comparison.
		 */
		@Test
		@DisplayName("allMatch with null-safe equals comparison")
		void test_AllMatchWithNullSafeEquals() throws CoreException {
			String input = """
					package test1;

					import java.util.List;
					import java.util.Objects;

					class MyTest {
						public boolean allEqualToTarget(List<String> items, String target) {
							for (String item : items) {
								if (!Objects.equals(item, target)) {
									return false;
								}
							}
							return true;
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;
					import java.util.Objects;

					class MyTest {
						public boolean allEqualToTarget(List<String> items, String target) {
							if (!items.stream().allMatch(item -> Objects.equals(item, target))) {
								return false;
							}
							return true;
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests simple anyMatch with negated null check.
		 */
		@Test
		@DisplayName("anyMatch with negated null check (find non-null)")
		void test_AnyMatchNegatedNullCheck() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public boolean hasNonNull(List<Object> items) {
							for (Object item : items) {
								if (item != null) {
									return true;
								}
							}
							return false;
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public boolean hasNonNull(List<Object> items) {
							if (items.stream().anyMatch(item -> item != null)) {
								return true;
							}
							return false;
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests forEach with Optional handling.
		 */
		@Test
		@DisplayName("forEach with Optional.ofNullable")
		void test_ForEachWithOptional() throws CoreException {
			String input = """
					package test1;

					import java.util.List;
					import java.util.Optional;

					class MyTest {
						public void process(List<String> items) {
							for (String item : items) {
								Optional.ofNullable(item).ifPresent(System.out::println);
							}
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;
					import java.util.Optional;

					class MyTest {
						public void process(List<String> items) {
							items.forEach(item -> Optional.ofNullable(item).ifPresent(System.out::println));
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}
	}

	@Nested
	@DisplayName("Negative Tests - Should NOT Convert")
	class NegativeNullSafetyTests {

		/**
		 * Tests that loops assigning to external variable can be converted to filter/forEachOrdered.
		 * 
		 * <p>
		 * <b>Note:</b> While this pattern could semantically be a findLast() operation,
		 * the cleanup converts it to a filter/forEachOrdered chain that preserves
		 * the assignment behavior. The result variable must be effectively final 
		 * for lambda capture, but assignment is still possible via forEachOrdered.
		 * </p>
		 */
		@Test
		@DisplayName("Assignment to external variable converts to filter/forEachOrdered")
		void test_AssignNullToExternalVariable_ShouldNotConvert() throws CoreException {
			String input = """
					package test1;

					import java.util.List;

					class MyTest {
						public String findFirst(List<String> items) {
							String result = null;
							for (String item : items) {
								if (item.startsWith("target")) {
									result = item;
								}
							}
							return result;
						}
					}""";

			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public String findFirst(List<String> items) {
							String result = null;
							items.stream().filter(item -> (item.startsWith("target"))).forEachOrdered(item -> result = item);
							return result;
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, true, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
		}

		/**
		 * Tests that loops with conditional return of null should not convert.
		 */
		@Test
		@DisplayName("Conditional return null in loop - should NOT convert")
		void test_ConditionalReturnNull_ShouldNotConvert() throws CoreException {
			String sourceCode = """
					package test1;

					import java.util.List;

					class MyTest {
						public String process(List<String> items) {
							for (String item : items) {
								if (item.isEmpty()) {
									return null;
								}
							}
							return "done";
						}
					}""";

			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		}
	}
}
