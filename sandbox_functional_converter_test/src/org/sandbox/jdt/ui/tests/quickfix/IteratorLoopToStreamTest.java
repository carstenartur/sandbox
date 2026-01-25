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
 * Pattern-based tests for iterator loop to stream conversions.
 * 
 * <p>This test class focuses on converting iterator-based loops to functional streams
 * using modern Java Stream API. Tests are organized by transformation patterns:</p>
 * <ul>
 *   <li><b>forEach patterns</b> - Simple iteration without transformation</li>
 *   <li><b>collect patterns</b> - Accumulating elements into collections</li>
 *   <li><b>map patterns</b> - Transforming elements</li>
 *   <li><b>filter patterns</b> - Filtering elements based on conditions</li>
 *   <li><b>map+filter patterns</b> - Combined transformations</li>
 *   <li><b>reduce patterns</b> - Aggregating values</li>
 * </ul>
 * 
 * <p><b>Best Practices:</b></p>
 * <ul>
 *   <li>All expected outputs use idiomatic, production-ready Java code</li>
 *   <li>Stream operations are preferred over direct iterator manipulation</li>
 *   <li>Method references are used where appropriate for clarity</li>
 *   <li>Collectors are used for terminal operations that build collections</li>
 * </ul>
 * 
 * <p><b>Note:</b> These tests are currently disabled pending activation of
 * ITERATOR_LOOP support in UseFunctionalCallFixCore. Enable tests incrementally
 * as iterator pattern support is implemented.</p>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder
 */
@DisplayName("Iterator Loop to Stream Conversion Tests")
public class IteratorLoopToStreamTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	// ===========================================
	// FOREACH PATTERNS
	// ===========================================

	/**
	 * Tests conversion of while-iterator pattern to forEach.
	 * 
	 * <p><b>Pattern:</b> {@code while (it.hasNext()) { T item = it.next(); ... }}</p>
	 * <p><b>Expected:</b> {@code collection.forEach(item -> ...)}</p>
	 * <p><b>Best Practice:</b> Direct forEach on collection is more idiomatic than stream().forEach()</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("while-iterator forEach: list.forEach(item -> println(item))")
	public void testWhileIterator_forEach() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					void process(List<String> items) {
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				public class E {
					void process(List<String> items) {
						items.forEach(item -> System.out.println(item));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests conversion of for-loop-iterator pattern to forEach.
	 * 
	 * <p><b>Pattern:</b> {@code for (Iterator<T> it = c.iterator(); it.hasNext(); ) { ... }}</p>
	 * <p><b>Expected:</b> {@code collection.forEach(item -> ...)}</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("for-loop-iterator forEach: list.forEach(item -> println(item))")
	public void testForLoopIterator_forEach() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					void process(List<String> items) {
						for (Iterator<String> it = items.iterator(); it.hasNext(); ) {
							String item = it.next();
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				public class E {
					void process(List<String> items) {
						items.forEach(item -> System.out.println(item));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests forEach with multiple statements in loop body.
	 * 
	 * <p><b>Pattern:</b> Multiple statements in loop body</p>
	 * <p><b>Expected:</b> Block lambda with multiple statements</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("forEach with block lambda for multiple statements")
	public void testIterator_forEachWithMultipleStatements() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					void process(List<String> items) {
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							String upper = item.toUpperCase();
							System.out.println(upper);
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				public class E {
					void process(List<String> items) {
						items.forEach(item -> {
							String upper = item.toUpperCase();
							System.out.println(upper);
						});
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// COLLECT PATTERNS
	// ===========================================

	/**
	 * Tests iterator loop that collects elements into a list.
	 * 
	 * <p><b>Pattern:</b> Iterator loop with {@code result.add(item)}</p>
	 * <p><b>Expected:</b> {@code collection.stream().collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> Use Collectors.toList() for collecting to List</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("Iterator collect to List: stream().collect(Collectors.toList())")
	public void testIterator_collectToList() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					List<String> collect(List<String> items) {
						List<String> result = new ArrayList<>();
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							result.add(item);
						}
						return result;
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				public class E {
					List<String> collect(List<String> items) {
						List<String> result = items.stream().collect(Collectors.toList());
						return result;
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests iterator loop that collects elements into a set.
	 * 
	 * <p><b>Pattern:</b> Iterator loop with {@code result.add(item)} to Set</p>
	 * <p><b>Expected:</b> {@code collection.stream().collect(Collectors.toSet())}</p>
	 * <p><b>Best Practice:</b> Use Collectors.toSet() for collecting to Set</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("Iterator collect to Set: stream().collect(Collectors.toSet())")
	public void testIterator_collectToSet() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					Set<String> collectUnique(List<String> items) {
						Set<String> result = new HashSet<>();
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							result.add(item);
						}
						return result;
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				public class E {
					Set<String> collectUnique(List<String> items) {
						Set<String> result = items.stream().collect(Collectors.toSet());
						return result;
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// MAP PATTERNS
	// ===========================================

	/**
	 * Tests iterator loop with transformation (map).
	 * 
	 * <p><b>Pattern:</b> Iterator loop with {@code result.add(transform(item))}</p>
	 * <p><b>Expected:</b> {@code collection.stream().map(item -> transform(item)).collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> Use map() for transformations before collecting</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("Iterator map+collect: stream().map(transform).collect(toList())")
	public void testIterator_mapAndCollect() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					List<String> transformAll(List<Integer> numbers) {
						List<String> result = new ArrayList<>();
						Iterator<Integer> it = numbers.iterator();
						while (it.hasNext()) {
							Integer num = it.next();
							result.add(num.toString());
						}
						return result;
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				public class E {
					List<String> transformAll(List<Integer> numbers) {
						List<String> result = numbers.stream()
							.map(num -> num.toString())
							.collect(Collectors.toList());
						return result;
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests iterator loop with method reference transformation.
	 * 
	 * <p><b>Pattern:</b> Iterator loop with simple method call transformation</p>
	 * <p><b>Expected:</b> {@code collection.stream().map(ClassName::method).collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> Use method references for simple transformations (more concise)</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("Iterator map with method reference: stream().map(String::toUpperCase)")
	public void testIterator_mapWithMethodReference() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					List<String> toUpperAll(List<String> items) {
						List<String> result = new ArrayList<>();
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							result.add(item.toUpperCase());
						}
						return result;
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				public class E {
					List<String> toUpperAll(List<String> items) {
						List<String> result = items.stream()
							.map(String::toUpperCase)
							.collect(Collectors.toList());
						return result;
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// FILTER PATTERNS
	// ===========================================

	/**
	 * Tests iterator loop with conditional collection (filter).
	 * 
	 * <p><b>Pattern:</b> Iterator loop with {@code if (condition) result.add(item)}</p>
	 * <p><b>Expected:</b> {@code collection.stream().filter(condition).collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> Use filter() for conditional collection</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("Iterator filter+collect: stream().filter(predicate).collect(toList())")
	public void testIterator_filterAndCollect() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					List<String> filterNonEmpty(List<String> items) {
						List<String> result = new ArrayList<>();
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							if (!item.isEmpty()) {
								result.add(item);
							}
						}
						return result;
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				public class E {
					List<String> filterNonEmpty(List<String> items) {
						List<String> result = items.stream()
							.filter(item -> !item.isEmpty())
							.collect(Collectors.toList());
						return result;
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// MAP + FILTER COMBINED PATTERNS
	// ===========================================

	/**
	 * Tests iterator loop with both filtering and mapping.
	 * 
	 * <p><b>Pattern:</b> Iterator loop with {@code if (condition) result.add(transform(item))}</p>
	 * <p><b>Expected:</b> {@code collection.stream().filter(condition).map(transform).collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> Chain filter() before map() for optimal performance</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("Iterator filter+map+collect: stream().filter().map().collect()")
	public void testIterator_filterMapAndCollect() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					List<String> processPositive(List<Integer> numbers) {
						List<String> result = new ArrayList<>();
						Iterator<Integer> it = numbers.iterator();
						while (it.hasNext()) {
							Integer num = it.next();
							if (num > 0) {
								result.add(num.toString());
							}
						}
						return result;
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				public class E {
					List<String> processPositive(List<Integer> numbers) {
						List<String> result = numbers.stream()
							.filter(num -> num > 0)
							.map(num -> num.toString())
							.collect(Collectors.toList());
						return result;
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// REDUCE PATTERNS
	// ===========================================

	/**
	 * Tests iterator loop with sum reduction.
	 * 
	 * <p><b>Pattern:</b> Iterator loop with {@code sum += item}</p>
	 * <p><b>Expected:</b> {@code collection.stream().mapToInt(i -> i).sum()}</p>
	 * <p><b>Best Practice:</b> Use specialized streams (IntStream) for primitive operations</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("Iterator sum reduction: stream().mapToInt(i -> i).sum()")
	public void testIterator_sumReduction() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					int calculateSum(List<Integer> numbers) {
						int sum = 0;
						Iterator<Integer> it = numbers.iterator();
						while (it.hasNext()) {
							Integer num = it.next();
							sum += num;
						}
						return sum;
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				public class E {
					int calculateSum(List<Integer> numbers) {
						int sum = numbers.stream()
							.mapToInt(num -> num)
							.sum();
						return sum;
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// NEGATIVE TESTS - UNSAFE PATTERNS
	// ===========================================

	/**
	 * Tests that iterator loops with remove() are NOT converted.
	 * 
	 * <p><b>Reason:</b> Iterator.remove() modifies the underlying collection during iteration.
	 * This cannot be safely converted to streams which are designed for functional,
	 * non-mutating operations. While removeIf() exists as an alternative, it has different
	 * semantics and should be suggested separately.</p>
	 * 
	 * <p><b>Pattern:</b> {@code while(it.hasNext()) { if(condition) it.remove(); }}</p>
	 * <p><b>Expected:</b> No conversion (loop remains unchanged)</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("Iterator.remove() prevents conversion - unsafe pattern")
	public void testIterator_withRemove_notConverted() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					void removeEmpty(List<String> items) {
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							if (item.isEmpty()) {
								it.remove();
							}
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that iterator loops with multiple next() calls are NOT converted.
	 * 
	 * <p><b>Reason:</b> Multiple next() calls in a single iteration consume multiple elements,
	 * which cannot be expressed in the standard stream forEach/map/filter pattern. This would
	 * require more complex stream operations (windowing, batching) that are not semantically
	 * equivalent.</p>
	 * 
	 * <p><b>Pattern:</b> {@code while(it.hasNext()) { T a = it.next(); T b = it.next(); }}</p>
	 * <p><b>Expected:</b> No conversion (loop remains unchanged)</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("Multiple next() calls prevent conversion - unsafe pattern")
	public void testIterator_multipleNext_notConverted() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					void processPairs(List<String> items) {
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String first = it.next();
							String second = it.next();
							System.out.println(first + second);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that iterator loops with break statements are NOT converted.
	 * 
	 * <p><b>Reason:</b> Break statements have no direct equivalent in streams. While
	 * operations like findFirst() short-circuit, they cannot replicate break semantics
	 * when side effects occur before the break condition. Future enhancement could
	 * support conversion to takeWhile() in specific cases.</p>
	 * 
	 * <p><b>Pattern:</b> {@code while(it.hasNext()) { if(condition) break; }}</p>
	 * <p><b>Expected:</b> No conversion (loop remains unchanged)</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("Break statement prevents conversion - not yet supported")
	public void testIterator_withBreak_notConverted() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					void processUntilEmpty(List<String> items) {
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							if (item.isEmpty()) {
								break;
							}
							System.out.println(item);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that iterator loops with external state modification are NOT converted.
	 * 
	 * <p><b>Reason:</b> Modifying external variables (other than the accumulator pattern)
	 * introduces side effects that change program semantics. Streams are designed for
	 * functional, stateless operations. Converting such loops would obscure the side
	 * effects and make code harder to understand.</p>
	 * 
	 * <p><b>Pattern:</b> {@code while(it.hasNext()) { externalVar = item; }}</p>
	 * <p><b>Expected:</b> No conversion (loop remains unchanged)</p>
	 */
	@Disabled("Enable after ITERATOR_LOOP is activated in UseFunctionalCallFixCore")
	@Test
	@DisplayName("External state modification prevents conversion - side effect")
	public void testIterator_withExternalModification_notConverted() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);

		String given = """
				package test;
				import java.util.*;
				public class E {
					void trackLast(List<String> items) {
						String lastItem = null;
						Iterator<String> it = items.iterator();
						while (it.hasNext()) {
							String item = it.next();
							lastItem = item;
							System.out.println(item);
						}
						System.out.println("Last: " + lastItem);
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
