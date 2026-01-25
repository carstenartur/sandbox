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

/**
 * Pattern-based tests for loop collect refactorings.
 * 
 * <p>This test class focuses on converting loops that collect/accumulate elements
 * into collections to use the Stream API's collect() terminal operation. Tests cover:</p>
 * <ul>
 *   <li><b>Identity collect</b> - Collecting elements without transformation</li>
 *   <li><b>Mapped collect</b> - Transforming elements before collecting</li>
 *   <li><b>Filtered collect</b> - Conditionally collecting elements</li>
 *   <li><b>Combined patterns</b> - Filter + map + collect chains</li>
 *   <li><b>Collection types</b> - List, Set, and other collection types</li>
 * </ul>
 * 
 * <p><b>Best Practices Demonstrated:</b></p>
 * <ul>
 *   <li>Use {@code Collectors.toList()} for List collection</li>
 *   <li>Use {@code Collectors.toSet()} for Set collection</li>
 *   <li>Use {@code Collectors.toCollection()} for specific collection implementations</li>
 *   <li>Chain filter() before map() for better performance</li>
 *   <li>Use method references where appropriate</li>
 * </ul>
 * 
 * <p><b>Expected Outputs:</b> All transformations produce idiomatic, production-ready
 * Java code following modern Stream API best practices.</p>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.CollectPatternDetector
 */
@DisplayName("Loop Collect Pattern Refactoring Tests")
public class LoopRefactoringCollectTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	// ===========================================
	// IDENTITY COLLECT - NO TRANSFORMATION
	// ===========================================

	/**
	 * Tests simple collect to List without transformation.
	 * 
	 * <p><b>Pattern:</b> {@code for (T item : collection) result.add(item);}</p>
	 * <p><b>Expected:</b> {@code collection.stream().collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> This is the canonical way to collect stream elements to a list</p>
	 */
	@Test
	@DisplayName("Identity collect to List: stream().collect(Collectors.toList())")
	void testIdentityCollectToList() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(List<Integer> items) {
						List<Integer> result = new ArrayList<>();
						for (Integer item : items) {
							result.add(item);
						}
						System.out.println(result);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(List<Integer> items) {
						List<Integer> result = items.stream().collect(Collectors.toList());
						System.out.println(result);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests simple collect to Set without transformation.
	 * 
	 * <p><b>Pattern:</b> {@code for (T item : collection) set.add(item);}</p>
	 * <p><b>Expected:</b> {@code collection.stream().collect(Collectors.toSet())}</p>
	 * <p><b>Best Practice:</b> Use toSet() when collecting unique elements</p>
	 */
	@Test
	@DisplayName("Identity collect to Set: stream().collect(Collectors.toSet())")
	void testIdentityCollectToSet() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						Set<String> uniqueItems = new HashSet<>();
						for (String item : items) {
							uniqueItems.add(item);
						}
						System.out.println(uniqueItems);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(List<String> items) {
						Set<String> uniqueItems = items.stream().collect(Collectors.toSet());
						System.out.println(uniqueItems);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// MAPPED COLLECT - WITH TRANSFORMATION
	// ===========================================

	/**
	 * Tests collect with simple transformation.
	 * 
	 * <p><b>Pattern:</b> {@code for (T item : collection) result.add(transform(item));}</p>
	 * <p><b>Expected:</b> {@code collection.stream().map(item -> transform(item)).collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> Use map() for transformations before collecting</p>
	 */
	@Test
	@DisplayName("Map+collect: stream().map(transform).collect(toList())")
	void testMappedCollect() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(List<Integer> numbers) {
						List<String> strings = new ArrayList<>();
						for (Integer num : numbers) {
							strings.add(num.toString());
						}
						System.out.println(strings);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(List<Integer> numbers) {
						List<String> strings = numbers.stream().map(num -> num.toString()).collect(Collectors.toList());
						System.out.println(strings);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests collect with method reference transformation.
	 * 
	 * <p><b>Pattern:</b> {@code for (T item : collection) result.add(item.method());}</p>
	 * <p><b>Expected:</b> {@code collection.stream().map(T::method).collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> Use method references for better readability when applicable</p>
	 */
	@Test
	@DisplayName("Map with method reference: stream().map(String::toUpperCase)")
	void testMappedCollectWithMethodReference() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						List<String> upperCase = new ArrayList<>();
						for (String item : items) {
							upperCase.add(item.toUpperCase());
						}
						System.out.println(upperCase);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(List<String> items) {
						List<String> upperCase = items.stream().map(String::toUpperCase).collect(Collectors.toList());
						System.out.println(upperCase);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests collect with complex transformation.
	 * 
	 * <p><b>Pattern:</b> {@code for (T item : collection) result.add(complex(item));}</p>
	 * <p><b>Expected:</b> {@code collection.stream().map(item -> complex(item)).collect(Collectors.toList())}</p>
	 */
	@Test
	@DisplayName("Map with complex expression: stream().map(x -> x * 2 + 1)")
	void testMappedCollectComplexExpression() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(List<Integer> numbers) {
						List<Integer> doubled = new ArrayList<>();
						for (Integer num : numbers) {
							doubled.add(num * 2);
						}
						System.out.println(doubled);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(List<Integer> numbers) {
						List<Integer> doubled = numbers.stream().map(num -> num * 2).collect(Collectors.toList());
						System.out.println(doubled);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// FILTERED COLLECT
	// ===========================================

	/**
	 * Tests conditional collect (filter pattern).
	 * 
	 * <p><b>Pattern:</b> {@code for (T item : collection) if (condition) result.add(item);}</p>
	 * <p><b>Expected:</b> {@code collection.stream().filter(condition).collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> Use filter() for conditional collection</p>
	 */
	@Test
	@DisplayName("Filter+collect: stream().filter(predicate).collect(toList())")
	void testFilteredCollect() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						List<String> nonEmpty = new ArrayList<>();
						for (String item : items) {
							if (!item.isEmpty()) {
								nonEmpty.add(item);
							}
						}
						System.out.println(nonEmpty);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(List<String> items) {
						List<String> nonEmpty = items.stream().filter(item -> !item.isEmpty()).collect(Collectors.toList());
						System.out.println(nonEmpty);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests null-filtering collect pattern.
	 * 
	 * <p><b>Pattern:</b> {@code for (T item : collection) if (item != null) result.add(item);}</p>
	 * <p><b>Expected:</b> {@code collection.stream().filter(item -> item != null).collect(Collectors.toList())}</p>
	 * <p><b>Note:</b> Current implementation uses lambda; future enhancement could use Objects::nonNull</p>
	 */
	@Test
	@DisplayName("Null filter: filter(item -> item != null)")
	void testNullFilteredCollect() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						List<String> nonNull = new ArrayList<>();
						for (String item : items) {
							if (item != null) {
								nonNull.add(item);
							}
						}
						System.out.println(nonNull);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(List<String> items) {
						List<String> nonNull = items.stream().filter(item -> item != null).collect(Collectors.toList());
						System.out.println(nonNull);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// FILTER + MAP + COLLECT COMBINED
	// ===========================================

	/**
	 * Tests filter followed by map and collect.
	 * 
	 * <p><b>Pattern:</b> {@code for (T item : c) if (cond) result.add(transform(item));}</p>
	 * <p><b>Expected:</b> {@code c.stream().filter(cond).map(transform).collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> Filter before map to reduce number of transformations</p>
	 */
	@Test
	@DisplayName("Filter+map+collect chain: optimal ordering for performance")
	void testFilterMapCollect() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(List<Integer> numbers) {
						List<String> positiveStrings = new ArrayList<>();
						for (Integer num : numbers) {
							if (num > 0) {
								positiveStrings.add(num.toString());
							}
						}
						System.out.println(positiveStrings);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(List<Integer> numbers) {
						List<String> positiveStrings = numbers.stream().filter(num -> num > 0).map(num -> num.toString()).collect(Collectors.toList());
						System.out.println(positiveStrings);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests complex filter+map chain with multiple conditions.
	 * 
	 * <p><b>Pattern:</b> Complex filtering and transformation</p>
	 * <p><b>Expected:</b> Chain of filter().map().collect() operations</p>
	 */
	@Test
	@DisplayName("Complex filter+map: multiple conditions and transformations")
	void testComplexFilterMapCollect() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						List<String> processed = new ArrayList<>();
						for (String item : items) {
							if (item != null && item.length() > 3) {
								processed.add(item.toUpperCase());
							}
						}
						System.out.println(processed);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(List<String> items) {
						List<String> processed = items.stream().filter(item -> item != null && item.length() > 3).map(item -> item.toUpperCase()).collect(Collectors.toList());
						System.out.println(processed);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	// ===========================================
	// ARRAY SOURCE COLLECT
	// ===========================================

	/**
	 * Tests collect from array source.
	 * 
	 * <p><b>Pattern:</b> {@code for (T item : array) result.add(item);}</p>
	 * <p><b>Expected:</b> {@code Arrays.stream(array).collect(Collectors.toList())}</p>
	 * <p><b>Best Practice:</b> Use Arrays.stream() for array sources</p>
	 */
	@Test
	@DisplayName("Array source collect: Arrays.stream(array).collect(toList())")
	void testArraySourceCollect() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(String[] items) {
						List<String> list = new ArrayList<>();
						for (String item : items) {
							list.add(item);
						}
						System.out.println(list);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(String[] items) {
						List<String> list = Arrays.stream(items).collect(Collectors.toList());
						System.out.println(list);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests map+collect from array source.
	 * 
	 * <p><b>Pattern:</b> {@code for (T item : array) result.add(transform(item));}</p>
	 * <p><b>Expected:</b> {@code Arrays.stream(array).map(transform).collect(Collectors.toList())}</p>
	 */
	@Test
	@DisplayName("Array map+collect: Arrays.stream(arr).map(f).collect(toList())")
	void testArraySourceMapCollect() throws CoreException {
		String input = """
				package test1;
				import java.util.*;
				class E {
					public void process(Integer[] numbers) {
						List<String> strings = new ArrayList<>();
						for (Integer num : numbers) {
							strings.add(num.toString());
						}
						System.out.println(strings);
					}
				}
				""";

		String expected = """
				package test1;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(Integer[] numbers) {
						List<String> strings = Arrays.stream(numbers).map(num -> num.toString()).collect(Collectors.toList());
						System.out.println(strings);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
