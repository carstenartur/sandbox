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
package org.sandbox.jdt.container.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

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
 * Extracts a narrow, closed local queue/stack protocol from an empty
 * {@link java.util.ArrayList}. The source list may only append at the tail,
 * remove from one end, observe size/emptiness, and iterate in encounter order.
 * Every other use fails closed.
 */
public final class LocalDequeSequenceAnalyzer {

	private static final String ARRAY_LIST= "java.util.ArrayList"; //$NON-NLS-1$
	private static final String LIST= "java.util.List"; //$NON-NLS-1$

	/** Returns source-ordered complete or rejected queue/stack profiles. */
	public List<ContainerUsageProfile> analyze(org.eclipse.jdt.core.dom.CompilationUnit unit) {
		Objects.requireNonNull(unit, "unit"); //$NON-NLS-1$
		List<ContainerUsageProfile> result= new ArrayList<>();
		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationFragment fragment) {
				candidate(fragment).map(LocalDequeSequenceAnalyzer::analyzeCandidate)
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
		ITypeBinding elementType= elementType(declaredType);
		if (!isListType(declaredType) || elementType == null || elementType.isPrimitive()) {
			return Optional.empty();
		}
		MethodDeclaration method= enclosingMethod(fragment);
		String key= binding.getVariableDeclaration().getKey();
		if (method == null || method.getBody() == null || key == null || key.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new Candidate(fragment, declaration, method,
				binding.getVariableDeclaration(), key, elementType));
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

	private static void classify(SimpleName name, Candidate candidate, Observations observations) {
		if (candidate.fragment().getName() == name) {
			return;
		}
		if (crossesExecutableBoundary(name, candidate.binding())) {
			observations.reject(Kind.CAPTURED_USAGE,
					"Sequence is captured across an executable boundary", name); //$NON-NLS-1$
			return;
		}
		ASTNode parent= name.getParent();
		if (parent instanceof MethodInvocation invocation && invocation.getExpression() == name) {
			classifyInvocation(invocation, candidate.bindingKey(), observations);
			return;
		}
		if (parent instanceof EnhancedForStatement enhanced && enhanced.getExpression() == name) {
			observations.iteration(name);
			return;
		}
		observations.reject(Kind.UNSAFE_ESCAPE,
				"Sequence use escapes or observes unsupported positional semantics", name); //$NON-NLS-1$
	}

	private static void classifyInvocation(MethodInvocation invocation, String bindingKey,
			Observations observations) {
		String method= invocation.getName().getIdentifier();
		if ("add".equals(method) && invocation.arguments().size() == 1) { //$NON-NLS-1$
			observations.append(invocation);
			if (invocation.arguments().get(0) instanceof NullLiteral) {
				observations.nullInserted= true;
			}
			return;
		}
		if ("remove".equals(method) && invocation.arguments().size() == 1) { //$NON-NLS-1$
			Object argument= invocation.arguments().get(0);
			if (isLiteral(argument, "0")) { //$NON-NLS-1$
				observations.headRemoval(invocation);
				return;
			}
			if (isTailIndex(argument, bindingKey)) {
				observations.tailRemoval(invocation);
				return;
			}
			observations.reject(Kind.UNCLASSIFIED_USAGE,
					"Only removal from index 0 or size() - 1 is a proven deque protocol", invocation); //$NON-NLS-1$
			return;
		}
		if (("size".equals(method) || "isEmpty".equals(method)) //$NON-NLS-1$ //$NON-NLS-2$
				&& invocation.arguments().isEmpty()) {
			return;
		}
		observations.reject(Kind.UNCLASSIFIED_USAGE,
				"Only tail append, end removal, size/isEmpty and enhanced-for are supported", invocation); //$NON-NLS-1$
	}

	private static boolean isTailIndex(Object expression, String bindingKey) {
		if (!(expression instanceof InfixExpression infix)
				|| infix.getOperator() != InfixExpression.Operator.MINUS
				|| !infix.extendedOperands().isEmpty()
				|| !isLiteral(infix.getRightOperand(), "1") //$NON-NLS-1$
				|| !(infix.getLeftOperand() instanceof MethodInvocation size)) {
			return false;
		}
		return "size".equals(size.getName().getIdentifier()) //$NON-NLS-1$
				&& size.arguments().isEmpty()
				&& size.getExpression() instanceof SimpleName receiver
				&& hasBinding(receiver.resolveBinding(), bindingKey);
	}

	private static boolean isLiteral(Object expression, String token) {
		return expression instanceof NumberLiteral literal && token.equals(literal.getToken());
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

	private static MethodDeclaration enclosingMethod(ASTNode node) {
		for (ASTNode current= node.getParent(); current != null; current= current.getParent()) {
			if (current instanceof MethodDeclaration method) {
				return method;
			}
		}
		return null;
	}

	private static boolean crossesExecutableBoundary(ASTNode reference, IVariableBinding declaration) {
		IMethodBinding declaringMethod= declaration.getDeclaringMethod();
		if (declaringMethod == null) {
			return true;
		}
		for (ASTNode current= reference.getParent(); current != null; current= current.getParent()) {
			if (current instanceof LambdaExpression || current instanceof AnonymousClassDeclaration
					|| current instanceof AbstractTypeDeclaration) {
				return true;
			}
			if (current instanceof MethodDeclaration method) {
				IMethodBinding actual= method.resolveBinding();
				return actual == null || !methodKey(declaringMethod).equals(methodKey(actual));
			}
		}
		return true;
	}

	private static String methodKey(IMethodBinding binding) {
		String key= binding.getMethodDeclaration().getKey();
		return key == null ? "" : key; //$NON-NLS-1$
	}

	private static boolean hasBinding(IBinding binding, String key) {
		return binding instanceof IVariableBinding variable
				&& key.equals(variable.getVariableDeclaration().getKey());
	}

	private static ElementDomain elementDomain(ITypeBinding type) {
		return type.isEnum() ? ElementDomain.ENUM : ElementDomain.REFERENCE;
	}

	private record Candidate(VariableDeclarationFragment fragment,
			VariableDeclarationStatement declaration, MethodDeclaration method,
			IVariableBinding binding, String bindingKey, ITypeBinding elementType) {
	}

	private static final class Observations {
		private final Candidate candidate;
		private final List<UsageEvidence> evidence= new ArrayList<>();
		private boolean appendSeen;
		private boolean headRemovalSeen;
		private boolean tailRemovalSeen;
		private boolean rejected;
		private boolean nullInserted;

		Observations(Candidate candidate) {
			this.candidate= candidate;
			add(Kind.REFERENCE_COMPONENT,
					"The local sequence has a resolved reference element type", candidate.declaration()); //$NON-NLS-1$
		}

		void append(ASTNode node) {
			appendSeen= true;
			add(Kind.APPEND_WRITE, "Elements are appended at the sequence tail", node); //$NON-NLS-1$
		}

		void headRemoval(ASTNode node) {
			headRemovalSeen= true;
			add(Kind.HEAD_REMOVAL, "Elements are removed from index 0 in FIFO order", node); //$NON-NLS-1$
		}

		void tailRemoval(ASTNode node) {
			tailRemovalSeen= true;
			add(Kind.TAIL_REMOVAL, "Elements are removed from size() - 1 in LIFO order", node); //$NON-NLS-1$
		}

		void iteration(ASTNode node) {
			add(Kind.ENCOUNTER_ITERATION, "The sequence is traversed in encounter order", node); //$NON-NLS-1$
		}

		void reject(Kind kind, String summary, ASTNode node) {
			rejected= true;
			add(kind, summary, node);
		}

		ContainerUsageProfile profile() {
			if (!appendSeen) {
				reject(Kind.REJECTION_BOUNDARY,
						"A deque recommendation requires at least one proven tail append", candidate.fragment()); //$NON-NLS-1$
			}
			if (!headRemovalSeen && !tailRemovalSeen) {
				reject(Kind.REJECTION_BOUNDARY,
						"A deque recommendation requires removal from one proven end", candidate.fragment()); //$NON-NLS-1$
			}
			if (headRemovalSeen && tailRemovalSeen) {
				reject(Kind.REJECTION_BOUNDARY,
						"Mixed head and tail removal is not a single proven FIFO or LIFO protocol", candidate.fragment()); //$NON-NLS-1$
			}
			boolean complete= !rejected;
			if (complete) {
				add(Kind.LOCAL_USAGE_COMPLETE,
						"Every use of the local sequence binding was classified as one deque protocol", //$NON-NLS-1$
						candidate.fragment());
			}
			evidence.sort(Comparator.comparingInt(UsageEvidence::sourceStart)
					.thenComparing(item -> item.kind().ordinal()));
			return new ContainerUsageProfile(
					new ContainerIdentity(candidate.bindingKey(),
							candidate.fragment().getName().getIdentifier(),
							candidate.fragment().getStartPosition(), candidate.fragment().getLength()),
					ContainerShape.LIST, elementDomain(candidate.elementType()),
					new AccessProfile(false, false, true, false, true, false, false),
					OrderRequirement.ENCOUNTER, UniquenessRequirement.DUPLICATES_ALLOWED,
					MutationLifecycle.CONTINUOUSLY_MUTABLE,
					nullInserted ? NullContract.ALLOWED : NullContract.UNKNOWN,
					complete ? AliasingContract.NO_OBSERVED_ALIAS : AliasingContract.UNKNOWN,
					EscapeLevel.LOCAL,
					complete ? new ConcurrencyProfile(ThreadExposure.THREAD_CONFINED,
							SynchronizationKind.NONE, IterationSemantics.LIVE,
							AtomicityRequirement.INDIVIDUAL_OPERATIONS, WorkloadShape.PRODUCER_CONSUMER)
							: ConcurrencyProfile.unknown(),
					complete ? AnalysisCompleteness.LOCAL_USAGE_COMPLETE : AnalysisCompleteness.REJECTED,
					evidence);
		}

		private void add(Kind kind, String summary, ASTNode node) {
			evidence.add(new UsageEvidence(kind, summary, node.getStartPosition(), node.getLength()));
		}
	}
}
