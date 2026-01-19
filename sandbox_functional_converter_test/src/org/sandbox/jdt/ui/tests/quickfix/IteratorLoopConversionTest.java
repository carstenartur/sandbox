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
 * Tests for converting traditional iterator-based while loops to functional stream operations.
 * 
 * <p>
 * This test class focuses on converting the classic iterator pattern used in legacy Java code
 * to modern functional stream operations. The pattern being converted is:
 * </p>
 * <pre>{@code
 * Iterator<T> it = collection.iterator();
 * while (it.hasNext()) {
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
 * @see org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopPattern
 * @see org.sandbox.jdt.internal.corext.fix.helper.IteratorLoopToFunctional
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 */
public class IteratorLoopConversionTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

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

		IPackageFragment pack = context.getfSourceFolder().createPackageFragment("test1", false, null);
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

		IPackageFragment pack = context.getfSourceFolder().createPackageFragment("test1", false, null);
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

		IPackageFragment pack = context.getfSourceFolder().createPackageFragment("test1", false, null);
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

		IPackageFragment pack = context.getfSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output }, null);
	}
}
