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
        BeforeJUnitPlugin plugin = new BeforeJUnitPlugin();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation, "@CleanupPattern annotation should be present");
        assertEquals("@Before", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.Before", annotation.qualifiedType());
        assertEquals("cleanup.junit.before", annotation.cleanupId());
    }
    
    @Test
    void testGetPattern_returnsCorrectPattern() {
        BeforeJUnitPlugin plugin = new BeforeJUnitPlugin();
        
        Pattern pattern = plugin.getPattern();
        
        assertNotNull(pattern);
        assertEquals("@Before", pattern.getValue());
        assertEquals(PatternKind.ANNOTATION, pattern.getKind());
        assertEquals("org.junit.Before", pattern.getQualifiedType());
    }
    
    @Test
    void testGetCleanupId_returnsIdFromAnnotation() {
        BeforeJUnitPlugin plugin = new BeforeJUnitPlugin();
        
        String cleanupId = plugin.getCleanupId();
        
        assertEquals("cleanup.junit.before", cleanupId);
    }
    
    @Test
    void testGetDescription_returnsDescriptionFromAnnotation() {
        BeforeJUnitPlugin plugin = new BeforeJUnitPlugin();
        
        String description = plugin.getDescription();
        
        assertEquals("Migrate @Before to @BeforeEach", description);
    }
    
    @Test
    void testGetPreview_beforeRefactoring() {
        BeforeJUnitPlugin plugin = new BeforeJUnitPlugin();
        
        String preview = plugin.getPreview(false);
        
        assertTrue(preview.contains("@Before"));
        assertFalse(preview.contains("@BeforeEach"));
    }
    
    @Test
    void testGetPreview_afterRefactoring() {
        BeforeJUnitPlugin plugin = new BeforeJUnitPlugin();
        
        String preview = plugin.getPreview(true);
        
        assertTrue(preview.contains("@BeforeEach"));
        // Verify it contains "@BeforeEach" but not the old "@Before" annotation on its own line
        assertTrue(preview.contains("@BeforeEach\n"));
        assertFalse(preview.contains("@Before\n"));
    }
    
    @Test
    void testAfterJUnitPlugin_annotation() {
        AfterJUnitPlugin plugin = new AfterJUnitPlugin();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation);
        assertEquals("@After", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.After", annotation.qualifiedType());
    }
    
    @Test
    void testBeforeClassJUnitPlugin_annotation() {
        BeforeClassJUnitPlugin plugin = new BeforeClassJUnitPlugin();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation);
        assertEquals("@BeforeClass", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.BeforeClass", annotation.qualifiedType());
    }
    
    @Test
    void testAfterClassJUnitPlugin_annotation() {
        AfterClassJUnitPlugin plugin = new AfterClassJUnitPlugin();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation);
        assertEquals("@AfterClass", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.AfterClass", annotation.qualifiedType());
    }
    
    @Test
    void testTestJUnitPlugin_annotation() {
        TestJUnitPlugin plugin = new TestJUnitPlugin();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation);
        assertEquals("@Test", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.Test", annotation.qualifiedType());
    }
    
    @Test
    void testIgnoreJUnitPlugin_annotation() {
        IgnoreJUnitPlugin plugin = new IgnoreJUnitPlugin();
        
        CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
        
        assertNotNull(annotation);
        assertEquals("@Ignore", annotation.value());
        assertEquals(PatternKind.ANNOTATION, annotation.kind());
        assertEquals("org.junit.Ignore", annotation.qualifiedType());
    }
    
    @Test
    void testIgnoreJUnitPlugin_preview_withReason() {
        IgnoreJUnitPlugin plugin = new IgnoreJUnitPlugin();
        
        String previewBefore = plugin.getPreview(false);
        String previewAfter = plugin.getPreview(true);
        
        // Both previews should show the reason being preserved
        assertTrue(previewBefore.contains("@Ignore(\"not implemented\")"));
        assertTrue(previewAfter.contains("@Disabled(\"not implemented\")"));
    }
}
