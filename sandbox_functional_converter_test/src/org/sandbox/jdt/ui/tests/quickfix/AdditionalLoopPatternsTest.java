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
 *   <li>✅ Enhanced for-loops → Stream (LOOP)</li>
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

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
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


		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
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


		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
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


		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	// ===========================================
	// INDEX-BASED FOR LOOPS
	// ===========================================

	/**
	 * Tests traditional index-based for-loop conversion to IntStream.range().
	 * 
	 * <p><b>Pattern:</b> {@code for (int i = 0; i < n; i++) { ... }}</p>
	 * <p><b>Conversion:</b> {@code IntStream.range(0, n).forEach(i -> ...)}</p>
	 * 
	 * <p>The handler detects:
	 * <ul>
	 *   <li>Initialization: {@code int i = start}</li>
	 *   <li>Condition: {@code i < end}</li>
	 *   <li>Update: {@code i++} or {@code i += 1}</li>
	 * </ul></p>
	 */
	@Test
	@DisplayName("Index-based for-loop to IntStream.range()")
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
	 * Tests that an index-based loop with collection access converts to IntStream.range().
	 * 
	 * <p><b>Pattern:</b> {@code for (int i = 0; i < list.size(); i++) { T item = list.get(i); ... }}</p>
	 * <p><b>Conversion:</b> {@code IntStream.range(0, list.size()).forEach(i -> { T item = list.get(i); ... })}</p>
	 * 
	 * <p>The ULR-based TraditionalForHandler converts to IntStream.range(). Index elimination
	 * (converting to collection.forEach()) is a future enhancement.</p>
	 */
	@Test
	@DisplayName("Index-based collection loop to IntStream.range()")
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

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.IntStream;
				public class MyTest {
					void process(List<String> items) {
						IntStream.range(0, items.size()).forEach(i -> {
							String item = items.get(i);
							System.out.println(item);
						});
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


		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
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


		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	// ===========================================
	// MULTIPLE LOOPS POPULATING A LIST
	// ===========================================

	/**
	 * Tests multiple for-each loops adding to same list - FEATURE REQUEST.
	 * 
	 * <p><b>Pattern:</b> Multiple {@code for (T item : collection) { list.add(...); }}</p>
	 * 
	 * <p><b>Current Behavior (BUG):</b> The cleanup incorrectly converts each loop 
	 * independently, producing {@code ruleEntries = ...collect(Collectors.toList())} which
	 * <b>overwrites</b> the list instead of adding to it. This loses entries from the first loop!</p>
	 * 
	 * <p><b>Expected Behavior:</b> Use {@code Stream.concat()} to combine both streams into
	 * a single list, preserving all entries from both loops.</p>
	 * 
	 * <p><b>Example:</b> JUnit's RuleChain building pattern</p>
	 * 
	 * <pre>{@code
	 * // Original:
	 * List<RuleEntry> ruleEntries = new ArrayList<>(...);
	 * for (MethodRule rule : methodRules) {
	 *     ruleEntries.add(new RuleEntry(rule, TYPE_METHOD_RULE, orderValues.get(rule)));
	 * }
	 * for (TestRule rule : testRules) {
	 *     ruleEntries.add(new RuleEntry(rule, TYPE_TEST_RULE, orderValues.get(rule)));
	 * }
	 * 
	 * // Expected conversion using Stream.concat():
	 * List<RuleEntry> ruleEntries = Stream.concat(
	 *     methodRules.stream().map(rule -> new RuleEntry(rule, TYPE_METHOD_RULE, orderValues.get(rule))),
	 *     testRules.stream().map(rule -> new RuleEntry(rule, TYPE_TEST_RULE, orderValues.get(rule)))
	 * ).collect(Collectors.toList());
	 * }</pre>
	 */
	@Test
	@DisplayName("Multiple for-each loops populating same list should use Stream.concat()")
	public void testMultipleLoopsPopulatingList_streamConcat() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class RuleChainBuilder {
					private List<MethodRule> methodRules = new ArrayList<>();
					private List<TestRule> testRules = new ArrayList<>();
					private Map<Object, Integer> orderValues = new HashMap<>();
					private static final Comparator<RuleEntry> ENTRY_COMPARATOR = Comparator.comparingInt(e -> e.order);

					private List<RuleEntry> getSortedEntries() {
						List<RuleEntry> ruleEntries = new ArrayList<RuleEntry>(
								methodRules.size() + testRules.size());
						for (MethodRule rule : methodRules) {
							ruleEntries.add(new RuleEntry(rule, RuleEntry.TYPE_METHOD_RULE, orderValues.get(rule)));
						}
						for (TestRule rule : testRules) {
							ruleEntries.add(new RuleEntry(rule, RuleEntry.TYPE_TEST_RULE, orderValues.get(rule)));
						}
						Collections.sort(ruleEntries, ENTRY_COMPARATOR);
						return ruleEntries;
					}

					interface MethodRule {}
					interface TestRule {}

					static class RuleEntry {
						static final int TYPE_METHOD_RULE = 1;
						static final int TYPE_TEST_RULE = 2;
						Object rule;
						int type;
						int order;
						RuleEntry(Object rule, int type, Integer order) {
							this.rule = rule;
							this.type = type;
							this.order = order != null ? order : 0;
						}
					}
				}
				""";

		String expected = """
package test1;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
public class RuleChainBuilder {
	private List<MethodRule> methodRules = new ArrayList<>();
	private List<TestRule> testRules = new ArrayList<>();
	private Map<Object, Integer> orderValues = new HashMap<>();
	private static final Comparator<RuleEntry> ENTRY_COMPARATOR = Comparator.comparingInt(e -> e.order);

	private List<RuleEntry> getSortedEntries() {
		List<RuleEntry> ruleEntries = Stream.concat(
				methodRules.stream()
						.map(rule -> new RuleEntry(rule, RuleEntry.TYPE_METHOD_RULE, orderValues.get(rule))),
				testRules.stream().map(rule -> new RuleEntry(rule, RuleEntry.TYPE_TEST_RULE, orderValues.get(rule))))
				.collect(Collectors.toList());
		Collections.sort(ruleEntries, ENTRY_COMPARATOR);
		return ruleEntries;
	}

	interface MethodRule {}
	interface TestRule {}

	static class RuleEntry {
		static final int TYPE_METHOD_RULE = 1;
		static final int TYPE_TEST_RULE = 2;
		Object rule;
		int type;
		int order;
		RuleEntry(Object rule, int type, Integer order) {
			this.rule = rule;
			this.type = type;
			this.order = order != null ? order : 0;
		}
	}
}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("RuleChainBuilder.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Documents the CURRENT BUGGY behavior of multiple loops adding to same list.
	 * 
	 * <p><b>WARNING:</b> This test documents incorrect behavior that should NOT be
	 * relied upon. The converted code is semantically wrong - it overwrites the list
	 * instead of accumulating entries from both loops.</p>
	 * 
	 * <p><b>Additional Issue - Mutability:</b> Even if the overwrite bug were fixed,
	 * using {@code Collectors.toList()} followed by {@code Collections.sort()} is risky
	 * because {@code Collectors.toList()} does not guarantee a mutable list. See
	 * {@link #testLoopWithSubsequentSort_convertsToStream()} for details.</p>
	 * 
	 * <p><b>When this test FAILS:</b> It means the bug has been fixed! Update the
	 * test above ({@code testMultipleLoopsPopulatingList_streamConcat}) to be enabled
	 * and delete this test.</p>
	 */
	@Disabled("FIXED: Bug has been fixed - multiple loops now use Stream.concat()")
	@Test
	@DisplayName("BUGGY BEHAVIOR: Multiple loops overwrite list instead of accumulating")
	public void testMultipleLoopsPopulatingList_currentBuggyBehavior() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class RuleChainBuilder {
					private List<MethodRule> methodRules = new ArrayList<>();
					private List<TestRule> testRules = new ArrayList<>();
					private Map<Object, Integer> orderValues = new HashMap<>();
					private static final Comparator<RuleEntry> ENTRY_COMPARATOR = Comparator.comparingInt(e -> e.order);

					private List<RuleEntry> getSortedEntries() {
						List<RuleEntry> ruleEntries = new ArrayList<RuleEntry>(
								methodRules.size() + testRules.size());
						for (MethodRule rule : methodRules) {
							ruleEntries.add(new RuleEntry(rule, RuleEntry.TYPE_METHOD_RULE, orderValues.get(rule)));
						}
						for (TestRule rule : testRules) {
							ruleEntries.add(new RuleEntry(rule, RuleEntry.TYPE_TEST_RULE, orderValues.get(rule)));
						}
						Collections.sort(ruleEntries, ENTRY_COMPARATOR);
						return ruleEntries;
					}

					interface MethodRule {}
					interface TestRule {}

					static class RuleEntry {
						static final int TYPE_METHOD_RULE = 1;
						static final int TYPE_TEST_RULE = 2;
						Object rule;
						int type;
						int order;
						RuleEntry(Object rule, int type, Integer order) {
							this.rule = rule;
							this.type = type;
							this.order = order != null ? order : 0;
						}
					}
				}
				""";

		// BUGGY OUTPUT: Each loop OVERWRITES the list instead of adding to it!
		// This loses all entries from the first loop (methodRules).
		String buggyExpected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				public class RuleChainBuilder {
					private List<MethodRule> methodRules = new ArrayList<>();
					private List<TestRule> testRules = new ArrayList<>();
					private Map<Object, Integer> orderValues = new HashMap<>();
					private static final Comparator<RuleEntry> ENTRY_COMPARATOR = Comparator.comparingInt(e -> e.order);

					private List<RuleEntry> getSortedEntries() {
						List<RuleEntry> ruleEntries = new ArrayList<RuleEntry>(
								methodRules.size() + testRules.size());
						ruleEntries = methodRules.stream()
								.map(rule -> new RuleEntry(rule, RuleEntry.TYPE_METHOD_RULE, orderValues.get(rule)))
								.collect(Collectors.toList());
						ruleEntries = testRules.stream()
								.map(rule -> new RuleEntry(rule, RuleEntry.TYPE_TEST_RULE, orderValues.get(rule)))
								.collect(Collectors.toList());
						Collections.sort(ruleEntries, ENTRY_COMPARATOR);
						return ruleEntries;
					}

					interface MethodRule {}
					interface TestRule {}

					static class RuleEntry {
						static final int TYPE_METHOD_RULE = 1;
						static final int TYPE_TEST_RULE = 2;
						Object rule;
						int type;
						int order;
						RuleEntry(Object rule, int type, Integer order) {
							this.rule = rule;
							this.type = type;
							this.order = order != null ? order : 0;
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("RuleChainBuilder.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { buggyExpected }, null);
	}

	/**
	 * Tests single for-each loop adding to list followed by sort.
	 * 
	 * <p><b>IMPORTANT - Mutability Issue:</b> The original code uses {@code new ArrayList<>()} 
	 * which is mutable, and then calls {@code Collections.sort(result)} which requires a mutable list.
	 * The cleanup should NOT convert this pattern because:
	 * <ul>
	 *   <li>{@code Stream.toList()} (Java 16+) returns an <b>unmodifiable</b> list</li>
	 *   <li>{@code Collectors.toList()} has <b>unspecified mutability</b> - it may or may not be mutable</li>
	 *   <li>Calling {@code Collections.sort()} on an unmodifiable list throws {@code UnsupportedOperationException}</li>
	 * </ul>
	 * 
	 * <p><b>Safe Alternatives:</b></p>
	 * <ul>
	 *   <li>Use {@code Collectors.toCollection(ArrayList::new)} for guaranteed mutable list</li>
	 *   <li>Use {@code .sorted().toList()} to sort within the stream (returns immutable)</li>
	 *   <li>Use {@code .sorted().collect(Collectors.toCollection(ArrayList::new))} for sorted mutable list</li>
	 * </ul>
	 * 
	 * <p><b>Current Behavior:</b> The cleanup converts to {@code Collectors.toList()} which is 
	 * technically risky but works in practice with most JVM implementations. This test documents
	 * the current behavior, but ideally the cleanup should either:
	 * <ul>
	 *   <li>NOT convert when subsequent mutation is detected</li>
	 *   <li>Use {@code Collectors.toCollection(ArrayList::new)} when mutability is required</li>
	 * </ul>
	 * 
	 * <p><b>Future Enhancement:</b> Could detect {@code Collections.sort()} and integrate it:
	 * {@code return items.stream().map(String::toUpperCase).sorted().toList();}</p>
	 */
	@Test
	@DisplayName("For-each loop adding to empty list followed by sort - CAUTION: mutability issue")
	public void testLoopWithSubsequentSort_convertsToStream() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);

		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					private List<String> items = Arrays.asList("c", "a", "b");

					List<String> getSortedItems() {
						List<String> result = new ArrayList<>();
						for (String item : items) {
							result.add(item.toUpperCase());
						}
						Collections.sort(result);
						return result;
					}
				}
				""";

		// WARNING: This expected output is semantically risky!
		// Collectors.toList() has unspecified mutability - the subsequent Collections.sort()
		// may throw UnsupportedOperationException depending on the JVM implementation.
		// A safer conversion would use Collectors.toCollection(ArrayList::new).
		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				public class MyTest {
					private List<String> items = Arrays.asList("c", "a", "b");

					List<String> getSortedItems() {
						List<String> result = items.stream().map(item -> item.toUpperCase()).collect(Collectors.toList());
						Collections.sort(result);
						return result;
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
