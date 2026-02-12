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
 * Specifies constraints on the body of a matched method declaration.
 * 
 * <p>This annotation is used with {@link TriggerPattern} annotations that have
 * {@code kind = PatternKind.METHOD_DECLARATION} to specify what should or should
 * not be present in the method body.</p>
 * 
 * <p>Example usage for detecting missing super calls:</p>
 * <pre>
 * {@code @TriggerPattern(
 *     value = "void dispose()",
 *     kind = PatternKind.METHOD_DECLARATION,
 *     overrides = "org.eclipse.swt.widgets.Widget"
 * )}
 * {@code @BodyConstraint(mustContain = "super.dispose()", negate = true)}
 * {@code @Hint(displayName = "Missing super.dispose() call")}
 * public static IJavaCompletionProposal checkMissingDispose(HintContext ctx) {
 *     // Implementation
 * }
 * </pre>
 * 
 * @since 1.2.6
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BodyConstraint {
	
	/**
	 * Pattern that must (or must not, if negate=true) be present in the method body.
	 * 
	 * <p>This can be a simple pattern like {@code "super.dispose()"} or a more
	 * complex pattern with placeholders like {@code "super.$methodName($args$)"}.</p>
	 * 
	 * @return the pattern that should be checked in the method body
	 */
	String mustContain();
	
	/**
	 * The kind of pattern specified in {@link #mustContain()}.
	 * 
	 * <p>Defaults to STATEMENT, which is appropriate for most method body checks.</p>
	 * 
	 * @return the pattern kind
	 */
	PatternKind kind() default PatternKind.STATEMENT;
	
	/**
	 * Whether to negate the constraint.
	 * 
	 * <p>When {@code true}, the constraint succeeds if the pattern is NOT found
	 * in the method body. This is useful for detecting missing calls.</p>
	 * 
	 * <p>When {@code false} (default), the constraint succeeds if the pattern IS found.</p>
	 * 
	 * @return true to negate the constraint, false otherwise
	 */
	boolean negate() default false;
}
