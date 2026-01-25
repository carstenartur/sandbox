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
 * Additional edge case tests for loop pattern expansion.
 * 
 * <p>This test class covers additional loop patterns and edge cases:</p>
 * <ul>
 *   <li><b>Classic while loops</b> - Non-iterator while patterns</li>
 *   <li><b>Do-while loops</b> - Loops that execute at least once</li>
 *   <li><b>Index-based loops</b> - Traditional for loops with counters</li>
 *   <li><b>Complex iterator patterns</b> - Multiple iterators, conditional iteration</li>
 * </ul>
 * 
 * <p><b>Current Implementation Status:</b></p>
 * <ul>
 *   <li>✅ Enhanced for-loops → Stream (LOOP, LOOP_V2)</li>
 *   <li>✅ Iterator while-loops → Stream (ITERATOR_LOOP)</li>
 *   <li>❌ Classic while-loops → Stream (not pattern-based, needs analysis)</li>
 *   <li>❌ Do-while loops → Stream (incompatible - must execute at least once)</li>
 *   <li>❌ Index-based for-loops → Stream (requires range analysis)</li>
 * </ul>
 */
@DisplayName("Additional Loop Pattern Edge Cases")
public class AdditionalLoopPatternsTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	// ===========================================
	// CLASSIC WHILE LOOPS
	// ===========================================

	/**
	 * Tests that classic while-loops (non-iterator) should NOT be converted.
	 * 
	 * <p><b>Pattern:</b> {@code while (condition) { ... }}</p>
	 * <p><b>Why not convertible:</b> Classic while-loops don't iterate over collections
	 * in a predictable way. The condition could be anything, making it impossible to
	 * determine what to stream over.</p>
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>{@code
	 * int i = 0;
	 * while (i < 10) {
	 *     System.out.println(i);
	 *     i++;
	 * }
	 * }</pre>
	 */
	@Test
	@DisplayName("Classic while-loop should NOT convert (no collection iteration)")
	public void testClassicWhileLoop_noConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				public class MyTest {
					void process() {
						int i = 0;
						while (i < 10) {
							System.out.println(i);
							i++;
						}
					}
				}
				""";

		// Should remain unchanged - no collection iteration
		String expected = given;

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that while-loops with complex conditions should NOT be converted.
	 * 
	 * <p><b>Pattern:</b> {@code while (complexCondition()) { ... }}</p>
	 * <p><b>Why not convertible:</b> The loop doesn't follow a recognizable iteration pattern.</p>
	 */
	@Test
	@DisplayName("While-loop with method call condition should NOT convert")
	public void testWhileWithMethodCondition_noConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					private Queue<String> queue = new LinkedList<>();
					
					void process() {
						while (!queue.isEmpty()) {
							String item = queue.poll();
							System.out.println(item);
						}
					}
				}
				""";

		// Should remain unchanged - not a standard iteration pattern
		String expected = given;

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// DO-WHILE LOOPS
	// ===========================================

	/**
	 * Tests that do-while loops should NOT be converted to streams.
	 * 
	 * <p><b>Pattern:</b> {@code do { ... } while (condition);}</p>
	 * <p><b>Why not convertible:</b> Do-while loops guarantee at least one execution
	 * of the loop body, even if the condition is initially false. Streams don't have
	 * this semantic - an empty stream would never execute the terminal operation.</p>
	 * 
	 * <p><b>Example showing the problem:</b></p>
	 * <pre>{@code
	 * do {
	 *     System.out.println("Executed at least once");
	 * } while (false);  // Still prints once
	 * 
	 * // Stream equivalent would NOT execute:
	 * Stream.empty().forEach(x -> System.out.println("Never executed"));
	 * }</pre>
	 */
	@Test
	@DisplayName("Do-while loop should NOT convert (semantic incompatibility)")
	public void testDoWhileLoop_noConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						Iterator<String> it = items.iterator();
						do {
							if (it.hasNext()) {
								String item = it.next();
								System.out.println(item);
							}
						} while (it.hasNext());
					}
				}
				""";

		// Should remain unchanged - do-while semantics incompatible with streams
		String expected = given;

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests do-while with at-least-once semantics.
	 */
	@Test
	@DisplayName("Do-while with guaranteed execution should NOT convert")
	public void testDoWhileGuaranteedExecution_noConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				public class MyTest {
					void process() {
						int count = 0;
						do {
							System.out.println("Count: " + count);
							count++;
						} while (count < 5);
					}
				}
				""";

		// Should remain unchanged
		String expected = given;

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// INDEX-BASED FOR LOOPS
	// ===========================================

	/**
	 * Tests traditional index-based for-loop (NOT YET SUPPORTED).
	 * 
	 * <p><b>Pattern:</b> {@code for (int i = 0; i < n; i++) { ... }}</p>
	 * <p><b>Potential conversion:</b> {@code IntStream.range(0, n).forEach(i -> ...)}</p>
	 * 
	 * <p><b>Implementation Note:</b> This would require analyzing the loop to detect:
	 * <ul>
	 *   <li>Initialization: {@code int i = start}</li>
	 *   <li>Condition: {@code i < end} or {@code i <= end}</li>
	 *   <li>Update: {@code i++} or {@code i += step}</li>
	 * </ul>
	 * Then convert to {@code IntStream.range(start, end)} or {@code IntStream.rangeClosed()}.</p>
	 */
	@Disabled("Index-based for-loops not yet supported - requires range analysis")
	@Test
	@DisplayName("Index-based for-loop to IntStream.range() (future)")
	public void testIndexBasedForLoop_toIntStream() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				public class MyTest {
					void process() {
						for (int i = 0; i < 10; i++) {
							System.out.println(i);
						}
					}
				}
				""";

		String expected = """
				package test1;
				
				import java.util.stream.IntStream;
				
				public class MyTest {
					void process() {
						IntStream.range(0, 10).forEach(i -> System.out.println(i));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests index-based loop with collection access (NOT YET SUPPORTED).
	 * 
	 * <p><b>Pattern:</b> {@code for (int i = 0; i < list.size(); i++) { T item = list.get(i); ... }}</p>
	 * <p><b>Potential conversion:</b> {@code list.forEach(item -> ...)}</p>
	 * 
	 * <p><b>Implementation Note:</b> Could detect {@code list.get(i)} pattern and convert
	 * to enhanced for-loop first, then to stream. However, if index is used in computation,
	 * would need {@code IntStream.range(0, list.size()).forEach(i -> ... list.get(i) ...)}.</p>
	 */
	@Disabled("Index-based collection access not yet supported")
	@Test
	@DisplayName("Index-based collection loop (future)")
	public void testIndexBasedCollectionLoop_toStream() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						for (int i = 0; i < items.size(); i++) {
							String item = items.get(i);
							System.out.println(item);
						}
					}
				}
				""";

		// If index not used in body, can convert to enhanced for or forEach
		String expected = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						items.forEach(item -> System.out.println(item));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// COMPLEX ITERATOR PATTERNS
	// ===========================================

	/**
	 * Tests multiple iterators in same loop (NOT SUPPORTED).
	 * 
	 * <p><b>Why not convertible:</b> Streams operate on a single source. Multiple
	 * iterators would require zip() operation which doesn't exist in standard Java streams.</p>
	 */
	@Test
	@DisplayName("Multiple iterators should NOT convert (no zip() in Java)")
	public void testMultipleIterators_noConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> list1, List<String> list2) {
						Iterator<String> it1 = list1.iterator();
						Iterator<String> it2 = list2.iterator();
						while (it1.hasNext() && it2.hasNext()) {
							String item1 = it1.next();
							String item2 = it2.next();
							System.out.println(item1 + " - " + item2);
						}
					}
				}
				""";

		// Should remain unchanged - cannot convert to stream without zip()
		String expected = given;

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests iterator with manual hasNext() check inside loop (edge case).
	 */
	@Test
	@DisplayName("Iterator with internal hasNext() check should NOT convert")
	public void testIteratorWithInternalCheck_noConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							System.out.println(item);
							// Manual hasNext() check for conditional processing
							if (it.hasNext()) {
								String next = it.next();
								System.out.println("Next: " + next);
							}
						}
					}
				}
				""";

		// Should remain unchanged - complex iterator manipulation
		String expected = given;

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
