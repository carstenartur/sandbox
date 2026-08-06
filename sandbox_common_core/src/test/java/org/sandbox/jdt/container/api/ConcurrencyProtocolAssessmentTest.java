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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ConcurrencyProtocol.Evidence;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.EvidenceKind;
import org.sandbox.jdt.container.api.ConcurrencyProtocol.ReentrancyContract;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.DiagnosticOnly;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.RecommendedMigration;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Rejected;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.RetainExistingLocking;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Severity;
import org.sandbox.jdt.container.api.ConcurrencyProtocolAssessment.Strategy;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AnalysisCompleteness;
import org.sandbox.jdt.container.api.ContainerUsageProfile.AtomicityRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.IterationSemantics;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.SynchronizationKind;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ThreadExposure;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.WorkloadShape;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;

class ConcurrencyProtocolAssessmentTest {

	@Test
	void copiesEvidenceAndReportsOneProtectingLock() {
		List<Evidence> evidence= new ArrayList<>();
		evidence.add(locked(EvidenceKind.LOCKED_READ, "this", 20)); //$NON-NLS-1$
		evidence.add(locked(EvidenceKind.LOCKED_WRITE, "this", 40)); //$NON-NLS-1$

		ConcurrencyProtocol protocol= protocol(
				SynchronizationKind.INTRINSIC_LOCK,
				AnalysisCompleteness.FLOW_COMPLETE,
				evidence);
		evidence.clear();

		assertEquals(2, protocol.evidence().size());
		assertEquals(Set.of("this"), protocol.lockIdentities()); //$NON-NLS-1$
		assertTrue(protocol.hasSingleProtectingLock());
		assertFalse(protocol.hasUnresolvedBoundary());
		assertThrows(UnsupportedOperationException.class,
				() -> protocol.evidence().add(locked(EvidenceKind.LOCKED_READ, "this", 60))); //$NON-NLS-1$
	}

	@Test
	void rejectsLockedEvidenceWithoutStableLockIdentity() {
		assertThrows(IllegalArgumentException.class,
				ConcurrencyProtocolAssessmentTest::createLockedEvidenceWithoutIdentity);
	}

	@Test
	void retainsCoherentExistingLockingAsPositiveOutcome() {
		ConcurrencyProtocol protocol= protocol(
				SynchronizationKind.INTRINSIC_LOCK,
				AnalysisCompleteness.FLOW_COMPLETE,
				List.of(
						locked(EvidenceKind.LOCKED_ITERATION, "listeners", 20), //$NON-NLS-1$
						locked(EvidenceKind.COMPOUND_UPDATE, "listeners", 40))); //$NON-NLS-1$
		List<String> explanations= new ArrayList<>();
		explanations.add("The same monitor protects iteration and compound updates."); //$NON-NLS-1$

		RetainExistingLocking result= new RetainExistingLocking(protocol, explanations);
		explanations.clear();

		assertFalse(result.permitsSourceRewrite());
		assertEquals(protocol, result.protocol());
		assertEquals(1, result.explanations().size());
		assertThrows(UnsupportedOperationException.class,
				() -> result.explanations().add("mutable")); //$NON-NLS-1$
	}

	@Test
	void retainingExistingLockingRequiresCompleteUsageProof() {
		ConcurrencyProtocol seedOnly= protocol(
				SynchronizationKind.INTRINSIC_LOCK,
				AnalysisCompleteness.LOCAL_SEED,
				List.of(locked(EvidenceKind.LOCKED_READ, "listeners", 20))); //$NON-NLS-1$

		assertThrows(IllegalArgumentException.class,
				() -> createRetainExistingLocking(seedOnly));
	}

	@Test
	void retainingExistingLockingRequiresLockBasedSynchronization() {
		ConcurrencyProtocol snapshot= protocol(
				SynchronizationKind.VOLATILE_SNAPSHOT,
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				List.of(locked(EvidenceKind.LOCKED_READ, "listeners", 20))); //$NON-NLS-1$

		assertThrows(IllegalArgumentException.class,
				() -> createRetainExistingLocking(snapshot));
	}

	@Test
	void recommendationRemainsReportOnlyAndRequiresResolvedUsage() {
		ConcurrencyProtocol protocol= protocol(
				SynchronizationKind.VOLATILE_SNAPSHOT,
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				List.of(new Evidence(EvidenceKind.SNAPSHOT_PUBLICATION, "", //$NON-NLS-1$
						"Readers observe an immutable volatile snapshot.", 20, 10))); //$NON-NLS-1$
		TargetContainerContract target= targetContract();

		RecommendedMigration result= new RecommendedMigration(
				protocol,
				target,
				Strategy.COPY_ON_WRITE_SEQUENCE,
				Confidence.MEDIUM,
				List.of("Frequent iteration and snapshot publication support this candidate.")); //$NON-NLS-1$

		assertFalse(result.permitsSourceRewrite());
		assertEquals(target, result.targetContract());

		ConcurrencyProtocol unresolved= protocol(
				SynchronizationKind.VOLATILE_SNAPSHOT,
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				List.of(new Evidence(EvidenceKind.UNRESOLVED_BOUNDARY, "", //$NON-NLS-1$
						"A framework callback could not be resolved.", 60, 8))); //$NON-NLS-1$
		assertThrows(IllegalArgumentException.class,
				() -> createRecommendedMigration(unresolved, target));
	}

	@Test
	void diagnosticRepresentsUnprotectedAccessWithoutSelectingTarget() {
		ConcurrencyProtocol protocol= protocol(
				SynchronizationKind.SYNCHRONIZED_WRAPPER,
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				List.of(new Evidence(EvidenceKind.UNPROTECTED_ACCESS, "", //$NON-NLS-1$
						"Iteration occurs without synchronizing on the wrapper.", 30, 12))); //$NON-NLS-1$

		DiagnosticOnly result= new DiagnosticOnly(protocol, Severity.WARNING,
				List.of("Synchronized-wrapper iteration requires external locking.")); //$NON-NLS-1$

		assertFalse(result.permitsSourceRewrite());
		assertFalse(protocol.hasSingleProtectingLock());
		assertEquals(Severity.WARNING, result.severity());
	}

	@Test
	void rejectedOutcomeRequiresRejectedProtocol() {
		ConcurrencyProtocol rejected= protocol(
				SynchronizationKind.UNKNOWN,
				AnalysisCompleteness.REJECTED,
				List.of(new Evidence(EvidenceKind.UNRESOLVED_BOUNDARY, "", //$NON-NLS-1$
						"Binary consumers prevent a closed protocol proof.", 80, 5))); //$NON-NLS-1$

		Rejected result= new Rejected(rejected,
				List.of("Binary consumers are outside the editable source scope.")); //$NON-NLS-1$

		assertFalse(result.permitsSourceRewrite());
		assertTrue(rejected.hasUnresolvedBoundary());
		assertThrows(IllegalArgumentException.class,
				ConcurrencyProtocolAssessmentTest::createRejectedOutcomeForSupportedProtocol);
	}

	private static Evidence createLockedEvidenceWithoutIdentity() {
		return new Evidence(EvidenceKind.CHECK_THEN_ACT, "", //$NON-NLS-1$
				"contains followed by add", 10, 5); //$NON-NLS-1$
	}

	private static RetainExistingLocking createRetainExistingLocking(ConcurrencyProtocol protocol) {
		return new RetainExistingLocking(protocol,
				List.of("The protocol does not meet the retained-locking proof boundary.")); //$NON-NLS-1$
	}

	private static RecommendedMigration createRecommendedMigration(
			ConcurrencyProtocol protocol,
			TargetContainerContract target) {
		return new RecommendedMigration(protocol, target,
				Strategy.COPY_ON_WRITE_SEQUENCE, Confidence.LOW,
				List.of("Unresolved evidence must reject this recommendation.")); //$NON-NLS-1$
	}

	private static Rejected createRejectedOutcomeForSupportedProtocol() {
		return new Rejected(protocol(
				SynchronizationKind.NONE,
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				List.of()),
				List.of("A supported protocol cannot use the rejected outcome.")); //$NON-NLS-1$
	}

	private static TargetContainerContract targetContract() {
		return new TargetContainerContract(
				ContainerShape.CONCURRENT_CONTAINER,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.REJECTED,
				"Copy-on-write snapshot semantics match the observed reader contract."); //$NON-NLS-1$
	}

	private static ConcurrencyProtocol protocol(
			SynchronizationKind synchronization,
			AnalysisCompleteness completeness,
			List<Evidence> evidence) {
		return new ConcurrencyProtocol(
				new ContainerIdentity("Lexample/Owner;.listeners", "listeners", 10, 9), //$NON-NLS-1$ //$NON-NLS-2$
				new ConcurrencyProfile(
						ThreadExposure.WORKER_SHARED,
						synchronization,
						IterationSemantics.EXTERNALLY_LOCKED,
						AtomicityRequirement.COMPOUND_UPDATE,
						WorkloadShape.BALANCED),
				ReentrancyContract.NO_CALLBACKS_OBSERVED,
				completeness,
				evidence);
	}

	private static Evidence locked(EvidenceKind kind, String lockIdentity, int sourceStart) {
		return new Evidence(kind, lockIdentity, kind.name(), sourceStart, 5);
	}
}
