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
package org.sandbox.jdt.container.api;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;

/**
 * Immutable report model for one observed concurrent-container protocol.
 *
 * <p>The protocol stores stable scalar evidence only. It does not retain AST nodes and
 * does not authorize a rewrite. A later binding-based analyzer may populate this model
 * before any migration strategy is considered.</p>
 *
 * @param identity stable container identity
 * @param summary aggregate concurrency facts already carried by the usage profile
 * @param reentrancy observed callback and recursive-modification contract
 * @param completeness completeness of the current protocol proof
 * @param evidence deterministic source-backed concurrency observations
 */
public record ConcurrencyProtocol(
		ContainerIdentity identity,
		ConcurrencyProfile summary,
		ReentrancyContract reentrancy,
		AnalysisCompleteness completeness,
		List<Evidence> evidence) {

	public ConcurrencyProtocol {
		Objects.requireNonNull(identity, "identity"); //$NON-NLS-1$
		Objects.requireNonNull(summary, "summary"); //$NON-NLS-1$
		Objects.requireNonNull(reentrancy, "reentrancy"); //$NON-NLS-1$
		Objects.requireNonNull(completeness, "completeness"); //$NON-NLS-1$
		evidence= List.copyOf(Objects.requireNonNull(evidence, "evidence")); //$NON-NLS-1$
	}

	/** Returns whether every required source and flow boundary has been classified. */
	public boolean isFlowComplete() {
		return completeness == AnalysisCompleteness.FLOW_COMPLETE;
	}

	/** Returns whether analysis encountered a source, binary or framework boundary it could not prove. */
	public boolean hasUnresolvedBoundary() {
		return evidence.stream().anyMatch(item -> item.kind() == EvidenceKind.UNRESOLVED_BOUNDARY);
	}

	/** Returns all explicit lock identities in deterministic first-observed order. */
	public Set<String> lockIdentities() {
		LinkedHashSet<String> result= new LinkedHashSet<>();
		for (Evidence item : evidence) {
			if (!item.lockIdentity().isBlank()) {
				result.add(item.lockIdentity());
			}
		}
		return Collections.unmodifiableSet(result);
	}

	/**
	 * Returns whether observed protected accesses consistently use one lock and no
	 * unprotected or unresolved access contradicts that proof.
	 */
	public boolean hasSingleProtectingLock() {
		if (hasUnresolvedBoundary() || evidence.stream()
				.anyMatch(item -> item.kind() == EvidenceKind.UNPROTECTED_ACCESS)) {
			return false;
		}
		return lockIdentities().size() == 1;
	}

	/** One stable source observation contributing to the concurrency protocol. */
	public record Evidence(
			EvidenceKind kind,
			String lockIdentity,
			String summary,
			int sourceStart,
			int sourceLength) {

		public Evidence {
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			lockIdentity= lockIdentity == null ? "" : lockIdentity.strip(); //$NON-NLS-1$
			summary= Objects.requireNonNull(summary, "summary").strip(); //$NON-NLS-1$
			if (summary.isEmpty()) {
				throw new IllegalArgumentException("summary must not be empty"); //$NON-NLS-1$
			}
			if (sourceStart < 0) {
				throw new IllegalArgumentException("sourceStart must not be negative"); //$NON-NLS-1$
			}
			if (sourceLength < 0) {
				throw new IllegalArgumentException("sourceLength must not be negative"); //$NON-NLS-1$
			}
			if (kind.requiresLockIdentity() && lockIdentity.isEmpty()) {
				throw new IllegalArgumentException(kind + " requires a stable lock identity"); //$NON-NLS-1$
			}
		}
	}

	/** Concurrency-specific evidence categories understood by report-only analysis. */
	public enum EvidenceKind {
		LOCKED_READ(true),
		LOCKED_WRITE(true),
		LOCKED_ITERATION(true),
		CHECK_THEN_ACT(true),
		COMPOUND_UPDATE(true),
		DRAIN(true),
		CALLBACK_UNDER_LOCK(true),
		CALLBACK_OUTSIDE_LOCK(false),
		SNAPSHOT_PUBLICATION(false),
		UNPROTECTED_ACCESS(false),
		UNRESOLVED_BOUNDARY(false);

		private final boolean lockIdentityRequired;

		EvidenceKind(boolean lockIdentityRequired) {
			this.lockIdentityRequired= lockIdentityRequired;
		}

		/** Returns whether this evidence is meaningful only with an identified lock. */
		public boolean requiresLockIdentity() {
			return lockIdentityRequired;
		}
	}

	/** Callback and recursive-modification semantics observed around the container. */
	public enum ReentrancyContract {
		CALLBACKS_UNDER_LOCK,
		CALLBACKS_OUTSIDE_LOCK,
		RECURSIVE_MODIFICATION_ALLOWED,
		RECURSIVE_MODIFICATION_REJECTED,
		NO_CALLBACKS_OBSERVED,
		UNKNOWN
	}
}
