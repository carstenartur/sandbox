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
import org.sandbox.jdt.container.api.ContainerFlowSearchPlan.SearchKind;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor.RuleOwnership;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.BridgeFeasibility;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PositionKind;
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

class ContainerSignatureAtomicityPlannerTest {

	private final ContainerSignatureAtomicityPlanner planner=
			new ContainerSignatureAtomicityPlanner();

	@Test
	void groupsOverrideParametersAndRequiresSemanticBridgePolicy() {
		FlowNode first= parameter("parameter:first:0", "first-handle", "First.java", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		FlowNode second= parameter("parameter:second:0", "second-handle", "Second.java", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		ResolvedContainerFlowSearchPlan resolved= new ResolvedContainerFlowSearchPlan(List.of(
				target("parameter:family:0", "first-handle", //$NON-NLS-1$ //$NON-NLS-2$
						SearchKind.METHOD_OVERRIDE_FAMILY, 0),
				target("parameter:family:0", "second-handle", //$NON-NLS-1$ //$NON-NLS-2$
						SearchKind.METHOD_OVERRIDE_FAMILY, 0)));

		ContainerSignatureMigrationPlan plan= planner.plan(
				component(List.of(first, second), ClosureStatus.LOCAL_CLOSED),
				resolved,
				recommendation());

		assertEquals(PlanningStatus.REPORT_ONLY, plan.status());
		assertEquals(1, plan.groups().size());
		var group= plan.groups().get(0);
		assertEquals(PositionKind.PARAMETER, group.positionKind());
		assertEquals(0, group.signatureIndex());
		assertEquals(List.of("first-handle", "second-handle"), //$NON-NLS-1$ //$NON-NLS-2$
				group.members().stream()
						.map(member -> member.javaElementHandle())
						.toList());
		assertEquals(BridgeFeasibility.OVERLOAD_POSSIBLE_POLICY_REQUIRED,
				group.bridgeFeasibility());
	}

	@Test
	void returnGroupRejectsSameNameDeprecatedBridge() {
		FlowNode returned= new FlowNode(
				"return:method", NodeKind.RETURN_POSITION, "", "method-key", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				"Owner.java", "method-handle", -1, true, 10, 2); //$NON-NLS-1$ //$NON-NLS-2$
		ResolvedContainerFlowSearchPlan resolved= new ResolvedContainerFlowSearchPlan(List.of(
				target("return:family", "method-handle", //$NON-NLS-1$ //$NON-NLS-2$
						SearchKind.METHOD_OVERRIDE_FAMILY, -1)));

		ContainerSignatureMigrationPlan plan= planner.plan(
				component(List.of(returned), ClosureStatus.LOCAL_CLOSED),
				resolved,
				recommendation());

		assertEquals(PlanningStatus.REPORT_ONLY, plan.status());
		assertEquals(PositionKind.RETURN, plan.groups().get(0).positionKind());
		assertEquals(BridgeFeasibility.SAME_NAME_RETURN_BRIDGE_IMPOSSIBLE,
				plan.groups().get(0).bridgeFeasibility());
		assertTrue(plan.groups().get(0).explanation().contains("return type")); //$NON-NLS-1$
	}

	@Test
	void rejectsSignaturePlanningBeforeFlowClosure() {
		FlowNode parameter= parameter("parameter:method:0", "method-handle", "Owner.java", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		ContainerSignatureMigrationPlan plan= planner.plan(
				component(List.of(parameter), ClosureStatus.REQUIRES_SCOPE_EXPANSION),
				new ResolvedContainerFlowSearchPlan(List.of(
						target(parameter.stableId(), "method-handle", //$NON-NLS-1$
								SearchKind.METHOD_OVERRIDE_FAMILY, 0))),
				recommendation());

		assertEquals(PlanningStatus.REJECTED, plan.status());
		assertEquals(DiagnosticKind.FLOW_NOT_CLOSED,
				plan.diagnostics().get(0).kind());
	}

	@Test
	void rejectsMissingExactSignatureMember() {
		FlowNode unrelated= parameter("parameter:other:0", "other-handle", "Other.java", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		ContainerSignatureMigrationPlan plan= planner.plan(
				component(List.of(unrelated), ClosureStatus.LOCAL_CLOSED),
				new ResolvedContainerFlowSearchPlan(List.of(
						target("parameter:family:0", "missing-handle", //$NON-NLS-1$ //$NON-NLS-2$
								SearchKind.METHOD_OVERRIDE_FAMILY, 0))),
				recommendation());

		assertEquals(PlanningStatus.REJECTED, plan.status());
		assertEquals(DiagnosticKind.MISSING_SIGNATURE_NODE,
				plan.diagnostics().get(0).kind());
	}

	@Test
	void callerOnlyPlanNeedsNoSignatureGroup() {
		FlowNode parameter= parameter("parameter:method:0", "method-handle", "Owner.java", 0); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		ContainerSignatureMigrationPlan plan= planner.plan(
				component(List.of(parameter), ClosureStatus.LOCAL_CLOSED),
				new ResolvedContainerFlowSearchPlan(List.of(
						target(parameter.stableId(), "method-handle", //$NON-NLS-1$
								SearchKind.METHOD_CALLERS, 0))),
				recommendation());

		assertEquals(PlanningStatus.NO_SIGNATURE_CHANGE, plan.status());
		assertTrue(plan.groups().isEmpty());
	}

	private static ContainerFlowComponent component(
			List<FlowNode> nodes,
			ClosureStatus status) {
		return new ContainerFlowComponent(
				nodes.get(0).stableId(), nodes, List.of(), status, List.of());
	}

	private static FlowNode parameter(
			String id,
			String handle,
			String unit,
			int index) {
		return new FlowNode(
				id, NodeKind.PARAMETER, "binding:" + handle, "owner:" + handle, //$NON-NLS-1$ //$NON-NLS-2$
				unit, handle, index, true, 1, 1);
	}

	private static ResolvedSearchTarget target(
			String sourceNodeId,
			String handle,
			SearchKind kind,
			int index) {
		return new ResolvedSearchTarget(
				sourceNodeId,
				kind,
				TargetKind.METHOD,
				"binding", //$NON-NLS-1$
				"owner-key", //$NON-NLS-1$
				handle,
				index,
				"Plan signature change"); //$NON-NLS-1$
	}

	private static ContainerRecommendation recommendation() {
		ContainerUsageProfile profile= new ContainerUsageProfile(
				new ContainerIdentity("binding", "values", 1, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, true, false, false, false, false),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.UNKNOWN,
				AliasingContract.UNKNOWN,
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
				profile, target, rule, Confidence.HIGH,
				AutomationLevel.REPORT_ONLY, List.of());
	}
}
