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
	 * Severity level (e.g., "warning", "info", "error").
	 * 
	 * @return the severity
	 */
	String severity() default "info";
}
