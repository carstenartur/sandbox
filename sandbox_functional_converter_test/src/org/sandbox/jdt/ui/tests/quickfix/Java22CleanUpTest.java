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
 *     Alexandru Gyori original code
 *     Carsten Hammer initial port to Eclipse
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Legacy test class for functional loop conversion - DEPRECATED.
 * 
 * <p>
 * <b>⚠️ DEPRECATED:</b> This test class has been refactored into multiple
 * focused test classes for better organization and maintainability.
 * </p>
 * 
 * <h2>Migration Guide</h2>
 * 
 * <p>
 * The tests in this file have been split into the following new test classes:
 * </p>
 * <ul>
 * <li>{@link FunctionalLoopSimpleConversionTest} - Basic forEach conversions (4 tests)</li>
 * <li>{@link FunctionalLoopFilterMapTest} - Filter and map operations (11 tests)</li>
 * <li>{@link FunctionalLoopReducerTest} - Reduction operations (9 representative tests)</li>
 * <li>{@link FunctionalLoopMatchPatternTest} - anyMatch/noneMatch/allMatch patterns (5 tests)</li>
 * <li>{@link FunctionalLoopComplexPatternTest} - Complex patterns and edge cases (3 tests)</li>
 * <li>{@link FunctionalLoopNegativeTest} - Tests for patterns that should NOT be converted (5 tests)</li>
 * </ul>
 * 
 * <p>
 * Use {@link FunctionalLoopTestHelper} for common test utilities.
 * </p>
 * 
 * <p>
 * <b>Benefits of the new structure:</b>
 * </p>
 * <ul>
 * <li>✅ Faster test execution (parallel execution capability)</li>
 * <li>✅ Better test failure reporting</li>
 * <li>✅ Easier to add new test cases</li>
 * <li>✅ Better IDE test navigation</li>
 * <li>✅ Comprehensive Javadoc for each test pattern</li>
 * </ul>
 * 
 * @deprecated Use the new focused test classes instead. This class will be
 *             removed in a future release.
 * @see FunctionalLoopSimpleConversionTest
 * @see FunctionalLoopFilterMapTest
 * @see FunctionalLoopReducerTest
 * @see FunctionalLoopMatchPatternTest
 * @see FunctionalLoopComplexPatternTest
 * @see FunctionalLoopNegativeTest
 * @see FunctionalLoopTestHelper
 */
@Deprecated(since = "2026-01", forRemoval = true)
public class Java22CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	enum UseFunctionalLoop {
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

		CHAININGMAP("""
			package test1;
			import java.util.Arrays;
			import java.util.List;
			class MyTest {
			    public static void main(String[] args) {
			        new MyTest().test(Arrays.asList(1, 2, 3));
			    }
			    public void test(List<Integer> ls) {
			        for (Integer l : ls) {
			            String s = l.toString();
			            System.out.println(s);
			        }
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
        ls.stream().map(l -> l.toString()).forEachOrdered(s -> System.out.println(s));
    }
}"""),

		DOUBLEINCREMENTREDUCER("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			/**
			 *
			 * @author alexandrugyori
			 */
			class MyTest {

			    /**
			     * @param args the command line arguments
			     */
			    public static void main( String[] args) {
			        // TODO code application logic here
			        List<Integer> ints=new ArrayList<>();
			        double len=0.;
			        for(int i : ints)
			            len++;

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
class MyTest {

    /**
     * @param args the command line arguments
     */
    public static void main( String[] args) {
        // TODO code application logic here
        List<Integer> ints=new ArrayList<>();
        double len=0.;
        len = ints.stream().map(i -> 1.0).reduce(len, (accumulator, _item) -> accumulator + 1);

    }
}"""),
		LONGINCREMENTREDUCER("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			/**
			 * Test for long increment reducer
			 */
			class MyTest {

			    public static void main( String[] args) {
			        List<Integer> ints=new ArrayList<>();
			        long len=0L;
			        for(int i : ints)
			            len++;

			    }
			}""",

"""
package test1;

import java.util.ArrayList;
import java.util.List;

/**
 * Test for long increment reducer
 */
class MyTest {

    public static void main( String[] args) {
        List<Integer> ints=new ArrayList<>();
        long len=0L;
        len = ints.stream().map(i -> 1L).reduce(len, Long::sum);

    }
}"""),
		FLOATINCREMENTREDUCER("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			/**
			 * Test for float increment reducer - must use lambda (no Float::sum)
			 */
			class MyTest {

			    public static void main( String[] args) {
			        List<Integer> ints=new ArrayList<>();
			        float len=0.0f;
			        for(int i : ints)
			            len++;

			    }
			}""",

				"""
					package test1;

					import java.util.ArrayList;
					import java.util.List;

					/**
					 * Test for float increment reducer - must use lambda (no Float::sum)
					 */
					class MyTest {

					    public static void main( String[] args) {
					        List<Integer> ints=new ArrayList<>();
					        float len=0.0f;
					        len = ints.stream().map(i -> 1.0f).reduce(len, (accumulator, _item) -> accumulator + 1);

					    }
					}"""),
		DOUBLESUMREDUCER("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			/**
			 * Test for double sum reducer - should use Double::sum
			 */
			class MyTest {

			    public static void main( String[] args) {
			        List<Integer> ints=new ArrayList<>();
			        double sum=0.0;
			        for(Integer i : ints)
			            sum += i.doubleValue();

			    }
			}""",

				"""
					package test1;

					import java.util.ArrayList;
					import java.util.List;

					/**
					 * Test for double sum reducer - should use Double::sum
					 */
					class MyTest {

					    public static void main( String[] args) {
					        List<Integer> ints=new ArrayList<>();
					        double sum=0.0;
					        sum = ints.stream().map(i -> i.doubleValue()).reduce(sum, Double::sum);

					    }
					}"""),
		LONGSUMREDUCER("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			/**
			 * Test for long sum reducer - should use Long::sum
			 */
			class MyTest {

			    public static void main( String[] args) {
			        List<Integer> ints=new ArrayList<>();
			        long sum=0L;
			        for(Integer i : ints)
			            sum += i.longValue();

			    }
			}""",

				"""
					package test1;

					import java.util.ArrayList;
					import java.util.List;

					/**
					 * Test for long sum reducer - should use Long::sum
					 */
					class MyTest {

					    public static void main( String[] args) {
					        List<Integer> ints=new ArrayList<>();
					        long sum=0L;
					        sum = ints.stream().map(i -> i.longValue()).reduce(sum, Long::sum);

					    }
					}"""),
		ChainingFilterMapForEachConvert("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

			    public static void main(String[] args) {
			        new MyTest().test(Arrays.asList(1, 2, 3));
			    }

			    public void test(List<Integer> ls) {
			        for (Integer l : ls) {
			            if(l!=null)
			            {
			                String s = l.toString();
			                System.out.println(s);
			            }
			        }


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
        ls.stream().filter(l -> (l != null)).map(l -> l.toString()).forEachOrdered(s -> System.out.println(s));


    }
}"""),
		SmoothLongerChaining("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

			    public static void main(String[] args) {
			        new MyTest().test(Arrays.asList(1,2,3));
			    }

			    public void test(List<Integer> ls) {
			        for (Integer a : ls) {
			            Integer l = new Integer(a.intValue());
			            if(l!=null)
			            {
			                String s = l.toString();
			                System.out.println(s);
			            }
			        }


			    }
			}""",

"""
package test1;

import java.util.Arrays;
import java.util.List;

class MyTest {

    public static void main(String[] args) {
        new MyTest().test(Arrays.asList(1,2,3));
    }

    public void test(List<Integer> ls) {
        ls.stream().map(a -> new Integer(a.intValue())).filter(l -> (l != null)).map(l -> l.toString())
				.forEachOrdered(s -> System.out.println(s));


    }
}"""),
		NonFilteringIfChaining("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

			    public static void main(String[] args) {
			        new MyTest().test(Arrays.asList(1,2,3));
			    }

			    public void test(List<Integer> ls) {
			        for (Integer a : ls) {
			            Integer l = new Integer(a.intValue());
			            if(l!=null)
			            {
			                String s = l.toString();
			                if(s!=null)
			                    System.out.println(s);
			                System.out.println("cucu");
			            }
			        }


			    }
			}""",

"""
package test1;

import java.util.Arrays;
import java.util.List;

class MyTest {

    public static void main(String[] args) {
        new MyTest().test(Arrays.asList(1,2,3));
    }

    public void test(List<Integer> ls) {
        ls.stream().map(a -> new Integer(a.intValue())).filter(l -> (l != null)).map(l -> l.toString()).map(s -> {
			if (s != null)
				System.out.println(s);
			return s;
		}).forEachOrdered(s -> System.out.println("cucu"));


    }
}"""),
		ContinuingIfFilterSingleStatement("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

			    public static void main(String[] args) {
			        new MyTest().test(Arrays.asList(1, 2, 3));
			    }

			    public void test(List<Integer> ls) {
			        for (Integer l : ls) {
			            if (l == null) {
			                continue;
			            }
			            String s = l.toString();
			            if (s != null) {
			                System.out.println(s);
			            }
			        }


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
        ls.stream().filter(l -> !(l == null)).map(l -> l.toString()).filter(s -> (s != null))
				.forEachOrdered(s -> System.out.println(s));


    }
}"""),
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
		NoNeededVariablesMerging("""
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
			}""",

				"""
					package test1;

					import java.util.Arrays;
					import java.util.List;

					class MyTest {

					    public static void main(String[] args) throws Exception {
					        new MyTest().test(Arrays.asList(1, 2, 3,7));
					    }


					    public Boolean test(List<Integer> ls) throws Exception {
					        Integer i=0;
					        ls.stream().map(_item -> {
					            System.out.println();
					            return _item;
					        }).forEachOrdered(_item -> {
					            System.out.println("");
					        });
					        System.out.println(i);
					        return false;


					    }
					    private void foo(Object o, int i) throws Exception
					    {

					    }
					}"""),
		SomeChainingWithNoNeededVar("""
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
}"""),
		SimpleReducer("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

			    public static void main(String[] args) {
			        new MyTest().test(Arrays.asList(1, 2, 3,7));
			    }


			    public Boolean test(List<Integer> ls) {
			        Integer i=0;
			        for(Integer l : ls)
			            i++;
			        System.out.println(i);
			        return true;


			    }
			}""",

"""
package test1;

import java.util.Arrays;
import java.util.List;

class MyTest {

    public static void main(String[] args) {
        new MyTest().test(Arrays.asList(1, 2, 3,7));
    }


    public Boolean test(List<Integer> ls) {
        Integer i=0;
        i = ls.stream().map(l -> 1).reduce(i, Integer::sum);
        System.out.println(i);
        return true;


    }
}"""),
		ChainedReducer("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

			    public static void main(String[] args) {
			        new MyTest().test(Arrays.asList(1, 2, 3,7));
			    }


			    public Boolean test(List<Integer> ls) {
			        Integer i=0;
			        for(Integer l : ls)
			        {
			            if(l!=null)
			            {
			                foo(l);
			                i++;
			            }

			        }
			        System.out.println(i);
			        return true;


			    }
			    private void foo(Object o)
			    {

			    }
			}""",

				"""
					package test1;

					import java.util.Arrays;
					import java.util.List;

					class MyTest {

					    public static void main(String[] args) {
					        new MyTest().test(Arrays.asList(1, 2, 3,7));
					    }


					    public Boolean test(List<Integer> ls) {
					        Integer i=0;
					        i = ls.stream().filter(l -> (l!=null)).map(l -> {
					            foo(l);
					            return l;
					        }).map(_item -> 1).reduce(i, Integer::sum);
					        System.out.println(i);
					        return true;


					    }
					    private void foo(Object o)
					    {

					    }
					}"""),
		ChainedReducerWithMerging("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class MyTest {

			    public static void main(String[] args) {
			        new MyTest().test(Arrays.asList(1, 2, 3,7));
			    }


			    public Boolean test(List<Integer> ls) {
			        Integer i=0;
			        for(Integer l : ls)
			        {
			            String s =l.toString();
			            System.out.println(s);
			            foo(l);
			            if(l!=null)
			            {
			                foo(l);
			                i--;
			            }

			        }
			        System.out.println(i);
			        return true;


			    }
			    private void foo(Object o)
			    {

			    }
			}""",

				"""
					package test1;

					import java.util.Arrays;
					import java.util.List;

					class MyTest {

					    public static void main(String[] args) {
					        new MyTest().test(Arrays.asList(1, 2, 3,7));
					    }


					    public Boolean test(List<Integer> ls) {
					        Integer i=0;
					        i = ls.stream().map(l -> {
					            String s =l.toString();
					            System.out.println(s);
					            foo(l);
					            return l;
					        }).filter(l -> (l!=null)).map(l -> {
					            foo(l);
					            return l;
					        }).map(_item -> 1).reduce(i, (accumulator, _item) -> accumulator - 1);
					        System.out.println(i);
					        return true;


					    }
					    private void foo(Object o)
					    {

					    }
					}"""),
		IncrementReducer("package test1;\n"
				+ "\n"
				+ "import java.util.ArrayList;\n"
				+ "import java.util.List;\n"
				+ "\n"
				+ "/**\n"
				+ " *\n"
				+ " * @author alexandrugyori\n"
				+ " */\n"
				+ "class MyTest {\n"
				+ "\n"
				+ "    /**\n"
				+ "     * @param args the command line arguments\n"
				+ "     */\n"
				+ "    public static void main( String[] args) {\n"
				+ "        List<Integer> ls = new ArrayList<>();\n"
				+ "        int i =0;\n"
				+ "        for ( Integer l : ls) {\n"
				+ "            i+=1;        \n"
				+ "        }\n"
				+ "\n"
				+ "    }\n"
				+ "\n"
				+ "    private static void foo(Integer l) {\n"
				+ "        throw new UnsupportedOperationException(\"Not supported yet.\"); //To change body of generated methods, choose Tools | Templates.\n"
				+ "    }\n"
				+ "}\n"
				+ "",

				"package test1;\n"
				+ "\n"
				+ "import java.util.ArrayList;\n"
				+ "import java.util.List;\n"
				+ "\n"
				+ "/**\n"
				+ " *\n"
				+ " * @author alexandrugyori\n"
				+ " */\n"
				+ "class MyTest {\n"
				+ "\n"
				+ "    /**\n"
				+ "     * @param args the command line arguments\n"
				+ "     */\n"
				+ "    public static void main( String[] args) {\n"
				+ "        List<Integer> ls = new ArrayList<>();\n"
				+ "        int i =0;\n"
				+ "        i = ls.stream().map(l -> 1).reduce(i, Integer::sum);\n"
				+ "\n"
				+ "    }\n"
				+ "\n"
				+ "    private static void foo(Integer l) {\n"
				+ "        throw new UnsupportedOperationException(\"Not supported yet.\"); //To change body of generated methods, choose Tools | Templates.\n"
				+ "    }\n"
				+ "}\n"
				+ ""),
		AccumulatingMapReduce("package test1;\n"
				+ "\n"
				+ "import java.util.ArrayList;\n"
				+ "import java.util.List;\n"
				+ "\n"
				+ "/**\n"
				+ " *\n"
				+ " * @author alexandrugyori\n"
				+ " */\n"
				+ "class MyTest {\n"
				+ "\n"
				+ "    /**\n"
				+ "     * @param args the command line arguments\n"
				+ "     */\n"
				+ "    public static void main( String[] args) {\n"
				+ "        List<Integer> ls = new ArrayList<>();\n"
				+ "        int i =0;\n"
				+ "        for ( Integer l : ls) {\n"
				+ "            i+=foo(l);        \n"
				+ "        }\n"
				+ "\n"
				+ "    }\n"
				+ "\n"
				+ "    private static int foo(Integer l) {\n"
				+ "        throw new UnsupportedOperationException(\"Not supported yet.\"); //To change body of generated methods, choose Tools | Templates.\n"
				+ "    }\n"
				+ "}\n"
				+ "",

				"package test1;\n"
						+ "\n"
						+ "import java.util.ArrayList;\n"
						+ "import java.util.List;\n"
						+ "\n"
						+ "/**\n"
						+ " *\n"
						+ " * @author alexandrugyori\n"
						+ " */\n"
						+ "class MyTest {\n"
						+ "\n"
						+ "    /**\n"
						+ "     * @param args the command line arguments\n"
						+ "     */\n"
						+ "    public static void main( String[] args) {\n"
						+ "        List<Integer> ls = new ArrayList<>();\n"
						+ "        int i =0;\n"
						+ "        i = ls.stream().map(l -> foo(l)).reduce(i, Integer::sum);\n"
						+ "\n"
						+ "    }\n"
						+ "\n"
						+ "    private static int foo(Integer l) {\n"
						+ "        throw new UnsupportedOperationException(\"Not supported yet.\"); //To change body of generated methods, choose Tools | Templates.\n"
						+ "    }\n"
						+ "}\n"
						+ ""),
		StringConcat("package test1;\n"
				+ "\n"
				+ "import java.util.ArrayList;\n"
				+ "import java.util.List;\n"
				+ "\n"
				+ "/**\n"
				+ " *\n"
				+ " * @author alexandrugyori\n"
				+ " */\n"
				+ "class MyTest {\n"
				+ "\n"
				+ "    /**\n"
				+ "     * @param args the command line arguments\n"
				+ "     */\n"
				+ "    public static void main( String[] args) {\n"
				+ "        List<Integer> ls = new ArrayList<>();\n"
				+ "        String i =\"\";\n"
				+ "        for ( Integer l : ls) {\n"
				+ "            i+=foo(l);        \n"
				+ "        }\n"
				+ "\n"
				+ "    }\n"
				+ "\n"
				+ "    private static String foo(Integer l) {\n"
				+ "        throw new UnsupportedOperationException(\"Not supported yet.\"); //To change body of generated methods, choose Tools | Templates.\n"
				+ "    }\n"
				+ "}\n"
				+ "",

				"package test1;\n"
						+ "\n"
						+ "import java.util.ArrayList;\n"
						+ "import java.util.List;\n"
						+ "\n"
						+ "/**\n"
						+ " *\n"
						+ " * @author alexandrugyori\n"
						+ " */\n"
						+ "class MyTest {\n"
						+ "\n"
						+ "    /**\n"
						+ "     * @param args the command line arguments\n"
						+ "     */\n"
						+ "    public static void main( String[] args) {\n"
						+ "        List<Integer> ls = new ArrayList<>();\n"
						+ "        String i =\"\";\n"
						+ "        i = ls.stream().map(l -> foo(l)).reduce(i, (a, b) -> a + b);\n"
						+ "\n"
						+ "    }\n"
						+ "\n"
						+ "    private static String foo(Integer l) {\n"
						+ "        throw new UnsupportedOperationException(\"Not supported yet.\"); //To change body of generated methods, choose Tools | Templates.\n"
						+ "    }\n"
						+ "}\n"
						+ ""),
		StringConcatWithNotNull("package test1;\n"
				+ "\n"
				+ "import java.util.ArrayList;\n"
				+ "import java.util.List;\n"
				+ "import org.eclipse.jdt.annotation.NotNull;\n"
				+ "\n"
				+ "/**\n"
				+ " *\n"
				+ " * @author alexandrugyori\n"
				+ " */\n"
				+ "class MyTest {\n"
				+ "\n"
				+ "    /**\n"
				+ "     * @param args the command line arguments\n"
				+ "     */\n"
				+ "    public static void main( String[] args) {\n"
				+ "        List<Integer> ls = new ArrayList<>();\n"
				+ "        @NotNull String i =\"\";\n"
				+ "        for ( Integer l : ls) {\n"
				+ "            i+=foo(l);        \n"
				+ "        }\n"
				+ "\n"
				+ "    }\n"
				+ "\n"
				+ "    private static @NotNull String foo(Integer l) {\n"
				+ "        return \"\";\n"
				+ "    }\n"
				+ "}\n"
				+ "",

				"package test1;\n"
						+ "\n"
						+ "import java.util.ArrayList;\n"
						+ "import java.util.List;\n"
						+ "import org.eclipse.jdt.annotation.NotNull;\n"
						+ "\n"
						+ "/**\n"
						+ " *\n"
						+ " * @author alexandrugyori\n"
						+ " */\n"
						+ "class MyTest {\n"
						+ "\n"
						+ "    /**\n"
						+ "     * @param args the command line arguments\n"
						+ "     */\n"
						+ "    public static void main( String[] args) {\n"
						+ "        List<Integer> ls = new ArrayList<>();\n"
						+ "        @NotNull String i =\"\";\n"
						+ "        i = ls.stream().map(l -> foo(l)).reduce(i, String::concat);\n"
						+ "\n"
						+ "    }\n"
						+ "\n"
						+ "    private static @NotNull String foo(Integer l) {\n"
						+ "        return \"\";\n"
						+ "    }\n"
						+ "}\n"
						+ ""),
		MergingOperations("""
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
					        strs.forEach(str -> {
					            int len1=str.length();
					            int len2 = str.length();
					            if (len1%2==0) {
					                len2++;
					                System.out.println(len2);
					                System.out.println();
					            }
					        });
					        return false;

					    }
					}"""),
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
					}"""),
		DecrementingReducer("""
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
			        for(String str : strs)
			            i-=1;
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
					        i = strs.stream().map(_item -> 1).reduce(i, (accumulator, _item) -> accumulator - _item);
					        return false;

					    }
					}"""),
		MaxReducer("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class MyTest {
			    public int findMax(List<Integer> numbers) {
			        int max = Integer.MIN_VALUE;
			        for (Integer num : numbers) {
			            max = Math.max(max, num);
			        }
			        return max;
			    }
			}""",

				"""
					package test1;

					import java.util.ArrayList;
					import java.util.List;

					class MyTest {
					    public int findMax(List<Integer> numbers) {
					        int max = Integer.MIN_VALUE;
					        max = numbers.stream().reduce(max, Integer::max);
					        return max;
					    }
					}"""),
		MinReducer("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class MyTest {
			    public int findMin(List<Integer> numbers) {
			        int min = Integer.MAX_VALUE;
			        for (Integer num : numbers) {
			            min = Math.min(min, num);
			        }
			        return min;
			    }
			}""",

				"""
					package test1;

					import java.util.ArrayList;
					import java.util.List;

					class MyTest {
					    public int findMin(List<Integer> numbers) {
					        int min = Integer.MAX_VALUE;
					        min = numbers.stream().reduce(min, Integer::min);
					        return min;
					    }
					}"""),
		MaxWithExpression("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class MyTest {
			    public int findMaxLength(List<String> strings) {
			        int maxLen = 0;
			        for (String str : strings) {
			            maxLen = Math.max(maxLen, str.length());
			        }
			        return maxLen;
			    }
			}""",

				"""
					package test1;

					import java.util.ArrayList;
					import java.util.List;

					class MyTest {
					    public int findMaxLength(List<String> strings) {
					        int maxLen = 0;
					        maxLen = strings.stream().map(str -> str.length()).reduce(maxLen, Integer::max);
					        return maxLen;
					    }
					}"""),
		MinWithExpression("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class MyTest {
			    public double findMinValue(List<Double> values) {
			        double minVal = Double.MAX_VALUE;
			        for (Double val : values) {
			            minVal = Math.min(minVal, val * 2.0);
			        }
			        return minVal;
			    }
			}""",

				"""
					package test1;

					import java.util.ArrayList;
					import java.util.List;

					class MyTest {
					    public double findMinValue(List<Double> values) {
					        double minVal = Double.MAX_VALUE;
					        minVal = values.stream().map(val -> val * 2.0).reduce(minVal, Double::min);
					        return minVal;
					    }
					}"""),
		FilteredMaxReduction("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class MyTest {
			    public int findMaxEvenNumber(List<Integer> numbers) {
			        int max = 0;
			        for (Integer num : numbers) {
			            if (num % 2 == 0) {
			                max = Math.max(max, num);
			            }
			        }
			        return max;
			    }
			}""",

				"""
					package test1;

					import java.util.ArrayList;
					import java.util.List;

					class MyTest {
					    public int findMaxEvenNumber(List<Integer> numbers) {
					        int max = 0;
					        max = numbers.stream().filter(num -> (num % 2 == 0)).reduce(max, Integer::max);
					        return max;
					    }
					}"""),
		ChainedMapWithMinReduction("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class MyTest {
			    public int findMinSquaredValue(List<Integer> numbers) {
			        int min = Integer.MAX_VALUE;
			        for (Integer num : numbers) {
			            int squared = num * num;
			            min = Math.min(min, squared);
			        }
			        return min;
			    }
			}""",

				"""
					package test1;

					import java.util.ArrayList;
					import java.util.List;

					class MyTest {
					    public int findMinSquaredValue(List<Integer> numbers) {
					        int min = Integer.MAX_VALUE;
					        min = numbers.stream().map(num -> num * num).reduce(min, Integer::min);
					        return min;
					    }
					}"""),
		ComplexFilterMapMaxReduction("""
			package test1;

			import java.util.List;

			class MyTest {
			    public int findMaxPositiveSquare(List<Integer> numbers) {
			        int max = 0;
			        for (Integer num : numbers) {
			            if (num > 0) {
			                int squared = num * num;
			                max = Math.max(max, squared);
			            }
			        }
			        return max;
			    }
			}""",

				"""
					package test1;

					import java.util.List;

					class MyTest {
					    public int findMaxPositiveSquare(List<Integer> numbers) {
					        int max = 0;
					        max = numbers.stream().filter(num -> (num > 0)).map(num -> num * num).reduce(max, Integer::max);
					        return max;
					    }
					}"""),
		ContinueWithMapAndForEach("""
			package test1;

			import java.util.List;

			class MyTest {
			    public void processPositiveSquares(List<Integer> numbers) {
			        for (Integer num : numbers) {
			            if (num <= 0) {
			                continue;
			            }
			            int squared = num * num;
			            System.out.println(squared);
			        }
			    }
			}""",
"""
package test1;

import java.util.List;

class MyTest {
    public void processPositiveSquares(List<Integer> numbers) {
        numbers.stream().filter(num -> !(num <= 0)).map(num -> num * num)
				.forEachOrdered(squared -> System.out.println(squared));
    }
}"""),
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
					}"""),
		NestedFilterCombination("""
			package test1;

			import java.util.List;

			class MyTest {
			    public void processValidItems(List<String> items) {
			        for (String item : items) {
			            if (item != null) {
			                if (item.length() > 5) {
			                    System.out.println(item);
			                }
			            }
			        }
			    }
			}""",

"""
package test1;

import java.util.List;

class MyTest {
    public void processValidItems(List<String> items) {
        items.stream().filter(item -> (item != null)).filter(item -> (item.length() > 5))
				.forEachOrdered(item -> System.out.println(item));
    }
}"""),
		MultipleContinueFilters("""
			package test1;

			import java.util.List;

			class MyTest {
			    public void processFiltered(List<Integer> numbers) {
			        for (Integer num : numbers) {
			            if (num == null) {
			                continue;
			            }
			            if (num <= 0) {
			                continue;
			            }
			            System.out.println(num);
			        }
			    }
			}""",
"""
package test1;

import java.util.List;

class MyTest {
    public void processFiltered(List<Integer> numbers) {
        numbers.stream().filter(num -> !(num == null)).filter(num -> !(num <= 0))
				.forEachOrdered(num -> System.out.println(num));
    }
}"""),
		
		// New regression tests for edge cases and previously fixed behaviors
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
		
		FilterWithComplexCondition("""
			package test1;

			import java.util.List;

			class MyTest {
			    public void processWithComplexFilter(List<String> items) {
			        for (String item : items) {
			            if (item != null && item.length() > 5 && item.startsWith("test")) {
			                System.out.println(item);
			            }
			        }
			    }
			}""",

"""
package test1;

import java.util.List;

class MyTest {
    public void processWithComplexFilter(List<String> items) {
        items.stream().filter(item -> (item != null && item.length() > 5 && item.startsWith("test")))
				.forEachOrdered(item -> System.out.println(item));
    }
}"""),
		
		ChainedFilterAndMapOperations("""
			package test1;

			import java.util.List;

			class MyTest {
			    public void processChained(List<Integer> numbers) {
			        for (Integer num : numbers) {
			            if (num != null && num > 0) {
			                int squared = num * num;
			                if (squared < 100) {
			                    System.out.println(squared);
			                }
			            }
			        }
			    }
			}""",

"""
package test1;

import java.util.List;

class MyTest {
    public void processChained(List<Integer> numbers) {
        numbers.stream().filter(num -> (num != null && num > 0)).map(num -> num * num)
				.filter(squared -> (squared < 100)).forEachOrdered(squared -> System.out.println(squared));
    }
}"""),
		
		ContinueWithNestedConditions("""
			package test1;

			import java.util.List;

			class MyTest {
			    public void processWithNestedContinue(List<String> items) {
			        for (String item : items) {
			            if (item == null || item.isEmpty()) {
			                continue;
			            }
			            String upper = item.toUpperCase();
			            System.out.println(upper);
			        }
			    }
			}""",
"""
package test1;

import java.util.List;

class MyTest {
    public void processWithNestedContinue(List<String> items) {
        items.stream().filter(item -> !(item == null || item.isEmpty())).map(item -> item.toUpperCase())
				.forEachOrdered(upper -> System.out.println(upper));
    }
}"""),
		
		MultipleMapOperations("""
			package test1;

			import java.util.List;

			class MyTest {
			    public void processMultipleMaps(List<Integer> numbers) {
			        for (Integer num : numbers) {
			            int doubled = num * 2;
			            int squared = doubled * doubled;
			            String result = String.valueOf(squared);
			            System.out.println(result);
			        }
			    }
			}""",

				"""
					package test1;

					import java.util.List;

					class MyTest {
					    public void processMultipleMaps(List<Integer> numbers) {
					        numbers.stream().map(num -> num * 2).map(doubled -> doubled * doubled).map(squared -> String.valueOf(squared)).forEachOrdered(result -> {
					            System.out.println(result);
					        });
					    }
					}"""),
		
		SumReductionWithFilter("""
			package test1;

			import java.util.List;

			class MyTest {
			    public int sumPositiveNumbers(List<Integer> numbers) {
			        int sum = 0;
			        for (Integer num : numbers) {
			            if (num > 0) {
			                sum += num;
			            }
			        }
			        return sum;
			    }
			}""",

				"""
					package test1;

					import java.util.List;

					class MyTest {
					    public int sumPositiveNumbers(List<Integer> numbers) {
					        int sum = 0;
					        sum = numbers.stream().filter(num -> (num > 0)).reduce(sum, Integer::sum);
					        return sum;
					    }
					}"""),
		
		ComplexReductionWithMapping("""
			package test1;

			import java.util.List;

			class MyTest {
			    public int sumOfSquares(List<Integer> numbers) {
			        int sum = 0;
			        for (Integer num : numbers) {
			            int squared = num * num;
			            sum += squared;
			        }
			        return sum;
			    }
			}""",

				"""
					package test1;

					import java.util.List;

					class MyTest {
					    public int sumOfSquares(List<Integer> numbers) {
					        int sum = 0;
					        sum = numbers.stream().map(num -> num * num).reduce(sum, Integer::sum);
					        return sum;
					    }
					}"""),
		
		FilterMapReduceChain("""
			package test1;

			import java.util.List;

			class MyTest {
			    public int sumOfPositiveSquares(List<Integer> numbers) {
			        int total = 0;
			        for (Integer num : numbers) {
			            if (num > 0) {
			                int squared = num * num;
			                total += squared;
			            }
			        }
			        return total;
			    }
			}""",

				"""
					package test1;

					import java.util.List;

					class MyTest {
					    public int sumOfPositiveSquares(List<Integer> numbers) {
					        int total = 0;
					        total = numbers.stream().filter(num -> (num > 0)).map(num -> num * num).reduce(total, Integer::sum);
					        return total;
					    }
					}""");

		String given;
		String expected;

		UseFunctionalLoop(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}

	@ParameterizedTest
	@EnumSource(value = UseFunctionalLoop.class, names = {
		"SIMPLECONVERT",
		"CHAININGMAP",
		"ChainingFilterMapForEachConvert",
		"SmoothLongerChaining",
//		"MergingOperations",
//		"BeautificationWorks",
//		"BeautificationWorks2",
		"NonFilteringIfChaining",
		"ContinuingIfFilterSingleStatement",
		"SimpleReducer",
//		"ChainedReducer",
		"IncrementReducer",
		"AccumulatingMapReduce",
		"DOUBLEINCREMENTREDUCER",
		"LONGINCREMENTREDUCER",
		"FLOATINCREMENTREDUCER",
		"DOUBLESUMREDUCER",
		"LONGSUMREDUCER",
//		"DecrementingReducer",
//		"ChainedReducerWithMerging",
		"StringConcat",
		"StringConcatWithNotNull",
//		"ChainedAnyMatch",  // Fixed by checking return after loop
//		"ChainedNoneMatch",  // Fixed by checking return after loop
//		"NoNeededVariablesMerging",
		"SomeChainingWithNoNeededVar",
//		"MaxReducer",
//		"MinReducer",
//		"MaxWithExpression",
//		"MinWithExpression",
//		"FilteredMaxReduction",
//		"ChainedMapWithMinReduction",
//		"ComplexFilterMapMaxReduction",
		"ContinueWithMapAndForEach",
//		"SimpleAllMatch",
//		"AllMatchWithNullCheck",  // Fixed by checking return after loop
//		"ChainedAllMatch",  // Fixed by checking return after loop
		"NestedFilterCombination",
		"MultipleContinueFilters",
		"EmptyCollectionHandling",
		"FilterWithComplexCondition",
		"ChainedFilterAndMapOperations",
		"ContinueWithNestedConditions",
//		"MultipleMapOperations",
		"SumReductionWithFilter",
		"ComplexReductionWithMapping",
		"FilterMapReduceChain"
	})
	public void testSimpleForEachConversion(UseFunctionalLoop test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("MyTest.java", test.given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);

		// Hier: AST parsen und Problems ausgeben!
		ASTParser parser = ASTParser.newParser(AST.JLS22); // deine verwendete JLS-Version
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		IProblem[] problems = astRoot.getProblems();
		for (IProblem problem : problems) {
		    System.out.println(problem.toString());
		}
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { test.expected }, null);
	}

	@Disabled("Not all functional loop patterns are implemented yet - enable incrementally as features are added")
	@ParameterizedTest
	@EnumSource(UseFunctionalLoop.class)
	public void testAllFunctionalLoopConversions(UseFunctionalLoop test) throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("MyTest.java", test.given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	@Disabled
	@ParameterizedTest
	@ValueSource(strings = {
			"""
				package test1;

				import java.util.ArrayList;
				import java.util.List;

				/**
				 *
				 * @author alexandrugyori
				 */
				class MyTest {

				    /**
				     * @param args the command line arguments
				     */
				    public boolean b() {
				        // TODO code application logic here
				        String[] strs = new String[10];
				        int i = 0;
				        int j = 0;
				        for(String str:strs)
				            i++;
				        return false;

				    }
				}""",

			"""
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {

				    public static void main(String[] args) {
				        new MyTest().test(Arrays.asList(1, 2, 3,7));
				    }


				    public Boolean test(List<Integer> ls) {
				        Integer i=0;
				        for(Integer l : ls)
				        {
				            String s =l.toString();
				            System.out.println(s);
				            foo(l,i);
				            if(l!=null)
				            {
				                i++;
				            }

				        }
				        System.out.println(i);
				        return true;


				    }
				    private void foo(Object o, int i)
				    {

				    }
				}""",

			"""
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {

				    public static void main(String[] args) {
				        new MyTest().test(Arrays.asList(1, 2, 3,7));
				    }


				    public Boolean test(List<Integer> ls) {
				        Integer i=0;
				        for(Integer l : ls)
				        {
				            if(l!=null)
				            {
				                break;
				            }

				        }
				        System.out.println(i);
				        return true;


				    }
				    private void foo(Object o, int i)
				    {

				    }
				}""",

			"""
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {

				    public static void main(String[] args) {
				        new MyTest().test(Arrays.asList(1, 2, 3,7));
				    }


				    public int test(List<Integer> ls) {
				        Integer i=0;
				        for(Integer l : ls)
				        {
				            if(l!=null)
				            {
				                return 0;
				            }

				        }
				        System.out.println(i);
				        return 1;


				    }
				    private void foo(Object o, int i)
				    {

				    }
				}""",

			"""
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {

				    public static void main(String[] args) {
				        new MyTest().test(Arrays.asList(1, 2, 3,7));
				    }


				    public Boolean test(List<Integer> ls) {
				        Integer i=0;
				        for(Integer l : ls)
				        {
				            if(l==null)
				            {
				                return true;
				            }
				            if(l.toString()==null)
				                return true;

				        }
				        System.out.println(i);
				        return false;


				    }
				    private void foo(Object o, int i)
				    {

				    }
				}""",

			"""
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {

				    public static void main(String[] args) {
				        new MyTest().test(Arrays.asList(1, 2, 3,7));
				    }


				    public Boolean test(List<Integer> ls) {
				        Integer i=0;
				        label:
				        for(Integer l : ls)
				        {
				            if(l==null)
				            {
				                continue label;
				            }
				            if(l.toString()==null)
				                return true;

				        }
				        System.out.println(i);
				        return false;


				    }
				    private void foo(Object o, int i)
				    {

				    }
				}""",

			"""
				package test1;

				import java.util.Arrays;
				import java.util.List;

				class MyTest {

				    public static void main(String[] args) {
				        new MyTest().test(Arrays.asList(1, 2, 3,7));
				    }


				    public Boolean test(List<Integer> ls) {
				        Integer i=0;

				        for(Integer l : ls)
				        {
				            if(l==null)
				            {
				                continue;
				            }
				            else if(l.toString()==null)
				                return true;

				        }
				        System.out.println(i);
				        return false;


				    }
				    private void foo(Object o, int i)
				    {

				    }
				}""",

			"""
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
				            foo(l,1);
				            if(l==null)
				            {
				                continue;
				            }
				            else if(l.toString()==null)
				                return true;

				        }
				        System.out.println(i);
				        return false;


				    }
				    private void foo(Object o, int i) throws Exception
				    {

				    }
				}"""
			,

			"""
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
				            throw new Exception();

				        }
				        System.out.println(i);
				        return false;


				    }
				    private void foo(Object o, int i) throws Exception
				    {

				    }
				}"""
			,

			"""
				package test1;
				import java.util.List;\
				class MyTest {
				    public void test(List<Integer> ls) throws Exception {
				        for(Integer l : ls) {
				            return ;
				        }
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
				class MyTest {

				    /**
				     * @param args the command line arguments
				     */
				    public static void main(String[] args) {
				        // TODO code application logic here
				        List<String> strs = new ArrayList<String>();
				        int i = 0;
				        int j = 0;
				        for(String str: strs)
				        {
				            if(str!=null){
				                str.toString();
				            i++;
				            j++;
				            }
				            //j++;
				        }

				    }
				}"""
			,

			"""
				package test1;

				import java.util.ArrayList;
				import java.util.List;

				/**
				 *
				 * @author alexandrugyori
				 */
				class MyTest {

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
				            if(str!=null){
				                return true;
				            }
				            System.out.println("gugu");
				        }
				        return false;

				    }
				}"""
			,

			"""
				package test1;

				import java.util.ArrayList;
				import java.util.List;

				/**
				 *
				 * @author alexandrugyori
				 */
				class MyTest {

				    /**
				     * @param args the command line arguments
				     */
				    public void b() {
				        // TODO code application logic here
				        List<String> strs = new ArrayList<String>();
				        int i = 0;
				        int j = 0;
				        for(String str: strs)
				        {
				            if(str!=null){
				                return;
				            }
				        }
				        return;

				    }
				}""",
			
			// Test case: Loop modifying external variable (should NOT convert due to unsafe side effect)
			"""
				package test1;
				
				import java.util.List;
				
				class MyTest {
				    public void processWithExternalModification(List<String> items) {
				        int count = 0;
				        for (String item : items) {
				            System.out.println(item);
				            count = count + 1;  // Assignment to external variable
				        }
				        System.out.println(count);
				    }
				}""",
			
			// Test case: Assignment to external variable in non-last statement (should NOT convert)
			"""
				package test1;
				
				import java.util.List;
				
				class MyTest {
				    public void processWithExternalAssignment(List<String> items) {
				        StringBuilder result = new StringBuilder();
				        for (String item : items) {
				            result = new StringBuilder(item);  // Assignment to external var (non-last statement)
				            System.out.println(item);
				        }
				    }
				}""",
			
			// Additional negative test cases for regression testing
			
			// Test case: Loop with break statement (should NOT convert)
			"""
				package test1;
				
				import java.util.List;
				
				class MyTest {
				    public void processWithBreak(List<String> items) {
				        for (String item : items) {
				            if (item.equals("stop")) {
				                break;
				            }
				            System.out.println(item);
				        }
				    }
				}""",
			
			// Test case: Loop with throw statement (should NOT convert)
			"""
				package test1;
				
				import java.util.List;
				
				class MyTest {
				    public void processWithThrow(List<String> items) throws Exception {
				        for (String item : items) {
				            if (item == null) {
				                throw new IllegalArgumentException("Null item");
				            }
				            System.out.println(item);
				        }
				    }
				}""",
			
			// Test case: Loop with labeled continue (should NOT convert)
			"""
				package test1;
				
				import java.util.List;
				
				class MyTest {
				    public void processWithLabeledContinue(List<String> items) {
				        outer:
				        for (String item : items) {
				            if (item.isEmpty()) {
				                continue outer;
				            }
				            System.out.println(item);
				        }
				    }
				}""",
			
			// Test case: Loop with non-effectively-final variable modification (should NOT convert)
			"""
				package test1;
				
				import java.util.List;
				
				class MyTest {
				    public void processWithMutableVar(List<String> items) {
				        for (String item : items) {
				            String modified = item;
				            modified = modified.toUpperCase();
				            modified = modified + "!";
				            System.out.println(modified);
				        }
				    }
				}""",
			
			// Test case: Loop with early return in middle (not anyMatch/noneMatch pattern)
			"""
				package test1;
				
				import java.util.List;
				
				class MyTest {
				    public boolean complexEarlyReturn(List<String> items) {
				        for (String item : items) {
				            System.out.println("Processing: " + item);
				            if (item.length() > 5) {
				                return true;
				            }
				            System.out.println("Done with: " + item);
				        }
				        return false;
				    }
				}"""

	})
	public void testFunctionalLoopConversionsdonttouch(String dontchange) throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", dontchange, true, null);

		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		// Hier: AST parsen und Problems ausgeben!
		ASTParser parser = ASTParser.newParser(AST.JLS22); // deine verwendete JLS-Version
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		IProblem[] problems = astRoot.getProblems();
		for (IProblem problem : problems) {
			System.out.println(problem.toString());
		}
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
