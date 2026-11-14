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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.sandbox.jdt.container.api.ClosedSourceReturnMigrationPlan.Consumer;
import org.sandbox.jdt.container.api.ClosedSourceReturnMigrationPlan.Range;

/** Binding proof for direct return of an append-array and length-only callers. */
public final class ClosedReturnArrayUses {
	private ClosedReturnArrayUses() {
	}

	public static Optional<Range> producer(CompilationUnit root, String methodKey, String localKey) {
		if (Arrays.stream(root.getProblems()).anyMatch(IProblem::isError)
				|| !(root.findDeclaringNode(methodKey) instanceof MethodDeclaration method)
				|| !(method.getParent() instanceof TypeDeclaration owner) || owner.getParent() != root
				|| !Modifier.isFinal(owner.getModifiers()) || !Modifier.isStatic(method.getModifiers())
				|| Modifier.isPublic(owner.getModifiers()) || Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers())
				|| !owner.superInterfaceTypes().isEmpty() || owner.getSuperclassType() != null
				|| owner.resolveBinding() == null || owner.resolveBinding().getAnnotations().length != 0
				|| !(method.getReturnType2() instanceof ArrayType array) || !method.extraDimensions().isEmpty()
				|| method.resolveBinding() == null || method.resolveBinding().getAnnotations().length != 0
				|| method.resolveBinding().isGenericMethod() || method.getBody() == null
				|| method.getBody().statements().isEmpty() || !(method.getBody().statements().getLast() instanceof ReturnStatement end)
				|| !(end.getExpression() instanceof SimpleName value) || !(value.resolveBinding() instanceof IVariableBinding binding)
				|| binding.getType() == null || !binding.getType().isArray() || binding.getType().getTypeAnnotations().length != 0 || binding.getType().getElementType().getTypeAnnotations().length != 0
				|| !localKey.equals(binding.getVariableDeclaration().getKey()) || !LocalArrayUsageAnalyzer.isFinalArrayPublication(value)
				|| array.resolveBinding().getTypeAnnotations().length != 0 || array.resolveBinding().getElementType().getTypeAnnotations().length != 0) {
			return Optional.empty();
		}
		return Optional.of(range(array));
	}

	/** Inspects every result reference, rejects reflection and unresolved/unsupported uses in this source unit. */
	public static Optional<List<Consumer>> consumers(CompilationUnit root, String unitHandle, String methodKey) {
		if (Arrays.stream(root.getProblems()).anyMatch(IProblem::isError)) {
			return Optional.empty();
		}
		List<Consumer> result= new ArrayList<>();
		boolean[] valid= { true };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				if (Modifier.isNative(method.getModifiers())) {
					valid[0]= false;
				}
				return valid[0];
			}

			@Override
			public boolean visit(SimpleName name) {
				if (!(name.resolveBinding() instanceof IMethodBinding resolved) || resolved.isRecovered()) {
					return true;
				}
				var method= resolved.getMethodDeclaration();
				String owner= method.getDeclaringClass().getErasure().getQualifiedName();
				if ("java.lang.Class".equals(owner) || owner.startsWith("java.lang.reflect.") //$NON-NLS-1$ //$NON-NLS-2$
						|| owner.startsWith("java.lang.invoke.")) { //$NON-NLS-1$
					valid[0]= false;
					return false;
				}
				if (!methodKey.equals(method.getKey()) || name.getParent() instanceof MethodDeclaration) {
					return true;
				}
				if (!(name.getParent() instanceof MethodInvocation call) || call.getName() != name
						|| !(call.getParent() instanceof VariableDeclarationFragment fragment) || fragment.getInitializer() != call
						|| !(fragment.getParent() instanceof VariableDeclarationStatement declaration) || declaration.fragments().size() != 1
						|| !fragment.extraDimensions().isEmpty() || !(declaration.getType() instanceof ArrayType type)
						|| fragment.resolveBinding() == null || !method.getReturnType().isEqualTo(fragment.resolveBinding().getType())
						|| type.resolveBinding().getTypeAnnotations().length != 0 || type.resolveBinding().getElementType().getTypeAnnotations().length != 0) {
					valid[0]= false;
					return false;
				}
				List<Range> lengths= new ArrayList<>();
				String key= fragment.resolveBinding().getVariableDeclaration().getKey();
				// Locals have lexical scope within one method; inspect nested captures as well.
				declaration.getParent().accept(new ASTVisitor() {
					@Override
					public boolean visit(SimpleName reference) {
						if (reference == fragment.getName() || !(reference.resolveBinding() instanceof IVariableBinding used)
								|| !key.equals(used.getVariableDeclaration().getKey())) {
							return true;
						}
						if (reference.getParent() instanceof QualifiedName length && length.getQualifier() == reference
								&& "length".equals(length.getName().getIdentifier())) { //$NON-NLS-1$
							lengths.add(range(length));
						} else {
							valid[0]= false;
						}
						return true;
					}
				});
				result.add(new Consumer(unitHandle, key, range(type), range(call), lengths));
				return true;
			}
		});
		return valid[0] ? Optional.of(List.copyOf(result)) : Optional.empty();
	}

	private static Range range(ASTNode node) {
		return new Range(node.getStartPosition(), node.getLength());
	}
}
