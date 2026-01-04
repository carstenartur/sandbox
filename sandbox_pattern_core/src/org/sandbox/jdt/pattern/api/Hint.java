/*******************************************************************************
 * Copyright (c) 2026 Sandbox contributors.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Sandbox contributors - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.pattern.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides metadata about a hint.
 * <p>
 * This annotation is optional and provides additional information
 * about a hint method for display purposes.
 * </p>
 * 
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Hint {
	/**
	 * The human-readable name of this hint.
	 * 
	 * @return the hint name
	 */
	String name() default "";

	/**
	 * A description of what this hint does.
	 * 
	 * @return the hint description
	 */
	String description() default "";

	/**
	 * Whether this hint is enabled by default.
	 * 
	 * @return true if enabled by default
	 */
	boolean enabledByDefault() default true;

	/**
	 * The severity level of this hint.
	 * 
	 * @return the severity (INFO, WARNING, ERROR)
	 */
	String severity() default "INFO";
}
