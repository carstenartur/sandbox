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
 * Declarative annotation for defining rewrite rules in TriggerPattern-based cleanup plugins.
 * 
 * <p>Eliminates the need for manual {@code process2Rewrite()} implementations by specifying
 * the transformation declaratively. Works in conjunction with {@link CleanupPattern}.</p>
 * 
 * <p><b>Example 1: Simple marker annotation replacement</b></p>
 * <pre>
 * {@literal @}CleanupPattern(value = "@Before", kind = PatternKind.ANNOTATION, qualifiedType = "org.junit.Before")
 * {@literal @}RewriteRule(
 *     replaceWith = "@BeforeEach",
 *     removeImports = {"org.junit.Before"},
 *     addImports = {"org.junit.jupiter.api.BeforeEach"}
 * )
 * public class BeforeJUnitPluginV2 extends TriggerPatternCleanupPlugin { }
 * </pre>
 * 
 * <p><b>Example 2: Annotation with preserved value</b></p>
 * <pre>
 * {@literal @}CleanupPattern(value = "@Ignore($value)", kind = PatternKind.ANNOTATION, qualifiedType = "org.junit.Ignore")
 * {@literal @}RewriteRule(
 *     replaceWith = "@Disabled($value)",
 *     removeImports = {"org.junit.Ignore"},
 *     addImports = {"org.junit.jupiter.api.Disabled"}
 * )
 * public class IgnoreJUnitPluginV2 extends TriggerPatternCleanupPlugin { }
 * </pre>
 * 
 * @since 1.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RewriteRule {
    /**
     * The replacement pattern. Can include placeholders from the source pattern.
     * 
     * <p><b>Supported patterns:</b></p>
     * <ul>
     *   <li>Simple marker annotation: {@code "@BeforeEach"}</li>
     *   <li>Single-value annotation: {@code "@Disabled($value)"}</li>
     *   <li>Multi-placeholder annotation: {@code "@Test($args$)"}</li>
     *   <li>Method call with qualified name: {@code "Assertions.assertEquals($args$)"}</li>
     * </ul>
     * 
     * <p><b>Placeholder syntax:</b></p>
     * <ul>
     *   <li>{@code $x} - Matches exactly one AST node</li>
     *   <li>{@code $x$} - Matches zero or more AST nodes (multi-placeholder)</li>
     *   <li>{@code $x:TypeName} - Matches one node of specified type (type constraint)</li>
     * </ul>
     * 
     * <p><b>Current limitations:</b></p>
     * <ul>
     *   <li>Only simple (unqualified) annotation names are supported, not fully qualified names like 
     *       {@code "@org.junit.jupiter.api.BeforeEach"}</li>
     *   <li>Named parameters (NormalAnnotation) are not supported. Annotations like 
     *       {@code @Ignore(value="reason")} require custom {@code process2Rewrite()} implementation.</li>
     * </ul>
     * 
     * @return the replacement pattern with optional placeholders
     */
    String replaceWith();
    
    /**
     * Imports to remove after transformation.
     * 
     * @return array of fully qualified import names to remove
     */
    String[] removeImports() default {};
    
    /**
     * Imports to add after transformation.
     * 
     * @return array of fully qualified import names to add
     */
    String[] addImports() default {};
    
    /**
     * Static imports to remove after transformation.
     * 
     * @return array of fully qualified static import names to remove
     */
    String[] removeStaticImports() default {};
    
    /**
     * Static imports to add after transformation.
     * 
     * @return array of fully qualified static import names to add
     */
    String[] addStaticImports() default {};
}
