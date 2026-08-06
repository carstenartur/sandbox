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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
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
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan.DiagnosticKind;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan.EditKind;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan.PlanningDiagnostic;
import org.sandbox.jdt.container.api.UniqueSequenceLocalRewritePlan.PlanningResult;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

class UniqueSequenceLocalRewritePlannerTest {

	@Test
	void infersAndPlansAnEncounterOrderedSet() {
		ContainerRecommendation recommendation=
				new UniqueSequenceContractInferrer().infer(profile()).orElseThrow();
		ContainerMigrationReadiness readiness= new ContainerMigrationReadiness(
				recommendation.targetContract(), ExecutionStatus.AUTOMATIC, List.of());

		PlanningResult result= new UniqueSequenceLocalRewritePlanner().plan(
				"=project/src<test{Sample.java", recommendation, readiness); //$NON-NLS-1$

		assertTrue(result.ready());
		var plan= result.plan().orElseThrow();
		assertEquals("java.util.Set", plan.targetInterfaceType()); //$NON-NLS-1$
		assertEquals("java.util.LinkedHashSet", plan.targetImplementationType()); //$NON-NLS-1$
		assertTrue(plan.edits().stream()
				.anyMatch(edit -> edit.kind() == EditKind.REPLACE_DUPLICATE_GUARD));
		assertEquals(ContainerShape.SET, recommendation.targetContract().shape());
	}

	@Test
	void planningResultRequiresExactlyOneOutcome() {
		ContainerRecommendation recommendation=
				new UniqueSequenceContractInferrer().infer(profile()).orElseThrow();
		ContainerMigrationReadiness readiness= new ContainerMigrationReadiness(
				recommendation.targetContract(), ExecutionStatus.AUTOMATIC, List.of());
		PlanningResult ready= new UniqueSequenceLocalRewritePlanner().plan(
				"=project/src<test{Sample.java", recommendation, readiness); //$NON-NLS-1$
		PlanningDiagnostic diagnostic= new PlanningDiagnostic(
				DiagnosticKind.NOT_AUTOMATIC, "Execution is blocked"); //$NON-NLS-1$

		PlanningResult rejected= PlanningResult.rejected(List.of(diagnostic));

		assertTrue(ready.ready());
		assertTrue(ready.diagnostics().isEmpty());
		assertFalse(rejected.ready());
		assertEquals(List.of(diagnostic), rejected.diagnostics());
		assertThrows(IllegalArgumentException.class,
				() -> new PlanningResult(Optional.empty(), List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new PlanningResult(ready.plan(), List.of(diagnostic)));
	}

	private static ContainerUsageProfile profile() {
		return new ContainerUsageProfile(
				new ContainerIdentity("binding", "values", 10, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.LIST,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, true, false, false, true, false),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.REQUIRED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.UNKNOWN,
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.LOCAL,
				new ConcurrencyProfile(
						ThreadExposure.THREAD_CONFINED,
						SynchronizationKind.NONE,
						IterationSemantics.LIVE,
						AtomicityRequirement.INDIVIDUAL_OPERATIONS,
						WorkloadShape.BALANCED),
				AnalysisCompleteness.LOCAL_USAGE_COMPLETE,
				List.of(
						new UsageEvidence(
								Kind.REFERENCE_COMPONENT,
								"reference element", 10, 6), //$NON-NLS-1$
						new UsageEvidence(
								Kind.HASH_STABLE_COMPONENT,
								"stable equality and hash", 10, 6), //$NON-NLS-1$
						new UsageEvidence(
								Kind.DUPLICATE_SUPPRESSION,
								"guarded insertion", 30, 40), //$NON-NLS-1$
						new UsageEvidence(
								Kind.ENCOUNTER_ITERATION,
								"ordered iteration", 80, 6), //$NON-NLS-1$
						new UsageEvidence(
								Kind.LOCAL_USAGE_COMPLETE,
								"complete local proof", 10, 6))); //$NON-NLS-1$
	}
}
