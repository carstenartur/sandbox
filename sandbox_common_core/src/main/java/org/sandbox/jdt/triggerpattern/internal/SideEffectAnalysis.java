/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.lang.reflect.Modifier;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.TextBlock;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypePattern;
import org.sandbox.jdt.triggerpattern.api.GuardContext;

/**
 * A deliberately limited expression-effects proof, shared by guard evaluation.
 * It does not prove that reads are stable, non-throwing or safe to move.
 * Type cases stay separate because JDT binding recovery can crash on grouped
 * patterns when their types are unresolved.
 */
final class SideEffectAnalysis {

	private SideEffectAnalysis() {
	}

	/**
	 * Proves a non-throwing, stable value read only. This says nothing about the
	 * mutability of the object it denotes or conversions introduced by a rewrite.
	 */
	static boolean isStableValue(Expression expression, GuardContext context) {
		if ((expression.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) {
			context.markUnknown("isStableValue", "Recovered or malformed expression"); //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		return switch (expression) {
		case BooleanLiteral _ -> true;
		case CharacterLiteral _ -> true;
		case NullLiteral _ -> true;
		case NumberLiteral _ -> true;
		case StringLiteral _ -> true;
		case TextBlock _ -> true;
		case ParenthesizedExpression parenthesized -> isStableValue(parenthesized.getExpression(), context);
		case SimpleName name -> {
			IBinding binding= name.resolveBinding();
			if (binding == null || binding.isRecovered()
					|| binding instanceof IVariableBinding variable
						&& (variable.getType() == null || variable.getType().isRecovered())) {
				context.markUnknown("isStableValue", "Variable binding unavailable: " + name); //$NON-NLS-1$ //$NON-NLS-2$
				yield false;
			}
			yield binding instanceof IVariableBinding variable && !variable.isField()
					&& Modifier.isFinal(variable.getModifiers());
		}
		default -> false;
		};
	}

	static boolean hasNoSideEffect(Expression expression, GuardContext context) {
		if ((expression.getFlags() & (ASTNode.MALFORMED | ASTNode.RECOVERED)) != 0) {
			return unknown(context, expression);
		}
		return switch (expression) {
		case BooleanLiteral _ -> true;
		case CharacterLiteral _ -> true;
		case NullLiteral _ -> true;
		case NumberLiteral _ -> true;
		case StringLiteral _ -> true;
		case TextBlock _ -> true;
		case ThisExpression _ -> true;
		case TypeLiteral _ -> true;
		case SimpleName name -> safeRead(name.resolveBinding(), context, name);
		case QualifiedName name -> safeRead(name.resolveBinding(), context, name)
				&& hasNoSideEffect(name.getQualifier(), context);
		case FieldAccess field -> safeRead(field.resolveFieldBinding(), context, field)
				&& hasNoSideEffect(field.getExpression(), context);
		case SuperFieldAccess field -> safeRead(field.resolveFieldBinding(), context, field);
		case ParenthesizedExpression parenthesized -> hasNoSideEffect(parenthesized.getExpression(), context);
		case CastExpression cast -> hasNoSideEffect(cast.getExpression(), context);
		case ArrayAccess access -> hasNoSideEffect(access.getArray(), context)
				&& hasNoSideEffect(access.getIndex(), context);
		case PrefixExpression prefix -> prefix.getOperator() != PrefixExpression.Operator.INCREMENT
				&& prefix.getOperator() != PrefixExpression.Operator.DECREMENT
				&& hasNoSideEffect(prefix.getOperand(), context);
		case InfixExpression infix -> safeInfix(infix, context);
		case ConditionalExpression conditional -> hasNoSideEffect(conditional.getExpression(), context)
				&& hasNoSideEffect(conditional.getThenExpression(), context)
				&& hasNoSideEffect(conditional.getElseExpression(), context);
		case InstanceofExpression instanceOf -> hasNoSideEffect(instanceOf.getLeftOperand(), context);
		case PatternInstanceofExpression instanceOf -> {
			boolean typePattern;
			if (instanceOf.getAST().apiLevel() >= AST.JLS21) {
				typePattern= instanceOf.getPattern() instanceof TypePattern;
			} else {
				// Older DOMs expose record patterns as synthetic, unbound declarations.
				IVariableBinding variable= instanceOf.getRightOperand().resolveBinding();
				typePattern= variable != null && !variable.isRecovered();
			}
			if (!typePattern) {
				// Record deconstruction invokes accessors, including in nested patterns.
				yield unknown(context, instanceOf);
			}
			yield hasNoSideEffect(instanceOf.getLeftOperand(), context);
		}
		case Assignment _ -> false;
		case PostfixExpression _ -> false;
		// Calls (including constructors), writes, allocation and unsupported syntax
		// never become proof merely because their outer node is not a method call.
		// Unknown purity must remain unknown under guard negation as well.
		default -> unknown(context, expression);
		};
	}

	private static boolean safeRead(IBinding binding, GuardContext context, ASTNode node) {
		if (binding == null || binding.isRecovered()) {
			return unknown(context, node);
		}
		if (binding instanceof IVariableBinding variable) {
			ITypeBinding type= variable.getType();
			if (type == null || type.isRecovered()) {
				return unknown(context, node);
			}
			if (Modifier.isVolatile(variable.getModifiers())) {
				return false;
			}
			if (Modifier.isStatic(variable.getModifiers()) && variable.getConstantValue() == null) {
				return unknown(context, node);
			}
			return true;
		}
		// Qualified static names also traverse their package/type qualifier. Merely
		// naming that qualifier does not initialize a class.
		return binding.getKind() == IBinding.TYPE || binding.getKind() == IBinding.PACKAGE;
	}

	private static boolean safeInfix(InfixExpression expression, GuardContext context) {
		if (!safeOperand(expression.getLeftOperand(), expression.getOperator(), context)
				|| !safeOperand(expression.getRightOperand(), expression.getOperator(), context)) {
			return false;
		}
		for (Object operand : expression.extendedOperands()) {
			if (!safeOperand((Expression) operand, expression.getOperator(), context)) {
				return false;
			}
		}
		return true;
	}

	private static boolean safeOperand(Expression operand, InfixExpression.Operator operator,
			GuardContext context) {
		if (!hasNoSideEffect(operand, context)) {
			return false;
		}
		if (operator != InfixExpression.Operator.PLUS) {
			return true;
		}
		// String concatenation can invoke an arbitrary Object.toString(), even
		// without an explicit invocation node in the source.
		if (operand instanceof NullLiteral || operand instanceof NumberLiteral
				|| operand instanceof BooleanLiteral || operand instanceof CharacterLiteral
				|| operand instanceof StringLiteral || operand instanceof TextBlock) {
			return true;
		}
		ITypeBinding type= operand.resolveTypeBinding();
		if (type == null || type.isRecovered()) {
			return unknown(context, operand);
		}
		return type.isPrimitive() || "java.lang.String".equals(type.getQualifiedName()) //$NON-NLS-1$
				|| unknown(context, operand);
	}

	private static boolean unknown(GuardContext context, ASTNode node) {
		context.markUnknown("hasNoSideEffect", "Effects could not be proven absent: " + node); //$NON-NLS-1$ //$NON-NLS-2$
		return false;
	}
}
