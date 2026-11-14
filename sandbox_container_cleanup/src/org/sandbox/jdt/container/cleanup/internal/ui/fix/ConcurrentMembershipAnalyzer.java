/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.ui.fix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTVisitor;
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
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/** Binding proof for the individual add, remove and contains operations on a private JDK set. */
final class ConcurrentMembershipAnalyzer {
	private ConcurrentMembershipAnalyzer() {
	}

	record Use(String operation, int offset) {}
	record Field(String key, int initializerOffset, List<Use> uses, String rejection) {
		Field {
			uses= List.copyOf(uses);
		}
	}

	private static final Set<String> OPERATIONS= Set.of("add", "remove", "contains"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	static Map<String, Field> analyze(CompilationUnit unit) {
		Map<String, VariableDeclarationFragment> seeds= new LinkedHashMap<>();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment fragment) {
				if (isSeed(fragment)) {
					seeds.put(fragment.resolveBinding().getVariableDeclaration().getKey(), fragment);
				}
				return true;
			}
		});
		Map<String, Field> results= new LinkedHashMap<>();
		for (var entry : seeds.entrySet()) {
			var uses= new ArrayList<Use>();
			String[] rejection= { "" }; //$NON-NLS-1$
			var seed= entry.getValue();
			ITypeBinding owner= seed.resolveBinding().getDeclaringClass();
			if (!(seed.getParent().getParent() instanceof TypeDeclaration type)
					|| !type.isPackageMemberTypeDeclaration() || !Modifier.isFinal(owner.getModifiers())
					|| owner.getAnnotations().length != 0 || owner.getInterfaces().length != 0 || !binaryType(owner.getSuperclass(), "java.lang.Object")) { //$NON-NLS-1$
				rejection[0]= "The owner must be a final top-level class without serialization or inherited protocols."; //$NON-NLS-1$
			}
			for (IProblem problem : unit.getProblems()) {
				if (problem.isError()) {
					rejection[0]= "Resolve the compilation errors before analyzing all field references."; //$NON-NLS-1$
					break;
				}
			}
			if (rejection[0].isEmpty()) {
				unit.accept(new ASTVisitor() {
					@Override
					public boolean visit(SimpleName name) {
						if (!name.isDeclaration() && name.resolveBinding() instanceof IVariableBinding binding
								&& entry.getKey().equals(binding.getVariableDeclaration().getKey())) {
							MethodInvocation operation= membershipOperation(name);
							if (operation == null) {
								rejection[0]= "A field access escapes, observes iteration or locking, or lacks a non-null String key."; //$NON-NLS-1$
							} else {
								uses.add(new Use(operation.getName().getIdentifier(), name.getStartPosition()));
							}
						}
						return true;
					}
				});
			}
			if (rejection[0].isEmpty() && uses.stream().noneMatch(use -> "add".equals(use.operation()))) { //$NON-NLS-1$
				rejection[0]= "No atomic membership insertion was found."; //$NON-NLS-1$
			}
			results.put(entry.getKey(), new Field(entry.getKey(), seed.getInitializer().getStartPosition(), uses, rejection[0]));
		}
		return results;
	}

	private static boolean isSeed(VariableDeclarationFragment field) {
		IVariableBinding binding= field.resolveBinding();
		if (binding == null || binding.isRecovered() || !binding.isField() || binding.getKey() == null
				|| !Modifier.isPrivate(binding.getModifiers()) || !Modifier.isFinal(binding.getModifiers())
				|| Modifier.isStatic(binding.getModifiers()) || !binaryType(binding.getType(), "java.util.Set") //$NON-NLS-1$
				|| binding.getAnnotations().length != 0 || binding.getType().getTypeArguments().length != 1
				|| !binaryType(binding.getType().getTypeArguments()[0], "java.lang.String") //$NON-NLS-1$
				|| !(field.getInitializer() instanceof MethodInvocation factory)
				|| !jdkMethod(factory, "java.util.Collections", "synchronizedSet") || factory.arguments().size() != 1 //$NON-NLS-1$ //$NON-NLS-2$
				|| !(factory.arguments().getFirst() instanceof ClassInstanceCreation fresh)
				|| !fresh.arguments().isEmpty() || fresh.getAnonymousClassDeclaration() != null) {
			return false;
		}
		var constructor= fresh.resolveConstructorBinding();
		return constructor != null && !constructor.isRecovered() && binaryType(fresh.resolveTypeBinding(), "java.util.HashSet"); //$NON-NLS-1$
	}

	private static MethodInvocation membershipOperation(SimpleName fieldName) {
		Expression receiver= fieldName;
		if (fieldName.getParent() instanceof FieldAccess access && access.getName() == fieldName) {
			receiver= access;
		}
		if (!(receiver.getParent() instanceof MethodInvocation operation) || operation.getExpression() != receiver
				|| !OPERATIONS.contains(operation.getName().getIdentifier()) || operation.arguments().size() != 1
				|| !jdkMethod(operation, "java.util.Set", operation.getName().getIdentifier())) { //$NON-NLS-1$
			return null;
		}
		Expression key= (Expression) operation.arguments().getFirst();
		if (!binaryType(key.resolveTypeBinding(), "java.lang.String")) { //$NON-NLS-1$
			return null;
		}
		if (key instanceof StringLiteral || key instanceof MethodInvocation guard
				&& guard.arguments().size() == 1 && jdkMethod(guard, "java.util.Objects", "requireNonNull")) { //$NON-NLS-1$ //$NON-NLS-2$
			return operation;
		}
		return null;
	}

	private static boolean jdkMethod(MethodInvocation call, String owner, String name) {
		IMethodBinding binding= call.resolveMethodBinding();
		return binding != null && !binding.isRecovered() && name.equals(binding.getName())
				&& binaryType(binding.getMethodDeclaration().getDeclaringClass(), owner);
	}

	private static boolean binaryType(ITypeBinding type, String name) {
		return type != null && !type.isRecovered() && !type.isFromSource()
				&& name.equals(type.getErasure().getQualifiedName());
	}
}
