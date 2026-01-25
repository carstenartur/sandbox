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
package org.sandbox.jdt.triggerpattern.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a cleanup plugin class with its pattern definition.
 * Enables declarative pattern-based code matching for Eclipse cleanup plugins.
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@literal @}CleanupPattern(
 *     value = "@Before",
 *     kind = PatternKind.ANNOTATION,
 *     qualifiedType = "org.junit.Before",
 *     cleanupId = "cleanup.junit.before",
 *     description = "Migrate @Before to @BeforeEach"
 * )
 * public class BeforeJUnitPlugin extends TriggerPatternCleanupPlugin { ... }
 * </pre>
 * 
 * @since 1.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CleanupPattern {
    
    /**
     * The pattern string with placeholders.
     * Examples: "@Before", "Assert.assertEquals($a, $b)", "@Rule public $type $name"
     */
    String value();
    
    /**
     * The kind of pattern to match.
     */
    PatternKind kind();
    
    /**
     * Optional fully qualified type name for type-binding validation.
     * Example: "org.junit.Before", "org.junit.Assert"
     */
    String qualifiedType() default "";
    
    /**
     * Identifier used in plugin.xml for cleanup configuration.
     * Should match the cleanup option ID in the preferences.
     */
    String cleanupId() default "";
    
    /**
     * Human-readable description for the cleanup dialog.
     */
    String description() default "";
    
    /**
     * Optional display name for UI.
     */
    String displayName() default "";
}
