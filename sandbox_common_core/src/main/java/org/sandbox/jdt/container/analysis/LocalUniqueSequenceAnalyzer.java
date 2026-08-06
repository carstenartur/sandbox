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
 * Proves a narrow local manually-unique sequence contract. Only an empty local
 * {@code ArrayList}, contains-before-add, size/isEmpty and enhanced-for are accepted.
 */
public final class LocalUniqueSequenceAnalyzer {

	private static final String ARRAY_LIST= "java.util.ArrayList"; //$NON-NLS-1$
	private static final String LIST= "java.util.List"; //$NON-NLS-1$
	private static final String STRING= "java.lang.String"; //$NON-NLS-1$

	/** Returns source-ordered complete or rejected profiles. */
	public List<ContainerUsageProfile> analyze(CompilationUnit unit) {
		Objects.requireNonNull(unit, "unit"); //$NON-NLS-1$
		List<ContainerUsageProfile> result= new ArrayList<>();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment fragment) {
				candidate(fragment).map(LocalUniqueSequenceAnalyzer::analyzeCandidate)
						.ifPresent(result::add);
				return true;
			}
		});
		result.sort(Comparator.comparingInt(profile -> profile.identity().sourceStart()));
		return List.copyOf(result);
	}

	private static Optional<Candidate> candidate(VariableDeclarationFragment fragment) {
		IVariableBinding binding= fragment.resolveBinding();
		if (binding == null || binding.isField() || binding.isParameter()
				|| !(fragment.getParent() instanceof VariableDeclarationStatement declaration)
				|| declaration.fragments().size() != 1
				|| !(fragment.getInitializer() instanceof ClassInstanceCreation creation)
				|| !isEmptyArrayList(creation)) {
			return Optional.empty();
		}
		ITypeBinding declaredType= declaration.getType().resolveBinding();
		if (!isListType(declaredType)) {
			return Optional.empty();
		}
		MethodDeclaration method= enclosingMethod(fragment);
		String key= binding.getVariableDeclaration().getKey();
		if (method == null || method.getBody() == null || key == null || key.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new Candidate(
				fragment, declaration, method, binding.getVariableDeclaration(), key,
				elementType(declaredType)));
	}

	private static ContainerUsageProfile analyzeCandidate(Candidate candidate) {
		Observations observations= new Observations(candidate);
		candidate.method().getBody().accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (hasBinding(name.resolveBinding(), candidate.bindingKey())) {
					classify(name, candidate, observations);
				}
				return true;
			}
		});
		return observations.profile();
	}

	private static void classify(
			SimpleName name,
			Candidate candidate,
			Observations observations) {
		observations.bindingSeen= true;
		if (candidate.fragment().getName() == name) {
			return;
		}
		if (crossesExecutableBoundary(name, candidate.binding())) {
			observations.reject(Kind.CAPTURED_USAGE,
					"Collection value is captured across an executable boundary", name); //$NON-NLS-1$
			return;
		}

		ASTNode parent= name.getParent();
		if (parent instanceof MethodInvocation invocation
				&& invocation.getExpression() == name) {
			classifyInvocation(invocation, candidate.bindingKey(), observations);
		} else if (parent instanceof EnhancedForStatement enhanced
				&& enhanced.getExpression() == name) {
			observations.iteration(name);
		} else {
			observations.reject(Kind.UNSAFE_ESCAPE,
					"Collection use escapes or observes unsupported list semantics", name); //$NON-NLS-1$
		}
	}

	private static void classifyInvocation(
			MethodInvocation invocation,
			String bindingKey,
			Observations observations) {
		Optional<GuardedAdd> guard= UniqueSequencePattern.enclosing(invocation, bindingKey);
		if (guard.isPresent()
				&& (guard.get().contains() == invocation || guard.get().add() == invocation)) {
			observations.guard(guard.get());
			return;
		}
		String method= invocation.getName().getIdentifier();
		if (("size".equals(method) || "isEmpty".equals(method)) //$NON-NLS-1$ //$NON-NLS-2$
				&& invocation.arguments().isEmpty()) {
			return;
		}
		observations.reject(Kind.UNCLASSIFIED_USAGE,
				"Only guarded insertion, size/isEmpty and enhanced-for are supported", //$NON-NLS-1$
				invocation);
	}

	private static boolean isEmptyArrayList(ClassInstanceCreation creation) {
		ITypeBinding type= creation.resolveTypeBinding();
		return type != null && ARRAY_LIST.equals(type.getErasure().getQualifiedName())
				&& creation.arguments().isEmpty()
				&& creation.getAnonymousClassDeclaration() == null;
	}

	private static boolean isListType(ITypeBinding type) {
		if (type == null) {
			return false;
		}
		String name= type.getErasure().getQualifiedName();
		return LIST.equals(name) || ARRAY_LIST.equals(name);
	}

	private static ITypeBinding elementType(ITypeBinding listType) {
		return listType != null && listType.getTypeArguments().length == 1
				? listType.getTypeArguments()[0] : null;
	}

	private static boolean hasStableHash(ITypeBinding elementType) {
		return elementType != null
				&& (elementType.isEnum()
						|| STRING.equals(elementType.getErasure().getQualifiedName()));
	}

	private static MethodDeclaration enclosingMethod(ASTNode node) {
		for (ASTNode current= node.getParent(); current != null; current= current.getParent()) {
			if (current instanceof MethodDeclaration method) {
				return method;
			}
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
		for (ASTNode current= reference.getParent(); current != null;
				current= current.getParent()) {
			if (isNestedExecutableBoundary(current)) {
				return true;
			}
			if (current instanceof MethodDeclaration method) {
				return !sameDeclaringMethod(declaringMethod, method.resolveBinding());
			}
		}
		return true;
	}

	private static boolean isNestedExecutableBoundary(ASTNode node) {
		return node instanceof LambdaExpression
				|| node instanceof AnonymousClassDeclaration
				|| node instanceof AbstractTypeDeclaration;
	}

	private static boolean sameDeclaringMethod(
			IMethodBinding expected,
			IMethodBinding actual) {
		return actual != null && methodKey(expected).equals(methodKey(actual));
	}

	private static String methodKey(IMethodBinding binding) {
		String key= binding.getMethodDeclaration().getKey();
		return key == null ? "" : key; //$NON-NLS-1$
	}

	private static boolean hasBinding(IBinding binding, String key) {
		return binding instanceof IVariableBinding variable
				&& key.equals(variable.getVariableDeclaration().getKey());
	}

	private static ElementDomain elementDomain(ITypeBinding elementType) {
		if (elementType == null) {
			return ElementDomain.UNKNOWN;
		}
		return elementType.isEnum() ? ElementDomain.ENUM : ElementDomain.REFERENCE;
	}

	private record Candidate(
			VariableDeclarationFragment fragment,
			VariableDeclarationStatement declaration,
			MethodDeclaration method,
			IVariableBinding binding,
			String bindingKey,
			ITypeBinding elementType) {
	}

	private static final class Observations {
		private final Candidate candidate;
		private final List<UsageEvidence> evidence= new ArrayList<>();
		private final Set<org.eclipse.jdt.core.dom.IfStatement> guards=
				java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		private boolean rejected;
		private boolean bindingSeen;

		Observations(Candidate candidate) {
			this.candidate= candidate;
			add(Kind.REFERENCE_COMPONENT,
					"The local sequence has a reference element type", //$NON-NLS-1$
					candidate.declaration());
			if (hasStableHash(candidate.elementType())) {
				add(Kind.HASH_STABLE_COMPONENT,
						"The element type has stable equality and hash semantics", //$NON-NLS-1$
						candidate.declaration());
			} else {
				reject(Kind.REJECTION_BOUNDARY,
						"Automatic set migration requires a proven hash-stable element type", //$NON-NLS-1$
						candidate.declaration());
			}
		}

		void guard(GuardedAdd guard) {
			if (guards.add(guard.statement())) {
				add(Kind.DUPLICATE_SUPPRESSION,
						"Membership is tested before the same stable value is inserted", //$NON-NLS-1$
						guard.statement());
			}
		}

		void iteration(ASTNode node) {
			add(Kind.ENCOUNTER_ITERATION,
					"The collection is traversed in encounter order", node); //$NON-NLS-1$
		}

		void reject(Kind kind, String summary, ASTNode node) {
			rejected= true;
			add(kind, summary, node);
		}

		ContainerUsageProfile profile() {
			boolean complete= bindingSeen && !guards.isEmpty() && !rejected;
			if (complete) {
				add(Kind.LOCAL_USAGE_COMPLETE,
						"Every use of the local collection binding was classified", //$NON-NLS-1$
						candidate.fragment());
			} else if (guards.isEmpty()) {
				reject(Kind.REJECTION_BOUNDARY,
						"No complete contains-before-add insertion was found", //$NON-NLS-1$
						candidate.fragment());
			}
			evidence.sort(Comparator.comparingInt(UsageEvidence::sourceStart)
					.thenComparing(item -> item.kind().ordinal()));
			return new ContainerUsageProfile(
					identity(candidate), ContainerShape.LIST,
					elementDomain(candidate.elementType()),
					new AccessProfile(false, false, true, false, false, true, false),
					OrderRequirement.ENCOUNTER, UniquenessRequirement.REQUIRED,
					MutationLifecycle.CONTINUOUSLY_MUTABLE, NullContract.UNKNOWN,
					complete ? AliasingContract.NO_OBSERVED_ALIAS : AliasingContract.UNKNOWN,
					EscapeLevel.LOCAL, concurrency(complete),
					complete ? AnalysisCompleteness.LOCAL_USAGE_COMPLETE
							: AnalysisCompleteness.REJECTED,
					evidence);
		}

		private void add(Kind kind, String summary, ASTNode node) {
			evidence.add(new UsageEvidence(
					kind, summary, node.getStartPosition(), node.getLength()));
		}
	}

	private static ContainerIdentity identity(Candidate candidate) {
		return new ContainerIdentity(
				candidate.bindingKey(), candidate.fragment().getName().getIdentifier(),
				candidate.fragment().getStartPosition(), candidate.fragment().getLength());
	}

	private static ConcurrencyProfile concurrency(boolean complete) {
		return complete
				? new ConcurrencyProfile(
						ThreadExposure.THREAD_CONFINED, SynchronizationKind.NONE,
						IterationSemantics.LIVE, AtomicityRequirement.INDIVIDUAL_OPERATIONS,
						WorkloadShape.BALANCED)
				: ConcurrencyProfile.unknown();
	}
}
