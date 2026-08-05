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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor.RuleOwnership;
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
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

class ContainerLocalRewritePlannerIterationTest {

	@Test
	void recordsThreadConfinedEncounterIterationAsAStalePlanCheck() {
		TargetContainerContract target= new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.ALLOWED,
				"Use a mutable dynamic sequence."); //$NON-NLS-1$
		ContainerUsageProfile profile= new ContainerUsageProfile(
				new ContainerIdentity("binding", "values", 10, 1), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, true, true, false, false, false, false),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.ALLOWED,
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.LOCAL,
				new ConcurrencyProfile(
						ThreadExposure.THREAD_CONFINED,
						SynchronizationKind.NONE,
						IterationSemantics.LIVE,
						AtomicityRequirement.INDIVIDUAL_OPERATIONS,
						WorkloadShape.READ_MOSTLY),
				AnalysisCompleteness.FLOW_COMPLETE,
				List.of(
						new UsageEvidence(Kind.REFERENCE_COMPONENT, "Reference component.", 10, 1), //$NON-NLS-1$
						new UsageEvidence(Kind.ARRAY_GROWTH, "Array grows by one.", 20, 4), //$NON-NLS-1$
						new UsageEvidence(Kind.APPEND_WRITE, "Tail slot receives the value.", 30, 4), //$NON-NLS-1$
						new UsageEvidence(Kind.ENCOUNTER_ITERATION, "Encounter order is observed.", 45, 3), //$NON-NLS-1$
						new UsageEvidence(Kind.LOCAL_USAGE_COMPLETE, "Local uses are complete.", 10, 1))); //$NON-NLS-1$
		FlowNode root= new FlowNode(
				"local:binding", //$NON-NLS-1$
				NodeKind.LOCAL_VARIABLE,
				"binding", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				"Owner.java", //$NON-NLS-1$
				"local-handle", //$NON-NLS-1$
				-1,
				true,
				10,
				1);
		ContainerFlowComponent component= new ContainerFlowComponent(
				root.stableId(), List.of(root), List.of(),
				ClosureStatus.LOCAL_CLOSED, List.of());
		ContainerRuleDescriptor rule= new ContainerRuleDescriptor(
				"semantic.array.append.sequence", //$NON-NLS-1$
				ContainerShape.ARRAY,
				ContainerShape.LIST,
				RuleOwnership.NOVEL,
				"", //$NON-NLS-1$
				"The migration changes representation."); //$NON-NLS-1$
		ContainerRecommendation recommendation= new ContainerRecommendation(
				profile,
				target,
				rule,
				Confidence.HIGH,
				AutomationLevel.AUTOMATIC,
				List.of());
		ContainerMigrationReadiness readiness= new ContainerMigrationReadiness(
				target, ExecutionStatus.AUTOMATIC, List.of());

		var result= new ContainerLocalRewritePlanner().plan(
				component, recommendation, readiness);

		assertTrue(result.ready());
		var iterationChecks= result.plan().orElseThrow().edits().stream()
				.filter(edit -> edit.kind() == EditKind.VERIFY_ENCOUNTER_ITERATION)
				.toList();
		assertEquals(1, iterationChecks.size());
		assertEquals(45, iterationChecks.get(0).sourceStart());
	}
}
