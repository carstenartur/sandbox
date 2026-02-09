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
//import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Tests for complex patterns and edge cases in functional loop conversion.
 * 
 * <p>
 * This test class focuses on complex scenarios where loops have mixed
 * operations, side effects without variable usage, operation merging, and other
 * advanced patterns that don't fit cleanly into simple forEach, filter, map, or
 * reduce categories.
 * </p>
 * 
 * <p>
 * <b>Patterns covered:</b>
 * </p>
 * <ul>
 * <li>Mixed operations without variable tracking</li>
 * <li>Side effects without variable usage</li>
 * <li>Complex operation merging</li>
 * <li>Multiple map operations with complex transformations</li>
 * </ul>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.StreamPipelineBuilder
 */
public class FunctionalLoopComplexPatternTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	/**
	 * Tests mixed operations without needed variable tracking.
	 * 
	 * <p>
	 * <b>Conversion Rule:</b> When loop operations create variables that are not
	 * used in subsequent statements, the cleanup uses {@code _item} as the lambda
	 * parameter name and wraps operations in map blocks.
	 * </p>
	 */
	@Test
	void test_SomeChainingWithNoNeededVar() throws CoreException {
		String input = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}

				public Boolean test(List<Integer> ls) {
					for(Integer a:ls)
					{
						Integer l = new Integer(a.intValue());
						if(l==null)
						{
							String s=l.toString();
							if(s!=null)
							{
								System.out.println(s);
							}
							System.out.println("cucu");
						}
						System.out.println();
					}

					return true;
				}

				Object foo(Object o)
				{
					return o;
				}
			}""";

		String expected = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) {
					new MyTest().test(Arrays.asList(1, 2, 3));
				}

				public Boolean test(List<Integer> ls) {
					ls.stream().map(a -> new Integer(a.intValue())).map(l -> {
						if (l == null) {
							String s = l.toString();
							if (s != null) {
								System.out.println(s);
							}
							System.out.println("cucu");
						}
						return l;
					}).forEachOrdered(l -> System.out.println());

					return true;
				}

				Object foo(Object o)
				{
					return o;
				}
			}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests operations without needed variables merging.
	 * 
	 * <p>
	 * <b>Conversion Rule:</b> Side effects without variable dependencies are
	 * converted to map operations that return the loop variable, with the side effects
	 * executed in the lambda body.
	 * </p>
	 */
	@Test
	void test_NoNeededVariablesMerging() throws CoreException {
		String input = """
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

				public static void main(String[] args) throws Exception {
					new MyTest().test(Arrays.asList(1, 2, 3,7));
				}


				public Boolean test(List<Integer> ls) throws Exception {
					Integer i=0;
					for(Integer l : ls)
					{
						System.out.println();
						System.out.println("");

					}
					System.out.println(i);
					return false;
				}
				private void foo(Object o, int i) throws Exception
				{

				}
			}""";

		String expected = """
package test1;

import java.util.Arrays;
import java.util.List;

class MyTest {

	public static void main(String[] args) throws Exception {
		new MyTest().test(Arrays.asList(1, 2, 3,7));
	}


	public Boolean test(List<Integer> ls) throws Exception {
		Integer i=0;
		ls.forEach(l -> {
			System.out.println();
			System.out.println("");
		});
		System.out.println(i);
		return false;
	}
	private void foo(Object o, int i) throws Exception
	{

	}
}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}

	/**
	 * Tests complex operation merging.
	 * 
	 * <p>
	 * <b>Conversion Rule:</b> When loop body has local variable declarations and
	 * conditional logic that doesn't guard all remaining statements, the entire
	 * block is treated as a single forEach operation.
	 * </p>
	 */
	@Test
	void test_MergingOperations() throws CoreException {
		String input = """
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
						int len1=str.length();
						int len2 = str.length();
						if(len1%2==0){
							len2++;
							System.out.println(len2);
							System.out.println();
						}

					}
					return false;

				}
			}""";

		String expected = """
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
		strs.forEach(str -> {
			int len1 = str.length();
			int len2 = str.length();
			if (len1 % 2 == 0) {
				len2++;
				System.out.println(len2);
				System.out.println();
			}
		});
		return false;

	}
}""";

		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("JavaApplication1.java", input, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected }, null);
	}
}
