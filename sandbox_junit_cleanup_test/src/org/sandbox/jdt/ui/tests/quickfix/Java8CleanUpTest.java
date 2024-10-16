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
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;


public class Java8CleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava8();
	
	public static final String JUNIT_CONTAINER_ID= "org.eclipse.jdt.junit.JUNIT_CONTAINER"; //$NON-NLS-1$
	public final static IPath JUNIT4_CONTAINER_PATH= new Path(JUNIT_CONTAINER_ID).append("4"); //$NON-NLS-1$
	
	IPackageFragmentRoot fRoot;
	@BeforeEach
	public void setup() throws CoreException {
		IJavaProject fProject = context.getJavaProject();
		fProject.setRawClasspath(context.getDefaultClasspath(), null);
		IClasspathEntry cpe= JavaCore.newContainerEntry(JUNIT4_CONTAINER_PATH);
		AbstractEclipseJava.addToClasspath(fProject, cpe);
		fRoot = AbstractEclipseJava.addSourceContainer(fProject, "src");
	}

	enum JUnitCleanupCases{
		PositiveCase("""
package test;
import static org.junit.Assert.*;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * 
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
	MyTest.class
})
public class MyTest {

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	@Ignore
	@Test
	public void test() {
		fail("Not yet implemented");
	}

	@Ignore("not implemented")
	@Test
	public void test2() {
		fail("Not yet implemented");
	}

	@Test
	public void test3() {
		Assert.assertEquals("expected", "actual");
	}

	@Test
	public void test4() {
		Assert.assertEquals("failuremessage", "expected", "actual");
		int result=5;
		Assert.assertEquals(5, result);  // expected = 5, actual = result
	}
}
			""", //$NON-NLS-1$

				"""
package test;
import static org.junit.Assert.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 *
 */
@Suite
@SelectClasses({
	MyTest.class
})
public class MyTest {

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeAll
	public static void setUpBeforeClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterAll
	public static void tearDownAfterClass() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeEach
	public void setUp() throws Exception {
	}

	/**
	 * @throws java.lang.Exception
	 */
	@AfterEach
	public void tearDown() throws Exception {
	}

	@Disabled
	@Test
	public void test() {
		fail("Not yet implemented");
	}

	@Disabled("not implemented")
	@Test
	public void test2() {
		fail("Not yet implemented");
	}

	@Test
	public void test3() {
		Assert.assertEquals("expected", "actual");
	}

	@Test
	public void test4() {
		Assert.assertEquals("expected", "actual", "failuremessage");
		int result=5;
		Assert.assertEquals(5, result);  // expected = 5, actual = result
	}
}
					"""); //$NON-NLS-1$

		String given;
		String expected;

		JUnitCleanupCases(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}

	@ParameterizedTest
	@EnumSource(JUnitCleanupCases.class)
	public void testJUnitCleanupParametrized(JUnitCleanupCases test) throws CoreException {
		IPackageFragment pack= fRoot.createPackageFragment("test", true, null);
		ICompilationUnit cu= pack.createCompilationUnit("Test.java", test.given, false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	enum NOJUnitCleanupCases {

		NOCase(
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
		;

		NOJUnitCleanupCases(String given) {
			this.given=given;
		}

		String given;
	}

	@ParameterizedTest
	@EnumSource(NOJUnitCleanupCases.class)
	public void testJUnitCleanupdonttouch(NOJUnitCleanupCases test) throws CoreException {
		IPackageFragment pack= context.getfSourceFolder().createPackageFragment("test", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("Test.java",test.given,false, null); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
