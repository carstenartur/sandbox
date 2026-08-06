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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

import org.sandbox.jdt.container.api.ContainerParameterRewritePlan;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;

/** Binding-based, fail-closed resolution of a closed-source parameter rewrite. */
final class ContainerParameterRewriteResolver {

	private static final String PLUGIN_ID= "sandbox_common"; //$NON-NLS-1$
	private static final String LIST= "java.util.List"; //$NON-NLS-1$

	private ContainerParameterRewriteResolver() {
	}

	static ResolvedPlan resolve(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			ContainerParameterRewritePlan plan) throws CoreException {
		Objects.requireNonNull(unit, "unit"); //$NON-NLS-1$
		Objects.requireNonNull(root, "root"); //$NON-NLS-1$
		Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$
		if (!plan.compilationUnitHandle().equals(unit.getHandleIdentifier())) {
			throw stale(unit, "compilation-unit handle changed"); //$NON-NLS-1$
		}
		if (!isExpectedTarget(plan)) {
			throw stale(unit, "target strategy changed"); //$NON-NLS-1$
		}

		MethodDeclaration method= findMethod(unit, root, plan.methodJavaElementHandle());
		if (plan.parameterIndex() >= method.parameters().size()) {
			throw stale(unit, "parameter index changed"); //$NON-NLS-1$
		}
		SingleVariableDeclaration parameter=
				(SingleVariableDeclaration) method.parameters().get(plan.parameterIndex());
		if (parameter.isVarargs()
				|| !parameter.extraDimensions().isEmpty()
				|| !(parameter.getType() instanceof ArrayType arrayType)
				|| arrayType.dimensions().size() != 1
				|| !isReferenceComponent(arrayType)) {
			throw stale(unit, "parameter is no longer a one-dimensional reference array"); //$NON-NLS-1$
		}
		IVariableBinding parameterBinding= parameter.resolveBinding();
		if (parameterBinding == null
				|| !plan.parameterBindingKey().equals(
						parameterBinding.getVariableDeclaration().getKey())) {
			throw stale(unit, "parameter binding changed"); //$NON-NLS-1$
		}

		List<SimpleName> references= matchingReferences(method, plan.parameterBindingKey());
		List<ResolvedLength> lengths= new ArrayList<>();
		List<Expression> encounterIterations= new ArrayList<>();
		for (SimpleName name : references) {
			if (parameter.getName() == name) {
				continue;
			}
			if (crossesExecutableBoundary(name, method)) {
				throw stale(unit, "parameter is captured across an executable boundary"); //$NON-NLS-1$
			}
			Expression reference= completeReferenceExpression(name);
			ResolvedLength length= lengthRead(reference);
			if (length != null) {
				lengths.add(length);
				continue;
			}
			if (reference.getParent() instanceof EnhancedForStatement enhanced
					&& enhanced.getExpression() == reference) {
				encounterIterations.add(reference);
				continue;
			}
			throw stale(unit, "unexpected parameter use at source offset " //$NON-NLS-1$
					+ name.getStartPosition());
		}
		verifyEditRanges(
				unit,
				plan,
				EditKind.REPLACE_LENGTH_WITH_SIZE,
				lengths.stream().map(ResolvedLength::expression).toList(),
				"array length occurrence count or source ranges changed"); //$NON-NLS-1$
		verifyEditRanges(
				unit,
				plan,
				EditKind.VERIFY_ENCOUNTER_ITERATION,
				encounterIterations,
				"encounter iteration occurrence count or source ranges changed"); //$NON-NLS-1$
		lengths.sort(Comparator.comparingInt(length -> length.expression().getStartPosition()));
		return new ResolvedPlan(plan, method, parameter, arrayType, lengths);
	}

	private static MethodDeclaration findMethod(
			ICompilationUnit unit,
			org.eclipse.jdt.core.dom.CompilationUnit root,
			String methodHandle) throws CoreException {
		List<MethodDeclaration> matches= new ArrayList<>();
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration method) {
				if (methodHandle.equals(javaElementHandle(method.resolveBinding()))) {
					matches.add(method);
				}
				return true;
			}
		});
		if (matches.size() != 1) {
			throw stale(unit, "exact method declaration could not be resolved"); //$NON-NLS-1$
		}
		return matches.get(0);
	}

	private static List<SimpleName> matchingReferences(
			MethodDeclaration method,
			String bindingKey) {
		List<SimpleName> references= new ArrayList<>();
		method.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (name.resolveBinding() instanceof IVariableBinding binding
						&& bindingKey.equals(binding.getVariableDeclaration().getKey())) {
					references.add(name);
				}
				return true;
			}
		});
		return references;
	}

	private static boolean crossesExecutableBoundary(
			ASTNode reference,
			MethodDeclaration expectedMethod) {
		for (ASTNode current= reference.getParent(); current != null;
				current= current.getParent()) {
			if (current instanceof LambdaExpression
					|| current instanceof AnonymousClassDeclaration
					|| current instanceof AbstractTypeDeclaration) {
				return true;
			}
			if (current instanceof MethodDeclaration method) {
				return method != expectedMethod;
			}
		}
		return true;
	}

	private static Expression completeReferenceExpression(SimpleName name) {
		Expression reference= name;
		while (reference.getParent() instanceof ParenthesizedExpression parenthesized) {
			reference= parenthesized;
		}
		return reference;
	}

	private static ResolvedLength lengthRead(Expression reference) {
		ASTNode parent= reference.getParent();
		if (parent instanceof QualifiedName qualified
				&& qualified.getQualifier() == reference
				&& "length".equals(qualified.getName().getIdentifier())) { //$NON-NLS-1$
			return new ResolvedLength(qualified, reference);
		}
		if (parent instanceof FieldAccess fieldAccess
				&& fieldAccess.getExpression() == reference
				&& "length".equals(fieldAccess.getName().getIdentifier())) { //$NON-NLS-1$
			return new ResolvedLength(fieldAccess, reference);
		}
		return null;
	}

	private static void verifyEditRanges(
			ICompilationUnit unit,
			ContainerParameterRewritePlan plan,
			EditKind kind,
			List<? extends ASTNode> resolvedNodes,
			String staleMessage) throws CoreException {
		List<ContainerParameterRewritePlan.ParameterEdit> planned= plan.edits().stream()
				.filter(edit -> edit.kind() == kind)
				.toList();
		Set<SourceRange> expected= HashSet.newHashSet(planned.size());
		for (ContainerParameterRewritePlan.ParameterEdit edit : planned) {
			expected.add(new SourceRange(edit.sourceStart(), edit.sourceLength()));
		}
		Set<SourceRange> actual= HashSet.newHashSet(resolvedNodes.size());
		for (ASTNode node : resolvedNodes) {
			actual.add(new SourceRange(node.getStartPosition(), node.getLength()));
		}
		if (expected.size() != planned.size()
				|| actual.size() != resolvedNodes.size()
				|| !expected.equals(actual)) {
			throw stale(unit, staleMessage);
		}
	}

	private static boolean isReferenceComponent(ArrayType arrayType) {
		ITypeBinding component= arrayType.getElementType().resolveBinding();
		return component != null && !component.isPrimitive();
	}

	private static boolean isExpectedTarget(ContainerParameterRewritePlan plan) {
		return LIST.equals(plan.targetInterfaceType())
				&& plan.targetContract().shape() == ContainerShape.LIST
				&& plan.targetContract().mutability() == Mutability.MUTABLE;
	}

	private static String javaElementHandle(IMethodBinding binding) {
		IJavaElement element= binding == null
				? null : binding.getMethodDeclaration().getJavaElement();
		return element == null ? "" : element.getHandleIdentifier(); //$NON-NLS-1$
	}

	private static CoreException stale(ICompilationUnit unit, String reason) {
		return new CoreException(new Status(
				IStatus.ERROR,
				PLUGIN_ID,
				"Container parameter rewrite plan is stale for " //$NON-NLS-1$
						+ unit.getElementName() + ": " + reason)); //$NON-NLS-1$
	}

	static record ResolvedPlan(
			ContainerParameterRewritePlan plan,
			MethodDeclaration method,
			SingleVariableDeclaration parameter,
			ArrayType arrayType,
			List<ResolvedLength> lengths) {

		ResolvedPlan {
			Objects.requireNonNull(plan, "plan"); //$NON-NLS-1$
			Objects.requireNonNull(method, "method"); //$NON-NLS-1$
			Objects.requireNonNull(parameter, "parameter"); //$NON-NLS-1$
			Objects.requireNonNull(arrayType, "arrayType"); //$NON-NLS-1$
			lengths= List.copyOf(Objects.requireNonNull(lengths, "lengths")); //$NON-NLS-1$
		}
	}

	static record ResolvedLength(Expression expression, Expression arrayExpression) {
		ResolvedLength {
			Objects.requireNonNull(expression, "expression"); //$NON-NLS-1$
			Objects.requireNonNull(arrayExpression, "arrayExpression"); //$NON-NLS-1$
		}
	}

	private record SourceRange(int start, int length) {
	}
}
