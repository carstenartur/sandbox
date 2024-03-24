/*******************************************************************************
 * Copyright (c) 2022
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer initial implementation
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

public class Java8CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();

	enum XMLCleanupCases{
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
						+ "}\n");

		XMLCleanupCases(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}

		String given, expected;
	}

	@Disabled
	@ParameterizedTest
	@EnumSource(XMLCleanupCases.class)
	public void testXMLCleanupParametrized(XMLCleanupCases test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("Test.java", test.given, false, null);
		context.enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	enum NO_XMLCleanupCases {

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
		;

		NO_XMLCleanupCases(String given) {
			this.given=given;
		}

		String given;
	}

	@Disabled
	@ParameterizedTest
	@EnumSource(NO_XMLCleanupCases.class)
	public void testXMLCleanup_donttouch(NO_XMLCleanupCases test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("Test.java",test.given,false, null);
		context.enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
