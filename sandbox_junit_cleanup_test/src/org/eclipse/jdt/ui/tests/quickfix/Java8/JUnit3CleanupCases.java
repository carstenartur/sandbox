package org.eclipse.jdt.ui.tests.quickfix.Java8;

/*-
 * #%L
 * Sandbox junit cleanup test
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

enum JUnit3CleanupCases{
		Junit3Case(
				"""
package test;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class MyTest extends TestCase {

    public MyTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        // Setup vor jedem Test
    }

    @Override
    protected void tearDown() throws Exception {
        // Aufräumen nach jedem Test
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new MyTest("testBasicAssertions"));
        suite.addTest(new MyTest("testArrayAssertions"));
        suite.addTest(new MyTest("testWithAssume"));
        suite.addTest(new MyTest("testAssertThat"));
        suite.addTest(new MyTest("testEqualityWithDelta"));
        suite.addTest(new MyTest("testFail"));
        return suite;
    }

    public void testBasicAssertions() {
        assertEquals("Values should match", 42, 42);
        assertTrue("Condition should be true", true);
        assertFalse("Condition should be false", false);
        assertNull("Should be null", null);
        assertNotNull("Should not be null", new Object());
    }

    public void testArrayAssertions() {
        int[] expected = {1, 2, 3};
        int[] actual = {1, 2, 3};
        assertEquals("Arrays should match", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Array element mismatch at index " + i, expected[i], actual[i]);
        }
    }

    public void testWithAssume() {
//        assumeTrue("Precondition failed", true);
//        assumeFalse("Precondition not met", false);
    }

    public void testAssertThat() {
        assertEquals("Value should match", 42, 42); // Ersatz für assertThat in JUnit 3
    }

    public void testEqualityWithDelta() {
        assertEquals("Floating point equality with delta", 0.1 + 0.2, 0.3, 0.0001);
    }

    public void testFail() {
        fail("This test should fail with a message.");
    }
}
				
				"""
				,
				"""
package test;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assumptions.*;

import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MyTest {

    @BeforeEach
    public void setUp() throws Exception {
        // Setup vor jedem Test
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Aufräumen nach jedem Test
    }

    @Test
    @Order(1)
    public void testBasicAssertions() {
        assertEquals(42, 42, "Values should match");
        assertTrue(true, "Condition should be true");
        assertFalse(false, "Condition should be false");
        assertNull(null, "Should be null");
        assertNotNull(new Object(), "Should not be null");
    }

    @Test
    @Order(2)
    public void testArrayAssertions() {
        int[] expected = {1, 2, 3};
        int[] actual = {1, 2, 3};
        assertArrayEquals(expected, actual, "Arrays should match");
    }

    @Test
    @Order(3)
    public void testWithAssume() {
        assumeTrue(true, "Precondition failed");
        assumeFalse(false, "Precondition not met");
    }

    @Test
    @Order(4)
    public void testAssertThat() {
        assertThat("Value should match", 42, is(42));
    }

    @Test
    @Order(5)
    public void testEqualityWithDelta() {
        assertEquals(0.3, 0.1 + 0.2, 0.0001, "Floating point equality with delta");
    }

    @Test
    @Order(6)
    public void testFail() {
        fail("This test should fail with a message.");
    }
}
				
				"""
				); //$NON-NLS-1$

		String given;
		String expected;

		JUnit3CleanupCases(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}
