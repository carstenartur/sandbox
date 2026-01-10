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
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/**
 * Negative tests for functional loop conversion.
 * 
 * <p>
 * This test class contains test cases that should NOT be converted to
 * functional streams. These tests verify that the cleanup correctly identifies
 * patterns that cannot be safely converted due to:
 * </p>
 * <ul>
 * <li>Break statements</li>
 * <li>Throw statements</li>
 * <li>Labeled continue statements</li>
 * <li>External variable modifications</li>
 * <li>Early returns with side effects (non-pattern)</li>
 * <li>Other unsafe transformations</li>
 * </ul>
 * 
 * <p>
 * Each test verifies that the source code remains unchanged after applying the
 * cleanup.
 * </p>
 * 
 * @see org.sandbox.jdt.internal.ui.fix.UseFunctionalLoopCleanUp
 * @see org.sandbox.jdt.internal.corext.fix.helper.PreconditionsChecker
 */
public class FunctionalLoopNegativeTest {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava22();

	@Disabled("Disabled until functional loop cleanup is stable")
	@ParameterizedTest
	@ValueSource(strings = {
// Test case: Break statement (should NOT convert)
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

// Test case: Throw statement (should NOT convert)
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
					}""",

// Test case: Labeled continue (should NOT convert)
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

// Test case: External variable modification (should NOT convert)
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

// Test case: Early return with side effects (should NOT convert)
			"""
					package test1;
					import java.util.List;
					class MyTest {
					    public void test(List<Integer> ls) throws Exception {
					        for(Integer l : ls) {
					            return ;
					        }
					    }
					}""" })
	@DisplayName("Test that loops with unsafe patterns are not converted")
	void testNoConversion(String sourceCode) throws CoreException {
		IPackageFragment pack = context.getSourceFolder().createPackageFragment("test1", false, null);
		ICompilationUnit cu = pack.createCompilationUnit("Test.java", sourceCode, true, null);

		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
