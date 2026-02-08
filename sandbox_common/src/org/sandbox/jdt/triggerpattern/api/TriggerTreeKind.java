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

import org.eclipse.jdt.core.dom.ASTNode;

/**
 * Triggers a hint based on AST node types rather than pattern matching.
 * 
 * <p>This is useful for hints that should fire on all occurrences of specific
 * AST node kinds without needing a pattern string.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * {@code @TriggerTreeKind(ASTNode.METHOD_DECLARATION)}
 * {@code @Hint(displayName = "Method analysis")}
 * public static IJavaCompletionProposal analyzeMethod(HintContext ctx) {
 *     // Implementation
 * }
 * </pre>
 * 
 * <p><b>Note:</b> Uses Eclipse JDT's {@link ASTNode} type constants
 * (e.g., {@code ASTNode.METHOD_DECLARATION = 31}, {@code ASTNode.TYPE_DECLARATION = 55})
 * rather than javac's Tree.Kind enum.</p>
 * 
 * @since 1.2.2
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TriggerTreeKind {
	
	/**
	 * The AST node type constants to trigger on.
	 * 
	 * <p>Use constants from {@link ASTNode}, such as:</p>
	 * <ul>
	 *   <li>{@code ASTNode.METHOD_DECLARATION} (31)</li>
	 *   <li>{@code ASTNode.TYPE_DECLARATION} (55)</li>
	 *   <li>{@code ASTNode.VARIABLE_DECLARATION_STATEMENT} (62)</li>
	 * </ul>
	 * 
	 * @return array of AST node type constants
	 */
	int[] value();
}
