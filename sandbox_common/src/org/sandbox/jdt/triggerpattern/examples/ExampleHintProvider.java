/*******************************************************************************
 * Copyright (c) 2025, 2026 Carsten Hammer.
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
package org.sandbox.jdt.triggerpattern.examples;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.sandbox.jdt.triggerpattern.api.Hint;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TriggerPattern;
import org.sandbox.jdt.triggerpattern.eclipse.HintContext;

/**
 * A small, behaviour-preserving annotation-based hint.
 *
 * <p>Double negation of a primitive boolean preserves its value, evaluation
 * count and exceptions. A boxed Boolean is deliberately excluded: replacing
 * {@code !!boxed} by {@code boxed} could remove unboxing and its null check.</p>
 *
 * @since 1.2.2
 */
public class ExampleHintProvider {

	/** Removes double negation only when the bound expression is a resolved primitive boolean. */
	@TriggerPattern(value= "!!$x", kind= PatternKind.EXPRESSION) //$NON-NLS-1$
	@Hint(displayName= "Remove boolean double negation", //$NON-NLS-1$
			description= "Replaces !!expression with a primitive boolean expression evaluated once") //$NON-NLS-1$
	public static IJavaCompletionProposal simplifyDoubleNegation(HintContext context) {
		ASTNode matchedNode= context.getMatch().getMatchedNode();
		ASTNode boundNode= context.getMatch().getBinding("$x"); //$NON-NLS-1$
		if (!(matchedNode instanceof PrefixExpression outer)
				|| outer.getOperator() != PrefixExpression.Operator.NOT
				|| !(outer.getOperand() instanceof PrefixExpression inner)
				|| inner.getOperator() != PrefixExpression.Operator.NOT
				|| !(boundNode instanceof Expression expression)
				|| inner.getOperand() != expression) {
			return null;
		}
		ITypeBinding type= expression.resolveTypeBinding();
		if (type == null || type.isRecovered() || !type.isPrimitive() || !"boolean".equals(type.getName())) { //$NON-NLS-1$
			return null;
		}
		for (Object value : context.getCompilationUnit().getCommentList()) {
			ASTNode comment= (ASTNode) value;
			int start= comment.getStartPosition();
			int end= start + comment.getLength();
			if (start >= outer.getStartPosition() && end <= outer.getStartPosition() + outer.getLength()
					&& !(start >= expression.getStartPosition() && end <= expression.getStartPosition() + expression.getLength())) {
				// Copying only the operand would discard a comment between the operators.
				return null;
			}
		}
		context.getASTRewrite().replace(outer, context.getASTRewrite().createCopyTarget(expression), null);
		return new ASTRewriteCorrectionProposal("Remove boolean double negation", //$NON-NLS-1$
				context.getICompilationUnit(), context.getASTRewrite(), 10, null);
	}
}
