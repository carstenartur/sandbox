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
 * <p>Type constraint checking requires resolved bindings. Both the indexed and
 * single-pattern matcher reject candidates with missing necessary bindings or
 * placeholders. The compilation-unit API enables binding resolution; callers
 * supplying an AST must provide an AST with bindings for constrained patterns.</p>
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
