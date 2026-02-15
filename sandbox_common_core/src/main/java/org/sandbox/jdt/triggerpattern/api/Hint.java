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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides metadata about a hint.
 * 
 * <p>This annotation is optional and provides additional information about a hint method
 * that is already marked with {@link TriggerPattern}.</p>
 * 
 * @since 1.2.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Hint {
	
	/**
	 * Display name for the hint (shown in UI).
	 * 
	 * @return the display name
	 */
	String displayName() default "";
	
	/**
	 * Description of what the hint does.
	 * 
	 * @return the description
	 */
	String description() default "";
	
	/**
	 * Whether this hint is enabled by default.
	 * 
	 * @return {@code true} if enabled by default
	 */
	boolean enabledByDefault() default true;
	
	/**
	 * Severity level.
	 * 
	 * @return the severity
	 */
	Severity severity() default Severity.INFO;
	
	/**
	 * Optional unique identifier for the hint.
	 * 
	 * @return the hint ID or empty string
	 */
	String id() default "";
	
	/**
	 * Category for grouping related hints.
	 * 
	 * @return the category or empty string
	 */
	String category() default "";
	
	/**
	 * SuppressWarnings keys that can be used to suppress this hint.
	 * 
	 * @return array of suppress warnings keys
	 */
	String[] suppressWarnings() default {};
	
	/**
	 * The kind of hint (inspection or action).
	 * 
	 * @return the hint kind
	 */
	HintKind hintKind() default HintKind.INSPECTION;
	
	/**
	 * Minimum Java source version required for this hint.
	 * 
	 * <p>Examples: "1.8", "11", "17"</p>
	 * 
	 * @return the minimum source version or empty string if no minimum
	 */
	String minSourceVersion() default "";
}
