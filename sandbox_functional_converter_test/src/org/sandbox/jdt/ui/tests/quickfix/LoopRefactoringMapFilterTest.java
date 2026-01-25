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
 * Additional map and filter combination patterns for loop refactoring.
 * 
 * <p>This test class supplements FunctionalLoopFilterMapTest with additional
 * patterns and best practice examples for map+filter combinations:</p>
 * <ul>
 *   <li><b>Filter-first patterns</b> - Filtering before mapping for performance</li>
 *   <li><b>Map-filter-map chains</b> - Complex transformation pipelines</li>
 *   <li><b>Multiple filters</b> - Combining multiple filter conditions</li>
 *   <li><b>Method reference patterns</b> - Using method references in map/filter</li>
 *   <li><b>Null-safe patterns</b> - Filtering nulls before transformations</li>
 * </ul>
 * 
 * <p><b>Best Practices Demonstrated:</b></p>
 * <ul>
 *   <li>Filter before map when possible (reduces transformations)</li>
 *   <li>Use method references for simple operations</li>
 *   <li>Combine multiple filter conditions with && when appropriate</li>
 *   <li>Use Objects::nonNull for null filtering</li>
 *   <li>Chain operations in logical order (filter → map → collect/forEach)</li>
 * </ul>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder
 */
@DisplayName("Additional Map+Filter Pattern Tests")
public class LoopRefactoringMapFilterTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
	 * Tests filter-first pattern for optimal performance.
	 * 
	 * <p><b>Pattern:</b> Filter elements before transforming them</p>
	 * <p><b>Expected:</b> {@code stream().filter(condition).map(transform).forEach(action)}</p>
	 * <p><b>Best Practice:</b> Filtering first reduces number of transformations</p>
	 */
	@Test
	@DisplayName("Filter-first: stream().filter().map().forEach()")
	void testFilterBeforeMap() throws CoreException {
		String input = """
				package test;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						for (String item : items) {
							if (item.length() > 3) {
								String upper = item.toUpperCase();
								System.out.println(upper);
							}
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						items.stream().filter(item -> item.length() > 3).map(item -> item.toUpperCase()).forEachOrdered(upper -> System.out.println(upper));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests map-filter-map chain.
	 * 
	 * <p><b>Pattern:</b> Transform → filter → transform chain</p>
	 * <p><b>Expected:</b> {@code stream().map().filter().map().forEach()}</p>
	 */
	@Test
	@DisplayName("Map-filter-map: multi-step transformation")
	void testMapFilterMap() throws CoreException {
		String input = """
				package test;
				import java.util.*;
				class E {
					public void process(List<Integer> numbers) {
						for (Integer num : numbers) {
							int doubled = num * 2;
							if (doubled > 10) {
								String result = "Value: " + doubled;
								System.out.println(result);
							}
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				class E {
					public void process(List<Integer> numbers) {
						numbers.stream().map(num -> num * 2).filter(doubled -> doubled > 10).map(doubled -> "Value: " + doubled).forEachOrdered(result -> System.out.println(result));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests multiple independent filters.
	 * 
	 * <p><b>Pattern:</b> Multiple sequential if statements</p>
	 * <p><b>Expected:</b> {@code stream().filter(cond1).filter(cond2).forEach()}</p>
	 * <p><b>Note:</b> Could be optimized to single filter with && but current
	 * implementation keeps them separate</p>
	 */
	@Test
	@DisplayName("Multiple filters: sequential conditions")
	void testMultipleFilters() throws CoreException {
		String input = """
				package test;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						for (String item : items) {
							if (item != null) {
								if (item.length() > 0) {
									System.out.println(item);
								}
							}
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						items.stream().filter(item -> item != null).filter(item -> item.length() > 0).forEachOrdered(item -> System.out.println(item));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests null-safe map operation.
	 * 
	 * <p><b>Pattern:</b> Null check before transformation</p>
	 * <p><b>Expected:</b> {@code stream().filter(Objects::nonNull).map(transform).forEach()}</p>
	 * <p><b>Best Practice:</b> Use Objects::nonNull for idiomatic null filtering</p>
	 */
	@Test
	@DisplayName("Null-safe map: filter(Objects::nonNull).map()")
	void testNullSafeMap() throws CoreException {
		String input = """
				package test;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						for (String item : items) {
							if (item != null) {
								String upper = item.toUpperCase();
								System.out.println(upper);
							}
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				import java.util.Objects;
				class E {
					public void process(List<String> items) {
						items.stream().filter(Objects::nonNull).map(item -> item.toUpperCase()).forEachOrdered(upper -> System.out.println(upper));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests combined AND condition in filter.
	 * 
	 * <p><b>Pattern:</b> Single if statement with && condition</p>
	 * <p><b>Expected:</b> {@code stream().filter(cond1 && cond2).map().forEach()}</p>
	 * <p><b>Best Practice:</b> Combined conditions in single filter is more efficient</p>
	 */
	@Test
	@DisplayName("Combined filter: single filter with && condition")
	void testCombinedFilterCondition() throws CoreException {
		String input = """
				package test;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						for (String item : items) {
							if (item != null && item.length() > 3) {
								String upper = item.toUpperCase();
								System.out.println(upper);
							}
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				class E {
					public void process(List<String> items) {
						items.stream().filter(item -> item != null && item.length() > 3).map(item -> item.toUpperCase()).forEachOrdered(upper -> System.out.println(upper));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests filter with transformation to collection.
	 * 
	 * <p><b>Pattern:</b> Filter then map then collect</p>
	 * <p><b>Expected:</b> {@code stream().filter().map().collect(Collectors.toList())}</p>
	 */
	@Test
	@DisplayName("Filter-map-collect: stream().filter().map().collect()")
	void testFilterMapCollect() throws CoreException {
		String input = """
				package test;
				import java.util.*;
				class E {
					public void process(List<Integer> numbers) {
						List<String> results = new ArrayList<>();
						for (Integer num : numbers) {
							if (num > 0) {
								results.add(num.toString());
							}
						}
						System.out.println(results);
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				import java.util.stream.Collectors;
				class E {
					public void process(List<Integer> numbers) {
						List<String> results = numbers.stream().filter(num -> num > 0).map(num -> num.toString()).collect(Collectors.toList());
						System.out.println(results);
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests complex transformation with multiple steps.
	 * 
	 * <p><b>Pattern:</b> Multiple variable transformations with filtering</p>
	 * <p><b>Expected:</b> Chain of map operations followed by filter</p>
	 */
	@Test
	@DisplayName("Complex chain: multiple transformations")
	void testComplexChain() throws CoreException {
		String input = """
				package test;
				import java.util.*;
				class E {
					public void process(List<Integer> numbers) {
						for (Integer num : numbers) {
							int doubled = num * 2;
							int plusTen = doubled + 10;
							if (plusTen < 100) {
								System.out.println(plusTen);
							}
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.*;
				class E {
					public void process(List<Integer> numbers) {
						numbers.stream().map(num -> num * 2).map(doubled -> doubled + 10).filter(plusTen -> plusTen < 100).forEachOrdered(plusTen -> System.out.println(plusTen));
					}
				}
				""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
