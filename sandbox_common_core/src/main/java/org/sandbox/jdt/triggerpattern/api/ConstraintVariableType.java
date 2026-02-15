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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Constrains a placeholder variable to a specific type.
 * 
 * <p>Used within {@link TriggerPattern#constraints()} to specify that
 * a placeholder must match a node of a particular Java type.</p>
 * 
 * <p>Example:</p>
 * <pre>
 * {@code @TriggerPattern(
 *     value = "$x.toString()",
 *     constraints = @ConstraintVariableType(variable = "$x", type = "java.lang.String")
 * )}
 * </pre>
 * 
 * <p><b>Note:</b> Type constraint checking currently requires binding resolution,
 * which is disabled in the current TriggerPatternEngine implementation. The infrastructure
 * is in place for future enhancement when binding resolution is enabled.</p>
 * 
 * @since 1.2.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface ConstraintVariableType {
	
	/**
	 * The placeholder variable name (must start with $).
	 * 
	 * @return the variable name
	 */
	String variable();
	
	/**
	 * The fully qualified Java type name (e.g., "java.lang.String").
	 * 
	 * @return the type name
	 */
	String type();
}
