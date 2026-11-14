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

import static org.sandbox.jdt.container.analysis.ContainerAstFacts.unwrap;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.variableBinding;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/** Source-binding facts shared by synchronized-list iteration and drain observations. */
final class SynchronizedListFieldFacts {
	private static final String LIST= "java.util.List"; //$NON-NLS-1$

	private SynchronizedListFieldFacts() {
	}

	static VariableDeclarationFragment synchronizedListField(CompilationUnit unit, Expression expression) {
		IVariableBinding field= currentInstanceField(expression);
		if (field == null || !Modifier.isPrivate(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())
				|| field.getKey() == null || field.getKey().isBlank()
				|| !(unit.findDeclaringNode(field) instanceof VariableDeclarationFragment declaration)
				|| declaration.getInitializer() == null || !isSynchronizedListInitializer(declaration.getInitializer())) {
			return null;
		}
		return declaration;
	}

	private static boolean isSynchronizedListInitializer(Expression initializer) {
		if (!(unwrap(initializer) instanceof MethodInvocation invocation)
				|| invocation.arguments().size() != 1
				|| !(unwrap((Expression) invocation.arguments().getFirst()) instanceof ClassInstanceCreation backing)
				|| backing.getAnonymousClassDeclaration() != null || !backing.arguments().isEmpty()) {
			return false;
		}
		IMethodBinding method= invocation.resolveMethodBinding();
		if (method == null || method.isRecovered()) {
			return false;
		}
		IMethodBinding declaration= method.getMethodDeclaration();
		ITypeBinding[] parameters= declaration.getParameterTypes();
		IMethodBinding constructor= backing.resolveConstructorBinding();
		return parameters.length == 1 && constructor != null
				&& !declaration.isRecovered() && Modifier.isStatic(declaration.getModifiers())
				&& "synchronizedList".equals(declaration.getName()) //$NON-NLS-1$
				&& isType(declaration.getDeclaringClass(), "java.util.Collections") //$NON-NLS-1$
				&& isType(parameters[0], LIST) && isType(declaration.getReturnType(), LIST)
				&& !constructor.isRecovered()
				&& isType(constructor.getDeclaringClass(), "java.util.ArrayList"); //$NON-NLS-1$
	}

	static boolean isType(ITypeBinding type, String qualifiedName) {
		return type != null && !type.isRecovered() && !type.isFromSource()
				&& qualifiedName.equals(type.getErasure().getQualifiedName());
	}

	static IVariableBinding currentInstanceField(Expression expression) {
		Expression reference= unwrap(expression);
		if (!(reference instanceof SimpleName)
				&& !(reference instanceof FieldAccess access && unwrap(access.getExpression()) instanceof ThisExpression self
						&& self.getQualifier() == null)) {
			return null;
		}
		IVariableBinding field= variableBinding(reference).orElse(null);
		if (field == null || field.isRecovered() || !field.isField() || Modifier.isStatic(field.getModifiers())) {
			return null;
		}
		IVariableBinding declaration= field.getVariableDeclaration();
		ITypeBinding receiver= enclosingType(reference);
		return !declaration.isRecovered() && receiver != null && !receiver.isRecovered()
				&& receiver.getTypeDeclaration().isEqualTo(declaration.getDeclaringClass()) ? declaration : null;
	}

	private static ITypeBinding enclosingType(ASTNode reference) {
		for (ASTNode parent= reference.getParent(); parent != null; parent= parent.getParent()) {
			if (parent instanceof AbstractTypeDeclaration type) {
				return type.resolveBinding();
			}
			if (parent instanceof AnonymousClassDeclaration type) {
				return type.resolveBinding();
			}
		}
		return null;
	}

}
