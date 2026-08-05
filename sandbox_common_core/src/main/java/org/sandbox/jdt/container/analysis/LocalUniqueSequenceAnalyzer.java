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
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import org.sandbox.jdt.container.analysis.UniqueSequencePattern.GuardedAdd;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerUsageProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AccessProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AliasingContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.MutationLifecycle;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

/**
 * Proves the first local manually-unique sequence contract.
 *
 * <p>The supported source is deliberately narrow: one method-local {@code ArrayList}
 * created empty, insertions expressed only as
 * {@code if (!values.contains(value)) values.add(value)}, optional
 * {@code size()}/{@code isEmpty()} queries, and enhanced-for iteration. Any alias,
 * escape, capture, positional operation or other method is retained as rejection
 * evidence.</p>
 */
public final class LocalUniqueSequenceAnalyzer {

	private static final String ARRAY_LIST= "java.util.ArrayList"; //$NON-NLS-1$
	private static final String LIST= "java.util.List"; //$NON-NLS-1$

	/** Returns source-ordered complete or rejected profiles for recognized local seeds. */
	public List<ContainerUsageProfile> analyze(CompilationUnit compilationUnit) {
		Objects.requireNonNull(compilationUnit, "compilationUnit"); //$NON-NLS-1$
		List<ContainerUsageProfile> profiles= new ArrayList<>();
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment fragment) {
				candidate(fragment).map(LocalUniqueSequenceAnalyzer::analyzeCandidate)
						.ifPresent(profiles::add);
				return true;
			}
		});
		profiles.sort(Comparator.comparingInt(profile -> profile.identity().sourceStart()));
		return List.copyOf(profiles);
	}

	private static Optional<Candidate> candidate(VariableDeclarationFragment fragment) {
		IVariableBinding binding= fragment.resolveBinding();
		if (binding == null || binding.isField() || binding.isParameter()
				|| !(fragment.getParent() instanceof VariableDeclarationStatement declaration)
				|| declaration.fragments().size() != 1
				|| !(fragment.getInitializer() instanceof ClassInstanceCreation creation)
				|| !isEmptyArrayList(creation)
				|| !isSupportedDeclaredType(declaration.getType().resolveBinding())) {
			return Optional.empty();
		}
		MethodDeclaration method= enclosingMethod(fragment);
		if (method == null || method.getBody() == null) {
			return Optional.empty();
		}
		String key= binding.getVariableDeclaration().getKey();
		if (key == null || key.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new Candidate(
				fragment, declaration, method, binding.getVariableDeclaration(), key));
	}

	private static ContainerUsageProfile analyzeCandidate(Candidate candidate) {
		Accumulator accumulator= new Accumulator(candidate);
		candidate.method().getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				classify(name, candidate, accumulator);
				return true;
			}
		});
		return accumulator.toProfile();
	}

	private static void classify(
			SimpleName name,
			Candidate candidate,
			Accumulator accumulator) {
		IBinding resolved= name.resolveBinding();
		if (!(resolved instanceof IVariableBinding variable)
				|| !candidate.bindingKey().equals(variable.getVariableDeclaration().getKey())) {
			return;
		}
		accumulator.bindingSeen= true;
		if (candidate.fragment().getName() == name) {
			return;
		}
		if (crossesExecutableBoundary(name, candidate.binding())) {
			accumulator.reject(
					Kind.CAPTURED_USAGE,
					"Collection value is captured by a lambda, nested type, or different method body", //$NON-NLS-1$
					name);
			return;
		}

		ASTNode parent= name.getParent();
		if (parent instanceof MethodInvocation invocation
				&& invocation.getExpression() == name) {
			classifyInvocation(invocation, candidate, accumulator);
		} else if (parent instanceof EnhancedForStatement enhanced
				&& enhanced.getExpression() == name) {
			accumulator.iteration(name);
		} else {
			accumulator.reject(
					Kind.UNSAFE_ESCAPE,
					"Collection use escapes or observes unsupported list semantics", //$NON-NLS-1$
					name);
		}
	}

	private static void classifyInvocation(
			MethodInvocation invocation,
			Candidate candidate,
			Accumulator accumulator) {
		Optional<GuardedAdd> guardedAdd=
				UniqueSequencePattern.enclosing(invocation, candidate.bindingKey());
		if (guardedAdd.isPresent()
				&& (guardedAdd.get().contains() == invocation
						|| guardedAdd.get().add() == invocation)) {
			accumulator.guard(guardedAdd.get());
			return;
		}
		String method= invocation.getName().getIdentifier();
		if (("size".equals(method) || "isEmpty".equals(method)) //$NON-NLS-1$ //$NON-NLS-2$
				&& invocation.arguments().isEmpty()) {
			return;
		}
		accumulator.reject(
				Kind.UNCLASSIFIED_USAGE,
				"Only guarded insertion, size/isEmpty queries, and enhanced-for iteration are supported", //$NON-NLS-1$
				invocation);
	}

	private static boolean isEmptyArrayList(ClassInstanceCreation creation) {
		ITypeBinding type= creation.resolveTypeBinding();
		return type != null
				&& ARRAY_LIST.equals(type.getErasure().getQualifiedName())
				&& creation.arguments().isEmpty()
				&& creation.getAnonymousClassDeclaration() == null;
	}

	private static boolean isSupportedDeclaredType(ITypeBinding type) {
		if (type == null) {
			return false;
		}
		String name= type.getErasure().getQualifiedName();
		return LIST.equals(name) || ARRAY_LIST.equals(name);
	}

	private static MethodDeclaration enclosingMethod(ASTNode node) {
		ASTNode current= node.getParent();
		while (current != null) {
			if (current instanceof MethodDeclaration method) {
				return method;
			}
			current= current.getParent();
		}
		return null;
	}

	private static boolean crossesExecutableBoundary(
			ASTNode reference,
			IVariableBinding declaration) {
		IMethodBinding declaringMethod= declaration.getDeclaringMethod();
		if (declaringMethod == null) {
			return true;
		}
		String declaringKey= methodKey(declaringMethod);
		ASTNode current= reference.getParent();
		while (current != null) {
			if (current instanceof LambdaExpression
					|| current instanceof AnonymousClassDeclaration
					|| current instanceof AbstractTypeDeclaration) {
				return true;
			}
			if (current instanceof MethodDeclaration method) {
				IMethodBinding currentBinding= method.resolveBinding();
				return currentBinding == null
						|| !declaringKey.equals(methodKey(currentBinding));
			}
			current= current.getParent();
		}
		return true;
	}

	private static String methodKey(IMethodBinding binding) {
		String key= binding.getMethodDeclaration().getKey();
		return key == null ? "" : key; //$NON-NLS-1$
	}

	private static ElementDomain elementDomain(VariableDeclarationStatement declaration) {
		ITypeBinding type= declaration.getType().resolveBinding();
		if (type == null || type.getTypeArguments().length != 1) {
			return ElementDomain.UNKNOWN;
		}
		ITypeBinding element= type.getTypeArguments()[0];
		return element.isEnum() ? ElementDomain.ENUM : ElementDomain.REFERENCE;
	}

	private record Candidate(
			VariableDeclarationFragment fragment,
			VariableDeclarationStatement declaration,
			MethodDeclaration method,
			IVariableBinding binding,
			String bindingKey) {
	}

	private static final class Accumulator {
		private final Candidate candidate;
		private final List<UsageEvidence> evidence= new ArrayList<>();
		private final Set<org.eclipse.jdt.core.dom.IfStatement> guards=
				java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		private boolean rejected;
		private boolean bindingSeen;

		Accumulator(Candidate candidate) {
			this.candidate= candidate;
			evidence.add(evidence(
					Kind.REFERENCE_COMPONENT,
					"The local sequence has a reference element type", //$NON-NLS-1$
					candidate.declaration()));
		}

		void guard(GuardedAdd guardedAdd) {
			if (guards.add(guardedAdd.statement())) {
				evidence.add(evidence(
						Kind.APPEND_WRITE,
						"Membership is tested before the same stable value is inserted", //$NON-NLS-1$
						guardedAdd.statement()));
			}
		}

		void iteration(ASTNode node) {
			evidence.add(evidence(
					Kind.ENCOUNTER_ITERATION,
					"The collection is traversed in encounter order", //$NON-NLS-1$
					node));
		}

		void reject(Kind kind, String summary, ASTNode node) {
			rejected= true;
			evidence.add(evidence(kind, summary, node));
		}

		ContainerUsageProfile toProfile() {
			boolean complete= bindingSeen && !guards.isEmpty() && !rejected;
			if (complete) {
				evidence.add(evidence(
						Kind.LOCAL_USAGE_COMPLETE,
						"Every use of the local collection binding was classified", //$NON-NLS-1$
						candidate.fragment()));
			} else if (guards.isEmpty()) {
				reject(
						Kind.REJECTION_BOUNDARY,
						"No complete contains-before-add insertion was found", //$NON-NLS-1$
						candidate.fragment());
			}
			evidence.sort(Comparator
					.comparingInt(UsageEvidence::sourceStart)
					.thenComparing(item -> item.kind().ordinal()));

			ConcurrencyProfile concurrency= complete
					? new ConcurrencyProfile(
							ThreadExposure.THREAD_CONFINED,
							SynchronizationKind.NONE,
							IterationSemantics.LIVE,
							AtomicityRequirement.INDIVIDUAL_OPERATIONS,
							WorkloadShape.BALANCED)
					: ConcurrencyProfile.unknown();
			return new ContainerUsageProfile(
					new ContainerIdentity(
							candidate.bindingKey(),
							candidate.fragment().getName().getIdentifier(),
							candidate.fragment().getStartPosition(),
							candidate.fragment().getLength()),
					ContainerShape.LIST,
					elementDomain(candidate.declaration()),
					new AccessProfile(false, false, true, false, false, true, false),
					OrderRequirement.ENCOUNTER,
					UniquenessRequirement.REQUIRED,
					MutationLifecycle.CONTINUOUSLY_MUTABLE,
					NullContract.UNKNOWN,
					complete ? AliasingContract.NO_OBSERVED_ALIAS : AliasingContract.UNKNOWN,
					EscapeLevel.LOCAL,
					concurrency,
					complete
							? AnalysisCompleteness.LOCAL_USAGE_COMPLETE
							: AnalysisCompleteness.REJECTED,
					evidence);
		}
	}

	private static UsageEvidence evidence(
			Kind kind,
			String summary,
			ASTNode node) {
		return new UsageEvidence(
				kind, summary, node.getStartPosition(), node.getLength());
	}
}
