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
package org.sandbox.jdt.internal.corext.fix;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPatternEngine;

/**
 * Fix core for shift out of range detection using TriggerPattern hints.
 *
 * <p>This class applies shift out of range patterns as cleanup operations,
 * detecting shift operations where the shift amount is out of range and
 * replacing them with the effective masked value.</p>
 *
 * @since 1.2.5
 */
public class ShiftOutOfRangeFixCore {

	private static final TriggerPatternEngine ENGINE = new TriggerPatternEngine();

	private static final int INT_SHIFT_MASK = 31;
	private static final int LONG_SHIFT_MASK = 63;

	/**
	 * Finds shift out of range operations in the compilation unit.
	 *
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add found operations to
	 */
	public static void findOperations(CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations) {

		// Pattern 1: $v << $c
		Pattern leftShiftPattern = new Pattern("$v << $c", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> leftShiftMatches = ENGINE.findMatches(compilationUnit, leftShiftPattern);
		for (Match match : leftShiftMatches) {
			addOperationIfOutOfRange(match, operations);
		}

		// Pattern 2: $v >> $c
		Pattern rightShiftPattern = new Pattern("$v >> $c", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> rightShiftMatches = ENGINE.findMatches(compilationUnit, rightShiftPattern);
		for (Match match : rightShiftMatches) {
			addOperationIfOutOfRange(match, operations);
		}

		// Pattern 3: $v >>> $c
		Pattern unsignedRightShiftPattern = new Pattern("$v >>> $c", PatternKind.EXPRESSION); //$NON-NLS-1$
		List<Match> unsignedRightShiftMatches = ENGINE.findMatches(compilationUnit, unsignedRightShiftPattern);
		for (Match match : unsignedRightShiftMatches) {
			addOperationIfOutOfRange(match, operations);
		}
	}

	private static void addOperationIfOutOfRange(Match match, Set<CompilationUnitRewriteOperation> operations) {
		ASTNode matchedNode = match.getMatchedNode();
		if (!(matchedNode instanceof InfixExpression)) {
			return;
		}

		// Get the shift amount
		ASTNode cNode = match.getBinding("$c"); //$NON-NLS-1$
		if (cNode == null || !(cNode instanceof Expression)) {
			return;
		}

		Expression shiftAmountExpr = (Expression) cNode;
		Object constantValue = shiftAmountExpr.resolveConstantExpressionValue();
		if (constantValue == null || !(constantValue instanceof Number)) {
			return;
		}
		long shiftAmount = ((Number) constantValue).longValue();

		// Get the left operand type
		ASTNode vNode = match.getBinding("$v"); //$NON-NLS-1$
		if (vNode == null || !(vNode instanceof Expression)) {
			return;
		}

		Expression leftOperand = (Expression) vNode;
		ITypeBinding typeBinding = leftOperand.resolveTypeBinding();
		if (typeBinding == null) {
			return;
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
				return; // in range
			}
			maskedValue = shiftAmount & INT_SHIFT_MASK;
		} else if (isLongLike) {
			if (shiftAmount >= 0 && shiftAmount <= LONG_SHIFT_MASK) {
				return; // in range
			}
			maskedValue = shiftAmount & LONG_SHIFT_MASK;
		} else {
			return;
		}

		operations.add(new ShiftOutOfRangeOperation(match, maskedValue));
	}

	/**
	 * Rewrite operation for shift out of range replacement.
	 */
	private static class ShiftOutOfRangeOperation extends CompilationUnitRewriteOperation {

		private final Match match;
		private final long maskedValue;

		public ShiftOutOfRangeOperation(Match match, long maskedValue) {
			this.match = match;
			this.maskedValue = maskedValue;
		}

		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedModel) {
			ASTRewrite rewrite = cuRewrite.getASTRewrite();
			AST ast = cuRewrite.getRoot().getAST();
			TextEditGroup group = createTextEditGroup("Replace out-of-range shift amount", cuRewrite); //$NON-NLS-1$

			ASTNode matchedNode = match.getMatchedNode();
			if (!(matchedNode instanceof InfixExpression)) {
				return;
			}

			InfixExpression infixExpr = (InfixExpression) matchedNode;
			NumberLiteral newLiteral = ast.newNumberLiteral(String.valueOf(maskedValue));
			rewrite.replace(infixExpr.getRightOperand(), newLiteral, group);
		}
	}
}
