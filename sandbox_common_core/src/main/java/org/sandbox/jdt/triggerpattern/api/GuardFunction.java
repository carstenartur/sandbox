/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

/**
 * Functional interface for guard functions that constrain pattern matches.
 * 
 * <p>A guard function evaluates a condition against a {@link GuardContext} and
 * optional arguments. Guard functions are registered in the
 * {@link org.sandbox.jdt.triggerpattern.internal.GuardRegistry} and referenced
 * by name from guard expressions.</p>
 * 
 * <p>Example usage:</p>
 * <pre>
 * GuardFunction isStatic = (ctx, args) -&gt; {
 *     ASTNode node = ctx.getBinding(args[0].toString());
 *     if (node instanceof MethodDeclaration md) {
 *         return Modifier.isStatic(md.getModifiers());
 *     }
 *     return false;
 * };
 * </pre>
 * 
 * @since 1.3.2
 */
@FunctionalInterface
public interface GuardFunction {
	
	/**
	 * Evaluates the guard condition.
	 * 
	 * @param ctx the guard context providing access to match bindings and compilation unit
	 * @param args optional arguments passed from the guard expression
	 * @return {@code true} if the guard condition is satisfied
	 */
	boolean evaluate(GuardContext ctx, Object... args);
}
