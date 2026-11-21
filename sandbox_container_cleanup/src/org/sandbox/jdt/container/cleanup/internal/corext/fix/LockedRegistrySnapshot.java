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
package org.sandbox.jdt.container.cleanup.internal.corext.fix;

import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequenceFlow.exact;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequenceFlow.plain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.container.analysis.UniqueSequencePattern;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequenceFlow.Edit;
import org.sandbox.jdt.container.cleanup.internal.corext.fix.ClosedUniqueSequenceFlow.Kind;
import org.sandbox.jdt.internal.common.ImportNameBindings;

/** Closes the registry field before passing its detached, ordered snapshots to the signature planner. */
final class LockedRegistrySnapshot {
	private LockedRegistrySnapshot() {}

	static Optional<List<Edit>> edits(MethodDeclaration method) {
		if (method.getBody() == null || method.getBody().statements().size() != 1
				|| !(method.getBody().statements().getFirst() instanceof SynchronizedStatement lock)
				|| !(lock.getExpression() instanceof SimpleName monitor) || lock.getBody().statements().size() != 1
				|| !(lock.getBody().statements().getFirst() instanceof ReturnStatement returned)
				|| !(returned.getExpression() instanceof ClassInstanceCreation copy)
				|| !arrayList(copy) || copy.arguments().size() != 1 || !(copy.arguments().getFirst() instanceof SimpleName argument)
				|| !(monitor.resolveBinding() instanceof IVariableBinding field) || !field.isEqualTo(argument.resolveBinding())
				|| !(method.getReturnType2() instanceof ParameterizedType result) || !ClosedUniqueSequenceFlow.list(result)
				|| !ClosedUniqueSequenceFlow.eligibleMethod(method.resolveBinding())
				|| !(method.getParent() instanceof TypeDeclaration owner)
				|| Arrays.stream(owner.getMethods()).anyMatch(member -> Modifier.isNative(member.getModifiers()))) {
			return Optional.empty();
		}
		var root= (CompilationUnit) method.getRoot();
		if (!(root.findDeclaringNode(field) instanceof VariableDeclarationFragment declaration)
				|| !(declaration.getParent() instanceof FieldDeclaration member) || member.getParent() != owner
				|| member.fragments().size() != 1 || !Modifier.isStatic(field.getModifiers())
				|| !Modifier.isPrivate(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())
				|| field.getAnnotations().length != 0 || !(member.getType() instanceof ParameterizedType declared)
				|| !ClosedUniqueSequenceFlow.list(declared) || !field.getType().isEqualTo(result.resolveBinding())
				|| !exact(field.getType().getTypeArguments()[0], "java.lang.String") //$NON-NLS-1$
				|| !(declaration.getInitializer() instanceof MethodInvocation wrapper)
				|| !(wrapper.getExpression() instanceof Name qualifier) || !exact(qualifier.resolveTypeBinding(), "java.util.Collections") //$NON-NLS-1$
				|| !(qualifier.resolveBinding() instanceof ITypeBinding)
				|| wrapper.resolveMethodBinding() == null || wrapper.resolveMethodBinding().isRecovered()
				|| !"synchronizedList".equals(wrapper.getName().getIdentifier()) || !wrapper.typeArguments().isEmpty() //$NON-NLS-1$
				|| wrapper.arguments().size() != 1 || !(wrapper.arguments().getFirst() instanceof ClassInstanceCreation fresh)
				|| !arrayList(fresh) || !fresh.arguments().isEmpty()) {
			return Optional.empty();
		}

		List<Edit> changes= new ArrayList<>();
		Set<SimpleName> observations= new HashSet<>(Set.of(monitor, argument));
		for (var register : owner.getMethods()) {
			if (register.getBody() == null || register.getBody().statements().size() != 1
					|| !(register.getBody().statements().getFirst() instanceof SynchronizedStatement guard)
					|| !(guard.getExpression() instanceof SimpleName receiver) || !field.isEqualTo(receiver.resolveBinding())
					|| guard.getBody().statements().size() != 1 || !(guard.getBody().statements().getFirst() instanceof IfStatement branch)
					|| ClosedUniqueSequenceFlow.losesGuardComment(branch)) {
				continue;
			}
			var insertion= UniqueSequencePattern.match(branch, field.getKey());
			if (insertion.isEmpty() || !(insertion.get().add().getExpression() instanceof SimpleName write)
					|| !(insertion.get().contains().getExpression() instanceof SimpleName read)
					|| !exact(insertion.get().value().resolveTypeBinding(), "java.lang.String")) { //$NON-NLS-1$
				continue;
			}
			observations.addAll(List.of(receiver, write, read));
			changes.add(new Edit(Kind.GUARDED_ADD, branch));
		}
		if (changes.isEmpty() || !allUsesCovered(root, field, observations)) {
			return Optional.empty();
		}
		changes.add(new Edit(Kind.SET_TYPE, result.getType()));
		changes.add(new Edit(Kind.SET_TYPE, declared.getType()));
		changes.add(new Edit(Kind.ORDERED_SET_TYPE, ((ParameterizedType) fresh.getType()).getType()));
		changes.add(new Edit(Kind.ORDERED_SET_TYPE, ((ParameterizedType) copy.getType()).getType()));
		changes.add(new Edit(Kind.SYNCHRONIZED_SET_FACTORY, wrapper.getName()));
		return Optional.of(List.copyOf(changes));
	}

	private static boolean allUsesCovered(CompilationUnit root, IVariableBinding field, Set<SimpleName> expected) {
		Set<SimpleName> actual= new HashSet<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (!name.isDeclaration() && field.isEqualTo(ImportNameBindings.resolve(name))) {
					actual.add(name);
				}
				return true;
			}
		});
		return expected.equals(actual);
	}

	private static boolean arrayList(ClassInstanceCreation instance) {
		return instance.getAnonymousClassDeclaration() == null && instance.getExpression() == null && instance.typeArguments().isEmpty()
				&& instance.getType() instanceof ParameterizedType constructed && plain(constructed)
				&& exact(instance.resolveTypeBinding(), "java.util.ArrayList") && instance.resolveTypeBinding().getTypeArguments().length == 1 //$NON-NLS-1$
				&& exact(instance.resolveTypeBinding().getTypeArguments()[0], "java.lang.String"); //$NON-NLS-1$
	}
}
