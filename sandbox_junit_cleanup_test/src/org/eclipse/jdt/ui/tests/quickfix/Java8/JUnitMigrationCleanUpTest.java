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
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;


public class JUnitMigrationCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context4junit4= new EclipseJava17();

	@RegisterExtension
	AbstractEclipseJava context4junit5= new EclipseJava17();

	IPackageFragmentRoot fRootJUnit4;
	IPackageFragmentRoot fRootJUnit5;

	@BeforeEach
	public void setup() throws CoreException {
		fRootJUnit4= context4junit4.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
		fRootJUnit5= context4junit5.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
	}

	@ParameterizedTest
	@EnumSource(JUnitCleanupCases.class)
	public void testJUnitCleanupParametrized(JUnitCleanupCases test) throws CoreException {
		IPackageFragment pack= fRootJUnit4.createPackageFragment("test", true, null);
		ICompilationUnit cu= pack.createCompilationUnit("MyTest.java", test.given, true, null); //$NON-NLS-1$
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context4junit4.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu}, new String[] {test.expected}, null);
	}

	enum NOJUnitCleanupCases {

NOCase(
"""
package test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
/**
 *
 */
public class MyTest {

	@Test
	public void test3() {
		assertEquals("expected", "actual");
	}
}
"""), //$NON-NLS-1$
UnrelatedCodeCase(
"""
package test;
/**
 *
 */
public class MyTest {

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
		IPackageFragment pack= fRootJUnit5.createPackageFragment("test", true, null);
		ICompilationUnit cu= pack.createCompilationUnit("MyTest.java",test.given,false, null); //$NON-NLS-1$
		context4junit5.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context4junit5.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testJUnitCleanupTwoFiles() throws CoreException {
		IPackageFragment pack= fRootJUnit4.createPackageFragment("test", true, null);
		ICompilationUnit cu= pack.createCompilationUnit("MyTest.java",
"""
package test;
import org.junit.Test;
import org.junit.Rule;
import test.MyExternalResource;
/**
 * 
 */
public class MyTest {
	
	@Rule
	public MyExternalResource er= new MyExternalResource();

	@Before
	public void genericbefore(){
		er.start();
	}

	@Test
	public void test3() {
	}
}
""", false, null); //$NON-NLS-1$
		ICompilationUnit cu2= pack.createCompilationUnit("MyExternalResource.java",
"""
package test;
import org.junit.rules.ExternalResource;
/**
 * 
 */
public class MyExternalResource extends ExternalResource {
		@Override
		protected void before() throws Throwable {
			int i=4;
		}

		@Override
		protected void after() {
		}
		
		public start(){
		}
}
""", false, null); //$NON-NLS-1$
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context4junit4.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu,cu2}, new String[] {
"""
package test;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import test.MyExternalResource;
/**
 *
 */
public class MyTest {

	@RegisterExtension
	public MyExternalResource er= new MyExternalResource();

	@Before
	public void genericbefore(){
		er.start();
	}

	@Test
	public void test3() {
	}
}
"""
,
"""
package test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
/**
 *
 */
public class MyExternalResource implements BeforeEachCallback, AfterEachCallback {
		@Override
		public void beforeEach(ExtensionContext context) {
			int i=4;
		}

		@Override
		public void afterEach(ExtensionContext context) {
		}

		public start(){
		}
}
"""
}, null);
	}
	
	@Test
	public void testJUnitCleanupTwoFilesb() throws CoreException {
		IPackageFragment pack= fRootJUnit4.createPackageFragment("test", true, null);
		ICompilationUnit cu= pack.createCompilationUnit("MyTest.java",
"""
package test;
import test.MyExternalResource;
/**
 * 
 */
public class MyTest extends MyExternalResource {

}
""", false, null); //$NON-NLS-1$
		ICompilationUnit cu2= pack.createCompilationUnit("MyExternalResource.java",
"""
package test;
import org.junit.rules.ExternalResource;
/**
 * 
 */
public class MyExternalResource extends ExternalResource {
		@Override
		protected void before() throws Throwable {
			int i=4;
		}

		@Override
		protected void after() {
		}
		
		public start(){
		}
}
""", false, null); //$NON-NLS-1$
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context4junit4.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu,cu2}, new String[] {
"""
package test;
import test.MyExternalResource;
/**
 *
 */
public class MyTest extends MyExternalResource {

}
"""
,
"""
package test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
/**
 *
 */
public class MyExternalResource implements BeforeEachCallback, AfterEachCallback {
		@Override
		public void beforeEach(ExtensionContext context) {
			int i=4;
		}

		@Override
		public void afterEach(ExtensionContext context) {
		}

		public start(){
		}
}
"""
}, null);
	}

	@Test
	public void testJUnitCleanupThreeFiles() throws CoreException {
		IPackageFragment pack= fRootJUnit4.createPackageFragment("test", true, null);
		ICompilationUnit cu= pack.createCompilationUnit("MyTest.java",
"""
package test;
import org.junit.Test;
import org.junit.Rule;
import test.MyExternalResource;

public class MyTest {
	
	@Rule
	public MyExternalResource er= new MyExternalResource();

	@Test
	public void test3() {
	}
}
""", false, null); //$NON-NLS-1$
		ICompilationUnit cu2= pack.createCompilationUnit("MyExternalResource.java",
"""
package test;
import test.MyExternalResource2;

public class MyExternalResource extends MyExternalResource2 {
		@Override
		protected void before() throws Throwable {
			super.before();
			int i=4;
		}

		@Override
		protected void after() {
		}
}
""", false, null); //$NON-NLS-1$
		
		ICompilationUnit cu3= pack.createCompilationUnit("MyExternalResource2.java",
"""
package test;
import org.junit.rules.ExternalResource;

public class MyExternalResource2 extends ExternalResource {
		@Override
		protected void before() throws Throwable {
			super.before();
			int i=4;
		}

		@Override
		protected void after() {
		}
}
""", false, null); //$NON-NLS-1$
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSERT);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_ASSUME);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_SUITE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORECLASS);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTERCLASS);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_IGNORE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETEMPORARYFOLDER);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RULETESTNAME);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_EXTERNALRESOURCE);
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_RUNWITH);
		context4junit4.assertRefactoringResultAsExpected(new ICompilationUnit[] {cu,cu2,cu3}, new String[] {
"""
package test;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import test.MyExternalResource;

public class MyTest {

	@RegisterExtension
	public MyExternalResource er= new MyExternalResource();

	@Test
	public void test3() {
	}
}
"""
,
"""
package test;
import org.junit.jupiter.api.extension.ExtensionContext;

import test.MyExternalResource2;

public class MyExternalResource extends MyExternalResource2 {
		@Override
		public void beforeEach(ExtensionContext context) {
			super.beforeEach(context);
			int i=4;
		}

		@Override
		public void afterEach(ExtensionContext context) {
		}
}
""",
"""
package test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MyExternalResource2 implements BeforeEachCallback, AfterEachCallback {
		@Override
		public void beforeEach(ExtensionContext context) {
			super.beforeEach(context);
			int i=4;
		}

		@Override
		public void afterEach(ExtensionContext context) {
		}
}
"""
}, null);
	}
}
