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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for converting traditional iterator-based loops to functional stream operations.
 * 
 * <p>
 * This test class focuses on converting the classic iterator patterns used in legacy Java code
 * to modern functional stream operations.
 * </p>
 * 
 * <p><b>Pattern 1 - While-Iterator:</b></p>
 * <pre>{@code
 * Iterator<T> it = collection.iterator();
 * while (it.hasNext()) {
 *     T item = it.next();
 *     // loop body
 * }
 * }</pre>
 * 
 * <p><b>Pattern 2 - For-Loop-Iterator:</b></p>
 * <pre>{@code
 * for (Iterator<T> it = collection.iterator(); it.hasNext(); ) {
 *     T item = it.next();
 *     // loop body
 * }
 * }</pre>
 * 
 * <p>
 * These loops can be converted to {@code collection.forEach()}, {@code collection.stream()...}, etc.,
 * depending on the loop body operations.
 * </p>
 * 
 * @see org.sandbox.jdt.internal.corext.fix.helper.IteratorPatternDetector
 * @see org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopAnalyzer
 * @see org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopBodyParser
 * @see org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopToFunctional
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 */
public class IteratorLoopConversionTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	// ==================== While-Iterator Pattern Tests ====================

	/**
	 * Tests simple iterator loop conversion to forEach.
	 * 
	 * <p><b>Pattern:</b> Traditional iterator pattern with a simple loop body</p>
	 * 
	 * <p><b>Why convertible:</b> This is the simplest iterator pattern - iterate
	 * over a collection and perform an action on each element without any transformation,
	 * filtering, or accumulation. This maps directly to {@code forEach()}.</p>
	 * 
	 * <p><b>Input Pattern:</b></p>
	 * <pre>{@code
	 * Iterator<String> it = list.iterator();
	 * while (it.hasNext()) {
	 *     String item = it.next();
	 *     System.out.println(item);
	 * }
	 * }</pre>
	 * 
	 * <p><b>Output Pattern:</b></p>
	 * <pre>{@code
	 * list.forEach(item -> System.out.println(item));
	 * }</pre>
	 * 
	 * <p><b>Semantic equivalence:</b> Both versions iterate in order and execute
	 * the same action for each element. The functional version is more concise
	 * and follows modern Java idioms.</p>
	 */
	@Test
	void test_SimpleIteratorLoopToForEach() throws CoreException {
		String input = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					Iterator<String> it = list.iterator();
					while (it.hasNext()) {
						String item = it.next();
						System.out.println(item);
					}
				}
			}""";

		String output = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					list.forEach(item -> System.out.println(item));
				}
			}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output }, null);
	}

	/**
	 * Tests iterator loop with multiple statements in body.
	 * 
	 * <p><b>Pattern:</b> Iterator loop with multiple side-effect statements</p>
	 * 
	 * <p><b>Why convertible:</b> Multiple statements can be wrapped in a lambda block.
	 * The iterator pattern is still simple (no transformations, no conditionals).</p>
	 */
	@Test
	void test_IteratorLoopWithMultipleStatements() throws CoreException {
		String input = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					Iterator<String> it = list.iterator();
					while (it.hasNext()) {
						String item = it.next();
						System.out.println("Processing: " + item);
						System.out.println("Length: " + item.length());
					}
				}
			}""";

		String output = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					list.forEach(item -> {
						System.out.println("Processing: " + item);
						System.out.println("Length: " + item.length());
					});
				}
			}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output }, null);
	}

	/**
	 * Tests that iterator loops with remove() are NOT converted.
	 * 
	 * <p><b>Pattern:</b> Iterator loop calling remove()</p>
	 * 
	 * <p><b>Why NOT convertible:</b> Stream operations cannot safely remove elements
	 * from the source collection during iteration. The iterator.remove() pattern
	 * has no direct functional equivalent and must be preserved.</p>
	 */
	@Test
	void test_IteratorLoopWithRemove_NotConverted() throws CoreException {
		String input = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					Iterator<String> it = list.iterator();
					while (it.hasNext()) {
						String item = it.next();
						if (item.isEmpty()) {
							it.remove();
						}
					}
				}
			}""";

		// Should NOT be converted - output should match input
		String output = input;

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output }, null);
	}

	/**
	 * Tests iterator loop that doesn't match the expected pattern.
	 * 
	 * <p><b>Pattern:</b> While loop with hasNext() but no next() call</p>
	 * 
	 * <p><b>Why NOT convertible:</b> The loop doesn't follow the standard iterator
	 * pattern. It's not safe to convert.</p>
	 */
	@Test
	void test_IteratorLoopWithoutNextCall_NotConverted() throws CoreException {
		String input = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					Iterator<String> it = list.iterator();
					while (it.hasNext()) {
						System.out.println("Has more items");
						break;
					}
				}
			}""";

		// Should NOT be converted - output should match input
		String output = input;

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output }, null);
	}

	// ==================== For-Loop-Iterator Pattern Tests ====================

	/**
	 * Tests for-loop iterator pattern conversion to forEach.
	 * 
	 * <p><b>Pattern:</b> Traditional for loop with iterator initialization and hasNext() condition</p>
	 * 
	 * <p><b>Why convertible:</b> This is a standard iterator pattern expressed
	 * as a for loop instead of a while loop. Functionally equivalent to the while pattern.</p>
	 */
	@Test
	void test_ForLoopIteratorToForEach() throws CoreException {
		String input = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					for (Iterator<String> it = list.iterator(); it.hasNext(); ) {
						String item = it.next();
						System.out.println(item);
					}
				}
			}""";

		String output = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					list.forEach(item -> System.out.println(item));
				}
			}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output }, null);
	}

	// ==================== Stream Operation Tests ====================

	/**
	 * Tests while-iterator loop with filter operation to stream.
	 * 
	 * <p><b>Pattern:</b> Iterator loop with conditional filtering</p>
	 * 
	 * <p><b>Why convertible:</b> Conditional statements map to .filter() operations.</p>
	 */
	@Test
	void test_WhileIteratorWithFilter() throws CoreException {
		String input = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					Iterator<String> it = list.iterator();
					while (it.hasNext()) {
						String item = it.next();
						if (item != null && item.length() > 3) {
							System.out.println(item);
						}
					}
				}
			}""";

		String output = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					list.stream().filter(item -> (item != null && item.length() > 3)).forEachOrdered(item -> {
						System.out.println(item);
					});
				}
			}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output }, null);
	}

	/**
	 * Tests while-iterator loop with map operation to stream.
	 * 
	 * <p><b>Pattern:</b> Iterator loop with variable transformation</p>
	 * 
	 * <p><b>Why convertible:</b> Variable declarations with transformations map to .map() operations.</p>
	 */
	@Test
	void test_WhileIteratorWithMap() throws CoreException {
		String input = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					Iterator<String> it = list.iterator();
					while (it.hasNext()) {
						String item = it.next();
						String upper = item.toUpperCase();
						System.out.println(upper);
					}
				}
			}""";

		String output = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public void test(List<String> list) {
					list.stream().map(item -> item.toUpperCase()).forEachOrdered(upper -> {
						System.out.println(upper);
					});
				}
			}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output }, null);
	}

	/**
	 * Tests while-iterator loop with reduce operation to stream.
	 * 
	 * <p><b>Pattern:</b> Iterator loop with accumulation</p>
	 * 
	 * <p><b>Why convertible:</b> Accumulation patterns map to .reduce() operations.</p>
	 */
	@Test
	void test_WhileIteratorWithReduce() throws CoreException {
		String input = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public int test(List<Integer> list) {
					int sum = 0;
					Iterator<Integer> it = list.iterator();
					while (it.hasNext()) {
						Integer item = it.next();
						sum += item;
					}
					return sum;
				}
			}""";

		String output = """
			package test1;
			
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public int test(List<Integer> list) {
					int sum = 0;
					sum = list.stream().map(item -> item).reduce(sum, Integer::sum);
					return sum;
				}
			}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output }, null);
	}

	/**
	 * Tests while-iterator loop with collect operation to stream.
	 * 
	 * <p><b>Pattern:</b> Iterator loop collecting into a new list</p>
	 * 
	 * <p><b>Why convertible:</b> Collection building patterns map to .collect() operations.</p>
	 */
	@Test
	void test_WhileIteratorToCollect() throws CoreException {
		String input = """
			package test1;
			
			import java.util.ArrayList;
			import java.util.Iterator;
			import java.util.List;
			
			class MyTest {
				public List<String> test(List<String> list) {
					List<String> result = new ArrayList<>();
					Iterator<String> it = list.iterator();
					while (it.hasNext()) {
						String item = it.next();
						result.add(item.toUpperCase());
					}
					return result;
				}
			}""";

		String output = """
			package test1;
			
			import java.util.ArrayList;
			import java.util.Iterator;
			import java.util.List;
			import java.util.stream.Collectors;
			
			class MyTest {
				public List<String> test(List<String> list) {
					List<String> result = new ArrayList<>();
					result = list.stream().map(item -> item.toUpperCase()).collect(Collectors.toList());
					return result;
				}
			}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output }, null);
	}
}
