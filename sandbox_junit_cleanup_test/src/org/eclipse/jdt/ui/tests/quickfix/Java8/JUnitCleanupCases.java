package org.eclipse.jdt.ui.tests.quickfix.Java8;

enum JUnitCleanupCases{
		PositiveCase("""
package test;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        MyTest.class
})
public class MyTest {

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // Setup vor allen Tests
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        // Aufräumen nach allen Tests
    }

    @Before
    public void setUp() throws Exception {
        // Setup vor jedem Test
    }

    @After
    public void tearDown() throws Exception {
        // Aufräumen nach jedem Test
    }

    @Ignore("Ignored with message")
    @Test
    public void ignoredTestWithMessage() {
        Assert.fail("This test is ignored with a message.");
    }

    @Ignore
    @Test
    public void ignoredTestWithoutMessage() {
        Assert.fail("This test is ignored without a message.");
    }

    @Test
    public void testBasicAssertions() {
        Assert.assertEquals("Values should match", 42, 42);
        Assert.assertEquals(42, 42);

        Assert.assertNotEquals("Values should not match", 42, 43);
        Assert.assertNotEquals(42, 43);

        Object o = "test";
        Assert.assertSame("Objects should be same", o, o);
        Assert.assertSame(o, o);

        Assert.assertNotSame("Objects should not be same", "test1", "test2");
        Assert.assertNotSame("test1", "test2");

        Assert.assertNull("Should be null", null);
        Assert.assertNull(null);

        Assert.assertNotNull("Should not be null", new Object());
        Assert.assertNotNull(new Object());

        Assert.assertTrue("Condition should be true", true);
        Assert.assertTrue(true);

        Assert.assertFalse("Condition should be false", false);
        Assert.assertFalse(false);
    }

    @Test
    public void testArrayAssertions() {
        int[] expected = {1, 2, 3};
        int[] actual = {1, 2, 3};

        Assert.assertArrayEquals("Arrays should match", expected, actual);
        Assert.assertArrayEquals(expected, actual);
    }

    @Test
    public void testWithAssume() {
        Assume.assumeTrue("Precondition failed", true);
        Assume.assumeTrue(true);

        Assume.assumeFalse("Precondition not met", false);
        Assume.assumeFalse(false);

        Assume.assumeNotNull("Value should not be null", new Object());
        Assume.assumeNotNull(new Object());

        Assume.assumeThat("Value should match condition", 42, CoreMatchers.is(42));
        Assume.assumeThat(42, CoreMatchers.is(42));
    }

    @Test
    public void testAssertThat() {
        Assert.assertThat("Value should match", 42, CoreMatchers.is(42));
        Assert.assertThat(42, CoreMatchers.is(42));

        Assert.assertThat("String should contain value", "Hello, world!", CoreMatchers.containsString("world"));
        Assert.assertThat("Hello, world!", CoreMatchers.containsString("world"));
    }

    @Test
    public void testEqualityWithDelta() {
        Assert.assertEquals("Floating point equality with delta", 0.1 + 0.2, 0.3, 0.0001);
        Assert.assertEquals(0.1 + 0.2, 0.3, 0.0001);

        Assert.assertNotEquals("Floating point inequality", 0.1 + 0.2, 0.4, 0.0001);
        Assert.assertNotEquals(0.1 + 0.2, 0.4, 0.0001);
    }

    @Test
    public void testFail() {
        // Testfälle für Assert.fail
        Assert.fail("This test should fail with a message.");
        Assert.fail();
    }
}
""", //$NON-NLS-1$

"""
package test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.junit.MatcherAssume.assumeThat;

import org.hamcrest.CoreMatchers;
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

@Suite
@SelectClasses({
        MyTest.class
})
public class MyTest {

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        // Setup vor allen Tests
    }

    @AfterAll
    public static void tearDownAfterClass() throws Exception {
        // Aufräumen nach allen Tests
    }

    @BeforeEach
    public void setUp() throws Exception {
        // Setup vor jedem Test
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Aufräumen nach jedem Test
    }

    @Disabled("Ignored with message")
    @Test
    public void ignoredTestWithMessage() {
        Assertions.fail("This test is ignored with a message.");
    }

    @Disabled
    @Test
    public void ignoredTestWithoutMessage() {
        Assertions.fail("This test is ignored without a message.");
    }

    @Test
    public void testBasicAssertions() {
        Assertions.assertEquals(42, 42, "Values should match");
        Assertions.assertEquals(42, 42);

        Assertions.assertNotEquals(42, 43, "Values should not match");
        Assertions.assertNotEquals(42, 43);

        Object o = "test";
        Assertions.assertSame(o, o, "Objects should be same");
        Assertions.assertSame(o, o);

        Assertions.assertNotSame("test1", "test2", "Objects should not be same");
        Assertions.assertNotSame("test1", "test2");

        Assertions.assertNull(null, "Should be null");
        Assertions.assertNull(null);

        Assertions.assertNotNull(new Object(), "Should not be null");
        Assertions.assertNotNull(new Object());

        Assertions.assertTrue(true, "Condition should be true");
        Assertions.assertTrue(true);

        Assertions.assertFalse(false, "Condition should be false");
        Assertions.assertFalse(false);
    }

    @Test
    public void testArrayAssertions() {
        int[] expected = {1, 2, 3};
        int[] actual = {1, 2, 3};

        Assertions.assertArrayEquals(expected, actual, "Arrays should match");
        Assertions.assertArrayEquals(expected, actual);
    }

    @Test
    public void testWithAssume() {
        Assumptions.assumeTrue(true, "Precondition failed");
        Assumptions.assumeTrue(true);

        Assumptions.assumeFalse(false, "Precondition not met");
        Assumptions.assumeFalse(false);

        Assumptions.assumeNotNull(new Object(), "Value should not be null");
        Assumptions.assumeNotNull(new Object());

        assumeThat("Value should match condition", 42, CoreMatchers.is(42));
        assumeThat(42, CoreMatchers.is(42));
    }

    @Test
    public void testAssertThat() {
        assertThat("Value should match", 42, CoreMatchers.is(42));
        assertThat(42, CoreMatchers.is(42));

        assertThat("String should contain value", "Hello, world!", CoreMatchers.containsString("world"));
        assertThat("Hello, world!", CoreMatchers.containsString("world"));
    }

    @Test
    public void testEqualityWithDelta() {
        Assertions.assertEquals(0.1 + 0.2, 0.3, 0.0001, "Floating point equality with delta");
        Assertions.assertEquals(0.1 + 0.2, 0.3, 0.0001);

        Assertions.assertNotEquals(0.1 + 0.2, 0.4, 0.0001, "Floating point inequality");
        Assertions.assertNotEquals(0.1 + 0.2, 0.4, 0.0001);
    }

    @Test
    public void testFail() {
        // Testfälle für Assert.fail
        Assertions.fail("This test should fail with a message.");
        Assertions.fail();
    }
}
"""),
		AlreadyJunit5Case(
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