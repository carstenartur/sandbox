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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor.RuleOwnership;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus;
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
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.ResolvedSearchTarget;
import org.sandbox.jdt.container.api.ResolvedContainerFlowSearchPlan.TargetKind;
import org.sandbox.jdt.container.api.TargetContainerContract;
import org.sandbox.jdt.container.api.TargetContainerContract.Mutability;

class ClosedSourceSignatureBoundaryTest {

	private final ContainerSignatureAtomicityPlanner planner=
			new ContainerSignatureAtomicityPlanner();

	@Test
	void returnGroupRemainsRejectedForAutomaticExecution() {
		FlowNode returned= new FlowNode(
				"return:produce", //$NON-NLS-1$
				NodeKind.RETURN_POSITION,
				"", //$NON-NLS-1$
				"produce-key", //$NON-NLS-1$
				"Produce.java", //$NON-NLS-1$
				"produce-handle", //$NON-NLS-1$
				-1,
				true,
				10,
				6);

		var plan= planner.planClosedSource(
				component(List.of(returned)),
				resolved(returned, SearchKind.METHOD_DECLARATION, -1),
				recommendation());

		assertEquals(PlanningStatus.REJECTED, plan.status());
		assertEquals(DiagnosticKind.UNSUPPORTED_AUTOMATIC_GROUP,
				plan.diagnostics().get(0).kind());
	}

	@Test
	void overrideFamilyRemainsRejectedForAutomaticExecution() {
		FlowNode first= parameter(
				"parameter:first:0", "First.java", "first-handle"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		FlowNode second= parameter(
				"parameter:second:0", "Second.java", "second-handle"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ResolvedContainerFlowSearchPlan resolved= new ResolvedContainerFlowSearchPlan(List.of(
				target(first, SearchKind.METHOD_OVERRIDE_FAMILY, 0,
						"parameter:family:0"), //$NON-NLS-1$
				target(second, SearchKind.METHOD_OVERRIDE_FAMILY, 0,
						"parameter:family:0"))); //$NON-NLS-1$

		var plan= planner.planClosedSource(
				component(List.of(first, second)), resolved, recommendation());

		assertEquals(PlanningStatus.REJECTED, plan.status());
		assertEquals(DiagnosticKind.UNSUPPORTED_AUTOMATIC_GROUP,
				plan.diagnostics().get(0).kind());
	}

	private static FlowNode parameter(String id, String unit, String handle) {
		return new FlowNode(
				id,
				NodeKind.PARAMETER,
				"binding:" + handle, //$NON-NLS-1$
				"owner:" + handle, //$NON-NLS-1$
				unit,
				handle,
				0,
				true,
				10,
				6);
	}

	private static ContainerFlowComponent component(List<FlowNode> nodes) {
		return new ContainerFlowComponent(
				nodes.get(0).stableId(),
				nodes,
				List.of(),
				ClosureStatus.LOCAL_CLOSED,
				List.of());
	}

	private static ResolvedContainerFlowSearchPlan resolved(
			FlowNode node,
			SearchKind kind,
			int index) {
		return new ResolvedContainerFlowSearchPlan(
				List.of(target(node, kind, index, node.stableId())));
	}

	private static ResolvedSearchTarget target(
			FlowNode node,
			SearchKind kind,
			int index,
			String sourceNodeId) {
		return new ResolvedSearchTarget(
				sourceNodeId,
				kind,
				TargetKind.METHOD,
				node.bindingKey().isBlank() ? "binding" : node.bindingKey(), //$NON-NLS-1$
				node.ownerKey(),
				node.javaElementHandle(),
				index,
				"Plan the exact signature declaration"); //$NON-NLS-1$
	}

	private static ContainerRecommendation recommendation() {
		ContainerUsageProfile profile= new ContainerUsageProfile(
				new ContainerIdentity("binding", "values", 1, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, false, false, false, false, false),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.UNKNOWN,
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.METHOD_BOUNDARY,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.FLOW_COMPLETE,
				List.of());
		TargetContainerContract target= new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.UNKNOWN,
				"Use a dynamic sequence contract."); //$NON-NLS-1$
		ContainerRuleDescriptor rule= new ContainerRuleDescriptor(
				"semantic.array.append.sequence", //$NON-NLS-1$
				ContainerShape.ARRAY,
				ContainerShape.LIST,
				RuleOwnership.NOVEL,
				"", //$NON-NLS-1$
				"The migration changes representation and signatures."); //$NON-NLS-1$
		return new ContainerRecommendation(
				profile,
				target,
				rule,
				Confidence.HIGH,
				AutomationLevel.REPORT_ONLY,
				List.of());
	}
}
