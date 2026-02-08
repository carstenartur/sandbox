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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.ShiftOutOfRangeFixCore;

/**
 * Helper class that detects shift operations where the shift amount is out of range
 * and replaces the shift amount with the effective masked value.
 *
 * <p>In Java, the shift amount is masked:</p>
 * <ul>
 * <li>For {@code int}: shift amount is masked with {@code 0x1f} (31), so valid range is 0-31</li>
 * <li>For {@code long}: shift amount is masked with {@code 0x3f} (63), so valid range is 0-63</li>
 * </ul>
 *
 * <p>This cleanup makes the actual Java behavior explicit by replacing the out-of-range
 * constant with the effective masked value.</p>
 */
public class ShiftOutOfRangeHelper {

	private static final int INT_SHIFT_MASK = 31;
	private static final int LONG_SHIFT_MASK = 63;

	public void find(ShiftOutOfRangeFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(InfixExpression node) {
				if (nodesprocessed.contains(node)) {
					return true;
				}
				InfixExpression.Operator op = node.getOperator();
				if (op != InfixExpression.Operator.LEFT_SHIFT
						&& op != InfixExpression.Operator.RIGHT_SHIFT_SIGNED
						&& op != InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED) {
					return true;
				}
				Expression rightOperand = node.getRightOperand();
				Object constantValue = rightOperand.resolveConstantExpressionValue();
				if (constantValue == null || !(constantValue instanceof Number)) {
					return true;
				}
				long shiftAmount = ((Number) constantValue).longValue();
				Expression leftOperand = node.getLeftOperand();
				ITypeBinding typeBinding = leftOperand.resolveTypeBinding();
				if (typeBinding == null) {
					return true;
				}
				String typeName = typeBinding.getName();
				if ("int".equals(typeName) || "byte".equals(typeName) || "short".equals(typeName) || "char".equals(typeName)) {
					if (shiftAmount < 0 || shiftAmount > INT_SHIFT_MASK) {
						long maskedValue = shiftAmount & INT_SHIFT_MASK;
						operations.add(fixcore.rewrite(node, maskedValue));
						nodesprocessed.add(node);
					}
				} else if ("long".equals(typeName)) {
					if (shiftAmount < 0 || shiftAmount > LONG_SHIFT_MASK) {
						long maskedValue = shiftAmount & LONG_SHIFT_MASK;
						operations.add(fixcore.rewrite(node, maskedValue));
						nodesprocessed.add(node);
					}
				}
				return true;
			}
		});
	}

	public void rewrite(ShiftOutOfRangeFixCore fixCore, InfixExpression visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group, long maskedValue) {
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getRoot().getAST();
		NumberLiteral newLiteral = ast.newNumberLiteral(String.valueOf(maskedValue));
		rewrite.replace(visited.getRightOperand(), newLiteral, group);
	}

	public String getPreview(boolean afterRefactoring) {
		if (!afterRefactoring) {
			return "int shifted = value << 32;\n";
		}
		return "int shifted = value << 0;\n";
	}
}
