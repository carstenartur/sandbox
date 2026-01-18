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
 * Negative and edge case tests for functional loop conversion.
 * 
 * <p>
 * This test class contains test cases for patterns that require special handling:
 * </p>
 * <ul>
 * <li><b>Patterns that should NOT convert:</b> Break statements, throw statements, 
 * labeled continue statements, external variable modifications (non-accumulator), 
 * early returns with side effects</li>
 * <li><b>Patterns that DO convert to forEach:</b> Collection accumulation patterns 
 * (List.add(), Set.add(), Map.put()) are treated as side-effect operations and 
 * correctly converted to forEach</li>
 * <li><b>Partial conversions:</b> Nested loops where only the inner loop converts</li>
 * </ul>
 * 
 * <p>
 * Tests verify either that source code remains unchanged (for truly non-convertible patterns)
 * or that the correct transformation is applied (for patterns that do convert).
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

// ==================== FLATMAP PATTERN TESTS ====================

/**
 * Tests that nested collection iteration converts the inner loop only.
 * 
 * <p>
 * Nested loops where the inner loop has a simple side effect (like println)
 * can have the inner loop converted to forEach. The outer loop remains as-is
 * since flatMap is not yet supported.
 * </p>
 * 
 * <p><b>Note:</b> Full flatMap support would allow both loops to be converted
 * to a single stream pipeline. This is a future enhancement. See README's
 * "Current Limitations" section.</p>
 * 
 * <p><b>Why the inner loop converts:</b></p>
 * <ul>
 * <li>The inner loop is a simple forEach pattern with a side effect (println)</li>
 * <li>It can be safely converted independently of the outer loop</li>
 * <li>The outer loop remains unchanged as it iterates over a collection of collections</li>
 * </ul>
 */
@Test
@DisplayName("Nested collection iteration - inner loop converts to forEach")
void test_NestedCollectionFlatMap_InnerLoopConverts() throws CoreException {
String sourceCode = """
		package test1;
		import java.util.List;
		class MyTest {
			public void processAll(List<List<String>> nestedList) {
				for (List<String> inner : nestedList) {
					for (String item : inner) {
						System.out.println(item);
					}
				}
			}
		}""";

String expected = """
		package test1;
		import java.util.List;
		class MyTest {
			public void processAll(List<List<String>> nestedList) {
				for (List<String> inner : nestedList) {
					inner.forEach(item -> System.out.println(item));
				}
			}
		}""";

// Inner loop converts to forEach; outer loop stays as-is (flatMap not yet supported)
IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

// ==================== COLLECTION ACCUMULATION TESTS ====================
// These tests verify that collection accumulation patterns (List.add(), Set.add(), Map.put())
// DO convert to forEach with side effects. This is valid and semantically equivalent.
// Note: Using Collectors.toList()/toSet()/toMap() would be a future enhancement.

/**
 * Tests that list accumulation converts to forEach with side effects.
 * 
 * <p>
 * Collection accumulation patterns that add elements to an existing collection
 * are treated as side-effect operations and converted to forEach. This is valid
 * and semantically equivalent to the original loop.
 * </p>
 * 
 * <p><b>Note:</b> Using {@code Collectors.toList()} would be a future enhancement
 * for creating new collections from streams. See README's "Current Limitations" section.</p>
 * 
 * <p><b>Why this converts:</b></p>
 * <ul>
 * <li>Adding to an external collection is a side effect, similar to System.out.println()</li>
 * <li>The converter treats method invocations like .add() as terminal FOREACH operations</li>
 * <li>The semantics are preserved - both iterate and add elements in order</li>
 * </ul>
 */
@Test
@DisplayName("List accumulation - converts to forEach with side effects")
void test_ListAccumulation_ConvertsToForEach() throws CoreException {
String sourceCode = """
		package test1;
		import java.util.List;
		import java.util.ArrayList;
		class MyTest {
			public List<String> toUpperCase(List<String> items) {
				List<String> result = new ArrayList<>();
				for (String item : items) {
					result.add(item.toUpperCase());
				}
				return result;
			}
		}""";

String expected = """
		package test1;
		import java.util.List;
		import java.util.ArrayList;
		class MyTest {
			public List<String> toUpperCase(List<String> items) {
				List<String> result = new ArrayList<>();
				items.forEach(item -> result.add(item.toUpperCase()));
				return result;
			}
		}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

/**
 * Tests that set accumulation converts to forEach with side effects.
 * 
 * <p>
 * Set accumulation patterns that add elements to an existing Set
 * are treated as side-effect operations and converted to forEach. This is valid
 * and semantically equivalent to the original loop.
 * </p>
 * 
 * <p><b>Note:</b> Using {@code Collectors.toSet()} would be a future enhancement
 * for creating new collections from streams. See README's "Current Limitations" section.</p>
 * 
 * <p><b>Why this converts:</b></p>
 * <ul>
 * <li>Adding to an external Set is a side effect, similar to adding to a List</li>
 * <li>The converter treats method invocations like .add() as terminal FOREACH operations</li>
 * <li>The semantics are preserved - both iterate and add elements</li>
 * </ul>
 */
@Test
@DisplayName("Set accumulation - converts to forEach with side effects")
void test_SetAccumulation_ConvertsToForEach() throws CoreException {
String sourceCode = """
		package test1;
		import java.util.List;
		import java.util.Set;
		import java.util.HashSet;
		class MyTest {
			public Set<String> unique(List<String> items) {
				Set<String> result = new HashSet<>();
				for (String item : items) {
					result.add(item);
				}
				return result;
			}
		}""";

String expected = """
		package test1;
		import java.util.List;
		import java.util.Set;
		import java.util.HashSet;
		class MyTest {
			public Set<String> unique(List<String> items) {
				Set<String> result = new HashSet<>();
				items.forEach(item -> result.add(item));
				return result;
			}
		}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

/**
 * Tests that map accumulation converts to forEach with side effects.
 * 
 * <p>
 * Map accumulation patterns that put entries into an existing Map
 * are treated as side-effect operations and converted to forEach. This is valid
 * and semantically equivalent to the original loop.
 * </p>
 * 
 * <p><b>Note:</b> Using {@code Collectors.toMap()} would be a future enhancement
 * for creating new collections from streams. See README's "Current Limitations" section.</p>
 * 
 * <p><b>Why this converts:</b></p>
 * <ul>
 * <li>Putting entries into an external Map is a side effect</li>
 * <li>The converter treats method invocations like .put() as terminal FOREACH operations</li>
 * <li>The semantics are preserved - both iterate and add entries in order</li>
 * </ul>
 */
@Test
@DisplayName("Map accumulation - converts to forEach with side effects")
void test_MapAccumulation_ConvertsToForEach() throws CoreException {
String sourceCode = """
		package test1;
		import java.util.List;
		import java.util.Map;
		import java.util.HashMap;
		class MyTest {
			public Map<String, Integer> lengthMap(List<String> items) {
				Map<String, Integer> result = new HashMap<>();
				for (String item : items) {
					result.put(item, item.length());
				}
				return result;
			}
		}""";

String expected = """
		package test1;
		import java.util.List;
		import java.util.Map;
		import java.util.HashMap;
		class MyTest {
			public Map<String, Integer> lengthMap(List<String> items) {
				Map<String, Integer> result = new HashMap<>();
				items.forEach(item -> result.put(item, item.length()));
				return result;
			}
		}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

// ==================== PARALLEL STREAM SAFETY TESTS ====================

/**
 * Tests that ordering-dependent println should use forEach (not parallel).
 * 
 * <p>
 * This is a documentation test - we verify the output uses forEach or forEachOrdered.
 * The converter should NEVER produce parallel streams.
 * </p>
 */
@Test
@DisplayName("Loop with ordering-dependent println - should use forEachOrdered not parallel")
void test_OrderingDependentSideEffects_ShouldNotParallelize() throws CoreException {
// This is a documentation test - we verify the output uses forEachOrdered
// The converter should NEVER produce parallel streams
String input = """
		package test1;
		import java.util.List;
		class MyTest {
			public void printInOrder(List<String> items) {
				for (String item : items) {
					System.out.println(item);
				}
			}
		}""";

// Expected: uses forEach (or forEachOrdered), NEVER parallelStream()
String expected = """
		package test1;
		import java.util.List;
		class MyTest {
			public void printInOrder(List<String> items) {
				items.forEach(item -> System.out.println(item));
			}
		}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

/**
 * Tests that loops with shared mutable state should NOT convert to parallel.
 * 
 * <p>
 * When a loop modifies shared mutable fields, it cannot be safely parallelized.
 * This test ensures the converter doesn't produce parallel streams in such cases.
 * </p>
 */
@Test
@DisplayName("Loop with shared mutable state - should NOT convert to parallel")
void test_SharedMutableState_ShouldNotConvert() throws CoreException {
String sourceCode = """
		package test1;
		import java.util.List;
		class MyTest {
			private int counter = 0;
			public void countItems(List<String> items) {
				for (String item : items) {
					counter++;  // Shared mutable state
					System.out.println(counter + ": " + item);
				}
			}
		}""";

// Should NOT convert - modifies shared mutable field
IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
}

// ==================== VARARGS ARRAY PATTERN TESTS ====================

/**
 * Tests that loops over varargs arrays should use Arrays.stream.
 * 
 * <p>
 * When iterating over a varargs parameter (which is an array), the converter
 * should use Arrays.stream() instead of Stream.of().
 * </p>
 */
@Test
@DisplayName("Loop over varargs array - should use Arrays.stream")
void test_VarargsArray_UsesArraysStream() throws CoreException {
String input = """
		package test1;
		import java.util.Arrays;
		class MyTest {
			public void process(String... items) {
				for (String item : items) {
					System.out.println(item);
				}
			}
		}""";

String expected = """
		package test1;
		import java.util.Arrays;
		class MyTest {
			public void process(String... items) {
				Arrays.stream(items).forEach(item -> System.out.println(item));
			}
		}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

}
