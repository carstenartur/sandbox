/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer and others.
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for anyMatch, noneMatch, allMatch patterns in functional loop
 * conversion.
 * 
 * <p>
 * This test class focuses on converting enhanced for-loops with early return
 * patterns into {@code stream().anyMatch()}, {@code stream().noneMatch()}, or
 * {@code stream().allMatch()} operations.
 * </p>
 * 
 * <p>
 * <b>Patterns covered:</b>
 * </p>
 * <ul>
 * <li>anyMatch - Loop that returns true if any element matches condition</li>
 * <li>noneMatch - Loop that returns false if any element matches condition</li>
 * <li>allMatch - Loop that returns false if any element doesn't match
 * condition</li>
 * <li>Chained operations before match (map + anyMatch/noneMatch/allMatch)</li>
 * </ul>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder
 * @see org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation.OperationType#ANYMATCH
 * @see org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation.OperationType#NONEMATCH
 * @see org.sandbox.jdt.internal.corext.fix.helper.ProspectiveOperation.OperationType#ALLMATCH
 */
public class FunctionalLoopMatchPatternTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
	 * Test data enum with input/expected output pairs.
	 */
	enum TestCase {
		/**
		 * Tests anyMatch with chained map operations.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Loop with early return true on condition match is
		 * converted to {@code stream().map(...).anyMatch(...)}.
		 * </p>
		 * 
		 * <p>
		 * <b>Input Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * for (Integer l : ls) {
		 * 	String s = l.toString();
		 * 	Object o = foo(s);
		 * 	if (o == null)
		 * 		return true;
		 * }
		 * return false;
		 * }
		 * </pre>
		 * 
		 * <p>
		 * <b>Output Pattern:</b>
		 * </p>
		 * 
		 * <pre>
		 * {@code
		 * if (ls.stream().map(l -> l.toString()).map(s -> foo(s)).anyMatch(o -> (o == null))) {
		 * 	return true;
		 * }
		 * return false;
		 * }
		 * </pre>
		 */
		ChainedAnyMatch("""
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {

				    public static void main(String[] args) {
				        new MyTest().test(Arrays.asList(1, 2, 3));
				    }

				    public Boolean test(List<Integer> ls) {
				        for(Integer l:ls)
				        {
				            String s = l.toString();
				            Object o = foo(s);
				            if(o==null)
				                return true;
				        }

				        return false;


				    }

				    Object foo(Object o)
				    {
				        return o;
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

						    public Boolean test(List<Integer> ls) {
						        if (ls.stream().map(l -> l.toString()).map(s -> foo(s)).anyMatch(o -> (o==null))) {
						            return true;
						        }

						        return false;


						    }

						    Object foo(Object o)
						    {
						        return o;
						    }
						}"""),

		/**
		 * Tests noneMatch with chained map operations.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Loop with early return false on condition match is
		 * converted to {@code stream().map(...).noneMatch(...)}.
		 * </p>
		 */
		ChainedNoneMatch("""
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {

				    public static void main(String[] args) {
				        new MyTest().test(Arrays.asList(1, 2, 3));
				    }

				    public Boolean test(List<Integer> ls) {
				        for(Integer l:ls)
				        {
				            String s = l.toString();
				            Object o = foo(s);
				            if(o==null)
				                return false;
				        }

				        return true;


				    }

				    Object foo(Object o)
				    {
				        return o;
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

						    public Boolean test(List<Integer> ls) {
						        if (!ls.stream().map(l -> l.toString()).map(s -> foo(s)).noneMatch(o -> (o==null))) {
						            return false;
						        }

						        return true;


						    }

						    Object foo(Object o)
						    {
						        return o;
						    }
						}"""),

		/**
		 * Tests simple allMatch pattern.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Loop that returns false if any element doesn't match
		 * condition is converted to {@code stream().allMatch(...)}.
		 * </p>
		 */
		SimpleAllMatch("""
				package test1;

				import java.util.List;

				class MyTest {
				    public boolean allValid(List<String> items) {
				        for (String item : items) {
				            if (!item.startsWith("valid")) {
				                return false;
				            }
				        }
				        return true;
				    }
				}""",

				"""
						package test1;

						import java.util.List;

						class MyTest {
						    public boolean allValid(List<String> items) {
						        if (!items.stream().allMatch(item -> item.startsWith("valid"))) {
						            return false;
						        }
						        return true;
						    }
						}"""),

		/**
		 * Tests allMatch with null checking.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Null checks in loops are converted to
		 * {@code allMatch()} predicates.
		 * </p>
		 */
		AllMatchWithNullCheck("""
				package test1;

				import java.util.List;

				class MyTest {
				    public boolean allNonNull(List<Object> items) {
				        for (Object item : items) {
				            if (!(item != null)) {
				                return false;
				            }
				        }
				        return true;
				    }
				}""",

				"""
						package test1;

						import java.util.List;

						class MyTest {
						    public boolean allNonNull(List<Object> items) {
						        if (!items.stream().allMatch(item -> (item != null))) {
						            return false;
						        }
						        return true;
						    }
						}"""),

		/**
		 * Tests allMatch with chained map operation.
		 * 
		 * <p>
		 * <b>Conversion Rule:</b> Transformation before condition check uses
		 * {@code map()} before {@code allMatch()}.
		 * </p>
		 */
		ChainedAllMatch("""
				package test1;

				import java.util.List;

				class MyTest {
				    public boolean allLongEnough(List<String> items) {
				        for (String item : items) {
				            int len = item.length();
				            if (!(len > 5)) {
				                return false;
				            }
				        }
				        return true;
				    }
				}""",

				"""
						package test1;

						import java.util.List;

						class MyTest {
						    public boolean allLongEnough(List<String> items) {
						        if (!items.stream().map(item -> item.length()).allMatch(len -> (len > 5))) {
						            return false;
						        }
						        return true;
						    }
						}""");

		final String input;
		final String expected;

		TestCase(String input, String expected) {
			this.input = input;
			this.expected = expected;
		}
	}

	@Disabled("Disabled until functional loop cleanup is stable")
	@ParameterizedTest
	@EnumSource(TestCase.class)
	@DisplayName("Test match pattern conversion")
	void testConversion(TestCase testCase) throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", testCase.input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { testCase.expected },
				null);
	}
}
