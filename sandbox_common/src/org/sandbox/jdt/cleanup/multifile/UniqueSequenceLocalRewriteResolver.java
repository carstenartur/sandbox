/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.sandbox.jdt.container.analysis.UniqueSequencePattern;
import org.sandbox.jdt.container.analysis.UniqueSequencePattern.GuardedAdd;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan.EditKind;

/** Re-resolves and verifies one local unique-sequence rewrite plan. */
final class UniqueSequenceLocalRewriteResolver {

	private static final String PLUGIN_ID= "sandbox_common"; //$NON-NLS-1$
	private static final String ARRAY_LIST= "java.util.ArrayList"; //$NON-NLS-1$
	private static final String LIST= "java.util.List"; //$NON-NLS-1$

	private UniqueSequenceLocalRewriteResolver() {
	}

	static ResolvedPlan resolve(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			UniqueSequenceLocalRewritePlan plan) throws CoreException {
		Objects.requireNonNull(unit, "unit"); //$NON-NLS-1$
		Objects.requireNonNull(root, "root"); //$NON-NLS-1$
		Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$
		if (!plan.compilationUnitHandle().equals(unit.getHandleIdentifier())) {
			throw stale(unit, "compilation-unit handle changed"); //$NON-NLS-1$
		}

		CollectedAst collected= collect(root, plan.bindingKey());
		VariableDeclarationFragment fragment= collected.declaration();
		if (fragment == null
				|| !(fragment.getParent() instanceof VariableDeclarationStatement declaration)
				|| declaration.fragments().size() != 1
				|| !(declaration.getType() instanceof ParameterizedType declarationType)
				|| declarationType.typeArguments().size() != 1
				|| !isSupportedSourceType(declaration.getType().resolveBinding())
				|| !(fragment.getInitializer() instanceof ClassInstanceCreation initializer)
				|| !isEmptyArrayList(initializer)) {
			throw stale(unit, "local list declaration or empty ArrayList initializer changed"); //$NON-NLS-1$
		}

		List<GuardedAdd> guards= collected.guards().stream()
				.sorted(Comparator.comparingInt(guard -> guard.statement().getStartPosition()))
				.toList();
		if (guards.size() != editCount(plan, EditKind.REPLACE_DUPLICATE_GUARD)) {
			throw stale(unit, "duplicate-guard occurrence count changed"); //$NON-NLS-1$
		}

		Set<MethodInvocation> recognizedInvocations=
				java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (GuardedAdd guard : guards) {
			recognizedInvocations.add(guard.contains());
			recognizedInvocations.add(guard.add());
		}

		int encounterIterations= 0;
		for (SimpleName reference : collected.references()) {
			if (fragment.getName() == reference) {
				continue;
			}
			ASTNode parent= reference.getParent();
			if (parent instanceof MethodInvocation invocation
					&& invocation.getExpression() == reference) {
				if (recognizedInvocations.contains(invocation)
						|| isReadOnlyCollectionQuery(invocation)) {
					continue;
				}
			}
			if (parent instanceof EnhancedForStatement enhanced
					&& enhanced.getExpression() == reference) {
				encounterIterations++;
				continue;
			}
			throw stale(unit, "unexpected use of local sequence binding at source offset " //$NON-NLS-1$
					+ reference.getStartPosition());
		}
		if (encounterIterations != editCount(plan, EditKind.VERIFY_ENCOUNTER_ITERATION)) {
			throw stale(unit, "encounter-iteration occurrence count changed"); //$NON-NLS-1$
		}

		return new ResolvedPlan(
				plan,
				declaration,
				fragment,
				declarationType,
				initializer,
				guards);
	}

	private static CollectedAst collect(
			org.eclipse.jdt.core.dom.CompilationUnit root,
			String bindingKey) {
		List<SimpleName> references= new ArrayList<>();
		List<GuardedAdd> guards= new ArrayList<>();
		VariableDeclarationFragment[] declaration= { null };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				IVariableBinding binding= variableBinding(name.resolveBinding());
				if (binding == null
						|| !bindingKey.equals(binding.getVariableDeclaration().getKey())) {
					return true;
				}
				references.add(name);
				if (name.getParent() instanceof VariableDeclarationFragment fragment
						&& fragment.getName() == name) {
					declaration[0]= fragment;
				}
				return true;
			}

			@Override
			public boolean visit(IfStatement statement) {
				UniqueSequencePattern.match(statement, bindingKey).ifPresent(guards::add);
				return true;
			}
		});
		return new CollectedAst(declaration[0], references, guards);
	}

	private static boolean isSupportedSourceType(ITypeBinding type) {
		if (type == null) {
			return false;
		}
		String name= type.getErasure().getQualifiedName();
		return LIST.equals(name) || ARRAY_LIST.equals(name);
	}

	private static boolean isEmptyArrayList(ClassInstanceCreation creation) {
		ITypeBinding type= creation.resolveTypeBinding();
		return type != null
				&& ARRAY_LIST.equals(type.getErasure().getQualifiedName())
				&& creation.arguments().isEmpty()
				&& creation.getAnonymousClassDeclaration() == null;
	}

	private static boolean isReadOnlyCollectionQuery(MethodInvocation invocation) {
		String method= invocation.getName().getIdentifier();
		return ("size".equals(method) || "isEmpty".equals(method)) //$NON-NLS-1$ //$NON-NLS-2$
				&& invocation.arguments().isEmpty();
	}

	private static int editCount(
			UniqueSequenceLocalRewritePlan plan,
			EditKind kind) {
		return (int) plan.edits().stream()
				.filter(edit -> edit.kind() == kind)
				.count();
	}

	private static IVariableBinding variableBinding(IBinding binding) {
		return binding instanceof IVariableBinding variable ? variable : null;
	}

	private static CoreException stale(ICompilationUnit unit, String reason) {
		return new CoreException(new Status(
				IStatus.ERROR,
				PLUGIN_ID,
				"Stale unique-sequence rewrite plan for " //$NON-NLS-1$
						+ unit.getElementName() + ": " + reason)); //$NON-NLS-1$
	}

	static record ResolvedPlan(
			UniqueSequenceLocalRewritePlan plan,
			VariableDeclarationStatement declaration,
			VariableDeclarationFragment fragment,
			ParameterizedType declarationType,
			ClassInstanceCreation initializer,
			List<GuardedAdd> guards) {

		ResolvedPlan {
			guards= List.copyOf(guards);
		}
	}

	private record CollectedAst(
			VariableDeclarationFragment declaration,
			List<SimpleName> references,
			List<GuardedAdd> guards) {

		CollectedAst {
			references= List.copyOf(references);
			guards= List.copyOf(guards);
		}
	}
}
