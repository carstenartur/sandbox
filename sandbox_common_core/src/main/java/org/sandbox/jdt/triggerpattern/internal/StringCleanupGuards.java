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
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.TextBlock;
import org.sandbox.jdt.triggerpattern.api.GuardContext;

/**
 * Binding-dependent safety contracts for String cleanups.
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

	static boolean indexOfContainsSafe(GuardContext context, Object... arguments) {
		if (arguments.length != 0 || hasCommentInRewriteRange(context)
				|| !(context.getMatchedNode() instanceof InfixExpression comparison)
				|| comparison.hasExtendedOperands()
				|| !(unparenthesize(comparison.getLeftOperand()) instanceof MethodInvocation invocation)) {
			return false;
		}
		Object constant= comparison.getRightOperand().resolveConstantExpressionValue();
		InfixExpression.Operator operator= comparison.getOperator();
		boolean positive= Integer.valueOf(0).equals(constant) && operator == InfixExpression.Operator.GREATER_EQUALS
				|| Integer.valueOf(-1).equals(constant) && operator == InfixExpression.Operator.NOT_EQUALS;
		boolean negative= Integer.valueOf(0).equals(constant) && operator == InfixExpression.Operator.LESS
				|| Integer.valueOf(-1).equals(constant) && operator == InfixExpression.Operator.EQUALS;
		if ((!positive && !negative) || (positive && comparison.getParent() instanceof LambdaExpression)) {
			return false;
		}
		return stringMethod(context, "stringIndexOfContainsSafe", invocation, "indexOf", STRING) //$NON-NLS-1$ //$NON-NLS-2$
				&& targetStableStringArgument(context, (Expression) invocation.arguments().get(0));
	}

	/** The replacement's CharSequence parameter must not change target-type inference. */
	private static boolean targetStableStringArgument(GuardContext context, Expression expression) {
		Expression argument= unparenthesize(expression);
		if (argument instanceof StringLiteral || argument instanceof TextBlock) {
			return true;
		}
		if (argument instanceof MethodInvocation call) {
			IMethodBinding binding= call.resolveMethodBinding();
			if (binding == null || binding.isRecovered()) {
				context.markUnknown("stringIndexOfContainsSafe", "Cannot resolve argument method signature"); //$NON-NLS-1$ //$NON-NLS-2$
				return false;
			}
			IMethodBinding declaration= binding.getMethodDeclaration();
			ITypeBinding returnType= declaration.getReturnType();
			return !declaration.isRecovered() && declaration.getTypeParameters().length == 0
					&& returnType != null && !returnType.isRecovered()
					&& STRING.equals(returnType.getQualifiedName());
		}
		if (argument instanceof Name name) {
			return declaredStringVariable(context, name.resolveBinding());
		}
		if (argument instanceof FieldAccess field) {
			return declaredStringVariable(context, field.resolveFieldBinding());
		}
		if (argument instanceof SuperFieldAccess field) {
			return declaredStringVariable(context, field.resolveFieldBinding());
		}
		if (!(argument instanceof CastExpression)) {
			return false;
		}
		ITypeBinding type= argument.resolveTypeBinding();
		if (type == null || type.isRecovered()) {
			context.markUnknown("stringIndexOfContainsSafe", "Cannot resolve argument type"); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		return STRING.equals(type.getQualifiedName());
	}

	private static boolean declaredStringVariable(GuardContext context, IBinding binding) {
		if (!(binding instanceof IVariableBinding variable) || variable.isRecovered()) {
			context.markUnknown("stringIndexOfContainsSafe", "Cannot resolve argument variable"); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		IVariableBinding declaration= variable.getVariableDeclaration();
		ITypeBinding type= declaration.getType();
		if (declaration.isRecovered() || type == null || type.isRecovered()) {
			context.markUnknown("stringIndexOfContainsSafe", "Cannot resolve declared argument type"); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		return STRING.equals(type.getQualifiedName());
	}

	static boolean substringSafe(GuardContext context, Object... arguments) {
		if (arguments.length != 0 || hasCommentInRewriteRange(context)
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
		if (arguments.length != 0 || hasCommentInRewriteRange(context)) {
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
			if (infix.getParent() instanceof LambdaExpression
					&& (infix.getOperator() == InfixExpression.Operator.EQUALS
							|| infix.getOperator() == InfixExpression.Operator.LESS_EQUALS
							|| infix.getOperator() == InfixExpression.Operator.LESS)) {
				// A bare isBlank() call would also make the lambda void-compatible.
				return false;
			}
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

	/** JDT's default rewrite range can extend to adjacent comments. */
	private static boolean hasCommentInRewriteRange(GuardContext context) {
		ASTNode node= context.getMatchedNode();
		if (node == null || context.getCompilationUnit() == null
				|| context.getCompilationUnit().getCommentList() == null
				|| node.getRoot() != context.getCompilationUnit()) {
			return true;
		}
		int start= context.getCompilationUnit().getExtendedStartPosition(node);
		int length= context.getCompilationUnit().getExtendedLength(node);
		if (start < 0 || length < 0 || start > node.getStartPosition()
				|| (long) start + length < (long) node.getStartPosition() + node.getLength()) {
			return true;
		}
		long end= (long) start + length;
		for (Object value : context.getCompilationUnit().getCommentList()) {
			Comment comment= (Comment) value;
			int commentStart= comment.getStartPosition();
			if (commentStart < end && (long) commentStart + comment.getLength() > start) {
				return true;
			}
		}
		return false;
	}
}
