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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava8;


public class JUnitMigrationCleanUpTest {

	@RegisterExtension
	AbstractEclipseJava context4junit4= new EclipseJava8();

	@RegisterExtension
	AbstractEclipseJava context4junit5= new EclipseJava8();

	IPackageFragmentRoot fRootJUnit4;
	IPackageFragmentRoot fRootJUnit5;

	@BeforeEach
	public void setup() throws CoreException {
		fRootJUnit4= context4junit4.createClasspathForJUnit("4");
		fRootJUnit5= context4junit5.createClasspathForJUnit("5");
	}

	enum JUnitCleanupCases{
		PositiveCase("""
package test;
import org.junit.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.Assume;
import static org.junit.Assume.assumeTrue;

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
		Assert.fail("Not yet implemented");
		Object o1,o2;
		o1="foo";
		o2=o1;
		Assert.assertSame("ohno", o1, o2);
	}

	@Ignore("not implemented")
	@Test
	public void test2() {
		Assert.fail("Not yet implemented");
	}

	@Test
	public void test3() {
		boolean condition=true;
		Assume.assumeFalse("Bedingung nicht erfüllt", condition);
		Assume.assumeFalse(condition);
		assumeTrue("Bedingung nicht erfüllt", condition);
		Assert.assertEquals("expected", "actual");
	}

	@Test
	public void test4() {
		Assert.assertEquals("failuremessage", "expected", "actual");
		int result=5;
		Assert.assertEquals(5, result);  // expected = 5, actual = result
		Assert.assertNotEquals("failuremessage",5, result);  // expected = 5, actual = result
		Assert.assertNotEquals(5, result);  // expected = 5, actual = result
		Assert.assertTrue("failuremessage",false);
		Assert.assertTrue(false);
		Assert.assertFalse("failuremessage",false);
		Assert.assertFalse(false);
		Assert.assertNull("failuremessage", null);
		Assert.assertNull(null);
	}
}
			""", //$NON-NLS-1$

				"""
package test;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
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
		Assertions.fail("Not yet implemented");
		Object o1,o2;
		o1="foo";
		o2=o1;
		Assertions.assertSame(o1, o2, "ohno");
	}

	@Disabled("not implemented")
	@Test
	public void test2() {
		Assertions.fail("Not yet implemented");
	}

	@Test
	public void test3() {
		boolean condition=true;
		Assumptions.assumeFalse(condition, "Bedingung nicht erfüllt");
		Assumptions.assumeFalse(condition);
		assumeTrue(condition, "Bedingung nicht erfüllt");
		Assertions.assertEquals("expected", "actual");
	}

	@Test
	public void test4() {
		Assertions.assertEquals("expected", "actual", "failuremessage");
		int result=5;
		Assertions.assertEquals(5, result);  // expected = 5, actual = result
		Assertions.assertNotEquals(5,result, "failuremessage");  // expected = 5, actual = result
		Assertions.assertNotEquals(5, result);  // expected = 5, actual = result
		Assertions.assertTrue(false,"failuremessage");
		Assertions.assertTrue(false);
		Assertions.assertFalse(false,"failuremessage");
		Assertions.assertFalse(false);
		Assertions.assertNull(null, "failuremessage");
		Assertions.assertNull(null);
	}
}
					"""),
		AlreadyJunit5Case("""
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
							""", //$NON-NLS-1$
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
"""),
		StaticImportCase("""
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
						assertEquals("expected", "actual");
					}

					@Test
					public void test4() {
						assertEquals("failuremessage", "expected", "actual");
						int result=5;
						assertEquals(5, result);  // expected = 5, actual = result
						assertNotEquals("failuremessage",5, result);  // expected = 5, actual = result
						assertTrue("failuremessage",false);
						assertFalse("failuremessage",false);
						assertTrue(false);
						assertFalse(false);
					}
				}
							""", //$NON-NLS-1$

								"""
				package test;
				import static org.junit.jupiter.api.Assertions.*;
				
				import org.junit.jupiter.api.AfterAll;
				import org.junit.jupiter.api.AfterEach;
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
						assertEquals("expected", "actual");
					}

					@Test
					public void test4() {
						assertEquals("expected", "actual", "failuremessage");
						int result=5;
						assertEquals(5, result);  // expected = 5, actual = result
						assertNotEquals(5,result, "failuremessage");  // expected = 5, actual = result
						assertTrue(false,"failuremessage");
						assertFalse(false,"failuremessage");
						assertTrue(false);
						assertFalse(false);
					}
				}
									"""),
		StaticExplicitImportCase("""
				package test;
				import static org.junit.Assert.fail;
				import static org.junit.Assert.assertEquals;
				import static org.junit.Assert.assertNotEquals;
				import static org.junit.Assert.assertTrue;
				import static org.junit.Assert.assertFalse;

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
						assertEquals("expected", "actual");
					}

					@Test
					public void test4() {
						assertEquals("failuremessage", "expected", "actual");
						int result=5;
						assertEquals(5, result);  // expected = 5, actual = result
						assertNotEquals("failuremessage",5, result);  // expected = 5, actual = result
						assertTrue("failuremessage",false);
						assertFalse("failuremessage",false);
						assertTrue(false);
						assertFalse(false);
					}
				}
							""", //$NON-NLS-1$

								"""
				package test;
				import static org.junit.jupiter.api.Assertions.assertEquals;
				import static org.junit.jupiter.api.Assertions.assertFalse;
				import static org.junit.jupiter.api.Assertions.assertNotEquals;
				import static org.junit.jupiter.api.Assertions.assertTrue;
				import static org.junit.jupiter.api.Assertions.fail;
				
				import org.junit.jupiter.api.AfterAll;
				import org.junit.jupiter.api.AfterEach;
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
						assertEquals("expected", "actual");
					}

					@Test
					public void test4() {
						assertEquals("expected", "actual", "failuremessage");
						int result=5;
						assertEquals(5, result);  // expected = 5, actual = result
						assertNotEquals(5,result, "failuremessage");  // expected = 5, actual = result
						assertTrue(false,"failuremessage");
						assertFalse(false,"failuremessage");
						assertTrue(false);
						assertFalse(false);
					}
				}
				"""),
		RuleAnonymousExternalResource(
"""
package test;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
/**
 * 
 */
public class MyTest {

	@Rule
	public ExternalResource er= new ExternalResource() {
	@Override
	protected void before() throws Throwable {
	};

	@Override
	protected void after() {
	};
	};

	@Test
	public void test3() {
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExternalResource;
/**
 *
 */
public class MyTest {

	@Rule
	public ExternalResource er= new ExternalResource() {
	@Override
	protected void before() throws Throwable {
	};

	@Override
	protected void after() {
	};
	};

	@Test
	public void test3() {
	}
}
"""),
RuleNestedExternalResource(
"""
package test;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
/**
 * 
 */
public class MyTest {

	final class MyExternalResource extends ExternalResource {
		@Override
		protected void before() throws Throwable {
			super.before();
			int i=4;
		}

		@Override
		protected void after() {
			super.after();
		}
	}
	
	@Rule
	public ExternalResource er= new MyExternalResource();

	@Test
	public void test3() {
	}
}
""", //$NON-NLS-1$
"""
package test;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
/**
 *
 */
public class MyTest {

	final class MyExternalResource implements BeforeEachCallback, AfterEachCallback {
		@Override
		public void beforeEach(ExtensionContext context) {
			super.beforeEach(context);
			int i=4;
		}

		@Override
		public void afterEach(ExtensionContext context) {
			super.afterEach(context);
		}
	}

	@RegisterExtension
	public ExternalResource er= new MyExternalResource();

	@Test
	public void test3() {
	}
}
"""),
		TestnameRule(
"""
package test;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TemporaryFolder;
/**
 * 
 */
public class MyTest {

	private static final String SRC= "src";

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Rule
	public TestName tn = new TestName();

	@Test
	public void test3() throws IOException{
		System.out.println("Test name: " + tn.getMethodName());
		File newFile = tempFolder.newFile("myfile.txt");
	}
}
""", //$NON-NLS-1$
"""
package test;
import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
/**
 *
 */
public class MyTest {

	@TempDir
	Path tempFolder;

	private String testName;

	@BeforeEach
	void init(TestInfo testInfo) {
		this.testName = testInfo.getDisplayName();
	}

	private static final String SRC= "src";

	@Test
	public void test3() throws IOException{
		System.out.println("Test name: " + testName);
		File newFile = tempFolder.resolve("myfile.txt").toFile();
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
		IPackageFragment pack= fRootJUnit4.createPackageFragment("test", true, null);
		ICompilationUnit cu= pack.createCompilationUnit("MyTest.java", test.given, false, null); //$NON-NLS-1$
		context4junit4.enable(MYCleanUpConstants.JUNIT_CLEANUP);
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
