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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.ArgumentTransfer;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.EditKind;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan;
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
	void combinesCallerAndCompleteOverrideFamilyIntoOnePlan() {
		ClosedSourceParameterMigrationPlan plan= readyPlan();

		assertEquals(List.of(
				"Caller.java", "Contract.java", "First.java", "Second.java"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				plan.affectedCompilationUnitHandles());
		assertEquals(3, plan.parameterPlans().size());
		assertEquals(List.of(
				"contract-method", "first-method", "second-method"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				plan.parameterPlans().stream()
						.map(ContainerParameterRewritePlan::methodJavaElementHandle)
						.toList());
		assertEquals(1, plan.callerPlan().edits().stream()
				.filter(edit -> edit.kind() == EditKind.VERIFY_ARGUMENT_TRANSFER)
				.count());
		assertEquals("contract-method", //$NON-NLS-1$
				plan.callerPlan().argumentTransfers().get(0)
						.methodJavaElementHandle());
	}

	@Test
	void missingOverrideProfileRejectsTheWholeFamily() {
		TargetContainerContract target= target();
		ContainerUsageProfile caller= callerProfile();
		ContainerRecommendation recommendation= recommendation(caller, target);
		ContainerMigrationReadiness readiness= new ContainerMigrationReadiness(
				target, ExecutionStatus.AUTOMATIC, List.of());

		var result= new ClosedSourceParameterMigrationPlanner().plan(
				component(),
				signaturePlan(target),
				recommendation,
				readiness,
				List.of(
						caller,
						parameterProfile("interface-binding", 20), //$NON-NLS-1$
						parameterProfile("first-binding", 40))); //$NON-NLS-1$

		assertFalse(result.ready());
		assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
				diagnostic.kind()
						== ClosedSourceParameterMigrationPlan.DiagnosticKind.PROFILE_NOT_FOUND));
	}

	@Test
	void duplicateParameterTargetsAreRejected() {
		ClosedSourceParameterMigrationPlan plan= readyPlan();
		ContainerParameterRewritePlan parameter= plan.parameterPlans().get(0);

		assertThrows(IllegalArgumentException.class,
				() -> assertNotNull(new ClosedSourceParameterMigrationPlan(
						plan.targetContract(),
						plan.callerPlan(),
						List.of(parameter, parameter))));
	}

	@Test
	void argumentTransferWithoutMatchingParameterIsRejected() {
		ClosedSourceParameterMigrationPlan plan= readyPlan();
		ContainerLocalRewritePlan caller= plan.callerPlan();
		ArgumentTransfer transfer= caller.argumentTransfers().get(0);
		ContainerLocalRewritePlan mismatchedCaller= new ContainerLocalRewritePlan(
				caller.compilationUnitHandle(),
				caller.bindingKey(),
				caller.targetInterfaceType(),
				caller.targetImplementationType(),
				caller.targetContract(),
				caller.edits(),
				List.of(new ArgumentTransfer(
						"missing-method", //$NON-NLS-1$
						transfer.parameterIndex(),
						transfer.sourceStart(),
						transfer.sourceLength())));

		assertThrows(IllegalArgumentException.class,
				() -> assertNotNull(new ClosedSourceParameterMigrationPlan(
						plan.targetContract(),
						mismatchedCaller,
						plan.parameterPlans())));
	}

	private static ClosedSourceParameterMigrationPlan readyPlan() {
		TargetContainerContract target= target();
		ContainerUsageProfile caller= callerProfile();
		List<ContainerUsageProfile> parameters= List.of(
				parameterProfile("interface-binding", 20), //$NON-NLS-1$
				parameterProfile("first-binding", 40), //$NON-NLS-1$
				parameterProfile("second-binding", 60)); //$NON-NLS-1$
		ContainerRecommendation recommendation= recommendation(caller, target);
		ContainerMigrationReadiness readiness= new ContainerMigrationReadiness(
				target, ExecutionStatus.AUTOMATIC, List.of());
		ContainerSignatureMigrationPlan signatures= signaturePlan(target);

		List<ContainerUsageProfile> profiles= new ArrayList<>(parameters.size() + 1);
		profiles.add(caller);
		profiles.addAll(parameters);
		var result= new ClosedSourceParameterMigrationPlanner().plan(
				component(), signatures, recommendation, readiness, profiles);

		assertTrue(result.ready());
		return result.plan().orElseThrow();
	}

	private static ContainerFlowComponent component() {
		FlowNode caller= callerNode();
		FlowNode contract= parameterNode(
				"parameter:contract:0", "interface-binding", //$NON-NLS-1$ //$NON-NLS-2$
				"contract-owner", "Contract.java", "contract-method", 20); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		FlowNode first= parameterNode(
				"parameter:first:0", "first-binding", //$NON-NLS-1$ //$NON-NLS-2$
				"first-owner", "First.java", "first-method", 40); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		FlowNode second= parameterNode(
				"parameter:second:0", "second-binding", //$NON-NLS-1$ //$NON-NLS-2$
				"second-owner", "Second.java", "second-method", 60); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return new ContainerFlowComponent(
				caller.stableId(),
				List.of(caller, contract, first, second),
				List.of(new LocatedFlowEdge(
						"Caller.java", //$NON-NLS-1$
						caller.stableId(),
						contract.stableId(),
						EdgeKind.ARGUMENT_TO_PARAMETER,
						80,
						6)),
				ClosureStatus.LOCAL_CLOSED,
				List.of());
	}

	private static ContainerSignatureMigrationPlan signaturePlan(
			TargetContainerContract target) {
		SignatureAtomicityGroup group= new SignatureAtomicityGroup(
				"parameter:family:0", //$NON-NLS-1$
				PositionKind.PARAMETER,
				0,
				List.of(
						member("contract-method", "contract-owner", //$NON-NLS-1$ //$NON-NLS-2$
								"Contract.java", "parameter:contract:0"), //$NON-NLS-1$ //$NON-NLS-2$
						member("first-method", "first-owner", //$NON-NLS-1$ //$NON-NLS-2$
								"First.java", "parameter:first:0"), //$NON-NLS-1$ //$NON-NLS-2$
						member("second-method", "second-owner", //$NON-NLS-1$ //$NON-NLS-2$
								"Second.java", "parameter:second:0")), //$NON-NLS-1$ //$NON-NLS-2$
				BridgeFeasibility.OVERLOAD_POSSIBLE_POLICY_REQUIRED,
				"Every source declaration is replaced atomically."); //$NON-NLS-1$
		return new ContainerSignatureMigrationPlan(
				target,
				List.of(group),
				PlanningStatus.CLOSED_SOURCE_AUTOMATIC,
				List.of());
	}

	private static SignatureMember member(
			String handle,
			String owner,
			String unit,
			String nodeId) {
		return new SignatureMember(handle, owner, unit, nodeId);
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

	private static FlowNode parameterNode(
			String id,
			String binding,
			String owner,
			String unit,
			String method,
			int sourceStart) {
		return new FlowNode(
				id,
				NodeKind.PARAMETER,
				binding,
				owner,
				unit,
				method,
				0,
				true,
				sourceStart,
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

	private static ContainerUsageProfile parameterProfile(
			String binding,
			int sourceStart) {
		return new ContainerUsageProfile(
				new ContainerIdentity(binding, "values", sourceStart, 6), //$NON-NLS-1$
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
						new UsageEvidence(Kind.REFERENCE_COMPONENT,
								"Reference component", sourceStart, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.ARRAY_LENGTH_READ,
								"Length is read", sourceStart + 10, 13), //$NON-NLS-1$
						new UsageEvidence(Kind.ENCOUNTER_ITERATION,
								"Encounter order is observed", sourceStart + 30, 6), //$NON-NLS-1$
						new UsageEvidence(Kind.LOCAL_USAGE_COMPLETE,
								"Parameter use is complete", sourceStart, 6))); //$NON-NLS-1$
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
						"The representation and complete parameter family migrate together."), //$NON-NLS-1$
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