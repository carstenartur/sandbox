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
package org.sandbox.jdt.triggerpattern.shift;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.swt.graphics.Image;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.HintContext;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;

/**
 * Hint provider for shift out of range detection using TriggerPattern.
 *
 * <p>In Java, shift amounts are automatically masked:</p>
 * <ul>
 * <li>For {@code int} (and {@code byte}, {@code short}, {@code char}): {@code amount & 0x1f} (range 0-31)</li>
 * <li>For {@code long}: {@code amount & 0x3f} (range 0-63)</li>
 * </ul>
 *
 * <p>This hint detects out-of-range shift amounts and suggests replacing them
 * with the effective masked value.</p>
 *
 * <p>Inspired by
 * <a href="https://github.com/apache/netbeans/blob/master/java/java.hints/src/org/netbeans/modules/java/hints/ShiftOutOfRange.java">
 * NetBeans ShiftOutOfRange</a>.</p>
 *
 * @since 1.2.5
 */
public class ShiftOutOfRangeHintProvider {

	private static final int INT_SHIFT_MASK = 31;
	private static final int LONG_SHIFT_MASK = 63;

	/**
	 * Detects {@code $v << $c} where $c is out of range.
	 *
	 * @param ctx the hint context
	 * @return a completion proposal, or null if not applicable
	 */
	@TriggerPattern(value = "$v << $c", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Shift left amount out of range",
	      description = "The shift amount is out of the valid range and will be masked by Java")
	public static IJavaCompletionProposal checkLeftShift(HintContext ctx) {
		return checkShiftOutOfRange(ctx);
	}

	/**
	 * Detects {@code $v >> $c} where $c is out of range.
	 *
	 * @param ctx the hint context
	 * @return a completion proposal, or null if not applicable
	 */
	@TriggerPattern(value = "$v >> $c", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Signed right shift amount out of range",
	      description = "The shift amount is out of the valid range and will be masked by Java")
	public static IJavaCompletionProposal checkRightShiftSigned(HintContext ctx) {
		return checkShiftOutOfRange(ctx);
	}

	/**
	 * Detects {@code $v >>> $c} where $c is out of range.
	 *
	 * @param ctx the hint context
	 * @return a completion proposal, or null if not applicable
	 */
	@TriggerPattern(value = "$v >>> $c", kind = PatternKind.EXPRESSION)
	@Hint(displayName = "Unsigned right shift amount out of range",
	      description = "The shift amount is out of the valid range and will be masked by Java")
	public static IJavaCompletionProposal checkRightShiftUnsigned(HintContext ctx) {
		return checkShiftOutOfRange(ctx);
	}

	private static IJavaCompletionProposal checkShiftOutOfRange(HintContext ctx) {
		ASTNode matchedNode = ctx.getMatch().getMatchedNode();
		if (!(matchedNode instanceof InfixExpression)) {
			return null;
		}

		InfixExpression infixExpr = (InfixExpression) matchedNode;

		// Get the shift amount
		ASTNode cNode = ctx.getMatch().getBinding("$c"); //$NON-NLS-1$
		if (cNode == null || !(cNode instanceof Expression)) {
			return null;
		}

		Expression shiftAmountExpr = (Expression) cNode;
		Object constantValue = shiftAmountExpr.resolveConstantExpressionValue();
		if (constantValue == null || !(constantValue instanceof Number)) {
			return null;
		}
		long shiftAmount = ((Number) constantValue).longValue();

		// Get the left operand type
		ASTNode vNode = ctx.getMatch().getBinding("$v"); //$NON-NLS-1$
		if (vNode == null || !(vNode instanceof Expression)) {
			return null;
		}

		Expression leftOperand = (Expression) vNode;
		ITypeBinding typeBinding = leftOperand.resolveTypeBinding();
		if (typeBinding == null) {
			return null;
		}

		String qualifiedName = typeBinding.getQualifiedName();
		long maskedValue;
		boolean isIntLike = "int".equals(qualifiedName) //$NON-NLS-1$
				|| "byte".equals(qualifiedName) //$NON-NLS-1$
				|| "short".equals(qualifiedName) //$NON-NLS-1$
				|| "char".equals(qualifiedName) //$NON-NLS-1$
				|| "java.lang.Integer".equals(qualifiedName) //$NON-NLS-1$
				|| "java.lang.Byte".equals(qualifiedName) //$NON-NLS-1$
				|| "java.lang.Short".equals(qualifiedName) //$NON-NLS-1$
				|| "java.lang.Character".equals(qualifiedName); //$NON-NLS-1$
		boolean isLongLike = "long".equals(qualifiedName) //$NON-NLS-1$
				|| "java.lang.Long".equals(qualifiedName); //$NON-NLS-1$
		if (isIntLike) {
			if (shiftAmount >= 0 && shiftAmount <= INT_SHIFT_MASK) {
				return null; // in range
			}
			maskedValue = shiftAmount & INT_SHIFT_MASK;
		} else if (isLongLike) {
			if (shiftAmount >= 0 && shiftAmount <= LONG_SHIFT_MASK) {
				return null; // in range
			}
			maskedValue = shiftAmount & LONG_SHIFT_MASK;
		} else {
			return null;
		}

		// Create the replacement
		AST ast = ctx.getASTRewrite().getAST();
		NumberLiteral newLiteral = ast.newNumberLiteral(String.valueOf(maskedValue));
		ctx.getASTRewrite().replace(infixExpr.getRightOperand(), newLiteral, null);

		String label = "Replace out-of-range shift amount " + shiftAmount + " with " + maskedValue; //$NON-NLS-1$ //$NON-NLS-2$
		ASTRewriteCorrectionProposal proposal = new ASTRewriteCorrectionProposal(
			label,
			ctx.getICompilationUnit(),
			ctx.getASTRewrite(),
			10,
			(Image) null
		);

		return proposal;
	}
}
