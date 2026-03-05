/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
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
package org.eclipse.jdt.ui.tests.quickfix.Java8;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/**
 * Tests for migrating JUnit 3 TestCase classes to JUnit 5.
 * Covers: extends TestCase removal, setUp → @BeforeEach, tearDown → @AfterEach,
 * testXxx → @Test, and assertion migration.
 */
public class MigrationJUnit3Test {

	@RegisterExtension
	AbstractEclipseJava context = new EclipseJava17();

	IPackageFragmentRoot fRoot;

	@BeforeEach
	public void setup() throws CoreException {
		fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT3_CONTAINER_PATH);
	}

	private void enableJUnit3Cleanup() throws CoreException {
		context.enable(MYCleanUpConstants.JUNIT3_CLEANUP);
		context.enable(MYCleanUpConstants.JUNIT_CLEANUP_3_TEST);
	}

	@Test
	public void removes_extends_TestCase() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class MyTest extends TestCase {
				    protected void setUp() {
				    }
				    public void testSomething() {
				    }
				}
				""", false, null);

		enableJUnit3Cleanup();

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;

				public class MyTest {
				    @BeforeEach
				    protected void setUp() {
				    }
				    @Test
				    public void testSomething() {
				    }
				}
				"""
		}, null);
	}

	@Test
	public void migrates_setUp_to_BeforeEach() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class MyTest extends TestCase {
				    @Override
				    protected void setUp() throws Exception {
				        // init resources
				    }
				    public void testMethod() {
				    }
				}
				""", false, null);

		enableJUnit3Cleanup();

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;

				public class MyTest {
				    @BeforeEach
				    protected void setUp() throws Exception {
				        // init resources
				    }
				    @Test
				    public void testMethod() {
				    }
				}
				"""
		}, null);
	}

	@Test
	public void migrates_tearDown_to_AfterEach() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class MyTest extends TestCase {
				    @Override
				    protected void tearDown() throws Exception {
				        // cleanup resources
				    }
				    public void testMethod() {
				    }
				}
				""", false, null);

		enableJUnit3Cleanup();

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.AfterEach;
				import org.junit.jupiter.api.Test;

				public class MyTest {
				    @AfterEach
				    protected void tearDown() throws Exception {
				        // cleanup resources
				    }
				    @Test
				    public void testMethod() {
				    }
				}
				"""
		}, null);
	}

	@Test
	public void adds_Test_annotation_to_testMethods() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class MyTest extends TestCase {
				    public void testFirst() {
				    }
				    public void testSecond() {
				    }
				    protected void setUp() {
				    }
				}
				""", false, null);

		enableJUnit3Cleanup();

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;

				public class MyTest {
				    @Test
				    public void testFirst() {
				    }
				    @Test
				    public void testSecond() {
				    }
				    @BeforeEach
				    protected void setUp() {
				    }
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertEquals_with_message() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class MyTest extends TestCase {
				    protected void setUp() {
				    }
				    public void testEquals() {
				        assertEquals("Values should match", 42, 42);
				    }
				}
				""", false, null);

		enableJUnit3Cleanup();

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
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
				    public void testEquals() {
				        Assertions.assertEquals(42, 42, "Values should match");
				    }
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertTrue_assertFalse() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class MyTest extends TestCase {
				    protected void setUp() {
				    }
				    public void testBooleans() {
				        assertTrue("Condition should be true", true);
				        assertFalse("Condition should be false", false);
				    }
				}
				""", false, null);

		enableJUnit3Cleanup();

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
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
				    public void testBooleans() {
				        Assertions.assertTrue(true, "Condition should be true");
				        Assertions.assertFalse(false, "Condition should be false");
				    }
				}
				"""
		}, null);
	}

	@Test
	public void migrates_assertNull_assertNotNull() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class MyTest extends TestCase {
				    protected void setUp() {
				    }
				    public void testNulls() {
				        assertNull("Should be null", null);
				        assertNotNull("Should not be null", new Object());
				    }
				}
				""", false, null);

		enableJUnit3Cleanup();

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
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
				    public void testNulls() {
				        Assertions.assertNull(null, "Should be null");
				        Assertions.assertNotNull(new Object(), "Should not be null");
				    }
				}
				"""
		}, null);
	}

	@Test
	public void migrates_fail_call() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class MyTest extends TestCase {
				    protected void setUp() {
				    }
				    public void testFail() {
				        fail("This test should fail");
				    }
				}
				""", false, null);

		enableJUnit3Cleanup();

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
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
				    public void testFail() {
				        Assertions.fail("This test should fail");
				    }
				}
				"""
		}, null);
	}

	@Test
	public void combined_lifecycle_and_assertions() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyTest.java", //$NON-NLS-1$
				"""
				package test;
				import junit.framework.TestCase;

				public class MyTest extends TestCase {
				    protected void setUp() {
				    }
				    protected void tearDown() {
				    }
				    public void testFirst() {
				        assertEquals("msg", 1, 1);
				        assertTrue("cond", true);
				    }
				    public void testSecond() {
				        assertNull("null check", null);
				    }
				}
				""", false, null);

		enableJUnit3Cleanup();

		context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
				"""
				package test;
				import org.junit.jupiter.api.AfterEach;
				import org.junit.jupiter.api.Assertions;
				import org.junit.jupiter.api.BeforeEach;
				import org.junit.jupiter.api.Test;

				public class MyTest {
				    @BeforeEach
				    protected void setUp() {
				    }
				    @AfterEach
				    protected void tearDown() {
				    }
				    @Test
				    public void testFirst() {
				        Assertions.assertEquals(1, 1, "msg");
				        Assertions.assertTrue(true, "cond");
				    }
				    @Test
				    public void testSecond() {
				        Assertions.assertNull(null, "null check");
				    }
				}
				"""
		}, null);
	}

	@Test
	public void no_change_for_non_TestCase_class() throws CoreException {
		IPackageFragment pack = fRoot.createPackageFragment("test", true, null); //$NON-NLS-1$
		ICompilationUnit cu = pack.createCompilationUnit("MyHelper.java", //$NON-NLS-1$
				"""
				package test;

				public class MyHelper {
				    public void setUp() {
				    }
				    public void testLike() {
				    }
				}
				""", false, null);

		enableJUnit3Cleanup();
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}
}
