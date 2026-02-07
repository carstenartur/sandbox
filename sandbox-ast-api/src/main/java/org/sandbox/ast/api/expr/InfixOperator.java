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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.ast.api.expr;

/**
 * Enum representing infix operators in Java expressions.
 * Provides type-safe representation of binary operators.
 */
public enum InfixOperator {
	/** Multiplication operator (*) */
	TIMES("*", true, false, false),
	
	/** Division operator (/) */
	DIVIDE("/", true, false, false),
	
	/** Remainder operator (%) */
	REMAINDER("%", true, false, false),
	
	/** Addition operator (+) */
	PLUS("+", true, false, false),
	
	/** Subtraction operator (-) */
	MINUS("-", true, false, false),
	
	/** Left shift operator (&lt;&lt;) */
	LEFT_SHIFT("<<", true, false, false),
	
	/** Signed right shift operator (&gt;&gt;) */
	RIGHT_SHIFT_SIGNED(">>", true, false, false),
	
	/** Unsigned right shift operator (&gt;&gt;&gt;) */
	RIGHT_SHIFT_UNSIGNED(">>>", true, false, false),
	
	/** Less than operator (&lt;) */
	LESS("<", false, true, false),
	
	/** Greater than operator (&gt;) */
	GREATER(">", false, true, false),
	
	/** Less than or equal operator (&lt;=) */
	LESS_EQUALS("<=", false, true, false),
	
	/** Greater than or equal operator (&gt;=) */
	GREATER_EQUALS(">=", false, true, false),
	
	/** Equality operator (==) */
	EQUALS("==", false, true, false),
	
	/** Inequality operator (!=) */
	NOT_EQUALS("!=", false, true, false),
	
	/** Bitwise XOR operator (^) */
	XOR("^", true, false, false),
	
	/** Bitwise OR operator (|) */
	OR("|", true, false, false),
	
	/** Bitwise AND operator (&amp;) */
	AND("&", true, false, false),
	
	/** Conditional AND operator (&amp;&amp;) */
	CONDITIONAL_AND("&&", false, false, true),
	
	/** Conditional OR operator (||) */
	CONDITIONAL_OR("||", false, false, true);
	
	private final String symbol;
	private final boolean isArithmetic;
	private final boolean isComparison;
	private final boolean isLogical;
	
	/**
	 * Creates an infix operator.
	 * 
	 * @param symbol the operator symbol
	 * @param isArithmetic true if arithmetic operator
	 * @param isComparison true if comparison operator
	 * @param isLogical true if logical operator
	 */
	InfixOperator(String symbol, boolean isArithmetic, boolean isComparison, boolean isLogical) {
		this.symbol = symbol;
		this.isArithmetic = isArithmetic;
		this.isComparison = isComparison;
		this.isLogical = isLogical;
	}
	
	/**
	 * Gets the operator symbol.
	 * 
	 * @return the symbol
	 */
	public String symbol() {
		return symbol;
	}
	
	/**
	 * Checks if this is an arithmetic operator.
	 * 
	 * @return true if arithmetic
	 */
	public boolean isArithmetic() {
		return isArithmetic;
	}
	
	/**
	 * Checks if this is a comparison operator.
	 * 
	 * @return true if comparison
	 */
	public boolean isComparison() {
		return isComparison;
	}
	
	/**
	 * Checks if this is a logical operator.
	 * 
	 * @return true if logical
	 */
	public boolean isLogical() {
		return isLogical;
	}
	
	/**
	 * Gets an operator by its symbol.
	 * 
	 * @param symbol the operator symbol
	 * @return the operator, or null if not found
	 */
	public static InfixOperator fromSymbol(String symbol) {
		for (InfixOperator op : values()) {
			if (op.symbol.equals(symbol)) {
				return op;
			}
		}
		return null;
	}
}
