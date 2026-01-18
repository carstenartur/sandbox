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
//import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for reduction operations in functional loop conversion.
 * 
 * <p>
 * This test class focuses on converting enhanced for-loops with reduction
 * operations (sum, product, increment, decrement, max, min, etc.) into
 * {@code stream().reduce()} operations.
 * </p>
 * 
 * <p>
 * <b>Patterns covered:</b>
 * </p>
 * <ul>
 * <li>Integer/Long/Double/Float increment and decrement</li>
 * <li>Sum operations with various numeric types</li>
 * <li>String concatenation</li>
 * <li>Max/Min reductions with Math.max/Math.min</li>
 * <li>Filtered reductions (filter + reduce)</li>
 * <li>Mapped reductions (map + reduce)</li>
 * <li>Complex chains (filter + map + reduce)</li>
 * </ul>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder
 * @see org.sandbox.jdt.internal.corext.fix.helper.OperationType#REDUCE
 */
public class FunctionalLoopReducerTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

/**
* Tests simple increment reducer.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Loop with {@code i++} is converted to
		 * {@code stream().map(item -> 1).reduce(i, Integer::sum)}.
		 * </p>
		 * 
		 * <p>
		 * <b>Input Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * Integer i = 0;
		 * for (Integer l : ls)
		 * 	i++;
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Output Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * i = ls.stream().map(l -> 1).reduce(i, Integer::sum);
		 * }
		 * </pre>
 */
@Test
void test_SimpleReducer() throws CoreException {
String input = """
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
						i++;
					System.out.println(i);
					return true;
				}
				}""";

String expected = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3,7));
				}


				public Boolean test(List<Integer> ls) {
					Integer i=0;
					i = ls.stream().map(l -> 1).reduce(i, Integer::sum);
					System.out.println(i);
					return true;
				}
				}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

/**
* Tests increment reducer with i+=1 pattern.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> {@code i+=1} is functionally equivalent to
		 * {@code i++} and converts the same way.
		 * </p>
 */
@Test
void test_IncrementReducer() throws CoreException {
String input = """
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			/**
			*
			* @author alexandrugyori
			*/
			class MyTest {

				/**
				* @param args the command line arguments
				*/
				public static void main( String[] args) {
					List<Integer> ls = new ArrayList<>();
					int i =0;
					for ( Integer l : ls) {
						i+=1;
					}

				}

				private static void foo(Integer l) {
					throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
				}
			}
						""";

String expected = """
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			/**
			*
			* @author alexandrugyori
			*/
			class MyTest {

				/**
				* @param args the command line arguments
				*/
				public static void main( String[] args) {
					List<Integer> ls = new ArrayList<>();
					int i =0;
					i = ls.stream().map(l -> 1).reduce(i, Integer::sum);

				}

				private static void foo(Integer l) {
					throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
				}
			}
						""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

/**
* Tests Long increment reducer.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> For {@code long} types, uses {@code Long::sum} method
		 * reference in the reduce operation.
		 * </p>
 */
@Test
void test_LongIncrementReducer() throws CoreException {
String input = """
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			/**
			* Test for long increment reducer
			*/
			class MyTest {

				public static void main( String[] args) {
					List<Integer> ints=new ArrayList<>();
					long len=0L;
					for(int i : ints)
						len++;

				}
				}""";

String expected = """
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			/**
			* Test for long increment reducer
			*/
			class MyTest {

				public static void main( String[] args) {
					List<Integer> ints=new ArrayList<>();
					long len=0L;
					len = ints.stream().map(i -> 1L).reduce(len, Long::sum);

				}
				}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

/**
* Tests Math.max reducer pattern.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> {@code max = Math.max(max, value)} is converted to
		 * {@code stream().reduce(max, Integer::max)}.
		 * </p>
 */
//	@Disabled("Not yet working - min/max reducer logic needs improvement")
@Test
void test_MaxReducer() throws CoreException {
String input = """
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class MyTest {
				public int findMax(List<Integer> numbers) {
					int max = Integer.MIN_VALUE;
					for (Integer num : numbers) {
						max = Math.max(max, num);
					}
					return max;
				}
				}""";

String expected = """
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class MyTest {
				public int findMax(List<Integer> numbers) {
					int max = Integer.MIN_VALUE;
					max = numbers.stream().reduce(max, Math::max);
					return max;
				}
				}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

/**
* Tests Math.min reducer pattern.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> {@code min = Math.min(min, value)} is converted to
		 * {@code stream().reduce(min, Integer::min)}.
		 * </p>
 */
//	@Disabled("Not yet working - min/max reducer logic needs improvement")
@Test
void test_MinReducer() throws CoreException {
String input = """
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class MyTest {
				public int findMin(List<Integer> numbers) {
					int min = Integer.MAX_VALUE;
					for (Integer num : numbers) {
						min = Math.min(min, num);
					}
					return min;
				}
				}""";

String expected = """
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class MyTest {
				public int findMin(List<Integer> numbers) {
					int min = Integer.MAX_VALUE;
					min = numbers.stream().reduce(min, Math::min);
					return min;
				}
				}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

/**
* Tests sum reduction with filter.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Filtered sum operations combine {@code filter()} and
		 * {@code reduce()} operations.
		 * </p>
 */
@Test
void test_SumReductionWithFilter() throws CoreException {
String input = """
			package test1;

			import java.util.List;

			class MyTest {
				public int sumPositiveNumbers(List<Integer> numbers) {
					int sum = 0;
					for (Integer num : numbers) {
						if (num > 0) {
							sum += num;
						}
					}
					return sum;
				}
				}""";

String expected = """
			package test1;

			import java.util.List;

			class MyTest {
				public int sumPositiveNumbers(List<Integer> numbers) {
					int sum = 0;
					sum = numbers.stream().filter(num -> (num > 0)).reduce(sum, Integer::sum);
					return sum;
				}
				}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

/**
* Tests complex reduction with mapping.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Transformation before accumulation uses {@code map()}
		 * before {@code reduce()}.
		 * </p>
 */
@Test
void test_ComplexReductionWithMapping() throws CoreException {
String input = """
			package test1;

			import java.util.List;

			class MyTest {
				public int sumOfSquares(List<Integer> numbers) {
					int sum = 0;
					for (Integer num : numbers) {
						int squared = num * num;
						sum += squared;
					}
					return sum;
				}
				}""";

String expected = """
			package test1;

			import java.util.List;

			class MyTest {
				public int sumOfSquares(List<Integer> numbers) {
					int sum = 0;
					sum = numbers.stream().map(num -> num * num).reduce(sum, Integer::sum);
					return sum;
				}
				}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

/**
* Tests full filter+map+reduce chain.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Complete pipeline with filtering, mapping, and
		 * reduction in sequence.
		 * </p>
 */
@Test
void test_FilterMapReduceChain() throws CoreException {
String input = """
			package test1;

			import java.util.List;

			class MyTest {
				public int sumOfPositiveSquares(List<Integer> numbers) {
					int total = 0;
					for (Integer num : numbers) {
						if (num > 0) {
							int squared = num * num;
							total += squared;
						}
					}
					return total;
				}
				}""";

String expected = """
			package test1;

			import java.util.List;

			class MyTest {
				public int sumOfPositiveSquares(List<Integer> numbers) {
					int total = 0;
					total = numbers.stream().filter(num -> (num > 0)).map(num -> num * num).reduce(total, Integer::sum);
					return total;
				}
				}""";

IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
}

}
