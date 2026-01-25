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

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.RewriteRule;

/**
 * Tests for @RewriteRule annotation functionality.
 */
class RewriteRuleTest {
    
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
}
