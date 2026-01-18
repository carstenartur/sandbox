/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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
 * Tests for collect() conversions in functional loop conversion.
 * 
 * <p>
 * This test class focuses on converting enhanced for-loops that accumulate
 * elements into collections (List, Set) into stream collect() operations.
 * </p>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder
 * @see org.sandbox.jdt.internal.corext.fix.helper.CollectPatternDetector
 */
public class FunctionalLoopCollectTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
	 * Tests simple collect to List conversion.
	 * 
	 * <p><b>Pattern:</b> Enhanced for-loop that adds each element to a list</p>
	 * 
	 * <p><b>Input Pattern:</b></p>
	 * <pre>{@code
	 * List<Integer> result = new ArrayList<>();
	 * for (Integer l : ls) {
	 *     result.add(l);
	 * }
	 * }</pre>
	 * 
	 * <p><b>Output Pattern:</b></p>
	 * <pre>{@code
	 * List<Integer> result = ls.stream().collect(Collectors.toList());
	 * }</pre>
	 */
	@Test
	void test_SimpleCollectToList() throws CoreException {
		String input = """
			package test1;
			import java.util.ArrayList;
			import java.util.Arrays;
			import java.util.List;
			class MyTest {
				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}
				public void test(List<Integer> ls) {
					List<Integer> result = new ArrayList<>();
					for (Integer l : ls) {
						result.add(l);
					}
					System.out.println(result);
				}
			}""";

		String expected = """
			package test1;
			import java.util.ArrayList;
			import java.util.Arrays;
			import java.util.List;
			import java.util.stream.Collectors;
			class MyTest {
				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}
				public void test(List<Integer> ls) {
					List<Integer> result = ls.stream().collect(Collectors.toList());
					System.out.println(result);
				}
			}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);

		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests collect to List with transformation (map).
	 * 
	 * <p><b>Pattern:</b> Enhanced for-loop that transforms elements and adds them to a list</p>
	 * 
	 * <p><b>Input Pattern:</b></p>
	 * <pre>{@code
	 * List<String> result = new ArrayList<>();
	 * for (Integer l : ls) {
	 *     result.add(l.toString());
	 * }
	 * }</pre>
	 * 
	 * <p><b>Output Pattern:</b></p>
	 * <pre>{@code
	 * List<String> result = ls.stream().map(l -> l.toString()).collect(Collectors.toList());
	 * }</pre>
	 */
	@Test
	void test_MappedCollectToList() throws CoreException {
		String input = """
			package test1;
			import java.util.ArrayList;
			import java.util.Arrays;
			import java.util.List;
			class MyTest {
				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}
				public void test(List<Integer> ls) {
					List<String> result = new ArrayList<>();
					for (Integer l : ls) {
						result.add(l.toString());
					}
					System.out.println(result);
				}
			}""";

		String expected = """
			package test1;
			import java.util.ArrayList;
			import java.util.Arrays;
			import java.util.List;
			import java.util.stream.Collectors;
			class MyTest {
				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}
				public void test(List<Integer> ls) {
					List<String> result = ls.stream().map(l -> l.toString()).collect(Collectors.toList());
					System.out.println(result);
				}
			}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);

		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
