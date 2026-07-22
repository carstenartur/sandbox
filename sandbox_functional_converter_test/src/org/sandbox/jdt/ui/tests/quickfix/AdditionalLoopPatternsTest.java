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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Additional supported and rejected loop-pattern regressions. */
@DisplayName("Additional Loop Pattern Edge Cases")
public class AdditionalLoopPatternsTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	@Test
	@DisplayName("Classic while-loop should NOT convert (no collection iteration)")
	public void testClassicWhileLoop_noConversion() throws CoreException {
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
		assertNoChange("MyTest.java", given);
	}

	@Test
	@DisplayName("While-loop with method call condition should NOT convert")
	public void testWhileWithMethodCondition_noConversion() throws CoreException {
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
		assertNoChange("MyTest.java", given);
	}

	@Test
	@DisplayName("Do-while loop should NOT convert (semantic incompatibility)")
	public void testDoWhileLoop_noConversion() throws CoreException {
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
		assertNoChange("MyTest.java", given);
	}

	@Test
	@DisplayName("Do-while with guaranteed execution should NOT convert")
	public void testDoWhileGuaranteedExecution_noConversion() throws CoreException {
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
		assertNoChange("MyTest.java", given);
	}

	@Test
	@DisplayName("Index-based for-loop to IntStream.range()")
	public void testIndexBasedForLoop_toIntStream() throws CoreException {
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
		assertConversion("MyTest.java", given, expected);
	}

	@Test
	@DisplayName("Index-based collection loop to IntStream.range()")
	public void testIndexBasedCollectionLoop_toStream() throws CoreException {
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
		assertConversion("MyTest.java", given, expected);
	}

	@Test
	@DisplayName("Multiple iterators should NOT convert (no zip() in Java)")
	public void testMultipleIterators_noConversion() throws CoreException {
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
		assertNoChange("MyTest.java", given);
	}

	@Test
	@DisplayName("Iterator with internal hasNext() check should NOT convert")
	public void testIteratorWithInternalCheck_noConversion() throws CoreException {
		String given = """
				package test1;
				import java.util.*;
				public class MyTest {
					void process(List<String> items) {
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							System.out.println(item);
							if (it.hasNext()) {
								String next = it.next();
								System.out.println("Next: " + next);
							}
						}
					}
				}
				""";
		assertNoChange("MyTest.java", given);
	}

	@Test
	@DisplayName("Multiple for-each loops preserve concrete accumulator and source order")
	public void testMultipleLoopsPopulatingList_preservesConcreteAccumulator() throws CoreException {
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
public class RuleChainBuilder {
	private List<MethodRule> methodRules = new ArrayList<>();
	private List<TestRule> testRules = new ArrayList<>();
	private Map<Object, Integer> orderValues = new HashMap<>();
	private static final Comparator<RuleEntry> ENTRY_COMPARATOR = Comparator.comparingInt(e -> e.order);

	private List<RuleEntry> getSortedEntries() {
		List<RuleEntry> ruleEntries = new ArrayList<RuleEntry>(
				methodRules.size() + testRules.size());
		methodRules.forEach(
				rule -> ruleEntries.add(new RuleEntry(rule, RuleEntry.TYPE_METHOD_RULE, orderValues.get(rule))));
		testRules
				.forEach(rule -> ruleEntries.add(new RuleEntry(rule, RuleEntry.TYPE_TEST_RULE, orderValues.get(rule))));
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
		assertConversion("RuleChainBuilder.java", given, expected);
	}

	@Test
	@DisplayName("For-each loop adding to empty list followed by sort - CAUTION: mutability issue")
	public void testLoopWithSubsequentSort_convertsToStream() throws CoreException {
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
		assertConversion("MyTest.java", given, expected);
	}

	private void assertNoChange(String fileName, String given) throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit(fileName, given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	private void assertConversion(String fileName, String given, String expected) throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit(fileName, given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
