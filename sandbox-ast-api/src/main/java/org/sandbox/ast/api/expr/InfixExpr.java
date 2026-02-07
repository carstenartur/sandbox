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

import java.util.List;
import java.util.Optional;

import org.sandbox.ast.api.info.TypeInfo;

/**
 * Immutable record representing an infix expression (binary operation).
 * Provides fluent access to operands and operator information.
 * 
 * <p>Example usage:</p>
 * <pre>
 * // Old style:
 * if (node instanceof InfixExpression) {
 *     InfixExpression infix = (InfixExpression) node;
 *     InfixExpression.Operator op = infix.getOperator();
 *     Expression left = infix.getLeftOperand();
 *     Expression right = infix.getRightOperand();
 *     // ...
 * }
 * 
 * // New style:
 * expr.asInfix()
 *     .filter(infix -&gt; infix.operator() == InfixOperator.PLUS)
 *     .filter(InfixExpr::isNumeric)
 *     .ifPresent(infix -&gt; { });
 * </pre>
 */
public record InfixExpr(
	ASTExpr leftOperand,
	ASTExpr rightOperand,
	List<ASTExpr> extendedOperands,
	InfixOperator operator,
	Optional<TypeInfo> type
) implements ASTExpr {
	
	/**
	 * Creates an InfixExpr record.
	 * 
	 * @param leftOperand the left operand
	 * @param rightOperand the right operand
	 * @param extendedOperands additional operands for chained operations
	 * @param operator the infix operator
	 * @param type the result type
	 */
	public InfixExpr {
		if (leftOperand == null) {
			throw new IllegalArgumentException("Left operand cannot be null");
		}
		if (rightOperand == null) {
			throw new IllegalArgumentException("Right operand cannot be null");
		}
		if (operator == null) {
			throw new IllegalArgumentException("Operator cannot be null");
		}
		extendedOperands = extendedOperands == null ? List.of() : List.copyOf(extendedOperands);
		type = type == null ? Optional.empty() : type;
	}
	
	/**
	 * Checks if this is an arithmetic operation.
	 * 
	 * @return true if arithmetic
	 */
	public boolean isArithmetic() {
		return operator.isArithmetic();
	}
	
	/**
	 * Checks if this is a comparison operation.
	 * 
	 * @return true if comparison
	 */
	public boolean isComparison() {
		return operator.isComparison();
	}
	
	/**
	 * Checks if this is a logical operation.
	 * 
	 * @return true if logical
	 */
	public boolean isLogical() {
		return operator.isLogical();
	}
	
	/**
	 * Checks if this is a string concatenation.
	 * 
	 * @return true if string concatenation
	 */
	public boolean isStringConcatenation() {
		return operator == InfixOperator.PLUS && 
		       (leftOperand.hasType(String.class) || rightOperand.hasType(String.class));
	}
	
	/**
	 * Checks if this is a numeric operation (both operands are numbers).
	 * 
	 * @return true if numeric
	 */
	public boolean isNumeric() {
		return leftOperand.type().map(TypeInfo::isNumeric).orElse(false) &&
		       rightOperand.type().map(TypeInfo::isNumeric).orElse(false);
	}
	
	/**
	 * Gets all operands (left, right, and extended).
	 * 
	 * @return list of all operands
	 */
	public List<ASTExpr> allOperands() {
		var all = new java.util.ArrayList<ASTExpr>();
		all.add(leftOperand);
		all.add(rightOperand);
		all.addAll(extendedOperands);
		return List.copyOf(all);
	}
	
	/**
	 * Checks if this has extended operands (chained operations like a + b + c).
	 * 
	 * @return true if has extended operands
	 */
	public boolean hasExtendedOperands() {
		return !extendedOperands.isEmpty();
	}
	
	/**
	 * Builder for creating InfixExpr instances.
	 */
	public static class Builder {
		private ASTExpr leftOperand;
		private ASTExpr rightOperand;
		private List<ASTExpr> extendedOperands = List.of();
		private InfixOperator operator;
		private Optional<TypeInfo> type = Optional.empty();
		
		/**
		 * Sets the left operand.
		 * 
		 * @param leftOperand the left operand
		 * @return this builder
		 */
		public Builder leftOperand(ASTExpr leftOperand) {
			this.leftOperand = leftOperand;
			return this;
		}
		
		/**
		 * Sets the right operand.
		 * 
		 * @param rightOperand the right operand
		 * @return this builder
		 */
		public Builder rightOperand(ASTExpr rightOperand) {
			this.rightOperand = rightOperand;
			return this;
		}
		
		/**
		 * Sets the extended operands.
		 * 
		 * @param extendedOperands the extended operands
		 * @return this builder
		 */
		public Builder extendedOperands(List<ASTExpr> extendedOperands) {
			this.extendedOperands = extendedOperands == null ? List.of() : extendedOperands;
			return this;
		}
		
		/**
		 * Adds an extended operand.
		 * 
		 * @param operand the operand
		 * @return this builder
		 */
		public Builder addExtendedOperand(ASTExpr operand) {
			this.extendedOperands = new java.util.ArrayList<>(this.extendedOperands);
			((java.util.ArrayList<ASTExpr>) this.extendedOperands).add(operand);
			return this;
		}
		
		/**
		 * Sets the operator.
		 * 
		 * @param operator the operator
		 * @return this builder
		 */
		public Builder operator(InfixOperator operator) {
			this.operator = operator;
			return this;
		}
		
		/**
		 * Sets the type information.
		 * 
		 * @param type the type
		 * @return this builder
		 */
		public Builder type(TypeInfo type) {
			this.type = Optional.ofNullable(type);
			return this;
		}
		
		/**
		 * Builds the InfixExpr.
		 * 
		 * @return the infix expression
		 */
		public InfixExpr build() {
			return new InfixExpr(leftOperand, rightOperand, extendedOperands, operator, type);
		}
	}
	
	/**
	 * Creates a new builder.
	 * 
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}
}
