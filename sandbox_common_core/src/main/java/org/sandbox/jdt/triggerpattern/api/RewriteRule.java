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
 * <h2>Import Handling</h2>
 * <p>Imports are derived automatically from the rule configuration:</p>
 * <ul>
 *   <li><b>{@code targetQualifiedType}:</b> (Recommended) FQN of the replacement type.
 *       The import is added automatically. Example: {@code "org.junit.jupiter.api.BeforeEach"}</li>
 *   <li><b>{@code removeImports}:</b> If empty, the old import is removed safely via
 *       {@code ImportRemover} — only when no other references to the type remain in the
 *       compilation unit. The {@code qualifiedType} from the associated {@link CleanupPattern}
 *       annotation is used as the candidate.</li>
 *   <li><b>{@code addImports}:</b> Legacy attribute — prefer {@code targetQualifiedType} instead.
 *       If both are empty, imports are auto-detected from FQNs in {@code replaceWith}.</li>
 * </ul>
 * 
 * <p><b>Example 1: Using targetQualifiedType (recommended)</b></p>
 * <pre>
 * {@literal @}CleanupPattern(value = "@Before", kind = PatternKind.ANNOTATION, qualifiedType = "org.junit.Before")
 * {@literal @}RewriteRule(replaceWith = "@BeforeEach", targetQualifiedType = "org.junit.jupiter.api.BeforeEach")
 * // removeImport: safely derived from qualifiedType via ImportRemover
 * // addImport: derived from targetQualifiedType
 * </pre>
 * 
 * <p><b>Example 2: Legacy explicit imports</b></p>
 * <pre>
 * {@literal @}CleanupPattern(value = "@Before", kind = PatternKind.ANNOTATION, qualifiedType = "org.junit.Before")
 * {@literal @}RewriteRule(
 *     replaceWith = "@BeforeEach",
 *     addImports = {"org.junit.jupiter.api.BeforeEach"}
 * )
 * public class BeforeJUnitPluginV2 extends TriggerPatternCleanupPlugin { }
 * </pre>
 * 
 * <p><b>Example 3: Annotation with preserved value</b></p>
 * <pre>
 * {@literal @}CleanupPattern(value = "@Ignore($value)", kind = PatternKind.ANNOTATION, qualifiedType = "org.junit.Ignore")
 * {@literal @}RewriteRule(
 *     replaceWith = "@Disabled($value)",
 *     targetQualifiedType = "org.junit.jupiter.api.Disabled"
 * )
 * public class IgnoreJUnitPluginV2 extends TriggerPatternCleanupPlugin { }
 * </pre>
 * 
 * @since 1.2.5
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
     * </ul>
     * 
     * <p><b>Current limitations:</b></p>
     * <ul>
     *   <li>Only simple (unqualified) annotation names are supported, not fully qualified names like 
     *       {@code "@org.junit.jupiter.api.BeforeEach"}</li>
     * </ul>
     * 
     * <p><b>NormalAnnotation support:</b> Annotations with named parameters like
     * {@code @Ignore(value="reason")} are supported. The "value" member is automatically
     * extracted and used as the placeholder binding when no explicit binding is found.</p>
     * 
     * @return the replacement pattern with optional placeholders
     */
    String replaceWith();
    
    /**
     * Fully qualified type name of the replacement type.
     * 
     * <p>When specified, this FQN is used to automatically add the import for the replacement type.
     * This is the <b>recommended</b> way to specify the new import, as it is more explicit and
     * less error-prone than {@code addImports}.</p>
     * 
     * <p><b>Example:</b> {@code "org.junit.jupiter.api.BeforeEach"}</p>
     * 
     * <p>If both {@code targetQualifiedType} and {@code addImports} are specified,
     * {@code targetQualifiedType} takes precedence.</p>
     * 
     * @return the fully qualified type name of the replacement type, or empty string if not specified
     * @since 1.3.1
     */
    String targetQualifiedType() default ""; //$NON-NLS-1$
    
    /**
     * Imports to remove after transformation.
     * 
     * <p><b>Note:</b> When empty (the default), import removal is handled automatically
     * using {@code ImportRemover} — the import derived from {@code @CleanupPattern.qualifiedType}
     * is only removed if no other references to the type exist in the compilation unit.
     * This is safer than explicit removal.</p>
     * 
     * @return array of fully qualified import names to remove
     */
    String[] removeImports() default {};
    
    /**
     * Imports to add after transformation.
     * 
     * <p><b>Prefer {@code targetQualifiedType} instead.</b> This attribute is retained for
     * backward compatibility and for cases requiring multiple imports.</p>
     * 
     * <p>When empty and {@code targetQualifiedType} is also empty, imports are auto-detected
     * from FQNs in {@code replaceWith}.</p>
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
