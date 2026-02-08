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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.sandbox.ast.api.info.TypeInfo;

class InfixExprTest {
	
	private final TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
	private final TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
	private final TypeInfo booleanType = TypeInfo.Builder.of("boolean").primitive().build();
	
	@Test
	void testBasicConstruction() {
		SimpleNameExpr left = SimpleNameExpr.builder().identifier("a").type(intType).build();
		SimpleNameExpr right = SimpleNameExpr.builder().identifier("b").type(intType).build();
		
		InfixExpr expr = InfixExpr.builder()
				.leftOperand(left)
				.rightOperand(right)
				.operator(InfixOperator.PLUS)
				.type(intType)
				.build();
		
		assertThat(expr.leftOperand()).isEqualTo(left);
		assertThat(expr.rightOperand()).isEqualTo(right);
		assertThat(expr.operator()).isEqualTo(InfixOperator.PLUS);
		assertThat(expr.type()).contains(intType);
		assertThat(expr.extendedOperands()).isEmpty();
	}
	
	@Test
	void testArithmeticChecks() {
		SimpleNameExpr left = SimpleNameExpr.builder().identifier("a").type(intType).build();
		SimpleNameExpr right = SimpleNameExpr.builder().identifier("b").type(intType).build();
		
		InfixExpr expr = InfixExpr.builder()
				.leftOperand(left)
				.rightOperand(right)
				.operator(InfixOperator.PLUS)
				.build();
		
		assertThat(expr.isArithmetic()).isTrue();
		assertThat(expr.isComparison()).isFalse();
		assertThat(expr.isLogical()).isFalse();
	}
	
	@Test
	void testComparisonChecks() {
		SimpleNameExpr left = SimpleNameExpr.builder().identifier("a").type(intType).build();
		SimpleNameExpr right = SimpleNameExpr.builder().identifier("b").type(intType).build();
		
		InfixExpr expr = InfixExpr.builder()
				.leftOperand(left)
				.rightOperand(right)
				.operator(InfixOperator.LESS)
				.type(booleanType)
				.build();
		
		assertThat(expr.isComparison()).isTrue();
		assertThat(expr.isArithmetic()).isFalse();
		assertThat(expr.isLogical()).isFalse();
	}
	
	@Test
	void testLogicalChecks() {
		SimpleNameExpr left = SimpleNameExpr.builder().identifier("a").type(booleanType).build();
		SimpleNameExpr right = SimpleNameExpr.builder().identifier("b").type(booleanType).build();
		
		InfixExpr expr = InfixExpr.builder()
				.leftOperand(left)
				.rightOperand(right)
				.operator(InfixOperator.CONDITIONAL_AND)
				.type(booleanType)
				.build();
		
		assertThat(expr.isLogical()).isTrue();
		assertThat(expr.isArithmetic()).isFalse();
		assertThat(expr.isComparison()).isFalse();
	}
	
	@Test
	void testStringConcatenation() {
		SimpleNameExpr left = SimpleNameExpr.builder().identifier("str").type(stringType).build();
		SimpleNameExpr right = SimpleNameExpr.builder().identifier("num").type(intType).build();
		
		InfixExpr expr = InfixExpr.builder()
				.leftOperand(left)
				.rightOperand(right)
				.operator(InfixOperator.PLUS)
				.type(stringType)
				.build();
		
		assertThat(expr.isStringConcatenation()).isTrue();
		assertThat(expr.isNumeric()).isFalse();
	}
	
	@Test
	void testNumericOperation() {
		SimpleNameExpr left = SimpleNameExpr.builder().identifier("a").type(intType).build();
		SimpleNameExpr right = SimpleNameExpr.builder().identifier("b").type(intType).build();
		
		InfixExpr expr = InfixExpr.builder()
				.leftOperand(left)
				.rightOperand(right)
				.operator(InfixOperator.PLUS)
				.type(intType)
				.build();
		
		assertThat(expr.isNumeric()).isTrue();
		assertThat(expr.isStringConcatenation()).isFalse();
	}
	
	@Test
	void testExtendedOperands() {
		SimpleNameExpr a = SimpleNameExpr.builder().identifier("a").type(intType).build();
		SimpleNameExpr b = SimpleNameExpr.builder().identifier("b").type(intType).build();
		SimpleNameExpr c = SimpleNameExpr.builder().identifier("c").type(intType).build();
		SimpleNameExpr d = SimpleNameExpr.builder().identifier("d").type(intType).build();
		
		InfixExpr expr = InfixExpr.builder()
				.leftOperand(a)
				.rightOperand(b)
				.addExtendedOperand(c)
				.addExtendedOperand(d)
				.operator(InfixOperator.PLUS)
				.build();
		
		assertThat(expr.hasExtendedOperands()).isTrue();
		assertThat(expr.extendedOperands()).containsExactly(c, d);
		assertThat(expr.allOperands()).containsExactly(a, b, c, d);
	}
	
	@Test
	void testNullLeftOperandThrows() {
		assertThatThrownBy(() -> InfixExpr.builder()
				.rightOperand(SimpleNameExpr.of("b"))
				.operator(InfixOperator.PLUS)
				.build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Left operand cannot be null");
	}
	
	@Test
	void testNullRightOperandThrows() {
		assertThatThrownBy(() -> InfixExpr.builder()
				.leftOperand(SimpleNameExpr.of("a"))
				.operator(InfixOperator.PLUS)
				.build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Right operand cannot be null");
	}
	
	@Test
	void testNullOperatorThrows() {
		assertThatThrownBy(() -> InfixExpr.builder()
				.leftOperand(SimpleNameExpr.of("a"))
				.rightOperand(SimpleNameExpr.of("b"))
				.build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Operator cannot be null");
	}
	
	@Test
	void testASTExprInterfaceMethods() {
		SimpleNameExpr left = SimpleNameExpr.builder().identifier("a").type(intType).build();
		SimpleNameExpr right = SimpleNameExpr.builder().identifier("b").type(intType).build();
		
		InfixExpr expr = InfixExpr.builder()
				.leftOperand(left)
				.rightOperand(right)
				.operator(InfixOperator.PLUS)
				.type(intType)
				.build();
		
		assertThat(expr.type()).contains(intType);
		assertThat(expr.hasType("int")).isTrue();
		assertThat(expr.asInfix()).contains(expr);
		assertThat(expr.asSimpleName()).isEmpty();
	}
}
