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

import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;

/**
 * Report-only outcome of concurrent-container protocol analysis.
 *
 * <p>All current outcomes deliberately deny source rewriting. They separate a possible
 * modernization strategy from the equally important conclusions that existing locking
 * should be retained, that only a diagnostic is justified, or that analysis was
 * incomplete. A later migration planner must establish its own stronger proof.</p>
 */
public sealed interface ConcurrencyProtocolAssessment
		permits ConcurrencyProtocolAssessment.RecommendedMigration,
				ConcurrencyProtocolAssessment.RetainExistingLocking,
				ConcurrencyProtocolAssessment.DiagnosticOnly,
				ConcurrencyProtocolAssessment.Rejected {

	/** Returns the immutable source protocol supporting this outcome. */
	ConcurrencyProtocol protocol();

	/** Returns concise, user-facing reasons for the outcome. */
	List<String> explanations();

	/** Concurrent-container analysis is report-only in the initial delivery stage. */
	default boolean permitsSourceRewrite() {
		return false;
	}

	/** A possible modernization strategy whose implementation still requires a planner proof. */
	record RecommendedMigration(
			ConcurrencyProtocol protocol,
			TargetContainerContract targetContract,
			Strategy strategy,
			Confidence confidence,
			List<String> explanations) implements ConcurrencyProtocolAssessment {

		public RecommendedMigration {
			Objects.requireNonNull(protocol, "protocol"); //$NON-NLS-1$
			Objects.requireNonNull(targetContract, "targetContract"); //$NON-NLS-1$
			Objects.requireNonNull(strategy, "strategy"); //$NON-NLS-1$
			Objects.requireNonNull(confidence, "confidence"); //$NON-NLS-1$
			explanations= copyExplanations(explanations);
			if (!hasCompleteUsageProof(protocol)) {
				throw new IllegalArgumentException(
						"A migration recommendation requires complete local usage without unresolved boundaries"); //$NON-NLS-1$
			}
		}

		@Override
		public List<String> explanations() {
			return List.copyOf(explanations);
		}
	}

	/** Positive conclusion that the observed lock-based protocol should not be replaced. */
	record RetainExistingLocking(
			ConcurrencyProtocol protocol,
			List<String> explanations) implements ConcurrencyProtocolAssessment {

		public RetainExistingLocking {
			Objects.requireNonNull(protocol, "protocol"); //$NON-NLS-1$
			explanations= copyExplanations(explanations);
			SynchronizationKind synchronization= protocol.summary().synchronization();
			if (!hasCompleteUsageProof(protocol)
					|| synchronization == SynchronizationKind.NONE
					|| synchronization == SynchronizationKind.UNKNOWN
					|| !protocol.hasSingleProtectingLock()) {
				throw new IllegalArgumentException(
						"Retaining existing locking requires complete usage proof and one protecting lock"); //$NON-NLS-1$
			}
		}

		@Override
		public List<String> explanations() {
			return List.copyOf(explanations);
		}
	}

	/** Actionable warning or observation that does not imply one replacement strategy. */
	record DiagnosticOnly(
			ConcurrencyProtocol protocol,
			Severity severity,
			List<String> explanations) implements ConcurrencyProtocolAssessment {

		public DiagnosticOnly {
			Objects.requireNonNull(protocol, "protocol"); //$NON-NLS-1$
			Objects.requireNonNull(severity, "severity"); //$NON-NLS-1$
			explanations= copyExplanations(explanations);
			if (protocol.completeness() == AnalysisCompleteness.REJECTED) {
				throw new IllegalArgumentException(
						"Rejected analysis must use the Rejected outcome"); //$NON-NLS-1$
			}
		}

		@Override
		public List<String> explanations() {
			return List.copyOf(explanations);
		}
	}

	/** Analysis result for unresolved, binary, stale or otherwise unsupported protocols. */
	record Rejected(
			ConcurrencyProtocol protocol,
			List<String> explanations) implements ConcurrencyProtocolAssessment {

		public Rejected {
			Objects.requireNonNull(protocol, "protocol"); //$NON-NLS-1$
			explanations= copyExplanations(explanations);
			if (protocol.completeness() != AnalysisCompleteness.REJECTED) {
				throw new IllegalArgumentException(
						"Rejected outcome requires a rejected protocol"); //$NON-NLS-1$
			}
		}

		@Override
		public List<String> explanations() {
			return List.copyOf(explanations);
		}
	}

	private static boolean hasCompleteUsageProof(ConcurrencyProtocol protocol) {
		AnalysisCompleteness completeness= protocol.completeness();
		return (completeness == AnalysisCompleteness.LOCAL_USAGE_COMPLETE
				|| completeness == AnalysisCompleteness.FLOW_COMPLETE)
				&& !protocol.hasUnresolvedBoundary();
	}

	private static List<String> copyExplanations(List<String> source) {
		List<String> result= Objects.requireNonNull(source, "explanations").stream() //$NON-NLS-1$
				.map(item -> Objects.requireNonNull(item, "explanation").strip()) //$NON-NLS-1$
				.toList();
		if (result.isEmpty() || result.stream().anyMatch(String::isEmpty)) {
			throw new IllegalArgumentException("At least one non-empty explanation is required"); //$NON-NLS-1$
		}
		return result;
	}

	/** Concurrency implementation family suggested by the report-only analyzer. */
	enum Strategy {
		COPY_ON_WRITE_SEQUENCE,
		COPY_ON_WRITE_SET,
		CONCURRENT_MEMBERSHIP_SET,
		CONCURRENT_QUEUE,
		CONCURRENT_DEQUE,
		IMMUTABLE_SNAPSHOT,
		PURPOSE_SPECIFIC_ABSTRACTION
	}

	/** Diagnostic importance independent of whether a rewrite exists. */
	enum Severity {
		INFO,
		WARNING,
		ERROR
	}
}
