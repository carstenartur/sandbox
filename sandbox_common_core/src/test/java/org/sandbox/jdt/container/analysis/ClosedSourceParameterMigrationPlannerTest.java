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

import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
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
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.BridgeFeasibility;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PositionKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureAtomicityGroup;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureMember;
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

class ClosedSourceParameterMigrationPlannerTest {

	@Test
	void combinesCallerAndParameterIntoOneTwoUnitPlan() {
		ContainerUsageProfile caller= callerProfile();
		ContainerUsageProfile parameter= parameterProfile();
		TargetContainerContract target= target();
		ContainerRecommendation recommendation= recommendation(caller, target);
		ContainerMigrationReadiness readiness= new ContainerMigrationReadiness(
				target, ExecutionStatus.AUTOMATIC, List.of());
		ContainerSignatureMigrationPlan signatures= signaturePlan(target);

		var result= new ClosedSourceParameterMigrationPlanner().plan(
				component(), signatures, recommendation, readiness,
				List.of(caller, parameter));

		assertTrue(result.ready());
		ClosedSourceParameterMigrationPlan plan= result.plan().orElseThrow();
		assertEquals(List.of("Caller.java", "Receiver.java"), //$NON-NLS-1$ //$NON-NLS-2$
				plan.affectedCompilationUnitHandles());
		assertEquals(1, plan.callerPlan().edits().stream()
				.filter(edit -> edit.kind() == EditKind.VERIFY_ARGUMENT_TRANSFER)
				.count());
		assertEquals(0, plan.parameterPlan().parameterIndex());
	}

	private static ContainerFlowComponent component() {
		FlowNode caller= callerNode();
		FlowNode parameter= parameterNode();
		return new ContainerFlowComponent(
				caller.stableId(),
				List.of(caller, parameter),
				List.of(new LocatedFlowEdge(
						"Caller.java", //$NON-NLS-1$
						caller.stableId(),
						parameter.stableId(),
						EdgeKind.ARGUMENT_TO_PARAMETER,
						80,
						6)),
				ClosureStatus.LOCAL_CLOSED,
				List.of());
	}

	private static ContainerSignatureMigrationPlan signaturePlan(
			TargetContainerContract target) {
		SignatureMember member= new SignatureMember(
				"receiver-method", //$NON-NLS-1$
				"receiver-owner", //$NON-NLS-1$
				"Receiver.java", //$NON-NLS-1$
				parameterNode().stableId());
		SignatureAtomicityGroup group= new SignatureAtomicityGroup(
				"receiver:parameter:0", //$NON-NLS-1$
				PositionKind.PARAMETER,
				0,
				List.of(member),
				BridgeFeasibility.OVERLOAD_POSSIBLE_POLICY_REQUIRED,
				"Every source caller and the parameter are replaced atomically."); //$NON-NLS-1$
		return new ContainerSignatureMigrationPlan(
				target,
				List.of(group),
				PlanningStatus.CLOSED_SOURCE_AUTOMATIC,
				List.of());
	}

	private static FlowNode callerNode() {
		return new FlowNode(
				"local:caller", //$NON-NLS-1$
				NodeKind.LOCAL_VARIABLE,
				"caller-binding", //$NON-NLS-1$
				"caller-owner", //$NON-NLS-1$
				"Caller.java", //$NON-NLS-1$
				"caller-local", //$NON-NLS-1$
				-1,
				true,
				10,
				6);
	}

	private static FlowNode parameterNode() {
		return new FlowNode(
				"parameter:receiver:0", //$NON-NLS-1$
				NodeKind.PARAMETER,
				"parameter-binding", //$NON-NLS-1$
				"receiver-owner", //$NON-NLS-1$
				"Receiver.java", //$NON-NLS-1$
				"receiver-method", //$NON-NLS-1$
				0,
				true,
				20,
				6);
	}

	private static ContainerUsageProfile callerProfile() {
		return new ContainerUsageProfile(
				new ContainerIdentity("caller-binding", "values", 10, 6), //$NON-NLS-1$ //$NON-NLS-2$
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
								"Array grows", 30, 10), //$NON-NLS-1$
						new UsageEvidence(Kind.APPEND_WRITE,
								"Tail slot is written", 45, 10), //$NON-NLS-1$
						new UsageEvidence(Kind.FLOW_CONTINUATION_ROOT,
								"Argument transfer is closed", 80, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.LOCAL_USAGE_COMPLETE,
								"Flow is complete", 10, 6))); //$NON-NLS-1$
	}

	private static ContainerUsageProfile parameterProfile() {
		return new ContainerUsageProfile(
				new ContainerIdentity("parameter-binding", "values", 20, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, false, false, false, false, false),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.ALLOWED,
				AliasingContract.NO_OBSERVED_ALIAS,
				EscapeLevel.METHOD_BOUNDARY,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.FLOW_COMPLETE,
				List.of(
						new UsageEvidence(Kind.FLOW_CONTINUATION_ROOT,
								"Parameter is the continuation root", 20, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.REFERENCE_COMPONENT,
								"Reference component", 20, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.ARRAY_LENGTH_READ,
								"Length is read", 40, 13), //$NON-NLS-1$
						new UsageEvidence(Kind.ENCOUNTER_ITERATION,
								"Encounter order is observed", 60, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.LOCAL_USAGE_COMPLETE,
								"Parameter use is complete", 20, 6))); //$NON-NLS-1$
	}

	private static ContainerRecommendation recommendation(
			ContainerUsageProfile caller,
			TargetContainerContract target) {
		return new ContainerRecommendation(
				caller,
				target,
				new ContainerRuleDescriptor(
						"semantic.array.append.sequence", //$NON-NLS-1$
						ContainerShape.ARRAY,
						ContainerShape.LIST,
						RuleOwnership.NOVEL,
						"", //$NON-NLS-1$
						"The representation and parameter signature migrate together."), //$NON-NLS-1$
				Confidence.HIGH,
				AutomationLevel.AUTOMATIC,
				List.of());
	}

	private static TargetContainerContract target() {
		return new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.ALLOWED,
				"Use one mutable dynamic sequence contract."); //$NON-NLS-1$
	}
}
