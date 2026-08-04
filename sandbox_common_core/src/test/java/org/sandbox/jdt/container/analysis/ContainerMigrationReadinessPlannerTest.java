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

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.BridgeDecision;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.BridgePolicyGroup;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.BridgeProperty;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.RequirementStatus;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.SemanticRequirement;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.BlockerProperty;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.BlockerSeverity;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionBlocker;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.AutomationLevel;
import org.sandbox.jdt.container.api.ContainerRecommendation.Confidence;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor.RuleOwnership;
import org.sandbox.jdt.container.api.ContainerShape;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.BridgeFeasibility;
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

class ContainerMigrationReadinessPlannerTest {

	private final ContainerMigrationReadinessPlanner planner=
			new ContainerMigrationReadinessPlanner();

	@Test
	void closedLocalMigrationWithPreservedContractsIsAutomatic() {
		ContainerMigrationReadiness readiness= planner.plan(
				component(ClosureStatus.LOCAL_CLOSED),
				recommendation(allPreservedAssessments()),
				noSignaturePlan(),
				noBridgePlan());

		assertEquals(ExecutionStatus.AUTOMATIC, readiness.status());
		assertTrue(readiness.isExecutable());
		assertTrue(readiness.blockers().isEmpty());
	}

	@Test
	void missingOrUnprovenContractsRemainReportOnly() {
		ContainerMigrationReadiness readiness= planner.plan(
				component(ClosureStatus.LOCAL_CLOSED),
				recommendation(List.of(assessment(
						ContractProperty.ORDER,
						Preservation.REQUIRES_PROOF,
						"Order still needs proof."))), //$NON-NLS-1$
				noSignaturePlan(),
				noBridgePlan());

		assertEquals(ExecutionStatus.REPORT_ONLY, readiness.status());
		assertFalse(readiness.isExecutable());
		assertTrue(readiness.blockers().stream().anyMatch(blocker ->
				blocker.property() == BlockerProperty.ORDER
						&& blocker.severity() == BlockerSeverity.PROOF_REQUIRED));
		assertTrue(readiness.blockers().stream().anyMatch(blocker ->
				blocker.property() == BlockerProperty.CONCURRENCY));
	}

	@Test
	void parameterAdapterPolicyProducesInteractivePolicyExecution() {
		ContainerMigrationReadiness readiness= planner.plan(
				component(ClosureStatus.LOCAL_CLOSED),
				recommendation(allPreservedAssessments()),
				parameterSignaturePlan(),
				parameterBridgePlan());

		assertEquals(ExecutionStatus.INTERACTIVE_POLICY, readiness.status());
		assertTrue(readiness.isExecutable());
		assertEquals(1, readiness.blockers().size());
		assertEquals(BlockerProperty.ADAPTER_FORM,
				readiness.blockers().get(0).property());
		assertEquals(BlockerSeverity.POLICY_REQUIRED,
				readiness.blockers().get(0).severity());
	}

	@Test
	void impossibleReturnBridgeProducesInteractiveBreakingExecution() {
		ContainerMigrationReadiness readiness= planner.plan(
				component(ClosureStatus.LOCAL_CLOSED),
				recommendation(allPreservedAssessments()),
				returnSignaturePlan(),
				returnBridgePlan());

		assertEquals(ExecutionStatus.INTERACTIVE_BREAKING, readiness.status());
		assertTrue(readiness.isExecutable());
		assertTrue(readiness.blockers().stream().anyMatch(blocker ->
				blocker.property() == BlockerProperty.SIGNATURES
						&& blocker.severity() == BlockerSeverity.BREAKING_CHANGE));
	}

	@Test
	void externalFlowBoundaryRejectsMigration() {
		ContainerMigrationReadiness readiness= planner.plan(
				component(ClosureStatus.EXTERNAL_BOUNDARY),
				recommendation(allPreservedAssessments()),
				noSignaturePlan(),
				noBridgePlan());

		assertEquals(ExecutionStatus.REJECTED, readiness.status());
		assertFalse(readiness.isExecutable());
		assertTrue(readiness.blockers().stream().anyMatch(blocker ->
				blocker.property() == BlockerProperty.FLOW
						&& blocker.severity() == BlockerSeverity.FATAL));
	}

	@Test
	void targetContractMismatchRejectsMigration() {
		TargetContainerContract other= new TargetContainerContract(
				ContainerShape.SET,
				OrderRequirement.NONE,
				UniquenessRequirement.REQUIRED,
				Mutability.MUTABLE,
				NullContract.UNKNOWN,
				"Use unique membership semantics."); //$NON-NLS-1$
		ContainerSignatureMigrationPlan signature= new ContainerSignatureMigrationPlan(
				other,
				List.of(),
				ContainerSignatureMigrationPlan.PlanningStatus.NO_SIGNATURE_CHANGE,
				List.of());

		ContainerMigrationReadiness readiness= planner.plan(
				component(ClosureStatus.LOCAL_CLOSED),
				recommendation(allPreservedAssessments()),
				signature,
				noBridgePlan());

		assertEquals(ExecutionStatus.REJECTED, readiness.status());
		assertTrue(readiness.blockers().stream().anyMatch(blocker ->
				blocker.property() == BlockerProperty.TARGET_CONTRACT));
	}

	@Test
	void proofRequirementTakesPrecedenceOverBreakingChoice() {
		List<ContractAssessment> assessments= allPreservedAssessments().stream()
				.filter(assessment -> assessment.property() != ContractProperty.CONCURRENCY)
				.toList();

		ContainerMigrationReadiness readiness= planner.plan(
				component(ClosureStatus.LOCAL_CLOSED),
				recommendation(assessments),
				returnSignaturePlan(),
				returnBridgePlan());

		assertEquals(ExecutionStatus.REPORT_ONLY, readiness.status());
		assertFalse(readiness.isExecutable());
	}

	@Test
	void readinessCollectionsAndStatusInvariantsAreEnforced() {
		ContainerMigrationReadiness readiness= planner.plan(
				component(ClosureStatus.LOCAL_CLOSED),
				recommendation(allPreservedAssessments()),
				noSignaturePlan(),
				noBridgePlan());

		assertThrows(UnsupportedOperationException.class,
				() -> readiness.blockers().clear());
		assertThrows(IllegalArgumentException.class, () ->
				new ContainerMigrationReadiness(
						targetContract(),
						ExecutionStatus.AUTOMATIC,
						List.of(new ExecutionBlocker(
								BlockerProperty.FLOW,
								BlockerSeverity.PROOF_REQUIRED,
								"flow", //$NON-NLS-1$
								"Flow needs proof.")))); //$NON-NLS-1$
	}

	private static List<ContractAssessment> allPreservedAssessments() {
		return List.of(
				assessment(ContractProperty.ORDER, Preservation.PRESERVED, "Order is preserved."), //$NON-NLS-1$
				assessment(ContractProperty.UNIQUENESS, Preservation.PRESERVED, "Duplicates are preserved."), //$NON-NLS-1$
				assessment(ContractProperty.MUTABILITY, Preservation.PRESERVED, "Mutation is preserved."), //$NON-NLS-1$
				assessment(ContractProperty.NULLS, Preservation.PRESERVED, "Null behavior is preserved."), //$NON-NLS-1$
				assessment(ContractProperty.ALIASING, Preservation.PRESERVED, "Aliasing is preserved."), //$NON-NLS-1$
				assessment(ContractProperty.CONCURRENCY, Preservation.PRESERVED, "Concurrency is preserved."), //$NON-NLS-1$
				assessment(ContractProperty.SIGNATURES, Preservation.PRESERVED, "Signatures are planned separately.")); //$NON-NLS-1$
	}

	private static ContractAssessment assessment(
			ContractProperty property,
			Preservation preservation,
			String explanation) {
		return new ContractAssessment(property, preservation, explanation);
	}

	private static ContainerFlowComponent component(ClosureStatus status) {
		FlowNode root= new FlowNode(
				"local:values", //$NON-NLS-1$
				NodeKind.LOCAL_VARIABLE,
				"binding", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				"Owner.java", //$NON-NLS-1$
				"local-handle", //$NON-NLS-1$
				-1,
				true,
				1,
				6);
		return new ContainerFlowComponent(
				root.stableId(), List.of(root), List.of(), status, List.of());
	}

	private static ContainerRecommendation recommendation(
			List<ContractAssessment> assessments) {
		ContainerRuleDescriptor rule= new ContainerRuleDescriptor(
				"semantic.array.append.sequence", //$NON-NLS-1$
				ContainerShape.ARRAY,
				ContainerShape.LIST,
				RuleOwnership.NOVEL,
				"", //$NON-NLS-1$
				"The migration changes representation and signatures."); //$NON-NLS-1$
		return new ContainerRecommendation(
				sourceProfile(),
				targetContract(),
				rule,
				Confidence.HIGH,
				AutomationLevel.REPORT_ONLY,
				assessments);
	}

	private static ContainerUsageProfile sourceProfile() {
		return new ContainerUsageProfile(
				new ContainerIdentity("binding", "values", 1, 6), //$NON-NLS-1$ //$NON-NLS-2$
				ContainerShape.ARRAY,
				ElementDomain.REFERENCE,
				new AccessProfile(false, false, true, false, false, false, false),
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				MutationLifecycle.CONTINUOUSLY_MUTABLE,
				NullContract.UNKNOWN,
				AliasingContract.UNKNOWN,
				EscapeLevel.LOCAL,
				ConcurrencyProfile.unknown(),
				AnalysisCompleteness.FLOW_COMPLETE,
				List.of());
	}

	private static TargetContainerContract targetContract() {
		return new TargetContainerContract(
				ContainerShape.LIST,
				OrderRequirement.ENCOUNTER,
				UniquenessRequirement.DUPLICATES_ALLOWED,
				Mutability.MUTABLE,
				NullContract.UNKNOWN,
				"Use a dynamic sequence contract."); //$NON-NLS-1$
	}

	private static ContainerSignatureMigrationPlan noSignaturePlan() {
		return new ContainerSignatureMigrationPlan(
				targetContract(),
				List.of(),
				ContainerSignatureMigrationPlan.PlanningStatus.NO_SIGNATURE_CHANGE,
				List.of());
	}

	private static ContainerBridgePolicyPlan noBridgePlan() {
		return new ContainerBridgePolicyPlan(
				targetContract(),
				List.of(),
				ContainerBridgePolicyPlan.PlanningStatus.NO_BRIDGE_NEEDED,
				List.of());
	}

	private static ContainerSignatureMigrationPlan parameterSignaturePlan() {
		return signaturePlan(PositionKind.PARAMETER, 0,
				BridgeFeasibility.OVERLOAD_POSSIBLE_POLICY_REQUIRED);
	}

	private static ContainerSignatureMigrationPlan returnSignaturePlan() {
		return signaturePlan(PositionKind.RETURN, -1,
				BridgeFeasibility.SAME_NAME_RETURN_BRIDGE_IMPOSSIBLE);
	}

	private static ContainerSignatureMigrationPlan signaturePlan(
			PositionKind position,
			int index,
			BridgeFeasibility feasibility) {
		SignatureAtomicityGroup group= new SignatureAtomicityGroup(
				"signature-group", //$NON-NLS-1$
				position,
				index,
				List.of(new SignatureMember(
						"method-handle", //$NON-NLS-1$
						"method-key", //$NON-NLS-1$
						"Owner.java", //$NON-NLS-1$
						"signature-node")), //$NON-NLS-1$
				feasibility,
				"Signature classification."); //$NON-NLS-1$
		return new ContainerSignatureMigrationPlan(
				targetContract(),
				List.of(group),
				ContainerSignatureMigrationPlan.PlanningStatus.REPORT_ONLY,
				List.of());
	}

	private static ContainerBridgePolicyPlan parameterBridgePlan() {
		List<SemanticRequirement> requirements= List.of(
				confirmed(BridgeProperty.ORDER),
				confirmed(BridgeProperty.UNIQUENESS),
				confirmed(BridgeProperty.MUTABILITY),
				confirmed(BridgeProperty.NULLS),
				confirmed(BridgeProperty.ALIASING),
				confirmed(BridgeProperty.CONCURRENCY),
				new SemanticRequirement(
						BridgeProperty.ADAPTER_FORM,
						RequirementStatus.EXPLICIT_POLICY_REQUIRED,
						Preservation.UNKNOWN,
						"Choose view, wrapper or copy.")); //$NON-NLS-1$
		return new ContainerBridgePolicyPlan(
				targetContract(),
				List.of(new BridgePolicyGroup(
						"signature-group", //$NON-NLS-1$
						PositionKind.PARAMETER,
						BridgeDecision.POLICY_REQUIRED,
						requirements,
						"Adapter policy is required.")), //$NON-NLS-1$
				ContainerBridgePolicyPlan.PlanningStatus.REPORT_ONLY,
				List.of());
	}

	private static SemanticRequirement confirmed(BridgeProperty property) {
		return new SemanticRequirement(
				property,
				RequirementStatus.CONFIRMED,
				Preservation.PRESERVED,
				property + " is preserved."); //$NON-NLS-1$
	}

	private static ContainerBridgePolicyPlan returnBridgePlan() {
		return new ContainerBridgePolicyPlan(
				targetContract(),
				List.of(new BridgePolicyGroup(
						"signature-group", //$NON-NLS-1$
						PositionKind.RETURN,
						BridgeDecision.IMPOSSIBLE,
						List.of(),
						"Use a different method name or perform a breaking migration.")), //$NON-NLS-1$
				ContainerBridgePolicyPlan.PlanningStatus.REPORT_ONLY,
				List.of());
	}
}
