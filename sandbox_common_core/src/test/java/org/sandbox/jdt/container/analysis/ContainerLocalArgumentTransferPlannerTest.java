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
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.ArgumentTransfer;
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
import org.sandbox.jdt.container.api.ContainerUsageProfile.ConcurrencyProfile;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ContainerIdentity;
import org.sandbox.jdt.container.api.ContainerUsageProfile.ElementDomain;
import org.sandbox.jdt.container.api.ContainerUsageProfile.EscapeLevel;
import org.sandbox.jdt.container.api.ContainerUsageProfile.MutationLifecycle;
import org.sandbox.jdt.container.api.ContainerUsageProfile.NullContract;
import org.sandbox.jdt.container.api.ContainerUsageProfile.OrderRequirement;
import org.sandbox.jdt.container.api.ContainerUsageProfile.UniquenessRequirement;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;
import org.sandbox.jdt.container.api.UsageEvidence;
import org.sandbox.jdt.container.api.UsageEvidence.Kind;

class ContainerLocalArgumentTransferPlannerTest {

	@Test
	void turnsClosedFlowEvidenceIntoAnExactTargetVerification() {
		ContainerUsageProfile profile= profile();
		TargetContainerContract target= target();
		ContainerRecommendation recommendation= new ContainerRecommendation(
				profile,
				target,
				new ContainerRuleDescriptor(
						"semantic.array.append.sequence", //$NON-NLS-1$
						ContainerShape.ARRAY,
						ContainerShape.LIST,
						RuleOwnership.NOVEL,
						"", //$NON-NLS-1$
						"The migration changes the representation."), //$NON-NLS-1$
				Confidence.HIGH,
				AutomationLevel.AUTOMATIC,
				List.of());
		ContainerMigrationReadiness readiness= new ContainerMigrationReadiness(
				target, ExecutionStatus.AUTOMATIC, List.of());
		ArgumentTransfer transfer= new ArgumentTransfer(
				"receiver-method", 0, 80, 6); //$NON-NLS-1$

		var result= new ContainerLocalRewritePlanner().plan(
				component(), recommendation, readiness, List.of(transfer));

		assertTrue(result.ready());
		var plan= result.plan().orElseThrow();
		var transferEdits= plan.edits().stream()
				.filter(edit -> edit.kind() == EditKind.VERIFY_ARGUMENT_TRANSFER)
				.toList();
		assertEquals(1, transferEdits.size());
		assertEquals(80, transferEdits.get(0).sourceStart());
		assertEquals(6, transferEdits.get(0).sourceLength());
		assertEquals(List.of(transfer), plan.argumentTransfers());
	}

	private static ContainerUsageProfile profile() {
		return new ContainerUsageProfile(
				new ContainerIdentity("binding", "values", 10, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, true, false, false, false, false),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.ALLOWED,
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.METHOD_BOUNDARY,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.FLOW_COMPLETE,
				List.of(
						new UsageEvidence(Kind.REFERENCE_COMPONENT,
								"Reference component", 10, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.ARRAY_GROWTH,
								"Array grows", 20, 20), //$NON-NLS-1$
						new UsageEvidence(Kind.APPEND_WRITE,
								"Tail slot is written", 45, 20), //$NON-NLS-1$
						new UsageEvidence(Kind.FLOW_CONTINUATION_ROOT,
								"Argument transfer is closed", 80, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.LOCAL_USAGE_COMPLETE,
								"Flow usage is complete", 10, 6))); //$NON-NLS-1$
	}

	private static ContainerFlowComponent component() {
		FlowNode local= new FlowNode(
				"local:binding", //$NON-NLS-1$
				NodeKind.LOCAL_VARIABLE,
				"binding", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				"Caller.java", //$NON-NLS-1$
				"local-handle", //$NON-NLS-1$
				-1,
				true,
				10,
				6);
		return new ContainerFlowComponent(
				local.stableId(), List.of(local), List.of(),
				ClosureStatus.LOCAL_CLOSED, List.of());
	}

	private static TargetContainerContract target() {
		return new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.ALLOWED,
				"Use a mutable dynamic sequence."); //$NON-NLS-1$
	}
}
