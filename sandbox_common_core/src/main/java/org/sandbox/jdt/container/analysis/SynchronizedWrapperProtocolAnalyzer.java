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

import static org.sandbox.jdt.container.analysis.ContainerAstFacts.unwrap;
import static org.sandbox.jdt.container.analysis.ContainerAstFacts.variableBinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.sandbox.jdt.container.api.ConcurrencyProtocol;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.Evidence;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.ReentrancyContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;
import org.sandbox.jdt.internal.common.AstProcessing;
import org.sandbox.jdt.internal.common.ReferenceHolder;

/**
 * Reports private fields created through {@code java.util.Collections.synchronized*}
 * and source-visible enhanced-for iteration over those fields.
 *
 * <p>This is deliberately a seed analyzer. Even when every observed enhanced-for loop
 * synchronizes on the wrapper itself, other operations, aliases and flow boundaries are
 * not yet proven. The resulting protocol therefore remains
 * {@link AnalysisCompleteness#LOCAL_SEED} and cannot by itself authorize a migration or
 * a "retain existing locking" conclusion.</p>
 */
public final class SynchronizedWrapperProtocolAnalyzer {

	/** Returns deterministic, source-ordered report-only protocol seeds. */
	public List<ConcurrencyProtocol> analyze(CompilationUnit compilationUnit) {
		Objects.requireNonNull(compilationUnit, "compilationUnit"); //$NON-NLS-1$

		ReferenceHolder<String, Candidate> candidates= ReferenceHolder.create();
		AstProcessing.independent(candidates)
				.on(VariableDeclarationFragment.class,
						(fragment, holder) -> collectCandidate(fragment, holder))
				.on(EnhancedForStatement.class,
						(loop, holder) -> collectIteration(loop, holder))
				.build(compilationUnit);

		return candidates.values().stream()
				.sorted(Comparator.comparingInt(Candidate::sourceStart))
				.map(Candidate::toProtocol)
				.toList();
	}

	private static boolean collectCandidate(
			VariableDeclarationFragment fragment,
			ReferenceHolder<String, Candidate> candidates) {
		candidate(fragment).ifPresent(candidate -> candidates.put(candidate.bindingKey(), candidate));
		return true;
	}

	private static Optional<Candidate> candidate(VariableDeclarationFragment fragment) {
		if (!(fragment.getParent() instanceof FieldDeclaration field)
				|| !Modifier.isPrivate(field.getModifiers())) {
			return Optional.empty();
		}
		Expression initializer= fragment.getInitializer();
		if (!(initializer != null && unwrap(initializer) instanceof MethodInvocation invocation)
				|| !isSynchronizedWrapperFactory(invocation)) {
			return Optional.empty();
		}
		IVariableBinding binding= fragment.resolveBinding();
		if (binding == null || binding.isRecovered() || !binding.isField()) {
			return Optional.empty();
		}
		String bindingKey= binding.getVariableDeclaration().getKey();
		if (bindingKey == null || bindingKey.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new Candidate(fragment.getName(), binding, invocation));
	}

	private static boolean isSynchronizedWrapperFactory(MethodInvocation invocation) {
		IMethodBinding binding= invocation.resolveMethodBinding();
		if (binding == null || binding.isRecovered() || binding.getDeclaringClass() == null
				|| !"java.util.Collections".equals( //$NON-NLS-1$
						binding.getDeclaringClass().getErasure().getQualifiedName())) {
			return false;
		}
		return switch (binding.getName()) {
			case "synchronizedCollection", //$NON-NLS-1$
					"synchronizedList", //$NON-NLS-1$
					"synchronizedMap", //$NON-NLS-1$
					"synchronizedNavigableMap", //$NON-NLS-1$
					"synchronizedNavigableSet", //$NON-NLS-1$
					"synchronizedSet", //$NON-NLS-1$
					"synchronizedSortedMap", //$NON-NLS-1$
					"synchronizedSortedSet" -> true; //$NON-NLS-1$
			default -> false;
		};
	}

	private static boolean collectIteration(
			EnhancedForStatement loop,
			ReferenceHolder<String, Candidate> candidates) {
		Optional<IVariableBinding> binding= variableBinding(loop.getExpression());
		if (binding.isEmpty()) {
			return true;
		}
		Candidate candidate= candidates.get(bindingKey(binding.get()));
		if (candidate == null) {
			return true;
		}
		Optional<String> protectingLock= protectingLock(loop, candidate.binding());
		if (protectingLock.isPresent()) {
			candidate.addEvidence(new Evidence(
					EvidenceKind.LOCKED_ITERATION,
					protectingLock.get(),
					"Enhanced-for iteration is protected by the synchronized wrapper monitor", //$NON-NLS-1$
					loop.getStartPosition(),
					loop.getLength()));
		} else {
			candidate.addEvidence(new Evidence(
					EvidenceKind.UNPROTECTED_ACCESS,
					"", //$NON-NLS-1$
					"Enhanced-for iteration is not synchronized on the wrapper itself", //$NON-NLS-1$
					loop.getStartPosition(),
					loop.getLength()));
		}
		return true;
	}

	private static Optional<String> protectingLock(
			EnhancedForStatement loop,
			IVariableBinding wrapperBinding) {
		ASTNode current= loop.getParent();
		while (current != null) {
			if (current instanceof SynchronizedStatement synchronizedStatement
					&& variableBinding(synchronizedStatement.getExpression())
							.map(SynchronizedWrapperProtocolAnalyzer::bindingKey)
							.filter(bindingKey(wrapperBinding)::equals)
							.isPresent()) {
				return Optional.of(bindingKey(wrapperBinding));
			}
			current= current.getParent();
		}
		return Optional.empty();
	}

	private static String bindingKey(IVariableBinding binding) {
		String key= binding.getVariableDeclaration().getKey();
		return key == null ? "" : key; //$NON-NLS-1$
	}

	private static final class Candidate {

		private final SimpleName name;
		private final IVariableBinding binding;
		private final List<Evidence> evidence= new ArrayList<>();

		private Candidate(
				SimpleName name,
				IVariableBinding binding,
				MethodInvocation initializer) {
			this.name= Objects.requireNonNull(name, "name"); //$NON-NLS-1$
			this.binding= Objects.requireNonNull(binding, "binding"); //$NON-NLS-1$
			evidence.add(new Evidence(
					EvidenceKind.WRAPPER_CREATION,
					"", //$NON-NLS-1$
					"Field is created by java.util.Collections synchronized-wrapper factory", //$NON-NLS-1$
					initializer.getStartPosition(),
					initializer.getLength()));
		}

		private String bindingKey() {
			return SynchronizedWrapperProtocolAnalyzer.bindingKey(binding);
		}

		private IVariableBinding binding() {
			return binding;
		}

		private int sourceStart() {
			return name.getStartPosition();
		}

		private void addEvidence(Evidence item) {
			evidence.add(Objects.requireNonNull(item, "item")); //$NON-NLS-1$
		}

		private ConcurrencyProtocol toProtocol() {
			boolean hasLockedIteration= evidence.stream()
					.anyMatch(item -> item.kind() == EvidenceKind.LOCKED_ITERATION);
			boolean hasUnprotectedIteration= evidence.stream()
					.anyMatch(item -> item.kind() == EvidenceKind.UNPROTECTED_ACCESS);
			IterationSemantics iteration= hasLockedIteration && !hasUnprotectedIteration
					? IterationSemantics.EXTERNALLY_LOCKED
					: IterationSemantics.UNKNOWN;
			ContainerIdentity identity= new ContainerIdentity(
					bindingKey(),
					name.getIdentifier(),
					name.getStartPosition(),
					name.getLength());
			ConcurrencyProfile summary= new ConcurrencyProfile(
					ThreadExposure.UNKNOWN,
					SynchronizationKind.SYNCHRONIZED_WRAPPER,
					iteration,
					AtomicityRequirement.UNKNOWN,
					WorkloadShape.UNKNOWN);
			return new ConcurrencyProtocol(
					identity,
					summary,
					ReentrancyContract.UNKNOWN,
					AnalysisCompleteness.LOCAL_SEED,
					evidence);
		}
	}
}
