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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/** Proves the list's complete private registration and snapshot-notification protocol. */
final class ListenerSnapshotAnalyzer {
	private ListenerSnapshotAnalyzer() {
	}

	record Plan(String key, int initializer, List<Integer> registrationLocks, List<Integer> snapshotLocks, String rejection) {
		Plan {
			registrationLocks= List.copyOf(registrationLocks);
			snapshotLocks= List.copyOf(snapshotLocks);
		}
	}
	record Notification(VariableDeclarationStatement array, SynchronizedStatement lock, EnhancedForStatement loop) {}
	record Observed(Plan plan, VariableDeclarationFragment field, List<SynchronizedStatement> registrations,
			List<Notification> notifications) {}

	static Map<String, Observed> analyze(CompilationUnit root) {
		Map<String, Observed> result= new LinkedHashMap<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment field) {
				if (isCandidate(field)) {
					Observed protocol= inspect(root, field);
					if (!protocol.notifications().isEmpty() || !protocol.registrations().isEmpty()) {
						result.put(protocol.plan().key(), protocol);
					}
				}
				return true;
			}
		});
		return result;
	}

	private static boolean isCandidate(VariableDeclarationFragment field) {
		var binding= field.resolveBinding();
		if (binding == null || binding.isRecovered() || !binding.isField() || binding.getKey() == null
				|| !Modifier.isPrivate(binding.getModifiers()) || !Modifier.isFinal(binding.getModifiers())
				|| Modifier.isStatic(binding.getModifiers()) || binding.getAnnotations().length != 0
				|| !jdkType(binding.getType(), "java.util.List") || binding.getType().getTypeArguments().length != 1 //$NON-NLS-1$
				|| !jdkType(binding.getType().getTypeArguments()[0], "java.lang.Runnable") //$NON-NLS-1$
				|| !(field.getInitializer() instanceof ClassInstanceCreation creation)
				|| creation.getAnonymousClassDeclaration() != null || !creation.arguments().isEmpty()
				|| !jdkType(creation.resolveTypeBinding(), "java.util.ArrayList")) { //$NON-NLS-1$
			return false;
		}
		var owner= binding.getDeclaringClass();
		return field.getParent().getParent() instanceof TypeDeclaration type && type.isPackageMemberTypeDeclaration()
				&& owner != null && !owner.isRecovered() && Modifier.isFinal(owner.getModifiers())
				&& owner.getAnnotations().length == 0 && owner.getInterfaces().length == 0
				&& jdkType(owner.getSuperclass(), "java.lang.Object"); //$NON-NLS-1$
	}

	private static Observed inspect(CompilationUnit root, VariableDeclarationFragment field) {
		var binding= field.resolveBinding();
		Set<SimpleName> references= new HashSet<>();
		Set<SimpleName> covered= new HashSet<>();
		List<SynchronizedStatement> registrations= new ArrayList<>();
		List<Notification> notifications= new ArrayList<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (!name.isDeclaration() && refers(name, binding)) {
					references.add(name);
				}
				return true;
			}
			@Override
			public boolean visit(SynchronizedStatement lock) {
				if (!(lock.getExpression() instanceof SimpleName monitor) || !refers(monitor, binding)
						|| !(lock.getParent() instanceof Block methodBody)
						|| !(methodBody.getParent() instanceof MethodDeclaration method) || method.isConstructor()
						|| method.getParent() != field.getParent().getParent() || Modifier.isSynchronized(method.getModifiers())
						|| lock.getBody().statements().size() != 1
						|| !(lock.getBody().statements().getFirst() instanceof ExpressionStatement statement)) {
					return true;
				}
				if (methodBody.statements().size() == 1 && method.parameters().size() == 1
						&& statement.getExpression() instanceof MethodInvocation add && call(add, binding, "add") //$NON-NLS-1$
						&& add.arguments().size() == 1 && add.arguments().getFirst() instanceof SimpleName value
						&& refers(value, ((SingleVariableDeclaration) method.parameters().getFirst()).resolveBinding())) {
					registrations.add(lock);
					covered.add(monitor);
					covered.add((SimpleName) add.getExpression());
				} else if (statement.getExpression() instanceof Assignment assign && assign.getOperator() == Assignment.Operator.ASSIGN
						&& assign.getRightHandSide() instanceof MethodInvocation copy && call(copy, binding, "toArray")) { //$NON-NLS-1$
					Notification notification= notification(methodBody, lock, assign, copy);
					if (notification != null) {
						notifications.add(notification);
						covered.add(monitor);
						covered.add((SimpleName) copy.getExpression());
					}
				}
				return true;
			}
		});
		String rejection= ""; //$NON-NLS-1$
		if (registrations.isEmpty() || notifications.isEmpty() || !references.equals(covered)) {
			rejection= "Registration and notification must account for every field reference, including monitors and escapes."; //$NON-NLS-1$
		}
		for (IProblem problem : root.getProblems()) {
			if (problem.isError()) {
				rejection= "Resolve compilation errors before proving listener snapshot semantics."; //$NON-NLS-1$
				break;
			}
		}
		var plan= new Plan(binding.getKey(), field.getInitializer().getStartPosition(),
				registrations.stream().map(ASTNode::getStartPosition).toList(),
				notifications.stream().map(n -> n.lock().getStartPosition()).toList(), rejection);
		return new Observed(plan, field, registrations, notifications);
	}

	private static Notification notification(Block body, SynchronizedStatement lock, Assignment assign, MethodInvocation copy) {
		if (body.statements().size() != 3 || body.statements().get(1) != lock
				|| !(body.statements().getFirst() instanceof VariableDeclarationStatement array) || array.fragments().size() != 1
				|| !(array.fragments().getFirst() instanceof VariableDeclarationFragment local) || local.getInitializer() != null
				|| !runnableArray(local.resolveBinding()) || !(assign.getLeftHandSide() instanceof SimpleName target)
				|| !refers(target, local.resolveBinding()) || copy.arguments().size() != 1
				|| !(copy.arguments().getFirst() instanceof ArrayCreation fresh)
				|| fresh.dimensions().size() != 1 || fresh.getInitializer() != null
				|| !(fresh.dimensions().getFirst() instanceof NumberLiteral size) || !"0".equals(size.getToken()) //$NON-NLS-1$
				|| !local.resolveBinding().getType().isEqualTo(fresh.resolveTypeBinding())
				|| !(body.statements().getLast() instanceof EnhancedForStatement loop)
				|| !(loop.getExpression() instanceof SimpleName sequence) || !refers(sequence, local.resolveBinding())
				|| !jdkType(loop.getParameter().getType().resolveBinding(), "java.lang.Runnable")) { //$NON-NLS-1$
			return null;
		}
		Statement action= loop.getBody();
		if (action instanceof Block block && block.statements().size() == 1) {
			action= (Statement) block.statements().getFirst();
		}
		if (!(action instanceof ExpressionStatement expression) || !(expression.getExpression() instanceof MethodInvocation run)
				|| !run.arguments().isEmpty() || !"run".equals(run.getName().getIdentifier()) //$NON-NLS-1$
				|| !(run.getExpression() instanceof SimpleName listener) || !refers(listener, loop.getParameter().resolveBinding())
				|| run.resolveMethodBinding() == null || !jdkType(run.resolveMethodBinding().getDeclaringClass(), "java.lang.Runnable")) { //$NON-NLS-1$
			return null;
		}
		return new Notification(array, lock, loop);
	}

	private static boolean runnableArray(IVariableBinding binding) {
		return binding != null && !binding.isRecovered() && binding.getType().isArray()
				&& binding.getType().getDimensions() == 1 && jdkType(binding.getType().getElementType(), "java.lang.Runnable"); //$NON-NLS-1$
	}

	private static boolean call(MethodInvocation call, IVariableBinding field, String name) {
		var binding= call.resolveMethodBinding();
		return binding != null && !binding.isRecovered() && name.equals(binding.getName())
				&& jdkType(binding.getMethodDeclaration().getDeclaringClass(), "java.util.List") //$NON-NLS-1$
				&& call.getExpression() instanceof SimpleName receiver && refers(receiver, field);
	}

	private static boolean refers(SimpleName name, IVariableBinding declaration) {
		return declaration != null && name.resolveBinding() instanceof IVariableBinding binding && !binding.isRecovered()
				&& declaration.getVariableDeclaration().isEqualTo(binding.getVariableDeclaration());
	}

	private static boolean jdkType(ITypeBinding type, String name) {
		return type != null && !type.isRecovered() && !type.isFromSource() && name.equals(type.getErasure().getQualifiedName());
	}
}
