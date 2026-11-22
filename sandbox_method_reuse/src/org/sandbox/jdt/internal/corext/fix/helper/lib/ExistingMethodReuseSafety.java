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
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/** Preconditions which structural matching alone cannot establish for a new call. */
public final class ExistingMethodReuseSafety {

	private ExistingMethodReuseSafety() {
	}

	public static boolean canReuse(MethodDeclaration target, MethodDeclaration caller,
			List<Statement> statements, VariableMapping mapping) {
		IMethodBinding method= target.resolveBinding();
		IMethodBinding context= caller.resolveBinding();
		if (method == null || context == null || method.isRecovered() || context.isRecovered()
				|| method.isConstructor() || method.isGenericMethod()
				|| !method.getDeclaringClass().isEqualTo(context.getDeclaringClass())
				|| Modifier.isSynchronized(method.getModifiers())
				|| !MethodCallReplacer.canCreateMethodCall(target, mapping)) {
			return false;
		}
		int modifiers= method.getModifiers();
		if ((!Modifier.isStatic(modifiers) && Modifier.isStatic(context.getModifiers()))
				|| !(Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers)
						|| Modifier.isFinal(modifiers) || Modifier.isFinal(method.getDeclaringClass().getModifiers()))) {
			// Calling an overridable method can execute a subclass body instead of this sequence.
			return false;
		}
		for (ITypeBinding exception : method.getExceptionTypes()) {
			if (!isUnchecked(exception) && !isDeclared(exception, context.getExceptionTypes())) {
				return false;
			}
		}
		Statement last= statements.get(statements.size() - 1);
		ITypeBinding resultType= method.getReturnType();
		boolean returnsValue= !"void".equals(resultType.getName()); //$NON-NLS-1$
		if (returnsValue && !(target.getBody().statements().getLast() instanceof ReturnStatement result
				&& result.getExpression() != null)) {
			return false;
		}
		if (last instanceof ReturnStatement result && result.getExpression() != null) {
			if (!resultType.isAssignmentCompatible(context.getReturnType())) {
				return false;
			}
		} else if (last instanceof VariableDeclarationStatement declaration
				&& target.getBody().statements().getLast() instanceof ReturnStatement) {
			VariableDeclarationFragment fragment= (VariableDeclarationFragment) declaration.fragments().get(0);
			if (fragment.resolveBinding() == null
					|| !resultType.isAssignmentCompatible(fragment.resolveBinding().getType())) {
				return false;
			}
		}
		return preservesControlFlowAndInputs(target)
				&& preservesLocalScope(caller, statements, returnsValue && last instanceof VariableDeclarationStatement);
	}

	private static boolean isUnchecked(ITypeBinding exception) {
		for (ITypeBinding current= exception; current != null; current= current.getSuperclass()) {
			if ("java.lang.RuntimeException".equals(current.getQualifiedName()) //$NON-NLS-1$
					|| "java.lang.Error".equals(current.getQualifiedName())) { //$NON-NLS-1$
				return true;
			}
		}
		return false;
	}

	private static boolean isDeclared(ITypeBinding exception, ITypeBinding[] declared) {
		for (ITypeBinding type : declared) {
			if (exception.isAssignmentCompatible(type)) {
				return true;
			}
		}
		return false;
	}

	private static boolean preservesControlFlowAndInputs(MethodDeclaration target) {
		ASTNode terminal= (ASTNode) target.getBody().statements().getLast();
		boolean[] safe= { true };
		target.getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(ReturnStatement node) {
				// An early return moved into the callee would no longer exit the caller.
				safe[0] &= node == terminal;
				return true;
			}

			@Override
			public boolean visit(SimpleName name) {
				if (name.resolveBinding() instanceof IVariableBinding variable && variable.isParameter()
						&& isWritten(name)) {
					// A callee can modify only its copy of a caller's local value.
					safe[0]= false;
				}
				return true;
			}
		});
		return safe[0];
	}

	private static boolean isWritten(SimpleName name) {
		ASTNode variable= name;
		while (variable.getParent() instanceof ParenthesizedExpression parentheses) {
			variable= parentheses;
		}
		ASTNode parent= variable.getParent();
		return parent instanceof Assignment assignment && assignment.getLeftHandSide() == variable
				|| parent instanceof PostfixExpression
				|| parent instanceof PrefixExpression prefix
						&& (prefix.getOperator() == PrefixExpression.Operator.INCREMENT
								|| prefix.getOperator() == PrefixExpression.Operator.DECREMENT);
	}

	private static boolean preservesLocalScope(MethodDeclaration caller, List<Statement> statements, boolean retainResult) {
		Statement first= statements.get(0);
		Statement last= statements.get(statements.size() - 1);
		int start= first.getStartPosition();
		int end= last.getStartPosition() + last.getLength();
		Set<String> removed= new HashSet<>();
		for (Statement statement : statements) {
			statement.accept(new ASTVisitor() {
				@Override
				public boolean visit(VariableDeclarationFragment fragment) {
					// The final result declaration is retained by the rewrite.
					if ((!retainResult || fragment.getParent() != last) && fragment.resolveBinding() != null) {
						removed.add(fragment.resolveBinding().getKey());
					}
					return true;
				}
			});
		}
		boolean[] safe= { true };
		caller.getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if ((name.getStartPosition() < start || name.getStartPosition() >= end)
						&& name.resolveBinding() instanceof IVariableBinding variable
						&& removed.contains(variable.getKey())) {
					safe[0]= false;
				}
				return true;
			}
		});
		return safe[0];
	}
}
