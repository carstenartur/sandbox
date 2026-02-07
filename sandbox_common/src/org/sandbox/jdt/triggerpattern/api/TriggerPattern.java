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
 * Marks a method as a trigger pattern hint.
 * 
 * <p>The annotated method must be {@code public static} and have one of these signatures:</p>
 * <ul>
 *   <li>{@code public static List<IJavaCompletionProposal> methodName(HintContext ctx)}</li>
 *   <li>{@code public static Optional<IJavaCompletionProposal> methodName(HintContext ctx)}</li>
 *   <li>{@code public static IJavaCompletionProposal methodName(HintContext ctx)}</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code @TriggerPattern(value = "$x + 1", kind = PatternKind.EXPRESSION)}
 * {@code @Hint(displayName = "Replace with increment", description = "Suggests replacing addition with ++x")}
 * public static IJavaCompletionProposal simplifyIncrement(HintContext ctx) {
 *     // Implementation
 * }
 * </pre>
 * 
 * @since 1.2.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TriggerPattern {
	
	/**
	 * The pattern string with placeholders (e.g., {@code "$x + 1"}).
	 * 
	 * @return the pattern string
	 */
	String value();
	
	/**
	 * The kind of pattern (EXPRESSION or STATEMENT).
	 * 
	 * @return the pattern kind
	 */
	PatternKind kind() default PatternKind.EXPRESSION;
	
	/**
	 * Optional unique identifier for the pattern.
	 * 
	 * @return the pattern ID or empty string
	 */
	String id() default "";
	
	/**
	 * Type constraints for placeholder variables.
	 * 
	 * <p>Specifies that certain placeholders must match nodes of specific Java types.</p>
	 * 
	 * <p><b>Note:</b> Type constraint checking requires binding resolution,
	 * which is currently disabled in TriggerPatternEngine. The infrastructure
	 * is in place for future enhancement when bindings are available.</p>
	 * 
	 * @return array of type constraints
	 */
	ConstraintVariableType[] constraints() default {};
}
