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
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.PlanningStatus;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.RequirementStatus;
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
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureDiagnostic;
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

class ContainerBridgePolicyPlannerTest {

	private final ContainerBridgePolicyPlanner planner= new ContainerBridgePolicyPlanner();

	@Test
	void parameterBridgeRequiresSemanticPolicyEvenWhenSomeFactsArePreserved() {
		ContainerRecommendation recommendation= recommendation(List.of(
				assessment(ContractProperty.ORDER, Preservation.PRESERVED,
						"Encounter order can be retained."), //$NON-NLS-1$
				assessment(ContractProperty.UNIQUENESS, Preservation.PRESERVED,
						"Arrays and lists permit duplicates."), //$NON-NLS-1$
				assessment(ContractProperty.MUTABILITY, Preservation.CHANGED,
						"Structural mutation depends on the adapter."), //$NON-NLS-1$
				assessment(ContractProperty.ALIASING, Preservation.REQUIRES_PROOF,
						"Alias visibility is not closed."))); //$NON-NLS-1$

		ContainerBridgePolicyPlan plan= planner.plan(
				signaturePlan(parameterGroup()), recommendation);

		assertEquals(PlanningStatus.REPORT_ONLY, plan.status());
		assertEquals(1, plan.groups().size());
		BridgePolicyGroup group= plan.groups().get(0);
		assertEquals(BridgeDecision.POLICY_REQUIRED, group.decision());
		assertEquals(7, group.requirements().size());
		assertEquals(RequirementStatus.CONFIRMED,
				requirement(group, BridgeProperty.ORDER).status());
		assertEquals(RequirementStatus.EXPLICIT_POLICY_REQUIRED,
				requirement(group, BridgeProperty.MUTABILITY).status());
		assertEquals(RequirementStatus.PROOF_REQUIRED,
				requirement(group, BridgeProperty.ALIASING).status());
		assertEquals(RequirementStatus.EXPLICIT_POLICY_REQUIRED,
				requirement(group, BridgeProperty.ADAPTER_FORM).status());
		assertFalse(group.allRequirementsConfirmed());
	}

	@Test
	void missingAssessmentsBecomeUnknownProofRequirements() {
		ContainerBridgePolicyPlan plan= planner.plan(
				signaturePlan(parameterGroup()), recommendation(List.of()));

		BridgePolicyGroup group= plan.groups().get(0);
		assertEquals(Preservation.UNKNOWN,
				requirement(group, BridgeProperty.NULLS).sourceAssessment());
		assertEquals(RequirementStatus.PROOF_REQUIRED,
				requirement(group, BridgeProperty.CONCURRENCY).status());
		assertTrue(requirement(group, BridgeProperty.CONCURRENCY)
				.explanation().contains("No contract assessment")); //$NON-NLS-1$
	}

	@Test
	void returnBridgeIsImpossibleUnderTheSameMethodName() {
		ContainerBridgePolicyPlan plan= planner.plan(
				signaturePlan(returnGroup()), recommendation(List.of()));

		assertEquals(PlanningStatus.REPORT_ONLY, plan.status());
		BridgePolicyGroup group= plan.groups().get(0);
		assertEquals(PositionKind.RETURN, group.positionKind());
		assertEquals(BridgeDecision.IMPOSSIBLE, group.decision());
		assertTrue(group.requirements().isEmpty());
		assertTrue(group.explanation().contains("different method name")); //$NON-NLS-1$
	}

	@Test
	void rejectedSignaturePlanRejectsBridgePlanning() {
		ContainerSignatureMigrationPlan rejected= new ContainerSignatureMigrationPlan(
				targetContract(),
				List.of(),
				ContainerSignatureMigrationPlan.PlanningStatus.REJECTED,
				List.of(new SignatureDiagnostic(
						ContainerSignatureMigrationPlan.DiagnosticKind.FLOW_NOT_CLOSED,
						"root", //$NON-NLS-1$
						"", //$NON-NLS-1$
						"Flow is not closed."))); //$NON-NLS-1$

		ContainerBridgePolicyPlan plan= planner.plan(rejected, recommendation(List.of()));

		assertEquals(PlanningStatus.REJECTED, plan.status());
		assertEquals(DiagnosticKind.SIGNATURE_PLAN_REJECTED,
				plan.diagnostics().get(0).kind());
	}

	@Test
	void conflictingAssessmentsRejectTheMatrix() {
		ContractAssessment first= assessment(
				ContractProperty.ORDER, Preservation.PRESERVED, "Order is preserved."); //$NON-NLS-1$
		ContractAssessment second= assessment(
				ContractProperty.ORDER, Preservation.CHANGED, "Order changes."); //$NON-NLS-1$

		ContainerBridgePolicyPlan plan= planner.plan(
				signaturePlan(parameterGroup()), recommendation(List.of(first, second)));

		assertEquals(PlanningStatus.REJECTED, plan.status());
		assertTrue(plan.diagnostics().stream().anyMatch(diagnostic ->
				diagnostic.kind() == DiagnosticKind.AMBIGUOUS_CONTRACT_ASSESSMENT));
	}

	@Test
	void targetContractMismatchRejectsTheMatrix() {
		TargetContainerContract differentTarget= new TargetContainerContract(
				ContainerShape.SET,
				OrderRequirement.NONE,
				UniquenessRequirement.REQUIRED,
				Mutability.MUTABLE,
				NullContract.UNKNOWN,
				"Use unique membership semantics."); //$NON-NLS-1$
		ContainerSignatureMigrationPlan signaturePlan= new ContainerSignatureMigrationPlan(
				differentTarget,
				List.of(parameterGroup()),
				ContainerSignatureMigrationPlan.PlanningStatus.REPORT_ONLY,
				List.of());

		ContainerBridgePolicyPlan plan= planner.plan(signaturePlan, recommendation(List.of()));

		assertEquals(PlanningStatus.REJECTED, plan.status());
		assertEquals(DiagnosticKind.TARGET_CONTRACT_MISMATCH,
				plan.diagnostics().get(0).kind());
	}

	@Test
	void noSignatureChangeNeedsNoBridge() {
		ContainerSignatureMigrationPlan signaturePlan= new ContainerSignatureMigrationPlan(
				targetContract(),
				List.of(),
				ContainerSignatureMigrationPlan.PlanningStatus.NO_SIGNATURE_CHANGE,
				List.of());

		ContainerBridgePolicyPlan plan= planner.plan(signaturePlan, recommendation(List.of()));

		assertEquals(PlanningStatus.NO_BRIDGE_NEEDED, plan.status());
		assertTrue(plan.groups().isEmpty());
	}

	@Test
	void bridgePolicyCollectionsAreImmutableAndValidated() {
		ContainerBridgePolicyPlan plan= planner.plan(
				signaturePlan(parameterGroup()), recommendation(List.of()));

		assertThrows(UnsupportedOperationException.class, () -> plan.groups().clear());
		assertThrows(IllegalArgumentException.class, () ->
				new ContainerBridgePolicyPlan.BridgePolicyGroup(
						"group", //$NON-NLS-1$
						PositionKind.PARAMETER,
						BridgeDecision.POLICY_REQUIRED,
						List.of(),
						"Missing requirements")); //$NON-NLS-1$
	}

	private static ContainerBridgePolicyPlan.SemanticRequirement requirement(
			BridgePolicyGroup group,
			BridgeProperty property) {
		return group.requirements().stream()
				.filter(candidate -> candidate.property() == property)
				.findFirst()
				.orElseThrow();
	}

	private static ContainerSignatureMigrationPlan signaturePlan(
			SignatureAtomicityGroup group) {
		return new ContainerSignatureMigrationPlan(
				targetContract(),
				List.of(group),
				ContainerSignatureMigrationPlan.PlanningStatus.REPORT_ONLY,
				List.of());
	}

	private static SignatureAtomicityGroup parameterGroup() {
		return new SignatureAtomicityGroup(
				"method:parameter:0", //$NON-NLS-1$
				PositionKind.PARAMETER,
				0,
				List.of(member()),
				BridgeFeasibility.OVERLOAD_POSSIBLE_POLICY_REQUIRED,
				"Parameter overload is technically possible."); //$NON-NLS-1$
	}

	private static SignatureAtomicityGroup returnGroup() {
		return new SignatureAtomicityGroup(
				"method:return", //$NON-NLS-1$
				PositionKind.RETURN,
				-1,
				List.of(member()),
				BridgeFeasibility.SAME_NAME_RETURN_BRIDGE_IMPOSSIBLE,
				"Return overload is impossible."); //$NON-NLS-1$
	}

	private static SignatureMember member() {
		return new SignatureMember(
				"method-handle", //$NON-NLS-1$
				"method-key", //$NON-NLS-1$
				"Owner.java", //$NON-NLS-1$
				"parameter:method:0"); //$NON-NLS-1$
	}

	private static ContractAssessment assessment(
			ContractProperty property,
			Preservation preservation,
			String explanation) {
		return new ContractAssessment(property, preservation, explanation);
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
				EscapeLevel.METHOD_BOUNDARY,
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
}
