/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Edge case and boundary condition tests for loop refactoring.
 * 
 * <p>This test class focuses on challenging edge cases and boundary conditions
 * that the loop refactoring must handle correctly:</p>
 * <ul>
 *   <li><b>Empty loops</b> - Loops with no body or empty collections</li>
 *   <li><b>Single element</b> - Collections with exactly one element</li>
 *   <li><b>Null handling</b> - Proper null checks and filtering</li>
 *   <li><b>Type inference</b> - Complex generic types and wildcards</li>
 *   <li><b>Nested structures</b> - Nested collections and complex data</li>
 *   <li><b>Method chaining</b> - Multiple method calls on stream elements</li>
 *   <li><b>Lambda scope</b> - Variable shadowing and scope issues</li>
 *   <li><b>Performance</b> - Ensuring transformations don't degrade performance</li>
 * </ul>
 * 
 * <p><b>Testing Philosophy:</b> Edge cases often reveal bugs in pattern detection
 * and transformation logic. Each test documents why the edge case is important
 * and what could go wrong.</p>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 */
@DisplayName("Loop Refactoring Edge Cases and Boundary Conditions")
public class LoopRefactoringEdgeCasesTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	// ===========================================
	// EMPTY AND SINGLE ELEMENT CASES
	// ===========================================

	/**
	 * Tests loop with empty collection.
	 * 
	 * <p><b>Edge Case:</b> Empty collection should not cause errors</p>
	 * <p><b>Expected:</b> Stream operations handle empty collections correctly</p>
	 * <p><b>Why Important:</b> Stream operations must be null-safe and handle empty inputs</p>
	 */
	@Test
	@DisplayName("Empty collection: stream handles empty input correctly")
	void testEmptyCollection() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process() {
						List<String> items = Collections.emptyList();
						for (String item : items) {
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process() {
						List<String> items = Collections.emptyList();
						items.forEach(item -> System.out.println(item));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests loop with single element collection.
	 * 
	 * <p><b>Edge Case:</b> Single element should still use stream operations</p>
	 * <p><b>Expected:</b> Consistent transformation regardless of size</p>
	 * 
	 * <p><b>Note:</b> Currently disabled - pattern not converting in V1. Needs investigation.</p>
	 */
	@Disabled("Pattern not converting in V1 - needs investigation")
	@Test
	@DisplayName("Single element: consistent transformation")
	void testSingleElement() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public static void main(String[] args) {
						new MyTest().process();
					}
					public void process() {
						List<String> items = Collections.singletonList("only");
						for (String item : items) {
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				class MyTest {
					public static void main(String[] args) {
						new MyTest().process();
					}
					public void process() {
						List<String> items = Collections.singletonList("only");
						items.forEach(item -> System.out.println(item));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// NULL HANDLING EDGE CASES
	// ===========================================

	/**
	 * Tests loop that explicitly checks for null elements.
	 * 
	 * <p><b>Edge Case:</b> Null checks converted to filter</p>
	 * <p><b>Expected:</b> Null filtering using lambda</p>
	 * <p><b>Note:</b> Current implementation uses lambda; future enhancement could use Objects::nonNull</p>
	 */
	@Test
	@DisplayName("Null check filter: filter(item -> item != null)")
	void testNullCheck() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						for (String item : items) {
							if (item != null) {
								System.out.println(item);
							}
						}
					}
				}
				""";

		String expected = """
package test1;
import java.util.*;
class MyTest {
	public void process(List<String> items) {
		items.stream().filter(item -> (item != null)).forEachOrdered(item -> System.out.println(item));
	}
}
""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests loop with null-safe method call.
	 * 
	 * <p><b>Edge Case:</b> Null checks combined with operations</p>
	 * <p><b>Expected:</b> Filter null before performing operations</p>
	 * <p><b>Note:</b> Current implementation uses lambda for filter</p>
	 */
	@Disabled("Filter+collect pattern not supported in V1 - requires V2 enhancement")
	@Test
	@DisplayName("Null-safe operation: filter before map")
	void testNullSafeOperation() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						List<String> upper = new ArrayList<>();
						for (String item : items) {
							if (item != null) {
								upper.add(item.toUpperCase());
							}
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class MyTest {
					public void process(List<String> items) {
						List<String> upper = items.stream().filter(item -> item != null).map(item -> item.toUpperCase()).collect(Collectors.toList());
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// COMPLEX GENERIC TYPES
	// ===========================================

	/**
	 * Tests loop with nested generic types.
	 * 
	 * <p><b>Edge Case:</b> Complex generics like {@code List<List<String>>}</p>
	 * <p><b>Expected:</b> Type inference handles nested generics correctly</p>
	 * <p><b>Why Important:</b> Generic type erasure can cause compilation issues</p>
	 * 
	 * <p><b>Note:</b> Currently disabled - pattern not converting in V1. Needs investigation.</p>
	 */
	@Disabled("Pattern not converting in V1 - needs investigation")
	@Test
	@DisplayName("Nested generics: List<List<T>> type inference")
	void testNestedGenerics() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public static void main(String[] args) {
						new MyTest().process(new ArrayList<>());
					}
					public void process(List<List<String>> matrix) {
						for (List<String> row : matrix) {
							System.out.println(row);
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				class MyTest {
					public static void main(String[] args) {
						new MyTest().process(new ArrayList<>());
					}
					public void process(List<List<String>> matrix) {
						matrix.forEach(row -> System.out.println(row));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests loop with wildcard generic types.
	 * 
	 * <p><b>Edge Case:</b> Wildcards like {@code List<? extends Number>}</p>
	 * <p><b>Expected:</b> Stream operations preserve wildcard semantics</p>
	 */
	@Test
	@DisplayName("Wildcard generics: List<? extends T> handling")
	void testWildcardGenerics() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<? extends Number> numbers) {
						for (Number num : numbers) {
							System.out.println(num);
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<? extends Number> numbers) {
						numbers.forEach(num -> System.out.println(num));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// METHOD CHAINING EDGE CASES
	// ===========================================

	/**
	 * Tests loop with method chaining on elements.
	 * 
	 * <p><b>Edge Case:</b> Multiple method calls chained on element</p>
	 * <p><b>Expected:</b> Lambda correctly preserves method chaining</p>
	 */
	@Test
	@DisplayName("Method chaining: element.method1().method2()")
	void testMethodChaining() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						for (String item : items) {
							System.out.println(item.trim().toUpperCase());
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						items.forEach(item -> System.out.println(item.trim().toUpperCase()));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests loop with method chaining in collect.
	 * 
	 * <p><b>Edge Case:</b> Chained transformations in accumulation</p>
	 * <p><b>Expected:</b> Map operation preserves chaining</p>
	 */
	@Test
	@DisplayName("Chained map: map(x -> x.m1().m2())")
	void testChainedMap() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						List<String> processed = new ArrayList<>();
						for (String item : items) {
							processed.add(item.trim().toUpperCase());
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class MyTest {
					public void process(List<String> items) {
						List<String> processed = items.stream().map(item -> item.trim().toUpperCase()).collect(Collectors.toList());
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// VARIABLE SHADOWING AND SCOPE
	// ===========================================

	/**
	 * Tests loop variable name that shadows outer variable.
	 * 
	 * <p><b>Edge Case:</b> Loop variable shadows an outer scope variable</p>
	 * <p><b>Expected:</b> Lambda parameter preserves shadowing semantics</p>
	 * <p><b>Why Important:</b> Incorrect transformation could change variable resolution</p>
	 */
	@Test
	@DisplayName("Variable shadowing: lambda preserves scope")
	void testVariableShadowing() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						String item = "outer";
						for (String item : items) {
							System.out.println(item);
						}
						System.out.println(item);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						String item = "outer";
						items.forEach(item -> System.out.println(item));
						System.out.println(item);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that loop variable name conflict with lambda body is handled.
	 * 
	 * <p><b>Edge Case:</b> Loop uses a variable name that would conflict</p>
	 * <p><b>Expected:</b> Transformation preserves all variable names correctly</p>
	 */
	@Test
	@DisplayName("Name conflict: avoid variable name collisions")
	void testNameConflict() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> list) {
						for (String item : list) {
							String value = item.toUpperCase();
							System.out.println(value);
						}
					}
					public static void main(String[] args) {
						new MyTest().process(Arrays.asList("a", "b"));
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> list) {
						list.forEach(item -> {
							String value = item.toUpperCase();
							System.out.println(value);
						});
					}
					public static void main(String[] args) {
						new MyTest().process(Arrays.asList("a", "b"));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// PERFORMANCE-SENSITIVE CASES
	// ===========================================

	/**
	 * Tests that simple forEach doesn't add unnecessary stream() overhead.
	 * 
	 * <p><b>Performance Edge Case:</b> Direct forEach is more efficient than stream().forEach()</p>
	 * <p><b>Expected:</b> {@code collection.forEach()} not {@code collection.stream().forEach()}</p>
	 * <p><b>Best Practice:</b> Avoid unnecessary stream creation for simple iteration</p>
	 */
	@Test
	@DisplayName("Performance: use direct forEach when possible")
	void testDirectForEachPerformance() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						for (String item : items) {
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						items.forEach(item -> System.out.println(item));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests primitive array handling for performance.
	 * 
	 * <p><b>Performance Edge Case:</b> Primitive arrays should use specialized streams</p>
	 * <p><b>Expected:</b> Use IntStream, LongStream, DoubleStream for primitive arrays</p>
	 * <p><b>Best Practice:</b> Avoid boxing overhead with specialized streams</p>
	 */
	@Test
	@DisplayName("Primitive array: use IntStream for int[] to avoid boxing")
	void testPrimitiveArrayPerformance() throws CoreException {
		String input = """
				package test1;
				class MyTest {
					public void process(int[] numbers) {
						for (int num : numbers) {
							System.out.println(num);
						}
					}
				}
				""";

		String expected = """
package test1;

import java.util.Arrays;

class MyTest {
	public void process(int[] numbers) {
		Arrays.stream(numbers).forEach(num -> System.out.println(num));
	}
}
""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// UNUSUAL BUT VALID PATTERNS
	// ===========================================

	/**
	 * Tests loop with no-op body (edge case of minimal operation).
	 * 
	 * <p><b>Edge Case:</b> Loop that does nothing useful</p>
	 * <p><b>Expected:</b> Still transforms correctly, though pattern is unusual</p>
	 */
	@Test
	@DisplayName("No-op loop: empty body still transforms")
	void testNoOpLoop() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						for (String item : items) {
							// Empty body
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				class MyTest {
					public void process(List<String> items) {
						items.forEach(item -> {
							// Empty body
						});
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests loop where element is never used.
	 * 
	 * <p><b>Edge Case:</b> Loop variable declared but never referenced</p>
	 * <p><b>Expected:</b> Lambda parameter created but not used (compiler warning)</p>
	 * 
	 * <p><b>Note:</b> Currently disabled - pattern not converting in V1. Needs investigation.</p>
	 */
	@Disabled("Pattern not converting in V1 - needs investigation")
	@Test
	@DisplayName("Unused element: lambda with unused parameter")
	void testUnusedElement() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class MyTest {
					private int counter = 0;
					public static void main(String[] args) {
						new MyTest().process(new ArrayList<>());
					}
					public void process(List<String> items) {
						for (String item : items) {
							counter++;
						}
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				class MyTest {
					private int counter = 0;
					public static void main(String[] args) {
						new MyTest().process(new ArrayList<>());
					}
					public void process(List<String> items) {
						items.forEach(item -> counter++);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
