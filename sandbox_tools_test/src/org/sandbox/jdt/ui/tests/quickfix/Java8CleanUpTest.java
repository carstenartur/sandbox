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
		whileWarningSelf("package test;\n"
				+ "import java.util.*;\n"
				+ "public class Test extends ArrayList<String> {\n"
				+ "    void m() {\n"
				+ "        Iterator it = iterator();\n"
				+ "        while (it.hasNext()) {\n"
				+ "            String s = (String) it.next();\n"
				+ "            System.out.println(s);\n"
				+ "            System.err.println(s);\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n",

				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test extends ArrayList<String> {\n"
						+ "    void m() {\n"
						+ "        for (String s : this) {\n"
						+ "            System.out.println(s);\n"
						+ "            System.err.println(s);\n"
						+ "        }\n"
						+ "    }\n"
						+ "}\n"),
		whileGenericSubtype("package test;\n"
				+ "import java.util.*;\n"
				+ "public class Test {\n"
				+ "    void m(List<ArrayList<String>> lists) {\n"
				+ "        Iterator it = lists.iterator();\n"
				+ "        while (it.hasNext()) {\n"
				+ "            List<String> list = (List<String>) it.next();\n"
				+ "            System.out.println(list);\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n",

				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List<ArrayList<String>> lists) {\n"
						+ "        for (List<String> list : lists) {\n"
						+ "            System.out.println(list);\n"
						+ "        }\n"
						+ "    }\n"
						+ "}\n"),
		whileFix("package test;\n"
				+ "import java.util.*;\n"
				+ "public class Test {\n"
				+ "    void m(List<String> strings) {\n"
				+ "        Collections.reverse(strings);\n"
				+ "        Iterator it = strings.iterator();\n"
				+ "        while (it.hasNext()) {\n"
				+ "            String s = (String) it.next();\n"
				+ "            System.out.println(s);\n"
				+ "            // OK\n"
				+ "            System.err.println(s);\n"
				+ "        }\n"
				+ "        System.out.println();\n"
				+ "    }\n"
				+ "}\n",

				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List<String> strings) {\n"
						+ "        Collections.reverse(strings);\n"
						+ "        for (String s : strings) {\n"
						+ "            System.out.println(s);\n"
						+ "            // OK\n"
						+ "            System.err.println(s);\n"
						+ "        }\n"
						+ "        System.out.println();\n"
						+ "    }\n"
						+ "}\n"),
		whileFixTwice("package test;\n"
				+ "import java.util.*;\n"
				+ "public class Test {\n"
				+ "    void m(List<String> strings) {\n"
				+ "        Collections.reverse(strings);\n"
				+ "        Iterator it = strings.iterator();\n"
				+ "        while (it.hasNext()) {\n"
				+ "            String s = (String) it.next();\n"
				+ "            System.out.println(s);\n"
				+ "            // OK\n"
				+ "            System.err.println(s);\n"
				+ "        }\n"
				+ "        System.out.println();\n"
				+ "    }\n"
				+ "    void n(List<String> strings) {\n"
				+ "        Collections.reverse(strings);\n"
				+ "        Iterator it = strings.iterator();\n"
				+ "        while (it.hasNext()) {\n"
				+ "            String s = (String) it.next();\n"
				+ "            System.out.println(s);\n"
				+ "            // OK\n"
				+ "            System.err.println(s);\n"
				+ "        }\n"
				+ "        System.out.println();\n"
				+ "    }\n"
				+ "}\n",

				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List<String> strings) {\n"
						+ "        Collections.reverse(strings);\n"
						+ "        for (String s : strings) {\n"
						+ "            System.out.println(s);\n"
						+ "            // OK\n"
						+ "            System.err.println(s);\n"
						+ "        }\n"
						+ "        System.out.println();\n"
						+ "    }\n"
						+ "    void n(List<String> strings) {\n"
						+ "        Collections.reverse(strings);\n"
						+ "        for (String s : strings) {\n"
						+ "            System.out.println(s);\n"
						+ "            // OK\n"
						+ "            System.err.println(s);\n"
						+ "        }\n"
						+ "        System.out.println();\n"
						+ "    }\n"
						+ "}\n"),
		whileFixNested("package test;\n"
				+ "import java.util.*;\n"
				+ "public class Test {\n"
				+ "    void m(List<String> strings,List<String> strings2) {\n"
				+ "        Collections.reverse(strings);\n"
				+ "        Iterator it = strings.iterator();\n"
				+ "        while (it.hasNext()) {\n"
				+ "            String s = (String) it.next();\n"
				+ "            Iterator it2 = strings2.iterator();\n"
				+ "            while (it2.hasNext()) {\n"
				+ "                String s2 = (String) it2.next();\n"
				+ "                System.out.println(s2);\n"
				+ "            }\n"
				+ "            // OK\n"
				+ "            System.err.println(s);\n"
				+ "        }\n"
				+ "        System.out.println();\n"
				+ "    }\n"
				+ "}\n",

				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List<String> strings,List<String> strings2) {\n"
						+ "        Collections.reverse(strings);\n"
						+ "        for (String s : strings) {\n"
						+ "            for (String s2 : strings2) {\n"
						+ "                System.out.println(s2);\n"
						+ "            }\n"
						+ "            // OK\n"
						+ "            System.err.println(s);\n"
						+ "        }\n"
						+ "        System.out.println();\n"
						+ "    }\n"
						+ "}\n"),
		whileFixNested2("package test;\n"
				+ "import java.util.*;\n"
				+ "public class Test {\n"
				+ "    void m(List<String> strings,List<String> strings2) {\n"
				+ "        Collections.reverse(strings);\n"
				+ "        Iterator it = strings.iterator();\n"
				+ "        while (it.hasNext()) {\n"
				+ "            Iterator it2 = strings2.iterator();\n"
				+ "            while (it2.hasNext()) {\n"
				+ "                String s2 = (String) it2.next();\n"
				+ "                System.out.println(s2);\n"
				+ "            }\n"
				+ "            // OK\n"
				+ "            System.out.println(it.next());\n"
				+ "        }\n"
				+ "        System.out.println();\n"
				+ "    }\n"
				+ "}\n",

				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List<String> strings,List<String> strings2) {\n"
						+ "        Collections.reverse(strings);\n"
						+ "        for (String string : strings) {\n"
						+ "            for (String s2 : strings2) {\n"
						+ "                System.out.println(s2);\n"
						+ "            }\n"
						+ "            // OK\n"
						+ "            System.out.println(string);\n"
						+ "        }\n"
						+ "        System.out.println();\n"
						+ "    }\n"
						+ "}\n"),
		whileWarning(
				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List<String> strings) {\n"
						+ "        Iterator it = strings.iterator();\n"
						+ "        while (it.hasNext()) {\n"
						+ "            String s = (String) it.next();\n"
						+ "            System.out.println(s);\n"
						+ "            System.err.println(s);\n"
						+ "        }\n"
						+ "    }\n"
						+ "}\n"
						,"package test;\n"
								+ "import java.util.*;\n"
								+ "public class Test {\n"
								+ "    void m(List<String> strings) {\n"
								+ "        for (String s : strings) {\n"
								+ "            System.out.println(s);\n"
								+ "            System.err.println(s);\n"
								+ "        }\n"
								+ "    }\n"
								+ "}\n"
								+ ""),
		whileNotRaw(
				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(MyList strings) {\n"
						+ "        Iterator it = strings.iterator();\n"
						+ "        while (it.hasNext()) {\n"
						+ "            String s = (String) it.next();\n"
						+ "            System.out.println(s);\n"
						+ "            System.err.println(s);\n"
						+ "        }\n"
						+ "    }\n"
						+ "    static class MyList extends ArrayList<String> {}\n"
						+ "}\n"
						,"package test;\n"
								+ "import java.util.*;\n"
								+ "public class Test {\n"
								+ "    void m(MyList strings) {\n"
								+ "        for (String s : strings) {\n"
								+ "            System.out.println(s);\n"
								+ "            System.err.println(s);\n"
								+ "        }\n"
								+ "    }\n"
								+ "    static class MyList extends ArrayList<String> {}\n"
								+ "}\n"
								+ ""),
		whileSubtype(
				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List<PropertyResourceBundle> bundles) {\n"
						+ "        Iterator it = bundles.iterator();\n"
						+ "        while (it.hasNext()) {\n"
						+ "            ResourceBundle bundle = (ResourceBundle) it.next();\n"
						+ "            System.out.println(bundle);\n"
						+ "            System.err.println(bundle);\n"
						+ "        }\n"
						+ "    }\n"
						+ "}\n"
						,"package test;\n"
								+ "import java.util.*;\n"
								+ "public class Test {\n"
								+ "    void m(List<PropertyResourceBundle> bundles) {\n"
								+ "        for (ResourceBundle bundle : bundles) {\n"
								+ "            System.out.println(bundle);\n"
								+ "            System.err.println(bundle);\n"
								+ "        }\n"
								+ "    }\n"
								+ "}\n"
								+ "");

		While2EnhancedForLoop(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}

		String given, expected;
	}

	@ParameterizedTest
	@EnumSource(While2EnhancedForLoop.class)
	public void testWhile2enhancedForLoopParametrized(While2EnhancedForLoop test) throws CoreException {
		IPackageFragment pack= context.fSourceFolder.createPackageFragment("test1", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("TestDemo.java", test.given, false, null);
		context.enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	enum NO_While2EnhancedForLoop {

		whileUsedSpecially(
				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List<String> strings) {\n"
						+ "        Iterator it = strings.iterator();\n"
						+ "        while (it.hasNext()) {\n"
						+ "            String s = (String) it.next();\n"
						+ "            if (s.isEmpty()) {\n"
						+ "                it.remove();\n"
						+ "            } else {\n"
						+ "                System.out.println(s);\n"
						+ "            }\n"
						+ "        }\n"
						+ "    }\n"
						+ "}\n")
		,
		whileRaw(
				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List strings) {\n"
						+ "        Iterator it = strings.iterator();\n"
						+ "        while (it.hasNext()) {\n"
						+ "            String s = (String) it.next();\n"
						+ "            System.out.println(s);\n"
						+ "            System.err.println(s);\n"
						+ "        }\n"
						+ "    }\n"
						+ "}\n")
		,
		whileWrongType(
				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List<java.net.URL> strings) {\n"
						+ "        Iterator it = strings.iterator();\n"
						+ "        while (it.hasNext()) {\n"
						+ "            String s = (String) it.next();\n"
						+ "            System.out.println(s);\n"
						+ "            System.err.println(s);\n"
						+ "        }\n"
						+ "    }\n"
						+ "}\n")
		,

		whileNotIterable(
				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(MyList strings) {\n"
						+ "        Iterator it = strings.iterator();\n"
						+ "        while (it.hasNext()) {\n"
						+ "            String s = (String) it.next();\n"
						+ "            System.out.println(s);\n"
						+ "            System.err.println(s);\n"
						+ "        }\n"
						+ "    }\n"
						+ "    interface MyList {\n"
						+ "        Iterator<String> iterator();\n"
						+ "    }\n"
						+ "}\n")
		,

		whileNotSubtype(
				"package test;\n"
						+ "import java.util.*;\n"
						+ "public class Test {\n"
						+ "    void m(List<ResourceBundle> bundles) {\n"
						+ "        Iterator it = bundles.iterator();\n"
						+ "        while (it.hasNext()) {\n"
						+ "            PropertyResourceBundle bundle = (PropertyResourceBundle) it.next();\n"
						+ "            System.out.println(bundle);\n"
						+ "            System.err.println(bundle);\n"
						+ "        }\n"
						+ "    }\n"
						+ "}\n");

		NO_While2EnhancedForLoop(String given) {
			this.given=given;
		}

		String given;
	}

	@ParameterizedTest
	@EnumSource(NO_While2EnhancedForLoop.class)
	public void testWhile2enhancedForLoop_donttouch(NO_While2EnhancedForLoop test) throws CoreException {
		IPackageFragment pack= context.fSourceFolder.createPackageFragment("test", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("Test.java",test.given,false, null);
		context.enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
