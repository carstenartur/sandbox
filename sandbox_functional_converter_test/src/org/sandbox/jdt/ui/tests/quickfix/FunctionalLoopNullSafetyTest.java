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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Tests null-safety and lambda-capture boundaries of functional loop conversion. */
@DisplayName("Functional Loop Null Safety Tests")
public class FunctionalLoopNullSafetyTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	@Nested
	@DisplayName("String Concatenation Reducer Tests")
	class StringConcatReducerTests {

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
			assertConversion(input, expected);
		}

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
			assertConversion(input, expected);
		}
	}

	@Nested
	@DisplayName("Method Invocation on Loop Variable Tests")
	class MethodInvocationNullSafetyTests {

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
			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							items.forEach(item -> System.out.println(item.toUpperCase()));
						}
					}""";
			assertConversion(input, expected);
		}

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
			String expected = """
					package test1;

					import java.util.List;

					class MyTest {
						public void process(List<String> items) {
							items.stream().filter(item -> !(item == null)).forEachOrdered(item -> System.out.println(item.toUpperCase()));
						}
					}""";
			assertConversion(input, expected);
		}
	}

	@Nested
	@DisplayName("Match Pattern Null Safety Tests")
	class MatchPatternNullSafetyTests {

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
			assertConversion(input, expected);
		}

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
			assertConversion(input, expected);
		}
	}

	@Nested
	@DisplayName("Reduce Operation Null Safety Tests")
	class ReduceNullSafetyTests {

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
			assertConversion(input, expected);
		}

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
			assertConversion(input, expected);
		}
	}

	@Nested
	@DisplayName("Edge Cases and Corner Cases")
	class EdgeCasesTests {

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
			assertConversion(input, expected);
		}

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
			assertConversion(input, expected);
		}

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
			assertConversion(input, expected);
		}

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
			assertConversion(input, expected);
		}
	}

	@Nested
	@DisplayName("Negative Tests - Should NOT Convert")
	class NegativeNullSafetyTests {

		@Test
		@DisplayName("Assignment to external variable - should NOT convert")
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
			IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
			ICompilationUnit cu = pack.createCompilationUnit("Test.java", input, true, null);
			context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
			context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
		}

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

	private void assertConversion(String input, String expected) throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
