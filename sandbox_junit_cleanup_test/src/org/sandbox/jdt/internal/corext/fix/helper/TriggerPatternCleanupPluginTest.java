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
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Tests for TriggerPatternCleanupPlugin and @CleanupPattern annotation.
 */
class TriggerPatternCleanupPluginTest {
    
    @Test
    void testCleanupPatternAnnotation_isPresent() {
        BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation, "@CleanupPattern annotation should be present");
        assertEquals("@Before", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.Before", annotation.qualifiedType());
        assertEquals("cleanup.junit.before", annotation.cleanupId());
    }
    
    @Test
    void testGetPattern_returnsCorrectPattern() {
        BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
        
        Pattern pattern = plugin.getPattern();
        
        assertNotNull(pattern);
        assertEquals("@Before", pattern.getValue());
        assertEquals(PatternKind.ANNOTATION, pattern.getKind());
        assertEquals("org.junit.Before", pattern.getQualifiedType());
    }
    
    @Test
    void testGetCleanupId_returnsIdFromAnnotation() {
        BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
        
        String cleanupId = plugin.getCleanupId();
        
        assertEquals("cleanup.junit.before", cleanupId);
    }
    
    @Test
    void testGetDescription_returnsDescriptionFromAnnotation() {
        BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
        
        String description = plugin.getDescription();
        
        assertEquals("Migrate @Before to @BeforeEach", description);
    }
    
    @Test
    void testGetPreview_beforeRefactoring() {
        BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
        
        String preview = plugin.getPreview(false);
        
        assertTrue(preview.contains("@Before"));
        assertFalse(preview.contains("@BeforeEach"));
    }
    
    @Test
    void testGetPreview_afterRefactoring() {
        BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
        
        String preview = plugin.getPreview(true);
        
        assertTrue(preview.contains("@BeforeEach"));
        // Verify it contains "@BeforeEach" but not the old "@Before" annotation on its own line
        assertTrue(preview.contains("@BeforeEach\n"));
        assertFalse(preview.contains("@Before\n"));
    }
    
    @Test
    void testAfterJUnitPluginV2_annotation() {
        AfterJUnitPluginV2 plugin = new AfterJUnitPluginV2();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation);
        assertEquals("@After", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.After", annotation.qualifiedType());
    }
    
    @Test
    void testBeforeClassJUnitPluginV2_annotation() {
        BeforeClassJUnitPluginV2 plugin = new BeforeClassJUnitPluginV2();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation);
        assertEquals("@BeforeClass", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.BeforeClass", annotation.qualifiedType());
    }
    
    @Test
    void testAfterClassJUnitPluginV2_annotation() {
        AfterClassJUnitPluginV2 plugin = new AfterClassJUnitPluginV2();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation);
        assertEquals("@AfterClass", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.AfterClass", annotation.qualifiedType());
    }
    
    @Test
    void testTestJUnitPluginV2_annotation() {
        TestJUnitPluginV2 plugin = new TestJUnitPluginV2();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation);
        assertEquals("@Test", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.Test", annotation.qualifiedType());
    }
    
    @Test
    void testIgnoreJUnitPluginV2_annotation() {
        IgnoreJUnitPluginV2 plugin = new IgnoreJUnitPluginV2();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation);
        assertEquals("@Ignore", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.Ignore", annotation.qualifiedType());
    }
    
    @Test
    void testIgnoreJUnitPluginV2_preview_withReason() {
        IgnoreJUnitPluginV2 plugin = new IgnoreJUnitPluginV2();
        
        String previewBefore = plugin.getPreview(false);
        String previewAfter = plugin.getPreview(true);
        
        // Both previews should show the reason being preserved
        assertTrue(previewBefore.contains("@Ignore(\"not implemented\")"));
        assertTrue(previewAfter.contains("@Disabled(\"not implemented\")"));
    }
}
