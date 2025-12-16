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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

public class Java8CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();

	enum UseFunctionalLoop {
		SIMPLECONVERT("""
			package test1;
			import java.util.Arrays;
			import java.util.List;
			class TestDemo {
			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1, 2, 3));
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
					class TestDemo {
					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1, 2, 3));
					    }
					    public void test(List<Integer> ls) {
					        ls.forEach(l -> System.out.println(l));
					    }
					}"""),

		CHAININGMAP("""
			package test1;
			import java.util.Arrays;
			import java.util.List;
			class TestDemo {
			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1, 2, 3));
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
					class TestDemo {
					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1, 2, 3));
					    }
					    public void test(List<Integer> ls) {
					        ls.stream().map(l -> l.toString()).forEachOrdered(s -> {
					            System.out.println(s);
					        });
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
			class TestDemo {

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
					class TestDemo {

					    /**
					     * @param args the command line arguments
					     */
					    public static void main( String[] args) {
					        // TODO code application logic here
					        List<Integer> ints=new ArrayList<>();
					        double len=0.;
					        len = ints.stream().map(_item -> 1.0).reduce(len, (accumulator, _item) -> accumulator + 1);

					    }
					}"""),
		ChainingFilterMapForEachConvert("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class TestDemo {

			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1, 2, 3));
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

					class TestDemo {

					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1, 2, 3));
					    }

					    public void test(List<Integer> ls) {
					        ls.stream().filter(l -> (l!=null)).map(l -> l.toString()).forEachOrdered(s -> {
					            System.out.println(s);
					        });


					    }
					}"""),
		SmoothLongerChaining("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class TestDemo {

			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1,2,3));
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
					package test1;;

					import java.util.Arrays;
					import java.util.List;

					class TestDemo {

					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1,2,3));
					    }

					    public void test(List<Integer> ls) {
					        ls.stream().map(a -> new Integer(a.intValue())).filter(l -> (l!=null)).map(l -> l.toString()).forEachOrdered(s -> {
					            System.out.println(s);
					        });


					    }
					}"""),
		NonFilteringIfChaining("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class TestDemo {

			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1,2,3));
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

					class TestDemo {

					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1,2,3));
					    }

					    public void test(List<Integer> ls) {
					        ls.stream().map(a -> new Integer(a.intValue())).filter(l -> (l!=null)).map(l -> l.toString()).map(s -> {
					            if(s!=null)
					                System.out.println(s);
					            return s;
					        }).forEachOrdered(_item -> {
					            System.out.println("cucu");
					        });


					    }
					}"""),
		ContinuingIfFilterSingleStatement("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class TestDemo {

			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1, 2, 3));
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

					class TestDemo {

					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1, 2, 3));
					    }

					    public void test(List<Integer> ls) {
					        ls.stream().filter(l -> !(l == null)).map(l -> l.toString()).filter(s -> (s != null)).forEachOrdered(s -> {
					            System.out.println(s);
					        });


					    }
					}"""),
		ChainedAnyMatch("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class TestDemo {

			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1, 2, 3));
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

					class TestDemo {

					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1, 2, 3));
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

			class TestDemo {

			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1, 2, 3));
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

					class TestDemo {

					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1, 2, 3));
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

			class TestDemo {

			    public static void main(String[] args) throws Exception {
			        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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

					class TestDemo {

					    public static void main(String[] args) throws Exception {
					        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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

			class TestDemo {

			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1, 2, 3));
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

					class TestDemo {

					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1, 2, 3));
					    }

					    public Boolean test(List<Integer> ls) {
					        ls.stream().map(a -> new Integer(a.intValue())).map(l -> {
					            if(l==null)
					            {
					                String s=l.toString();
					                if(s!=null)
					                {
					                    System.out.println(s);
					                }
					                System.out.println("cucu");
					            }
					            return l;
					        }).forEachOrdered(_item -> {
					            System.out.println();
					        });

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

			class TestDemo {

			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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

					class TestDemo {

					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1, 2, 3,7));
					    }


					    public Boolean test(List<Integer> ls) {
					        Integer i=0;
					        i = ls.stream().map(_item -> 1).reduce(i, Integer::sum);
					        System.out.println(i);
					        return true;


					    }
					}"""),
		ChainedReducer("""
			package test1;

			import java.util.Arrays;
			import java.util.List;

			class TestDemo {

			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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

					class TestDemo {

					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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

			class TestDemo {

			    public static void main(String[] args) {
			        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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

					class TestDemo {

					    public static void main(String[] args) {
					        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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
				+ "class TestDemo {\n"
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
						+ "class TestDemo {\n"
						+ "\n"
						+ "    /**\n"
						+ "     * @param args the command line arguments\n"
						+ "     */\n"
						+ "    public static void main( String[] args) {\n"
						+ "        List<Integer> ls = new ArrayList<>();\n"
						+ "        int i =0;\n"
						+ "        i = ls.stream().map(_item -> 1).reduce(i, Integer::sum);\n"
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
				+ "class TestDemo {\n"
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
						+ "class TestDemo {\n"
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
				+ "class TestDemo {\n"
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
						+ "class TestDemo {\n"
						+ "\n"
						+ "    /**\n"
						+ "     * @param args the command line arguments\n"
						+ "     */\n"
						+ "    public static void main( String[] args) {\n"
						+ "        List<Integer> ls = new ArrayList<>();\n"
						+ "        String i =\"\";\n"
						+ "        i = ls.stream().map(l -> foo(l)).reduce(i, String::concat);\n"
						+ "\n"
						+ "    }\n"
						+ "\n"
						+ "    private static String foo(Integer l) {\n"
						+ "        throw new UnsupportedOperationException(\"Not supported yet.\"); //To change body of generated methods, choose Tools | Templates.\n"
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

			class TestDemo {
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

					class TestDemo {
					    public int findMax(List<Integer> numbers) {
					        int max = Integer.MIN_VALUE;
					        max = numbers.stream().map(num -> num).reduce(max, Math::max);
					        return max;
					    }
					}"""),
		MinReducer("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class TestDemo {
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

					class TestDemo {
					    public int findMin(List<Integer> numbers) {
					        int min = Integer.MAX_VALUE;
					        min = numbers.stream().map(num -> num).reduce(min, Math::min);
					        return min;
					    }
					}"""),
		MaxWithExpression("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class TestDemo {
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

					class TestDemo {
					    public int findMaxLength(List<String> strings) {
					        int maxLen = 0;
					        maxLen = strings.stream().map(str -> str.length()).reduce(maxLen, Math::max);
					        return maxLen;
					    }
					}"""),
		MinWithExpression("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class TestDemo {
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

					class TestDemo {
					    public double findMinValue(List<Double> values) {
					        double minVal = Double.MAX_VALUE;
					        minVal = values.stream().map(val -> val * 2.0).reduce(minVal, Math::min);
					        return minVal;
					    }
					}"""),
		FilteredMaxReduction("""
			package test1;

			import java.util.ArrayList;
			import java.util.List;

			class TestDemo {
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

					class TestDemo {
					    public int findMaxEvenNumber(List<Integer> numbers) {
					        int max = 0;
					        max = numbers.stream().filter(num -> (num % 2 == 0)).map(num -> num).reduce(max, Math::max);
					        return max;
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
		"MergingOperations",
		"BeautificationWorks",
		"BeautificationWorks2",
		"NonFilteringIfChaining",
		"ContinuingIfFilterSingleStatement",
		"SimpleReducer",
		"ChainedReducer",
		"IncrementReducer",
		"AccumulatingMapReduce",
		"DOUBLEINCREMENTREDUCER",
		"DecrementingReducer",
		"ChainedReducerWithMerging",
		"StringConcat",
		"ChainedAnyMatch",
		"ChainedNoneMatch",
		"NoNeededVariablesMerging",
		"SomeChainingWithNoNeededVar",
		"MaxReducer",
		"MinReducer",
		"MaxWithExpression",
		"MinWithExpression",
		"FilteredMaxReduction"
	})
	public void testSimpleForEachConversion(UseFunctionalLoop test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("TestDemo.java", test.given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	@Disabled("Not all functional loop patterns are implemented yet - enable incrementally as features are added")
	@ParameterizedTest
	@EnumSource(UseFunctionalLoop.class)
	public void testAllFunctionalLoopConversions(UseFunctionalLoop test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("TestDemo.java", test.given, false, null);
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	@Disabled
	@ParameterizedTest
	@ValueSource(strings = {
			"""
				package testdemo;

				import java.util.ArrayList;
				import java.util.List;

				/**
				 *
				 * @author alexandrugyori
				 */
				class TestDemo {

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
				package testdemo;

				import java.util.Arrays;
				import java.util.List;

				class TestDemo {

				    public static void main(String[] args) {
				        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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
				package testdemo;

				import java.util.Arrays;
				import java.util.List;

				class TestDemo {

				    public static void main(String[] args) {
				        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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
				package testdemo;

				import java.util.Arrays;
				import java.util.List;

				class TestDemo {

				    public static void main(String[] args) {
				        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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
				package testdemo;

				import java.util.Arrays;
				import java.util.List;

				class TestDemo {

				    public static void main(String[] args) {
				        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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
				package testdemo;

				import java.util.Arrays;
				import java.util.List;

				class TestDemo {

				    public static void main(String[] args) {
				        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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
				package testdemo;

				import java.util.Arrays;
				import java.util.List;

				class TestDemo {

				    public static void main(String[] args) {
				        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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
				package testdemo;

				import java.util.Arrays;
				import java.util.List;

				class TestDemo {

				    public static void main(String[] args) throws Exception {
				        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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
				package testdemo;

				import java.util.Arrays;
				import java.util.List;

				class TestDemo {

				    public static void main(String[] args) throws Exception {
				        new TestDemo().test(Arrays.asList(1, 2, 3,7));
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
				package testdemo;
				import java.util.List;\
				class TestDemo {
				    public void test(List<Integer> ls) throws Exception {
				        for(Integer l : ls) {
				            return ;
				        }
				    }
				}""",

			"""
				package testdemo;

				import java.util.ArrayList;
				import java.util.List;

				/**
				 *
				 * @author alexandrugyori
				 */
				class TestDemo {

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
				package testdemo;

				import java.util.ArrayList;
				import java.util.List;

				/**
				 *
				 * @author alexandrugyori
				 */
				class TestDemo {

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
				package testdemo;

				import java.util.ArrayList;
				import java.util.List;

				/**
				 *
				 * @author alexandrugyori
				 */
				class TestDemo {

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
				}"""

	})
	public void testExplicitEncodingdonttouch(String dontchange) throws CoreException  {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("testdemo", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("TestDemo.java",
				dontchange,
				false, null);

		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);

		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
