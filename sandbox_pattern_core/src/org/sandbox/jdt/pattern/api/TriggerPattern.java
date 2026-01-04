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
 * Marks a public static method as a hint provider with an associated pattern.
 * <p>
 * The annotated method will be called when the pattern matches code.
 * The method signature should be one of:
 * <ul>
 * <li>{@code public static List<IJavaCompletionProposal> methodName(HintContext ctx)}</li>
 * <li>{@code public static Optional<IJavaCompletionProposal> methodName(HintContext ctx)}</li>
 * <li>{@code public static IJavaCompletionProposal methodName(HintContext ctx)}</li>
 * </ul>
 * </p>
 * <p>
 * Example:
 * <pre>
 * &#64;TriggerPattern(value = "$x.size() == 0", kind = PatternKind.EXPRESSION)
 * &#64;Hint(name = "Use isEmpty()", description = "Replace size() == 0 with isEmpty()")
 * public static IJavaCompletionProposal useIsEmpty(HintContext ctx) {
 *     // Generate proposal to replace with isEmpty()
 * }
 * </pre>
 * </p>
 * 
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TriggerPattern {
	/**
	 * The pattern string to match, using $ prefixes for placeholders.
	 * 
	 * @return the pattern string
	 */
	String value();

	/**
	 * The kind of pattern to match.
	 * 
	 * @return the pattern kind
	 */
	PatternKind kind() default PatternKind.EXPRESSION;
}
