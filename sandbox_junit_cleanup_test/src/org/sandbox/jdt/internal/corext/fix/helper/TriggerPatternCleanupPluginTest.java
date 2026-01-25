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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Tests for TriggerPatternCleanupPlugin and @CleanupPattern annotation.
 */
class TriggerPatternCleanupPluginTest {
    
    @Nested
    @DisplayName("BeforeJUnitPluginV2 Tests")
    class BeforeJUnitPluginV2Tests {
        
        @Test
        void testCleanupPatternAnnotation() {
            BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
            CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
            
            assertNotNull(annotation, "@CleanupPattern annotation should be present");
            assertEquals("@Before", annotation.value());
            assertEquals(PatternKind.ANNOTATION, annotation.kind());
            assertEquals("org.junit.Before", annotation.qualifiedType());
            assertEquals("cleanup.junit.before", annotation.cleanupId());
        }
        
        @Test
        void testGetPattern() {
            BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
            Pattern pattern = plugin.getPattern();
            
            assertNotNull(pattern);
            assertEquals("@Before", pattern.getValue());
            assertEquals(PatternKind.ANNOTATION, pattern.getKind());
            assertEquals("org.junit.Before", pattern.getQualifiedType());
        }
        
        @Test
        void testGetPreview() {
            BeforeJUnitPluginV2 plugin = new BeforeJUnitPluginV2();
            
            assertTrue(plugin.getPreview(false).contains("@Before\n"));
            assertTrue(plugin.getPreview(true).contains("@BeforeEach"));
        }
    }
    
    @Nested
    @DisplayName("AfterJUnitPluginV2 Tests")
    class AfterJUnitPluginV2Tests {
        
        @Test
        void testCleanupPatternAnnotation() {
            AfterJUnitPluginV2 plugin = new AfterJUnitPluginV2();
            CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
            
            assertNotNull(annotation);
            assertEquals("@After", annotation.value());
            assertEquals(PatternKind.ANNOTATION, annotation.kind());
            assertEquals("org.junit.After", annotation.qualifiedType());
            assertEquals("cleanup.junit.after", annotation.cleanupId());
        }
        
        @Test
        void testGetPreview() {
            AfterJUnitPluginV2 plugin = new AfterJUnitPluginV2();
            
            assertTrue(plugin.getPreview(false).contains("@After\n"));
            assertTrue(plugin.getPreview(true).contains("@AfterEach"));
        }
    }
    
    @Nested
    @DisplayName("BeforeClassJUnitPluginV2 Tests")
    class BeforeClassJUnitPluginV2Tests {
        
        @Test
        void testCleanupPatternAnnotation() {
            BeforeClassJUnitPluginV2 plugin = new BeforeClassJUnitPluginV2();
            CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
            
            assertNotNull(annotation);
            assertEquals("@BeforeClass", annotation.value());
            assertEquals(PatternKind.ANNOTATION, annotation.kind());
            assertEquals("org.junit.BeforeClass", annotation.qualifiedType());
            assertEquals("cleanup.junit.beforeclass", annotation.cleanupId());
        }
        
        @Test
        void testGetPreview() {
            BeforeClassJUnitPluginV2 plugin = new BeforeClassJUnitPluginV2();
            
            assertTrue(plugin.getPreview(false).contains("@BeforeClass"));
            assertTrue(plugin.getPreview(true).contains("@BeforeAll"));
        }
    }
    
    @Nested
    @DisplayName("AfterClassJUnitPluginV2 Tests")
    class AfterClassJUnitPluginV2Tests {
        
        @Test
        void testCleanupPatternAnnotation() {
            AfterClassJUnitPluginV2 plugin = new AfterClassJUnitPluginV2();
            CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
            
            assertNotNull(annotation);
            assertEquals("@AfterClass", annotation.value());
            assertEquals(PatternKind.ANNOTATION, annotation.kind());
            assertEquals("org.junit.AfterClass", annotation.qualifiedType());
            assertEquals("cleanup.junit.afterclass", annotation.cleanupId());
        }
        
        @Test
        void testGetPreview() {
            AfterClassJUnitPluginV2 plugin = new AfterClassJUnitPluginV2();
            
            assertTrue(plugin.getPreview(false).contains("@AfterClass"));
            assertTrue(plugin.getPreview(true).contains("@AfterAll"));
        }
    }
    
    @Nested
    @DisplayName("TestJUnitPluginV2 Tests")
    class TestJUnitPluginV2Tests {
        
        @Test
        void testCleanupPatternAnnotation() {
            TestJUnitPluginV2 plugin = new TestJUnitPluginV2();
            CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
            
            assertNotNull(annotation);
            assertEquals("@Test", annotation.value());
            assertEquals(PatternKind.ANNOTATION, annotation.kind());
            assertEquals("org.junit.Test", annotation.qualifiedType());
            assertEquals("cleanup.junit.test", annotation.cleanupId());
        }
        
        @Test
        void testGetPreview() {
            TestJUnitPluginV2 plugin = new TestJUnitPluginV2();
            
            String beforePreview = plugin.getPreview(false);
            String afterPreview = plugin.getPreview(true);
            
            assertTrue(beforePreview.contains("@Test"));
            assertTrue(afterPreview.contains("@Test"));
            // Both contain @Test but the import changes (not visible in preview)
        }
    }
    
    @Nested
    @DisplayName("IgnoreJUnitPluginV2 Tests")
    class IgnoreJUnitPluginV2Tests {
        
        @Test
        void testCleanupPatternAnnotation() {
            IgnoreJUnitPluginV2 plugin = new IgnoreJUnitPluginV2();
            CleanupPattern annotation = plugin.getClass().getAnnotation(CleanupPattern.class);
            
            assertNotNull(annotation);
            assertEquals("@Ignore", annotation.value());
            assertEquals(PatternKind.ANNOTATION, annotation.kind());
            assertEquals("org.junit.Ignore", annotation.qualifiedType());
            assertEquals("cleanup.junit.ignore", annotation.cleanupId());
        }
        
        @Test
        void testGetPreview() {
            IgnoreJUnitPluginV2 plugin = new IgnoreJUnitPluginV2();
            
            assertTrue(plugin.getPreview(false).contains("@Ignore"));
            assertTrue(plugin.getPreview(true).contains("@Disabled"));
        }
    }
}
