/*******************************************************************************
 * Copyright (c) 2021
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer initial port to Eclipse
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

public class Java8CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();

	enum While2EnhancedForLoop {
		whileWarningSelf("""
			package test;
			import java.util.*;
			public class Test extends ArrayList<String> {
			    void m() {
			        Iterator it = iterator();
			        while (it.hasNext()) {
			            String s = (String) it.next();
			            System.out.println(s);
			            System.err.println(s);
			        }
			    }
			}
			""", //$NON-NLS-1$

				"""
					package test;
					import java.util.*;
					public class Test extends ArrayList<String> {
					    void m() {
					        for (String s : this) {
					            System.out.println(s);
					            System.err.println(s);
					        }
					    }
					}
					"""), //$NON-NLS-1$
		whileGenericSubtype("""
			package test;
			import java.util.*;
			public class Test {
			    void m(List<ArrayList<String>> lists) {
			        Iterator it = lists.iterator();
			        while (it.hasNext()) {
			            List<String> list = (List<String>) it.next();
			            System.out.println(list);
			        }
			    }
			}
			""", //$NON-NLS-1$

				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<ArrayList<String>> lists) {
					        for (List<String> list : lists) {
					            System.out.println(list);
					        }
					    }
					}
					"""), //$NON-NLS-1$
		whileFix("""
			package test;
			import java.util.*;
			public class Test {
			    void m(List<String> strings) {
			        Collections.reverse(strings);
			        Iterator it = strings.iterator();
			        while (it.hasNext()) {
			            String s = (String) it.next();
			            System.out.println(s);
			            // OK
			            System.err.println(s);
			        }
			        System.out.println();
			    }
			}
			""", //$NON-NLS-1$

				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<String> strings) {
					        Collections.reverse(strings);
					        for (String s : strings) {
					            System.out.println(s);
					            // OK
					            System.err.println(s);
					        }
					        System.out.println();
					    }
					}
					"""), //$NON-NLS-1$
		whileFixTwice("""
			package test;
			import java.util.*;
			public class Test {
			    void m(List<String> strings) {
			        Collections.reverse(strings);
			        Iterator it = strings.iterator();
			        while (it.hasNext()) {
			            String s = (String) it.next();
			            System.out.println(s);
			            // OK
			            System.err.println(s);
			        }
			        System.out.println();
			    }
			    void n(List<String> strings) {
			        Collections.reverse(strings);
			        Iterator it = strings.iterator();
			        while (it.hasNext()) {
			            String s = (String) it.next();
			            System.out.println(s);
			            // OK
			            System.err.println(s);
			        }
			        System.out.println();
			    }
			}
			""", //$NON-NLS-1$

				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<String> strings) {
					        Collections.reverse(strings);
					        for (String s : strings) {
					            System.out.println(s);
					            // OK
					            System.err.println(s);
					        }
					        System.out.println();
					    }
					    void n(List<String> strings) {
					        Collections.reverse(strings);
					        for (String s : strings) {
					            System.out.println(s);
					            // OK
					            System.err.println(s);
					        }
					        System.out.println();
					    }
					}
					"""), //$NON-NLS-1$
		whileFixNested("""
			package test;
			import java.util.*;
			public class Test {
			    void m(List<String> strings,List<String> strings2) {
			        Collections.reverse(strings);
			        Iterator it = strings.iterator();
			        while (it.hasNext()) {
			            String s = (String) it.next();
			            Iterator it2 = strings2.iterator();
			            while (it2.hasNext()) {
			                String s2 = (String) it2.next();
			                System.out.println(s2);
			            }
			            // OK
			            System.err.println(s);
			        }
			        System.out.println();
			    }
			}
			""", //$NON-NLS-1$

				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<String> strings,List<String> strings2) {
					        Collections.reverse(strings);
					        for (String s : strings) {
					            for (String s2 : strings2) {
					                System.out.println(s2);
					            }
					            // OK
					            System.err.println(s);
					        }
					        System.out.println();
					    }
					}
					"""), //$NON-NLS-1$
		whileFixNested2("""
			package test;
			import java.util.*;
			public class Test {
			    void m(List<String> strings,List<String> strings2) {
			        Collections.reverse(strings);
			        Iterator it = strings.iterator();
			        while (it.hasNext()) {
			            Iterator it2 = strings2.iterator();
			            while (it2.hasNext()) {
			                String s2 = (String) it2.next();
			                System.out.println(s2);
			            }
			            // OK
			            System.out.println(it.next());
			        }
			        System.out.println();
			    }
			}
			""", //$NON-NLS-1$

				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<String> strings,List<String> strings2) {
					        Collections.reverse(strings);
					        for (String string : strings) {
					            for (String s2 : strings2) {
					                System.out.println(s2);
					            }
					            // OK
					            System.out.println(string);
					        }
					        System.out.println();
					    }
					}
					"""), //$NON-NLS-1$
		whileWarning(
				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<String> strings) {
					        Iterator it = strings.iterator();
					        while (it.hasNext()) {
					            String s = (String) it.next();
					            System.out.println(s);
					            System.err.println(s);
					        }
					    }
					}
					""" //$NON-NLS-1$
						,"package test;\n" //$NON-NLS-1$
								+ "import java.util.*;\n" //$NON-NLS-1$
								+ "public class Test {\n" //$NON-NLS-1$
								+ "    void m(List<String> strings) {\n" //$NON-NLS-1$
								+ "        for (String s : strings) {\n" //$NON-NLS-1$
								+ "            System.out.println(s);\n" //$NON-NLS-1$
								+ "            System.err.println(s);\n" //$NON-NLS-1$
								+ "        }\n" //$NON-NLS-1$
								+ "    }\n" //$NON-NLS-1$
								+ "}\n" //$NON-NLS-1$
								+ ""), //$NON-NLS-1$
		whileNotRaw(
				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(MyList strings) {
					        Iterator it = strings.iterator();
					        while (it.hasNext()) {
					            String s = (String) it.next();
					            System.out.println(s);
					            System.err.println(s);
					        }
					    }
					    static class MyList extends ArrayList<String> {}
					}
					""" //$NON-NLS-1$
						,"package test;\n" //$NON-NLS-1$
								+ "import java.util.*;\n" //$NON-NLS-1$
								+ "public class Test {\n" //$NON-NLS-1$
								+ "    void m(MyList strings) {\n" //$NON-NLS-1$
								+ "        for (String s : strings) {\n" //$NON-NLS-1$
								+ "            System.out.println(s);\n" //$NON-NLS-1$
								+ "            System.err.println(s);\n" //$NON-NLS-1$
								+ "        }\n" //$NON-NLS-1$
								+ "    }\n" //$NON-NLS-1$
								+ "    static class MyList extends ArrayList<String> {}\n" //$NON-NLS-1$
								+ "}\n" //$NON-NLS-1$
								+ ""), //$NON-NLS-1$
		whileSubtype(
				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<PropertyResourceBundle> bundles) {
					        Iterator it = bundles.iterator();
					        while (it.hasNext()) {
					            ResourceBundle bundle = (ResourceBundle) it.next();
					            System.out.println(bundle);
					            System.err.println(bundle);
					        }
					    }
					}
					""" //$NON-NLS-1$
						,"package test;\n" //$NON-NLS-1$
								+ "import java.util.*;\n" //$NON-NLS-1$
								+ "public class Test {\n" //$NON-NLS-1$
								+ "    void m(List<PropertyResourceBundle> bundles) {\n" //$NON-NLS-1$
								+ "        for (ResourceBundle bundle : bundles) {\n" //$NON-NLS-1$
								+ "            System.out.println(bundle);\n" //$NON-NLS-1$
								+ "            System.err.println(bundle);\n" //$NON-NLS-1$
								+ "        }\n" //$NON-NLS-1$
								+ "    }\n" //$NON-NLS-1$
								+ "}\n" //$NON-NLS-1$
								+ ""); //$NON-NLS-1$

		String given, expected;

		While2EnhancedForLoop(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}

	@ParameterizedTest
	@EnumSource(While2EnhancedForLoop.class)
	public void testWhile2enhancedForLoopParametrized(While2EnhancedForLoop test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("TestDemo.java", test.given, false, null); //$NON-NLS-1$
		context.enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	enum NOWhile2EnhancedForLoop {

		whileUsedSpecially(
				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<String> strings) {
					        Iterator it = strings.iterator();
					        while (it.hasNext()) {
					            String s = (String) it.next();
					            if (s.isEmpty()) {
					                it.remove();
					            } else {
					                System.out.println(s);
					            }
					        }
					    }
					}
					""") //$NON-NLS-1$
		,
		whileRaw(
				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List strings) {
					        Iterator it = strings.iterator();
					        while (it.hasNext()) {
					            String s = (String) it.next();
					            System.out.println(s);
					            System.err.println(s);
					        }
					    }
					}
					""") //$NON-NLS-1$
		,
		whileWrongType(
				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<java.net.URL> strings) {
					        Iterator it = strings.iterator();
					        while (it.hasNext()) {
					            String s = (String) it.next();
					            System.out.println(s);
					            System.err.println(s);
					        }
					    }
					}
					""") //$NON-NLS-1$
		,

		whileNotIterable(
				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(MyList strings) {
					        Iterator it = strings.iterator();
					        while (it.hasNext()) {
					            String s = (String) it.next();
					            System.out.println(s);
					            System.err.println(s);
					        }
					    }
					    interface MyList {
					        Iterator<String> iterator();
					    }
					}
					""") //$NON-NLS-1$
		,

		whileNotSubtype(
				"""
					package test;
					import java.util.*;
					public class Test {
					    void m(List<ResourceBundle> bundles) {
					        Iterator it = bundles.iterator();
					        while (it.hasNext()) {
					            PropertyResourceBundle bundle = (PropertyResourceBundle) it.next();
					            System.out.println(bundle);
					            System.err.println(bundle);
					        }
					    }
					}
					"""); //$NON-NLS-1$

		NOWhile2EnhancedForLoop(String given) {
			this.given=given;
		}

		String given;
	}

	@ParameterizedTest
	@EnumSource(NOWhile2EnhancedForLoop.class)
	public void testWhile2enhancedForLoopdonttouch(NOWhile2EnhancedForLoop test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("Test.java",test.given,false, null); //$NON-NLS-1$
		context.enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
