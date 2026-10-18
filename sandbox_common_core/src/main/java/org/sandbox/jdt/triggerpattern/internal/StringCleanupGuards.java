/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.Comment;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.sandbox.jdt.triggerpattern.api.GuardContext;

/**
 * Binding-dependent safety contracts for the two JDT reference String cleanups.
 *
 * <p>These are deliberately narrower than JDT's AST rewrite implementations:
 * text replacements decline internal comments, and substring accepts stable
 * local/literal receivers and passive local expressions for the start index.
 * Unknown or recovered bindings never authorize a rewrite.</p>
 */
final class StringCleanupGuards {

	private static final String STRING= "java.lang.String"; //$NON-NLS-1$
	private static final Set<String> STRIP_METHODS= Set.of("strip", "stripLeading", "stripTrailing"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	private StringCleanupGuards() {
	}

	static boolean substringSafe(GuardContext context, Object... arguments) {
		if (arguments.length != 0 || hasInternalComment(context)
				|| !(context.getMatchedNode() instanceof MethodInvocation substring)
				|| !stringMethod(context, "stringSubstringSafe", substring, "substring", "int", "int")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			return false;
		}
		Expression receiver= unparenthesize(substring.getExpression());
		Expression start= (Expression) substring.arguments().get(0);
		Expression end= unparenthesize((Expression) substring.arguments().get(1));
		if (!(end instanceof MethodInvocation length)
				|| !stringMethod(context, "stringSubstringSafe", length, "length") //$NON-NLS-1$ //$NON-NLS-2$
				|| !isStableLocalReceiver(receiver) || !isStableExpression(start)) {
			return false;
		}
		Expression endReceiver= unparenthesize(length.getExpression());
		if (receiver instanceof SimpleName name && endReceiver instanceof SimpleName endName) {
			IBinding binding= name.resolveBinding();
			return binding != null && binding.isEqualTo(endName.resolveBinding());
		}
		return endReceiver != null && receiver.subtreeMatch(new ASTMatcher(), endReceiver);
	}

	static boolean isBlankSafe(GuardContext context, Object... arguments) {
		if (arguments.length != 0 || hasInternalComment(context)) {
			return false;
		}
		ASTNode node= context.getMatchedNode();
		if (node instanceof MethodInvocation invocation) {
			if (stringMethod(context, "stringIsBlankSafe", invocation, "isEmpty")) { //$NON-NLS-1$ //$NON-NLS-2$
				return stringStrip(context, invocation.getExpression());
			}
			if (stringMethod(context, "stringIsBlankSafe", invocation, "equals", "java.lang.Object")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				Expression argument= (Expression) invocation.arguments().get(0);
				return (isEmptyLiteral(invocation.getExpression()) && stringStrip(context, argument))
						|| (isEmptyLiteral(argument) && stringStrip(context, invocation.getExpression()));
			}
		}
		if (node instanceof InfixExpression infix && !infix.hasExtendedOperands()
				&& isSupportedLengthComparison(infix)
				&& unparenthesize(infix.getLeftOperand()) instanceof MethodInvocation length
				&& stringMethod(context, "stringIsBlankSafe", length, "length")) { //$NON-NLS-1$ //$NON-NLS-2$
			return stringStrip(context, length.getExpression());
		}
		return false;
	}

	private static boolean isSupportedLengthComparison(InfixExpression infix) {
		Object constant= infix.getRightOperand().resolveConstantExpressionValue();
		if (Integer.valueOf(0).equals(constant)) {
			return infix.getOperator() == InfixExpression.Operator.EQUALS
					|| infix.getOperator() == InfixExpression.Operator.LESS_EQUALS
					|| infix.getOperator() == InfixExpression.Operator.NOT_EQUALS
					|| infix.getOperator() == InfixExpression.Operator.GREATER;
		}
		return Integer.valueOf(1).equals(constant)
				&& (infix.getOperator() == InfixExpression.Operator.LESS
						|| infix.getOperator() == InfixExpression.Operator.GREATER_EQUALS);
	}

	private static boolean stringStrip(GuardContext context, Expression expression) {
		Expression unwrapped= unparenthesize(expression);
		return unwrapped instanceof MethodInvocation strip
				&& STRIP_METHODS.contains(strip.getName().getIdentifier())
				&& stringMethod(context, "stringIsBlankSafe", strip, strip.getName().getIdentifier()); //$NON-NLS-1$
	}

	private static boolean stringMethod(GuardContext context, String guard,
			MethodInvocation invocation, String name, String... parameterTypes) {
		if (!name.equals(invocation.getName().getIdentifier())
				|| invocation.arguments().size() != parameterTypes.length) {
			return false;
		}
		IMethodBinding binding= invocation.resolveMethodBinding();
		if (binding == null || binding.isRecovered() || binding.getDeclaringClass() == null
				|| binding.getDeclaringClass().isRecovered()) {
			context.markUnknown(guard, "Cannot resolve String." + name + " method signature"); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		if (!STRING.equals(binding.getDeclaringClass().getErasure().getQualifiedName())
				|| !name.equals(binding.getName())) {
			return false;
		}
		ITypeBinding[] parameters= binding.getParameterTypes();
		if (parameters.length != parameterTypes.length) {
			return false;
		}
		for (int i= 0; i < parameters.length; i++) {
			if (parameters[i].isRecovered()) {
				context.markUnknown(guard, "Cannot resolve String." + name + " parameter type"); //$NON-NLS-1$ //$NON-NLS-2$
				return false;
			}
			if (!parameterTypes[i].equals(parameters[i].getErasure().getQualifiedName())) {
				return false;
			}
		}
		return true;
	}

	private static boolean isEmptyLiteral(Expression expression) {
		return unparenthesize(expression) instanceof StringLiteral literal && literal.getLiteralValue().isEmpty();
	}

	private static boolean isStableLocalReceiver(Expression expression) {
		return expression instanceof StringLiteral || expression instanceof SimpleName name && isLocal(name);
	}

	/** Declines calls, fields, arrays, allocations and writes across the removed read. */
	private static boolean isStableExpression(Expression expression) {
		Expression node= unparenthesize(expression);
		if (node == null) {
			return false;
		}
		if (node instanceof SimpleName name) {
			return isLocal(name);
		}
		if (node.getNodeType() == ASTNode.NUMBER_LITERAL || node.getNodeType() == ASTNode.CHARACTER_LITERAL
				|| node.getNodeType() == ASTNode.BOOLEAN_LITERAL) {
			return true;
		}
		if (node instanceof InfixExpression infix) {
			if (!isStableExpression(infix.getLeftOperand()) || !isStableExpression(infix.getRightOperand())) {
				return false;
			}
			for (Object operand : infix.extendedOperands()) {
				if (!isStableExpression((Expression) operand)) {
					return false;
				}
			}
			return true;
		}
		if (node instanceof PrefixExpression prefix) {
			return prefix.getOperator() != PrefixExpression.Operator.INCREMENT
					&& prefix.getOperator() != PrefixExpression.Operator.DECREMENT
					&& isStableExpression(prefix.getOperand());
		}
		if (node instanceof ConditionalExpression conditional) {
			return isStableExpression(conditional.getExpression())
					&& isStableExpression(conditional.getThenExpression())
					&& isStableExpression(conditional.getElseExpression());
		}
		return node instanceof CastExpression cast && cast.getType().isPrimitiveType()
				&& isStableExpression(cast.getExpression());
	}

	private static boolean isLocal(SimpleName name) {
		return name.resolveBinding() instanceof IVariableBinding variable && !variable.isRecovered()
				&& !variable.isField() && !variable.isEnumConstant();
	}

	private static Expression unparenthesize(Expression expression) {
		Expression result= expression;
		while (result instanceof ParenthesizedExpression parenthesized) {
			result= parenthesized.getExpression();
		}
		return result;
	}

	/** Text substitution cannot relocate comments inside the replaced AST range. */
	private static boolean hasInternalComment(GuardContext context) {
		ASTNode node= context.getMatchedNode();
		if (node == null || context.getCompilationUnit() == null
				|| context.getCompilationUnit().getCommentList() == null) {
			return true;
		}
		int start= node.getStartPosition();
		int end= start + node.getLength();
		for (Object value : context.getCompilationUnit().getCommentList()) {
			Comment comment= (Comment) value;
			if (comment.getStartPosition() >= start && comment.getStartPosition() < end) {
				return true;
			}
		}
		return false;
	}
}
