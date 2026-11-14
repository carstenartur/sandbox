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

import java.util.Arrays;
import java.util.Optional;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;
import org.sandbox.jdt.container.api.LosslessParameterBridgePlan;

/** Java overload resolution, signature and behavioral proof for an array-length bridge. */
public final class LosslessArrayLengthBridge {
	private LosslessArrayLengthBridge() {
	}

	public static Optional<LosslessParameterBridgePlan> prove(CompilationUnit root, String source,
			ClosedSourceParameterMigrationPlan migration) {
		if (migration.parameterPlans().size() != 1 || Arrays.stream(root.getProblems()).anyMatch(IProblem::isError)) {
			return Optional.empty();
		}
		var parameter= migration.parameterPlans().getFirst();
		if (!parameter.ownedFieldBindingKey().isEmpty() || parameter.parameterIndex() != 0
				|| !(root.findDeclaringNode(parameter.parameterBindingKey()) instanceof SingleVariableDeclaration declaration)
				|| !(declaration.getParent() instanceof MethodDeclaration method) || !hasEligibleOwner(root, method)) {
			return Optional.empty();
		}
		var binding= method.resolveBinding();
		if (!hasEligibleMethod(method, binding) || !hasDirectLengthReturn(method, declaration, binding)
				|| !hasEligibleArray(declaration) || hasCompetingOverload(binding)) {
			return Optional.empty();
		}
		return Optional.of(new LosslessParameterBridgePlan(migration, binding.getMethodDeclaration().getKey(),
				source.substring(method.getStartPosition(), method.getStartPosition() + method.getLength())));
	}

	private static boolean hasEligibleOwner(CompilationUnit root, MethodDeclaration method) {
		if (method.parameters().size() != 1 || !(method.getParent() instanceof TypeDeclaration owner)) {
			return false;
		}
		return owner.getParent() == root && Modifier.isFinal(owner.getModifiers()) && !Modifier.isPublic(owner.getModifiers())
				&& owner.getSuperclassType() == null && owner.superInterfaceTypes().isEmpty()
				&& owner.typeParameters().isEmpty() && !owner.isInterface();
	}

	private static boolean hasEligibleMethod(MethodDeclaration method, IMethodBinding binding) {
		if (binding == null || binding.isRecovered()) {
			return false;
		}
		int flags= method.getModifiers();
		return Modifier.isStatic(flags) && !Modifier.isPublic(flags) && !Modifier.isProtected(flags)
				&& !Modifier.isSynchronized(flags) && !method.isConstructor() && !binding.isVarargs()
				&& !binding.isGenericMethod() && binding.getAnnotations().length == 0
				&& binding.getDeclaringClass().getAnnotations().length == 0
				&& binding.getDeclaringClass().getSuperclass() != null;
	}

	private static boolean hasDirectLengthReturn(MethodDeclaration method, SingleVariableDeclaration declaration,
			IMethodBinding binding) {
		if (!method.thrownExceptionTypes().isEmpty() || declaration.resolveBinding() == null
				|| !declaration.modifiers().isEmpty() || !method.extraDimensions().isEmpty()
				|| !declaration.extraDimensions().isEmpty() || !"int".equals(binding.getReturnType().getName()) //$NON-NLS-1$
				|| method.getBody() == null || method.getBody().statements().size() != 1
				|| !(method.getBody().statements().getFirst() instanceof ReturnStatement ret)
				|| !(ret.getExpression() instanceof QualifiedName length) || !"length".equals(length.getName().getIdentifier()) //$NON-NLS-1$
				|| !(length.getQualifier() instanceof SimpleName local)) {
			return false;
		}
		return declaration.resolveBinding().isEqualTo(local.resolveBinding());
	}

	private static boolean hasEligibleArray(SingleVariableDeclaration declaration) {
		var array= declaration.resolveBinding().getType();
		return array.isArray() && array.getDimensions() == 1 && array.getTypeAnnotations().length == 0
				&& array.getElementType().getTypeAnnotations().length == 0
				&& !array.getElementType().isParameterizedType() && !array.getElementType().isTypeVariable()
				&& !array.getElementType().isPrimitive();
	}

	private static boolean hasCompetingOverload(IMethodBinding binding) {
		// Excluding every other same-name member rejects erasure collisions and competing overloads.
		for (IMethodBinding other : binding.getDeclaringClass().getDeclaredMethods()) {
			if (other.getName().equals(binding.getName()) && !other.getMethodDeclaration().isEqualTo(binding.getMethodDeclaration())) {
				return true;
			}
		}
		for (IMethodBinding inherited : binding.getDeclaringClass().getSuperclass().getDeclaredMethods()) {
			if (inherited.getName().equals(binding.getName())) {
				return true;
			}
		}
		return false;
	}

	/** Any existing untyped null call or reflective/native consumer prevents retaining the extra overload. */
	public static boolean hasSafeCallers(CompilationUnit root, String methodKey) {
		if (Arrays.stream(root.getProblems()).anyMatch(IProblem::isError)) {
			return false;
		}
		boolean[] valid= { true };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration declaration) {
				if (Modifier.isNative(declaration.getModifiers())) {
					valid[0]= false;
				}
				return valid[0];
			}

			@Override
			public boolean visit(SimpleName name) {
				if (!(name.resolveBinding() instanceof IMethodBinding resolved) || resolved.isRecovered()) {
					return true;
				}
				var called= resolved.getMethodDeclaration();
				String owner= called.getDeclaringClass().getErasure().getQualifiedName();
				if ("java.lang.Class".equals(owner) || owner.startsWith("java.lang.reflect.") || owner.startsWith("java.lang.invoke.")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					valid[0]= false;
				} else if (methodKey.equals(called.getKey()) && !(name.getParent() instanceof MethodDeclaration)) {
					// The shared closed-source component already proves the exact array transfer.
					if (!(name.getParent() instanceof MethodInvocation call) || call.getName() != name
							|| call.arguments().size() != 1 || !(call.arguments().getFirst() instanceof SimpleName argument)
							|| !(argument.resolveBinding() instanceof IVariableBinding value) || !value.getType().isArray()) {
						valid[0]= false;
					}
				}
				return valid[0];
			}
		});
		return valid[0];
	}
}
