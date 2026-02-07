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
 * Container annotation for multiple {@link TriggerPattern} annotations on a single method.
 * 
 * <p>This allows a hint method to be triggered by multiple different patterns.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code @TriggerPatterns({
 *     @TriggerPattern(value = "$x + 1", kind = PatternKind.EXPRESSION),
 *     @TriggerPattern(value = "$x + 1L", kind = PatternKind.EXPRESSION)
 * })}
 * {@code @Hint(displayName = "Replace with increment")}
 * public static IJavaCompletionProposal simplifyIncrement(HintContext ctx) {
 *     // Implementation
 * }
 * </pre>
 * 
 * @since 1.2.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TriggerPatterns {
	
	/**
	 * The array of trigger patterns.
	 * 
	 * @return the trigger patterns
	 */
	TriggerPattern[] value();
}
