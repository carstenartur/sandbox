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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InfixOperatorTest {
	
	@Test
	void testArithmeticOperators() {
		assertThat(InfixOperator.PLUS.isArithmetic()).isTrue();
		assertThat(InfixOperator.MINUS.isArithmetic()).isTrue();
		assertThat(InfixOperator.TIMES.isArithmetic()).isTrue();
		assertThat(InfixOperator.DIVIDE.isArithmetic()).isTrue();
		assertThat(InfixOperator.REMAINDER.isArithmetic()).isTrue();
		
		assertThat(InfixOperator.PLUS.isComparison()).isFalse();
		assertThat(InfixOperator.PLUS.isLogical()).isFalse();
	}
	
	@Test
	void testComparisonOperators() {
		assertThat(InfixOperator.EQUALS.isComparison()).isTrue();
		assertThat(InfixOperator.NOT_EQUALS.isComparison()).isTrue();
		assertThat(InfixOperator.LESS.isComparison()).isTrue();
		assertThat(InfixOperator.GREATER.isComparison()).isTrue();
		assertThat(InfixOperator.LESS_EQUALS.isComparison()).isTrue();
		assertThat(InfixOperator.GREATER_EQUALS.isComparison()).isTrue();
		
		assertThat(InfixOperator.EQUALS.isArithmetic()).isFalse();
		assertThat(InfixOperator.EQUALS.isLogical()).isFalse();
	}
	
	@Test
	void testLogicalOperators() {
		assertThat(InfixOperator.CONDITIONAL_AND.isLogical()).isTrue();
		assertThat(InfixOperator.CONDITIONAL_OR.isLogical()).isTrue();
		
		assertThat(InfixOperator.CONDITIONAL_AND.isArithmetic()).isFalse();
		assertThat(InfixOperator.CONDITIONAL_AND.isComparison()).isFalse();
	}
	
	@Test
	void testBitwiseOperators() {
		assertThat(InfixOperator.AND.isArithmetic()).isTrue();
		assertThat(InfixOperator.OR.isArithmetic()).isTrue();
		assertThat(InfixOperator.XOR.isArithmetic()).isTrue();
		
		assertThat(InfixOperator.LEFT_SHIFT.isArithmetic()).isTrue();
		assertThat(InfixOperator.RIGHT_SHIFT_SIGNED.isArithmetic()).isTrue();
		assertThat(InfixOperator.RIGHT_SHIFT_UNSIGNED.isArithmetic()).isTrue();
	}
	
	@Test
	void testSymbol() {
		assertThat(InfixOperator.PLUS.symbol()).isEqualTo("+");
		assertThat(InfixOperator.MINUS.symbol()).isEqualTo("-");
		assertThat(InfixOperator.EQUALS.symbol()).isEqualTo("==");
		assertThat(InfixOperator.NOT_EQUALS.symbol()).isEqualTo("!=");
		assertThat(InfixOperator.CONDITIONAL_AND.symbol()).isEqualTo("&&");
		assertThat(InfixOperator.CONDITIONAL_OR.symbol()).isEqualTo("||");
	}
	
	@Test
	void testFromSymbol() {
		assertThat(InfixOperator.fromSymbol("+")).isEqualTo(InfixOperator.PLUS);
		assertThat(InfixOperator.fromSymbol("-")).isEqualTo(InfixOperator.MINUS);
		assertThat(InfixOperator.fromSymbol("==")).isEqualTo(InfixOperator.EQUALS);
		assertThat(InfixOperator.fromSymbol("&&")).isEqualTo(InfixOperator.CONDITIONAL_AND);
		assertThat(InfixOperator.fromSymbol("unknown")).isNull();
	}
}
