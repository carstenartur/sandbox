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
 * Integration tests for Issue #670 security measures.
 * 
 * <p>Tests high-priority security features:</p>
 * <ul>
 * <li>Concurrent collection detection</li>
 * <li>Map modification method detection</li>
 * <li>Field access modification detection</li>
 * <li>Additional collection modification methods (removeIf, replaceAll, sort)</li>
 * </ul>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
 */
@DisplayName("Issue #670 Security Measures Integration Tests")
public class SecurityMeasuresIntegrationTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
	 * Tests that loops over CopyOnWriteArrayList are handled correctly.
	 * The cleanup produces a lambda form, not a method reference.
	 */
	@Test
	@DisplayName("CopyOnWriteArrayList - simple forEach conversion should work")
	void testCopyOnWriteArrayListSimpleForEach() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.concurrent.CopyOnWriteArrayList;
				public class E {
					public void foo(CopyOnWriteArrayList<String> list) {
						for (String item : list) {
							System.out.println(item);
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.concurrent.CopyOnWriteArrayList;
				public class E {
					public void foo(CopyOnWriteArrayList<String> list) {
						list.forEach(item -> System.out.println(item));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that list.removeIf() modification on the iterated collection blocks conversion.
	 */
	@Test
	@DisplayName("list.removeIf() should block conversion")
	void testRemoveIfBlocksConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				public class E {
					public void foo(List<String> list) {
						for (String item : list) {
							list.removeIf(s -> s.isEmpty());
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that list.replaceAll() modification on the iterated collection blocks conversion.
	 */
	@Test
	@DisplayName("list.replaceAll() on iterated collection should block conversion")
	void testReplaceAllBlocksConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				public class E {
					public void foo(List<String> list) {
						for (String item : list) {
							list.replaceAll(String::toUpperCase);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that list.sort() modification on the iterated collection blocks conversion.
	 */
	@Test
	@DisplayName("list.sort() on iterated collection should block conversion")
	void testSortBlocksConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				public class E {
					public void foo(List<String> list) {
						for (String item : list) {
							list.sort(null);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that map.putIfAbsent() on a different collection allows conversion.
	 * The CollectionModificationDetector only blocks when the SAME collection is modified.
	 */
	@Test
	@DisplayName("map.putIfAbsent() on different collection allows conversion")
	void testMapPutIfAbsentAllowsConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				import java.util.Map;
				public class E {
					public void foo(List<String> list, Map<String, String> map) {
						for (String item : list) {
							map.putIfAbsent(item, "default");
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.List;
				import java.util.Map;
				public class E {
					public void foo(List<String> list, Map<String, String> map) {
						list.forEach(item -> map.putIfAbsent(item, "default"));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that map.compute() on a different collection allows conversion.
	 * The CollectionModificationDetector only blocks when the SAME collection is modified.
	 */
	@Test
	@DisplayName("map.compute() on different collection allows conversion")
	void testMapComputeAllowsConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				import java.util.Map;
				public class E {
					public void foo(List<String> list, Map<String, Integer> map) {
						for (String item : list) {
							map.compute(item, (k, v) -> v == null ? 1 : v + 1);
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.List;
				import java.util.Map;
				public class E {
					public void foo(List<String> list, Map<String, Integer> map) {
						list.forEach(item -> map.compute(item, (k, v) -> v == null ? 1 : v + 1));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that map.computeIfAbsent() on a different collection allows conversion.
	 * The CollectionModificationDetector only blocks when the SAME collection is modified.
	 */
	@Test
	@DisplayName("map.computeIfAbsent() on different collection allows conversion")
	void testMapComputeIfAbsentAllowsConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				import java.util.Map;
				public class E {
					public void foo(List<String> list, Map<String, String> map) {
						for (String item : list) {
							map.computeIfAbsent(item, k -> "value");
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.List;
				import java.util.Map;
				public class E {
					public void foo(List<String> list, Map<String, String> map) {
						list.forEach(item -> map.computeIfAbsent(item, k -> "value"));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that map.merge() on a different collection allows conversion.
	 * The CollectionModificationDetector only blocks when the SAME collection is modified.
	 */
	@Test
	@DisplayName("map.merge() on different collection allows conversion")
	void testMapMergeAllowsConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				import java.util.Map;
				public class E {
					public void foo(List<String> list, Map<String, Integer> map) {
						for (String item : list) {
							map.merge(item, 1, Integer::sum);
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.List;
				import java.util.Map;
				public class E {
					public void foo(List<String> list, Map<String, Integer> map) {
						list.forEach(item -> map.merge(item, 1, Integer::sum));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that this.items.add() field access modification is detected and blocks
	 * conversion when iterating the same field by simple name.
	 */
	@Test
	@DisplayName("this.items.add() should block conversion when iterating same field")
	void testFieldAccessModificationBlocksConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				import java.util.ArrayList;
				public class E {
					private List<String> items = new ArrayList<>();
					public void foo() {
						for (String item : items) {
							this.items.add("x");
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that ConcurrentHashMap iteration works for simple cases.
	 */
	@Test
	@DisplayName("ConcurrentHashMap - simple forEach conversion should work")
	void testConcurrentHashMapSimpleForEach() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.concurrent.ConcurrentHashMap;
				import java.util.Map;
				public class E {
					public void foo(ConcurrentHashMap<String, String> map) {
						for (Map.Entry<String, String> entry : map.entrySet()) {
							System.out.println(entry.getKey());
						}
					}
				}
				""";

		String expected = """
				package test;
				import java.util.concurrent.ConcurrentHashMap;
				import java.util.Map;
				public class E {
					public void foo(ConcurrentHashMap<String, String> map) {
						map.entrySet().forEach(entry -> System.out.println(entry.getKey()));
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
	
	/**
	 * Tests that getList().remove() getter method invocation blocks conversion.
	 */
	@Test
	@DisplayName("getList().remove() should block conversion")
	void testGetterMethodInvocationBlocksConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				import java.util.ArrayList;
				public class E {
					private List<String> getList() {
						return new ArrayList<>();
					}
					public void foo(List<String> list) {
						for (String item : list) {
							getList().add(item);
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
	
	/**
	 * Tests that getItems().clear() blocks conversion when iterating over items.
	 */
	@Test
	@DisplayName("getItems().clear() should block conversion when iterating items")
	void testGetItemsClearBlocksConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				import java.util.ArrayList;
				public class E {
					private List<String> getItems() {
						return new ArrayList<>();
					}
					public void foo(List<String> items) {
						for (String item : items) {
							getItems().clear();
						}
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
