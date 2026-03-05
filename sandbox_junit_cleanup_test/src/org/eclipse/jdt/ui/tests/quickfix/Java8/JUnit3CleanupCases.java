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
		MinimalCase(
				"""
package test;

import junit.framework.TestCase;

public class MyTest extends TestCase {

    protected void setUp() {
    }

    public void testSomething() {
        assertEquals("msg", 1, 1);
    }
}
				""", //$NON-NLS-1$
				"""
package test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MyTest {

    @BeforeEach
    protected void setUp() {
    }

    @Test
    public void testSomething() {
        Assertions.assertEquals(1, 1, "msg");
    }
}
				""" //$NON-NLS-1$
				),
		SetUpTearDownCase(
				"""
package test;

import junit.framework.TestCase;

public class MyTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        // Setup
    }

    @Override
    protected void tearDown() throws Exception {
        // Cleanup
    }

    public void testFirst() {
    }

    public void testSecond() {
    }
}
				""", //$NON-NLS-1$
				"""
package test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MyTest {

    @BeforeEach
    protected void setUp() throws Exception {
        // Setup
    }

    @AfterEach
    protected void tearDown() throws Exception {
        // Cleanup
    }

    @Test
    public void testFirst() {
    }

    @Test
    public void testSecond() {
    }
}
				""" //$NON-NLS-1$
				),
		AssertionsCase(
				"""
package test;

import junit.framework.TestCase;

public class MyTest extends TestCase {

    protected void setUp() {
    }

    public void testAssertions() {
        assertEquals("Values should match", 42, 42);
        assertTrue("Should be true", true);
        assertFalse("Should be false", false);
        assertNull("Should be null", null);
        assertNotNull("Should not be null", new Object());
    }

    public void testFail() {
        fail("This test should fail");
    }
}
				""", //$NON-NLS-1$
				"""
package test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MyTest {

    @BeforeEach
    protected void setUp() {
    }

    @Test
    public void testAssertions() {
        Assertions.assertEquals(42, 42, "Values should match");
        Assertions.assertTrue(true, "Should be true");
        Assertions.assertFalse(false, "Should be false");
        Assertions.assertNull(null, "Should be null");
        Assertions.assertNotNull(new Object(), "Should not be null");
    }

    @Test
    public void testFail() {
        Assertions.fail("This test should fail");
    }
}
				""" //$NON-NLS-1$
				); //$NON-NLS-1$

		String given;
		String expected;

		JUnit3CleanupCases(String given, String expected) {
			this.given=given;
			this.expected=expected;
		}
	}
