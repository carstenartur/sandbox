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

import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Annotation for marking methods that handle pattern matches in a reflective cleanup plugin.
 * 
 * <p>Methods annotated with {@code @PatternHandler} are automatically discovered and invoked
 * when the specified pattern matches in the source code. The method signature must be:</p>
 * 
 * <pre>
 * {@code @PatternHandler("pattern_string")}
 * public void handlePattern(PatternContext context) {
 *     // transformation logic
 * }
 * </pre>
 * 
 * <p>This annotation enables a declarative style for defining cleanup transformations,
 * similar to NetBeans' {@code @TriggerPattern} approach.</p>
 * 
 * @since 1.3.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PatternHandler {
	/**
	 * The pattern string to match.
	 * 
	 * <p>Supports placeholder syntax like {@code $var}, {@code $expr}, etc.</p>
	 * 
	 * @return the pattern string
	 */
	String value();
	
	/**
	 * The kind of pattern to match.
	 * 
	 * @return the pattern kind (default: STATEMENT)
	 */
	PatternKind kind() default PatternKind.STATEMENT;
	
	/**
	 * Optional qualified type name for type-specific matching.
	 * 
	 * <p>Used for annotation patterns to ensure the annotation is of the expected type.</p>
	 * 
	 * @return the qualified type name, or empty string if not specified
	 */
	String qualifiedType() default "";
}
