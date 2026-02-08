/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
 * Annotation to mark methods as pattern handlers in a reflective pattern cleanup plugin.
 * 
 * <p>Methods annotated with {@code @PatternHandler} will be automatically invoked
 * by {@link org.sandbox.jdt.triggerpattern.cleanup.ReflectivePatternCleanupPlugin}
 * when a matching pattern is found during code analysis.</p>
 * 
 * <p><b>Method Signature Requirements:</b></p>
 * <ul>
 *   <li>Method must accept a {@link PatternContext} parameter</li>
 *   <li>Method must return {@code void} or {@code boolean}</li>
 *   <li>If returning {@code boolean}, {@code true} stops further processing</li>
 * </ul>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>
 * public class MyCleanupPlugin extends ReflectivePatternCleanupPlugin {
 *     {@literal @}PatternHandler(pattern = "if ($condition) { $body } else { $elseBody }")
 *     public void handleIfElse(PatternContext context) {
 *         // Transformation logic here
 *     }
 * }
 * </pre>
 * 
 * @since 1.3.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PatternHandler {
    
    /**
     * The pattern string to match in the source code.
     * 
     * <p>Pattern syntax follows TriggerPattern conventions:
     * <ul>
     *   <li>{@code $variable} - single node placeholder</li>
     *   <li>{@code $variable$} - multi-node placeholder</li>
     *   <li>Supports annotations, method calls, expressions, statements</li>
     * </ul>
     * 
     * @return the pattern to match
     */
    String pattern();
    
    /**
     * The kind of pattern being matched.
     * 
     * <p>Defaults to {@link PatternKind#EXPRESSION} for general code patterns.</p>
     * 
     * @return the pattern kind
     */
    PatternKind kind() default PatternKind.EXPRESSION;
    
    /**
     * Optional fully qualified type name for type-specific matching.
     * 
     * <p>When specified, the pattern will only match nodes of the given type.
     * For example, {@code "org.junit.Before"} for annotation matching.</p>
     * 
     * @return the qualified type, or empty string for no type constraint
     */
    String qualifiedType() default "";
    
    /**
     * Optional priority for ordering multiple pattern handlers.
     * 
     * <p>Handlers are invoked in ascending priority order. Handlers with
     * the same priority are invoked in declaration order.</p>
     * 
     * @return the handler priority (default: 0)
     */
    int priority() default 0;
}
