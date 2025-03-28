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
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;

public class Java8CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();

	enum XMLCleanupCases{
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
			""",

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
					""");

		String given;
		String expected;

		XMLCleanupCases(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}

	@Disabled
	@ParameterizedTest
	@EnumSource(XMLCleanupCases.class)
	public void testXMLCleanupParametrized(XMLCleanupCases test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("Test.java", test.given, false, null);
		context.enable(MYCleanUpConstants.XML_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	enum NOXMLCleanupCases {

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
					""")
		;

		NOXMLCleanupCases(String given) {
			this.given=given;
		}

		String given;
	}

	@Disabled
	@ParameterizedTest
	@EnumSource(NOXMLCleanupCases.class)
	public void testXMLCleanupdonttouch(NOXMLCleanupCases test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test", false, null);
		ICompilationUnit cu= pack.createCompilationUnit("Test.java",test.given,false, null);
		context.enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
