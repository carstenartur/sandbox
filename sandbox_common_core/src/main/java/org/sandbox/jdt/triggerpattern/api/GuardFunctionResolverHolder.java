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

import java.util.function.Function;

/**
 * Holder for the guard function resolver, allowing it to be set at runtime.
 * 
 * <p>This is used by {@link GuardExpression.FunctionCall} to look up guard
 * functions by name. In an OSGi environment, the resolver is typically set
 * by the GuardRegistry during initialization.</p>
 * 
 * @since 1.3.2
 */
public final class GuardFunctionResolverHolder {
	
	static volatile Function<String, GuardFunction> resolver;
	
	private GuardFunctionResolverHolder() {
		// Utility class
	}
	
	/**
	 * Sets the guard function resolver.
	 * 
	 * @param resolverFn a function that maps guard function names to implementations
	 */
	public static void setResolver(Function<String, GuardFunction> resolverFn) {
		resolver = resolverFn;
	}
	
	/**
	 * Returns the current guard function resolver.
	 * 
	 * @return the resolver, or {@code null} if not set
	 */
	public static Function<String, GuardFunction> getResolver() {
		return resolver;
	}
}
