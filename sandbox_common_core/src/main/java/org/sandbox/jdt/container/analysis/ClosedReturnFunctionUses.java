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
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.sandbox.jdt.container.api.ClosedSourceReturnMigrationPlan.FunctionTarget;
import org.sandbox.jdt.container.api.ClosedSourceReturnMigrationPlan.Range;

/** Proves all calls of one local, precisely typed Function forwarding to an array producer. */
record ClosedReturnFunctionUses(FunctionTarget target, List<MethodInvocation> calls) {
	private static final String FUNCTION= "java.util.function.Function"; //$NON-NLS-1$

	/** The expression is the resolved producer invocation or method reference. */
	static Optional<ClosedReturnFunctionUses> find(Expression expression, IMethodBinding method) {
		if (method == null || method.isRecovered() || !Modifier.isStatic(method.getModifiers()) || method.isGenericMethod() || method.isVarargs()
				|| method.getParameterTypes().length != 1 || !method.getReturnType().isArray()) {
			return Optional.empty();
		}
		Expression initializer= forwardingInitializer(expression, method);
		if (initializer == null) {
			return Optional.empty();
		}
		if (!(initializer.getParent() instanceof VariableDeclarationFragment fragment) || fragment.getInitializer() != initializer
				|| !(fragment.getParent() instanceof VariableDeclarationStatement declaration) || declaration.fragments().size() != 1
				|| !(declaration.getType() instanceof ParameterizedType type) || type.typeArguments().size() != 2
				|| !(type.typeArguments().get(1) instanceof ArrayType array) || array.resolveBinding() == null
				|| !method.getReturnType().isEqualTo(array.resolveBinding()) || array.resolveBinding().getTypeAnnotations().length != 0
				|| array.resolveBinding().getElementType().getTypeAnnotations().length != 0 || fragment.resolveBinding() == null) {
			return Optional.empty();
		}
		var function= fragment.resolveBinding();
		var typeBinding= function.getType();
		if (!FUNCTION.equals(typeBinding.getErasure().getQualifiedName()) || typeBinding.getTypeArguments().length != 2
				|| !typeBinding.getTypeArguments()[0].isEqualTo(method.getParameterTypes()[0])
				|| !typeBinding.getTypeArguments()[1].isEqualTo(method.getReturnType())) {
			return Optional.empty();
		}
		for (IMethodBinding declared : method.getDeclaringClass().getDeclaredMethods()) {
			// Changing the function result type can change overload selection at a method reference.
			if (method.getName().equals(declared.getName()) && !method.getMethodDeclaration().isEqualTo(declared.getMethodDeclaration())) {
				return Optional.empty();
			}
		}
		List<MethodInvocation> calls= applications(declaration, fragment, method);
		if (calls.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new ClosedReturnFunctionUses(new FunctionTarget(function.getKey(),
				new Range(array.getStartPosition(), array.getLength()), new Range(initializer.getStartPosition(), initializer.getLength())), List.copyOf(calls)));
	}

	private static Expression forwardingInitializer(Expression expression, IMethodBinding method) {
		if (expression instanceof MethodInvocation invocation) {
			if (invocation.getExpression() != null && (!(invocation.getExpression() instanceof Name qualifier)
					|| !(qualifier.resolveBinding() instanceof ITypeBinding owner) || !owner.isEqualTo(method.getDeclaringClass()))) {
				return null;
			}
			if (!(invocation.getParent() instanceof LambdaExpression lambda) || lambda.getBody() != invocation
					|| lambda.parameters().size() != 1 || !(lambda.parameters().getFirst() instanceof VariableDeclarationFragment parameter)
					|| invocation.arguments().size() != 1 || !(invocation.arguments().getFirst() instanceof SimpleName argument)
					|| parameter.resolveBinding() == null || !parameter.resolveBinding().isEqualTo(argument.resolveBinding())) {
				return null;
			}
			return lambda;
		} else if (expression instanceof MethodReference) {
			ITypeBinding qualifier= null;
			if (expression instanceof ExpressionMethodReference named && named.getExpression() instanceof Name name
					&& name.resolveBinding() instanceof ITypeBinding resolved) {
				qualifier= resolved;
			} else if (expression instanceof TypeMethodReference typed) {
				qualifier= typed.getType().resolveBinding();
			}
			return method.getDeclaringClass().isEqualTo(qualifier) ? expression : null;
		}
		return null;
	}

	private static List<MethodInvocation> applications(VariableDeclarationStatement declaration, VariableDeclarationFragment fragment,
			IMethodBinding method) {
		var function= fragment.resolveBinding();
		List<MethodInvocation> calls= new ArrayList<>();
		boolean[] closed= { true };
		declaration.getParent().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (name == fragment.getName() || !(name.resolveBinding() instanceof IVariableBinding binding)
						|| !binding.getVariableDeclaration().isEqualTo(function)) {
					return true;
				}
				if (name.getParent() instanceof MethodInvocation call && call.getExpression() == name && applies(call, method)) {
					calls.add(call);
				} else {
					closed[0]= false;
				}
				return true;
			}
		});
		return closed[0] ? calls : List.of();
	}

	private static boolean applies(MethodInvocation call, IMethodBinding producer) {
		IMethodBinding invoked= call.resolveMethodBinding();
		return invoked != null && !invoked.isRecovered() && "apply".equals(invoked.getName()) //$NON-NLS-1$
				&& FUNCTION.equals(invoked.getDeclaringClass().getErasure().getQualifiedName())
				&& producer.getReturnType().isEqualTo(invoked.getReturnType());
	}
}
