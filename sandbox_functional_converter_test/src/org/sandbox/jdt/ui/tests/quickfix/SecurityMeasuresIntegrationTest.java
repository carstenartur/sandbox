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
	 * 
	 * <p>CopyOnWriteArrayList has weakly consistent iterators and does not support
	 * iterator.remove(). The cleanup should be able to convert simple forEach patterns
	 * but must not generate iterator.remove() calls.</p>
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
						list.forEach(System.out::println);
					}
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests that list.removeIf() modification is detected and blocks conversion.
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
	 * Tests that list.replaceAll() modification is detected and blocks conversion.
	 */
	@Test
	@DisplayName("list.replaceAll() should block conversion")
	void testReplaceAllBlocksConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				public class E {
					public void foo(List<String> list, List<String> other) {
						for (String item : other) {
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
	 * Tests that list.sort() modification is detected and blocks conversion.
	 */
	@Test
	@DisplayName("list.sort() should block conversion")
	void testSortBlocksConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				public class E {
					public void foo(List<String> list, List<String> other) {
						for (String item : other) {
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
	 * Tests that map.putIfAbsent() modification is detected and blocks conversion.
	 */
	@Test
	@DisplayName("map.putIfAbsent() should block conversion")
	void testMapPutIfAbsentBlocksConversion() throws CoreException {
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

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that map.compute() modification is detected and blocks conversion.
	 */
	@Test
	@DisplayName("map.compute() should block conversion")
	void testMapComputeBlocksConversion() throws CoreException {
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

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that map.computeIfAbsent() modification is detected and blocks conversion.
	 */
	@Test
	@DisplayName("map.computeIfAbsent() should block conversion")
	void testMapComputeIfAbsentBlocksConversion() throws CoreException {
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

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that map.merge() modification is detected and blocks conversion.
	 */
	@Test
	@DisplayName("map.merge() should block conversion")
	void testMapMergeBlocksConversion() throws CoreException {
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

		ICompilationUnit cu = pack.createCompilationUnit("E.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	/**
	 * Tests that this.list.remove() field access modification is detected and blocks conversion.
	 */
	@Test
	@DisplayName("this.list.remove() should block conversion")
	void testFieldAccessModificationBlocksConversion() throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", false, null);
		
		String input = """
				package test;
				import java.util.List;
				import java.util.ArrayList;
				public class E {
					private List<String> list = new ArrayList<>();
					
					public void foo(List<String> items) {
						for (String item : items) {
							this.list.add(item);
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
}
