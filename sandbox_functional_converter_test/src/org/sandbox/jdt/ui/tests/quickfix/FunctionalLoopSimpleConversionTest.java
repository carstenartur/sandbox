/*******************************************************************************
 * Copyright (c) 2021 Alexandru Gyori and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Alexandru Gyori - original code
 *     Carsten Hammer - initial port to Eclipse
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for simple forEach conversions in functional loop conversion.
 * 
 * <p>
 * This test class focuses on basic loop-to-forEach conversions without complex
 * transformations like filtering, mapping, or reduction operations. These are
 * the simplest cases where an enhanced for-loop is directly converted to a
 * {@code forEach()} call.
 * </p>
 * 
 * <p>
 * <b>Pattern:</b> Enhanced for-loops with simple bodies (single or multiple
 * statements) that can be converted to {@code collection.forEach(item -> ...)}
 * </p>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder
 * @see org.sandbox.jdt.internal.corext.fix.helper.PreconditionsChecker
 */
public class FunctionalLoopSimpleConversionTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
	 * Test data enum with input/expected output pairs. Each enum value represents
	 * a specific test scenario for simple forEach conversions.
	 */
	enum TestCase {
		/**
		 * Tests simple forEach conversion without transformations.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> A simple enhanced for-loop with a single statement
		 * body is converted to {@code collection.forEach(item -> statement)}.
		 * </p>
		 * 
		 * <p>
		 * <b>Input Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * for (Integer l : ls)
		 *     System.out.println(l);
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Output Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * ls.forEach(l -> System.out.println(l));
		 * }
		 * </pre>
		 * 
		 * @see org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation.OperationType#FOREACH
		 */
		SIMPLECONVERT("""
				package test1;
				import java.util.Arrays;
				import java.util.List;
				class MyTest {
				    public static void main(String[] args) {
				        new MyTest().test(Arrays.asList(1, 2, 3));
				    }
				    public void test(List<Integer> ls) {
				        for (Integer l : ls)
				            System.out.println(l);
				    }
				}""",

				"""
						package test1;
						import java.util.Arrays;
						import java.util.List;
						class MyTest {
						    public static void main(String[] args) {
						        new MyTest().test(Arrays.asList(1, 2, 3));
						    }
						    public void test(List<Integer> ls) {
						        ls.forEach(l -> System.out.println(l));
						    }
						}"""),

		/**
		 * Tests forEach conversion with empty collections.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Even for empty collections, the loop structure
		 * should be converted to {@code forEach()}. The behavior is identical (no-op
		 * for empty collection).
		 * </p>
		 * 
		 * <p>
		 * <b>Input Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * List<String> items = new ArrayList<>();
		 * for (String item : items) {
		 *     System.out.println(item);
		 * }
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Output Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * items.forEach(item -> System.out.println(item));
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Edge Case:</b> Tests that the converter handles empty collection
		 * initialization correctly.
		 * </p>
		 */
		EmptyCollectionHandling("""
				package test1;

				import java.util.List;
				import java.util.ArrayList;

				class MyTest {
				    public void processEmpty() {
				        List<String> items = new ArrayList<>();
				        for (String item : items) {
				            System.out.println(item);
				        }
				    }
				}""",

				"""
						package test1;

						import java.util.List;
						import java.util.ArrayList;

						class MyTest {
						    public void processEmpty() {
						        List<String> items = new ArrayList<>();
						        items.forEach(item -> System.out.println(item));
						    }
						}"""),

		/**
		 * Tests variable naming in lambda expressions with transformations.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> When a loop variable is not used in the forEach
		 * body, the cleanup uses {@code _item} as the lambda parameter name.
		 * Intermediate variables in map operations retain their original names.
		 * </p>
		 * 
		 * <p>
		 * <b>Input Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * for (String str : strs) {
		 *     String s = "foo";
		 *     s = s.toString();
		 *     System.out.println(s);
		 * }
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Output Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * strs.stream()
		 *     .map(_item -> "foo")
		 *     .map(s -> s.toString())
		 *     .forEachOrdered(s -> { System.out.println(s); });
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Note:</b> This demonstrates the naming convention where unused loop
		 * variables become {@code _item} in the lambda.
		 * </p>
		 * 
		 * @see org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder#parseLoopBody
		 */
		BeautificationWorks("""
				package test1;

				import java.util.ArrayList;
				import java.util.List;

				/**
				 *
				 * @author alexandrugyori
				 */
				class JavaApplication1 {

				    /**
				     * @param args the command line arguments
				     */
				    public boolean b() {
				        // TODO code application logic here
				        List<String> strs = new ArrayList<String>();
				        int i = 0;
				        int j = 0;
				        for(String str: strs)
				        {
				            String s = "foo";
				            s=s.toString();
				            System.out.println(s);

				        }
				        return false;

				    }
				}""",

				"""
						package test1;

						import java.util.ArrayList;
						import java.util.List;

						/**
						 *
						 * @author alexandrugyori
						 */
						class JavaApplication1 {

						    /**
						     * @param args the command line arguments
						     */
						    public boolean b() {
						        // TODO code application logic here
						        List<String> strs = new ArrayList<String>();
						        int i = 0;
						        int j = 0;
						        strs.stream().map(_item -> "foo").map(s -> s.toString()).forEachOrdered(s -> {
						            System.out.println(s);
						        });
						        return false;

						    }
						}"""),

		/**
		 * Tests lambda parameter naming when loop variable is unused in body.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> When the original loop variable is not used in the
		 * loop body at all, the cleanup uses {@code _item} as the lambda parameter in
		 * the final {@code forEach()} operation.
		 * </p>
		 * 
		 * <p>
		 * <b>Input Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * for (String str : strs) {
		 *     String s = "foo";
		 *     s = s.toString();
		 *     System.out.println();  // Note: 's' is not used here
		 * }
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Output Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * strs.stream()
		 *     .map(_item -> "foo")
		 *     .map(s -> s.toString())
		 *     .forEachOrdered(_item -> { System.out.println(); });
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Note:</b> The final forEach uses {@code _item} instead of {@code s}
		 * because {@code s} is not referenced in the forEach body.
		 * </p>
		 */
		BeautificationWorks2("""
				package test1;

				import java.util.ArrayList;
				import java.util.List;

				/**
				 *
				 * @author alexandrugyori
				 */
				class JavaApplication1 {

				    /**
				     * @param args the command line arguments
				     */
				    public boolean b() {
				        // TODO code application logic here
				        List<String> strs = new ArrayList<String>();
				        int i = 0;
				        int j = 0;
				        for(String str: strs)
				        {
				            String s = "foo";
				            s=s.toString();
				            System.out.println();

				        }
				        return false;

				    }
				}""",

				"""
						package test1;

						import java.util.ArrayList;
						import java.util.List;

						/**
						 *
						 * @author alexandrugyori
						 */
						class JavaApplication1 {

						    /**
						     * @param args the command line arguments
						     */
						    public boolean b() {
						        // TODO code application logic here
						        List<String> strs = new ArrayList<String>();
						        int i = 0;
						        int j = 0;
						        strs.stream().map(_item -> "foo").map(s -> s.toString()).forEachOrdered(_item -> {
						            System.out.println();
						        });
						        return false;

						    }
						}""");

		final String input;
		final String expected;

		TestCase(String input, String expected) {
			this.input = input;
			this.expected = expected;
		}
	}

	@ParameterizedTest
	@EnumSource(TestCase.class)
	@DisplayName("Test simple forEach conversion")
	void testConversion(TestCase testCase) throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", testCase.input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { testCase.expected },
				null);
	}
}
