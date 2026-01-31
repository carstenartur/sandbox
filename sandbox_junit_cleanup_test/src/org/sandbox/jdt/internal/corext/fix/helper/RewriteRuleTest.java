/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.junit.JUnitCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.triggerpattern.api.RewriteRule;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/**
 * Tests for @RewriteRule annotation functionality.
 * Includes both unit tests for annotation presence and integration tests for actual transformation.
 */
class RewriteRuleTest {
    
    @RegisterExtension
    AbstractEclipseJava context = new EclipseJava17();
    
    @Test
    void testBeforeJUnitPluginV2_hasRewriteRuleAnnotation() {
        BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
        
        RewriteRule rewriteRule = plugin.getClass().getAnnotation(RewriteRule.class);
        
        assertNotNull(rewriteRule, "@RewriteRule annotation should be present");
        assertEquals("@BeforeEach", rewriteRule.replaceWith());
        assertArrayEquals(new String[]{"org.junit.Before"}, rewriteRule.removeImports());
        assertArrayEquals(new String[]{"org.junit.jupiter.api.BeforeEach"}, rewriteRule.addImports());
    }
    
    @Test
    void testAfterJUnitPluginV2_hasRewriteRuleAnnotation() {
        AfterJUnitPluginV2 plugin = new AfterJUnitPluginV2();
        
        RewriteRule rewriteRule = plugin.getClass().getAnnotation(RewriteRule.class);
        
        assertNotNull(rewriteRule, "@RewriteRule annotation should be present");
        assertEquals("@AfterEach", rewriteRule.replaceWith());
        assertArrayEquals(new String[]{"org.junit.After"}, rewriteRule.removeImports());
        assertArrayEquals(new String[]{"org.junit.jupiter.api.AfterEach"}, rewriteRule.addImports());
    }
    
    @Test
    void testRewriteRule_defaultValues() {
        // Test that default values are properly defined
        RewriteRule rewriteRule = BeforeJUnitPluginV2.class.getAnnotation(RewriteRule.class);
        
        // removeStaticImports and addStaticImports should have empty defaults
        assertNotNull(rewriteRule.removeStaticImports());
        assertNotNull(rewriteRule.addStaticImports());
        assertEquals(0, rewriteRule.removeStaticImports().length);
        assertEquals(0, rewriteRule.addStaticImports().length);
    }
    
    @Test
    void testRewriteRule_integrationWithCleanupPattern() {
        // Verify that both @CleanupPattern and @RewriteRule work together
        BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
        
        assertNotNull(plugin.getPattern(), "Pattern should be extracted from @CleanupPattern");
        assertNotNull(plugin.getClass().getAnnotation(RewriteRule.class), 
                "@RewriteRule should be present");
        
        // The plugin should be able to get both annotations
        assertEquals("cleanup.junit.before", plugin.getCleanupId());
        assertEquals("@Before", plugin.getPattern().getValue());
    }
    
    /**
     * Integration test: Verifies that @RewriteRule actually transforms code correctly.
     * This tests the end-to-end cleanup execution using the declarative annotation approach.
     */
    @Test
    void testRewriteRule_actualTransformation_Before() throws CoreException {
        IPackageFragmentRoot fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
        IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
        ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
                """
                package test;
                import org.junit.Before;
                import org.junit.Test;
                
                public class MyTest {
                    @Before
                    public void setUp() {
                        // Setup code
                    }
                    
                    @Test
                    public void testSomething() {
                    }
                }
                """, false, null);

        context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
        context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_BEFORE);
        context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

        context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
                """
                package test;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                
                public class MyTest {
                    @BeforeEach
                    public void setUp() {
                        // Setup code
                    }
                    
                    @Test
                    public void testSomething() {
                    }
                }
                """
        }, null);
    }
    
    /**
     * Integration test: Verifies that @RewriteRule transforms @After correctly.
     */
    @Test
    void testRewriteRule_actualTransformation_After() throws CoreException {
        IPackageFragmentRoot fRoot = context.createClasspathForJUnit(JUnitCore.JUNIT4_CONTAINER_PATH);
        IPackageFragment pack = fRoot.createPackageFragment("test", true, null);
        ICompilationUnit cu = pack.createCompilationUnit("MyTest.java",
                """
                package test;
                import org.junit.After;
                import org.junit.Test;
                
                public class MyTest {
                    @After
                    public void tearDown() {
                        // Cleanup code
                    }
                    
                    @Test
                    public void testSomething() {
                    }
                }
                """, false, null);

        context.enable(MYCleanUpConstants.JUNIT_CLEANUP);
        context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_AFTER);
        context.enable(MYCleanUpConstants.JUNIT_CLEANUP_4_TEST);

        context.assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] {
                """
                package test;
                import org.junit.jupiter.api.AfterEach;
                import org.junit.jupiter.api.Test;
                
                public class MyTest {
                    @AfterEach
                    public void tearDown() {
                        // Cleanup code
                    }
                    
                    @Test
                    public void testSomething() {
                    }
                }
                """
        }, null);
    }
}
