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
 * Tests for filter and map operation chains in functional loop conversion.
 * 
 * <p>
 * This test class focuses on converting enhanced for-loops with filtering (if
 * statements or continue) and mapping (variable transformations) into
 * {@code stream().filter().map().forEach()} chains.
 * </p>
 * 
 * <p>
 * <b>Patterns covered:</b>
 * </p>
 * <ul>
 * <li>Basic map operations</li>
 * <li>Filter + map + forEach combinations</li>
 * <li>Multiple map operations (chaining)</li>
 * <li>IF statements converted to filters</li>
 * <li>Continue statements converted to filters</li>
 * <li>Nested filter combinations</li>
 * <li>Complex filter conditions</li>
 * </ul>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder
 * @see org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation.OperationType#FILTER
 * @see org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation.OperationType#MAP
 */
public class FunctionalLoopFilterMapTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
* Tests basic map operation chaining.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> A loop with variable transformation followed by usage
		 * is converted to {@code stream().map(...).forEachOrdered(...)}.
		 * </p>
		 * 
		 * <p>
		 * <b>Input Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * for (Integer l : ls) {
		 * 	String s = l.toString();
		 * 	System.out.println(s);
		 * }
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Output Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * ls.stream().map(l -> l.toString()).forEachOrdered(s -> System.out.println(s));
		 * }
		 * </pre>
	 */
	@Test
	void test_ChainingMap() throws CoreException {
		String input = """
			package test1;
			import java.util.Arrays;
			import java.util.List;
			class MyTest {
				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}
				public void test(List<Integer> ls) {
					for (Integer l : ls) {
						String s = l.toString();
						System.out.println(s);
					}
				}
				}""";

		String expected = """
			package test1;
			import java.util.Arrays;
			import java.util.List;
			class MyTest {
				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}
				public void test(List<Integer> ls) {
					ls.stream().map(l -> l.toString()).forEachOrdered(s -> System.out.println(s));
				}
						}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
* Tests filter + map + forEach conversion.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> An IF statement that guards all remaining statements
		 * is converted to a {@code filter()} operation.
		 * </p>
		 * 
		 * <p>
		 * <b>Input Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * for (Integer l : ls) {
		 * 	if (l != null) {
		 * 		String s = l.toString();
		 * 		System.out.println(s);
		 * 	}
		 * }
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Output Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * ls.stream().filter(l -> (l != null)).map(l -> l.toString()).forEachOrdered(s -> System.out.println(s));
		 * }
		 * </pre>
	 */
	@Test
	void test_ChainingFilterMapForEachConvert() throws CoreException {
		String input = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}

				public void test(List<Integer> ls) {
					for (Integer l : ls) {
						if(l!=null)
						{
							String s = l.toString();
							System.out.println(s);
						}
					}


				}
				}""";

		String expected = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}

				public void test(List<Integer> ls) {
					ls.stream().filter(l -> (l != null)).map(l -> l.toString()).forEachOrdered(s -> System.out.println(s));


				}
						}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
* Tests multiple map operations in sequence.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Each variable transformation becomes a separate
		 * {@code map()} operation, creating a chain of transformations.
		 * </p>
	 */
	@Test
	void test_SmoothLongerChaining() throws CoreException {
		String input = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1,2,3));
				}

				public void test(List<Integer> ls) {
					for (Integer a : ls) {
						Integer l = new Integer(a.intValue());
						if(l!=null)
						{
							String s = l.toString();
							System.out.println(s);
						}
					}


				}
				}""";

		String expected = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1,2,3));
				}

				public void test(List<Integer> ls) {
					ls.stream().map(a -> new Integer(a.intValue())).filter(l -> (l != null)).map(l -> l.toString())
			.forEachOrdered(s -> System.out.println(s));


				}
						}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
* Tests IF statements with nested logic that cannot be fully filtered.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> When an IF statement contains code that needs to be
		 * executed along with statements after it, the IF block is wrapped in a
		 * {@code map()} operation.
		 * </p>
	 */
	@Test
	void test_NonFilteringIfChaining() throws CoreException {
		String input = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1,2,3));
				}

				public void test(List<Integer> ls) {
					for (Integer a : ls) {
						Integer l = new Integer(a.intValue());
						if(l!=null)
						{
							String s = l.toString();
							if(s!=null)
								System.out.println(s);
							System.out.println("cucu");
						}
					}


				}
				}""";

		String expected = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1,2,3));
				}

				public void test(List<Integer> ls) {
					ls.stream().map(a -> new Integer(a.intValue())).filter(l -> (l != null)).map(l -> l.toString()).map(s -> {
			if (s != null)
			System.out.println(s);
			return s;
			}).forEachOrdered(s -> System.out.println("cucu"));


				}
						}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
* Tests continue statement conversion to filter.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> A {@code continue} statement inside an IF is
		 * converted to a {@code filter()} with negated condition.
		 * </p>
		 * 
		 * <p>
		 * <b>Input Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * for (Integer l : ls) {
		 * 	if (l == null) {
		 * 		continue;
		 * 	}
		 * 	// rest of code
		 * }
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Output Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * ls.stream().filter(l -> !(l == null))...
		 * }
		 * </pre>
	 */
	@Test
	void test_ContinuingIfFilterSingleStatement() throws CoreException {
		String input = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}

				public void test(List<Integer> ls) {
					for (Integer l : ls) {
						if (l == null) {
							continue;
						}
						String s = l.toString();
						if (s != null) {
							System.out.println(s);
						}
					}


				}
				}""";

		String expected = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}

				public void test(List<Integer> ls) {
					ls.stream().filter(l -> !(l == null)).map(l -> l.toString()).filter(s -> (s != null))
			.forEachOrdered(s -> System.out.println(s));


				}
				}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
* Tests nested IF statements converted to multiple filters.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Nested IF statements that guard execution are each
		 * converted to separate {@code filter()} operations.
		 * </p>
	 */
	@Test
	void test_NestedFilterCombination() throws CoreException {
		String input = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processValidItems(List<String> items) {
					for (String item : items) {
						if (item != null) {
							if (item.length() > 5) {
								System.out.println(item);
							}
						}
					}
				}
				}""";

		String expected = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processValidItems(List<String> items) {
					items.stream().filter(item -> (item != null)).filter(item -> (item.length() > 5))
			.forEachOrdered(item -> System.out.println(item));
				}
				}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
* Tests multiple continue statements converted to multiple filters.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Multiple {@code continue} statements are each
		 * converted to a separate {@code filter()} with negated conditions.
		 * </p>
	 */
	@Test
	void test_MultipleContinueFilters() throws CoreException {
		String input = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processFiltered(List<Integer> numbers) {
					for (Integer num : numbers) {
						if (num == null) {
							continue;
						}
						if (num <= 0) {
							continue;
						}
						System.out.println(num);
					}
				}
				}""";

		String expected = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processFiltered(List<Integer> numbers) {
					numbers.stream().filter(num -> !(num == null)).filter(num -> !(num <= 0))
			.forEachOrdered(num -> System.out.println(num));
				}
				}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
* Tests complex filter conditions with multiple boolean operators.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Complex boolean expressions in IF statements are
		 * preserved as-is in the {@code filter()} predicate.
		 * </p>
	 */
	@Test
	void test_FilterWithComplexCondition() throws CoreException {
		String input = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processWithComplexFilter(List<String> items) {
					for (String item : items) {
						if (item != null && item.length() > 5 && item.startsWith("test")) {
							System.out.println(item);
						}
					}
				}
				}""";

		String expected = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processWithComplexFilter(List<String> items) {
					items.stream().filter(item -> (item != null && item.length() > 5 && item.startsWith("test")))
			.forEachOrdered(item -> System.out.println(item));
				}
						}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
* Tests chained filter and map operations with multiple transformations.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Filters and maps are chained in the order they appear
		 * in the original loop, preserving the semantic flow.
		 * </p>
	 */
	@Test
	void test_ChainedFilterAndMapOperations() throws CoreException {
		String input = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processChained(List<Integer> numbers) {
					for (Integer num : numbers) {
						if (num != null && num > 0) {
							int squared = num * num;
							if (squared < 100) {
								System.out.println(squared);
							}
						}
					}
				}
				}""";

		String expected = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processChained(List<Integer> numbers) {
					numbers.stream().filter(num -> (num != null && num > 0)).map(num -> num * num)
			.filter(squared -> (squared < 100)).forEachOrdered(squared -> System.out.println(squared));
				}
						}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
* Tests continue with complex boolean conditions.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> {@code continue} with OR conditions is negated
		 * properly using De Morgan's laws.
		 * </p>
	 */
	@Test
	void test_ContinueWithNestedConditions() throws CoreException {
		String input = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processWithNestedContinue(List<String> items) {
					for (String item : items) {
						if (item == null || item.isEmpty()) {
							continue;
						}
						String upper = item.toUpperCase();
						System.out.println(upper);
					}
				}
				}""";

		String expected = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processWithNestedContinue(List<String> items) {
					items.stream().filter(item -> !(item == null || item.isEmpty())).map(item -> item.toUpperCase())
			.forEachOrdered(upper -> System.out.println(upper));
				}
				}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
* Tests continue with map and forEach operations.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> {@code continue} statements are converted to
		 * {@code filter()}, then subsequent variable transformations become
		 * {@code map()} operations.
		 * </p>
	 */
	@Test
	void test_ContinueWithMapAndForEach() throws CoreException {
		String input = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processPositiveSquares(List<Integer> numbers) {
					for (Integer num : numbers) {
						if (num <= 0) {
							continue;
						}
						int squared = num * num;
						System.out.println(squared);
					}
				}
				}""";

		String expected = """
			package test1;

			import java.util.List;

			class MyTest {
				public void processPositiveSquares(List<Integer> numbers) {
					numbers.stream().filter(num -> !(num <= 0)).map(num -> num * num)
			.forEachOrdered(squared -> System.out.println(squared));
				}
				}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

}
