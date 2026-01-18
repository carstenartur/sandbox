/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

/**
 * Context object holding dependencies needed by {@link StatementHandlerType} handlers.
 * 
 * <p>This context is separate from {@link StatementParsingContext} because it contains
 * dependencies that are shared across all statements being parsed, while
 * {@code StatementParsingContext} contains statement-specific information.</p>
 * 
 * <p><b>Contained Dependencies:</b></p>
 * <ul>
 * <li>{@link LoopBodyParser} - For recursive parsing of nested statements</li>
 * <li>{@link SideEffectChecker} - For checking if statements have safe side effects</li>
 * </ul>
 * 
 * @see StatementHandlerType
 * @see StatementParsingContext
 */
public final class StatementHandlerContext {

	private final LoopBodyParser parser;
	private final SideEffectChecker sideEffectChecker;

	/**
	 * Creates a new StatementHandlerContext.
	 * 
	 * @param parser           the loop body parser for recursive parsing
	 * @param sideEffectChecker the side effect checker
	 */
	public StatementHandlerContext(LoopBodyParser parser, SideEffectChecker sideEffectChecker) {
		this.parser = parser;
		this.sideEffectChecker = sideEffectChecker;
	}

	/**
	 * Returns the loop body parser for recursive parsing of nested statements.
	 * 
	 * @return the parser
	 */
	public LoopBodyParser getParser() {
		return parser;
	}

	/**
	 * Returns the side effect checker.
	 * 
	 * @return the side effect checker
	 */
	public SideEffectChecker getSideEffectChecker() {
		return sideEffectChecker;
	}
}
