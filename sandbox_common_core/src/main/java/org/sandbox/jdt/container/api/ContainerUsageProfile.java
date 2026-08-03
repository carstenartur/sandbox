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

import java.util.List;
import java.util.Objects;

/**
 * Immutable semantic description of how one connected container value is used.
 *
 * <p>A profile is an analysis result, not yet a rewrite decision. In particular,
 * {@link AnalysisCompleteness#LOCAL_SEED} means that a useful local motif was found
 * but project-wide data flow, escape and compatibility checks are still outstanding.</p>
 *
 * @param identity stable identity and source anchor of the candidate
 * @param currentShape current structural representation
 * @param elementDomain known element-domain category
 * @param access observed access operations
 * @param orderRequirement ordering required by observed code
 * @param uniquenessRequirement uniqueness required by observed code
 * @param mutationLifecycle observed mutation lifecycle
 * @param nullContract observed null behaviour
 * @param aliasingContract observed aliasing behaviour
 * @param escapeLevel widest known escape boundary
 * @param concurrencyContract observed concurrency behaviour
 * @param completeness completeness of the semantic proof
 * @param evidence source-backed observations supporting the profile
 */
public record ContainerUsageProfile(
		ContainerIdentity identity,
		ContainerShape currentShape,
		ElementDomain elementDomain,
		AccessProfile access,
		OrderRequirement orderRequirement,
		UniquenessRequirement uniquenessRequirement,
		MutationLifecycle mutationLifecycle,
		NullContract nullContract,
		AliasingContract aliasingContract,
		EscapeLevel escapeLevel,
		ConcurrencyContract concurrencyContract,
		AnalysisCompleteness completeness,
		List<UsageEvidence> evidence) {

	public ContainerUsageProfile {
		Objects.requireNonNull(identity, "identity"); //$NON-NLS-1$
		Objects.requireNonNull(currentShape, "currentShape"); //$NON-NLS-1$
		Objects.requireNonNull(elementDomain, "elementDomain"); //$NON-NLS-1$
		Objects.requireNonNull(access, "access"); //$NON-NLS-1$
		Objects.requireNonNull(orderRequirement, "orderRequirement"); //$NON-NLS-1$
		Objects.requireNonNull(uniquenessRequirement, "uniquenessRequirement"); //$NON-NLS-1$
		Objects.requireNonNull(mutationLifecycle, "mutationLifecycle"); //$NON-NLS-1$
		Objects.requireNonNull(nullContract, "nullContract"); //$NON-NLS-1$
		Objects.requireNonNull(aliasingContract, "aliasingContract"); //$NON-NLS-1$
		Objects.requireNonNull(escapeLevel, "escapeLevel"); //$NON-NLS-1$
		Objects.requireNonNull(concurrencyContract, "concurrencyContract"); //$NON-NLS-1$
		Objects.requireNonNull(completeness, "completeness"); //$NON-NLS-1$
		evidence= List.copyOf(Objects.requireNonNull(evidence, "evidence")); //$NON-NLS-1$
	}

	/** Returns whether a later planner may treat this profile as a complete flow proof. */
	public boolean isFlowComplete() {
		return completeness == AnalysisCompleteness.FLOW_COMPLETE;
	}

	/** Stable identity of the represented value, independent of retained AST nodes. */
	public record ContainerIdentity(String bindingKey, String displayName, int sourceStart, int sourceLength) {

		public ContainerIdentity {
			bindingKey= bindingKey == null ? "" : bindingKey; //$NON-NLS-1$
			displayName= Objects.requireNonNull(displayName, "displayName").strip(); //$NON-NLS-1$
			if (displayName.isEmpty()) {
				throw new IllegalArgumentException("displayName must not be empty"); //$NON-NLS-1$
			}
			if (sourceStart < 0) {
				throw new IllegalArgumentException("sourceStart must not be negative"); //$NON-NLS-1$
			}
			if (sourceLength < 0) {
				throw new IllegalArgumentException("sourceLength must not be negative"); //$NON-NLS-1$
			}
		}

		/** Returns whether binding resolution supplied a stable key. */
		public boolean hasResolvedBinding() {
			return !bindingKey.isBlank();
		}

		/** Returns a deterministic identifier suitable for reports and maps. */
		public String stableId() {
			return hasResolvedBinding() ? bindingKey : displayName + '@' + sourceStart;
		}
	}

	/** Operations observed on the represented value. */
	public record AccessProfile(
			boolean indexedRead,
			boolean indexedWrite,
			boolean append,
			boolean positionalInsert,
			boolean positionalRemove,
			boolean membershipQuery,
			boolean keyLookup) {

		/** Initial access facts for a syntactically recognised append-only array seed. */
		public static AccessProfile appendOnlyArraySeed() {
			return new AccessProfile(false, true, true, false, false, false, false);
		}

		/** Returns whether position is already part of the observed contract. */
		public boolean hasPositionalSemantics() {
			return indexedRead || positionalInsert || positionalRemove;
		}
	}

	public enum ElementDomain {
		REFERENCE,
		PRIMITIVE,
		ENUM,
		UNKNOWN
	}

	public enum OrderRequirement {
		NONE,
		ENCOUNTER,
		SORTED,
		POSITIONAL,
		UNKNOWN
	}

	public enum UniquenessRequirement {
		REQUIRED,
		DUPLICATES_ALLOWED,
		UNKNOWN
	}

	public enum MutationLifecycle {
		FIXED,
		BUILD_THEN_FREEZE,
		CONTINUOUSLY_MUTABLE,
		SNAPSHOT_PUBLISHED,
		UNKNOWN
	}

	public enum NullContract {
		ALLOWED,
		REJECTED,
		NOT_APPLICABLE,
		UNKNOWN
	}

	public enum AliasingContract {
		SHARED_MUTATION,
		DEFENSIVE_COPY,
		IDENTITY_OBSERVED,
		NO_OBSERVED_ALIAS,
		UNKNOWN
	}

	public enum EscapeLevel {
		LOCAL,
		FIELD,
		METHOD_BOUNDARY,
		OVERRIDE_FAMILY,
		EXTERNAL_OR_BINARY,
		UNKNOWN
	}

	public enum ConcurrencyContract {
		THREAD_CONFINED,
		LOCK_PROTECTED,
		COPY_ON_WRITE,
		CONCURRENT_MEMBERSHIP,
		PRODUCER_CONSUMER,
		ATOMIC_DRAIN,
		UNKNOWN
	}

	public enum AnalysisCompleteness {
		LOCAL_SEED,
		LOCAL_USAGE_COMPLETE,
		FLOW_COMPLETE,
		REJECTED
	}
}
