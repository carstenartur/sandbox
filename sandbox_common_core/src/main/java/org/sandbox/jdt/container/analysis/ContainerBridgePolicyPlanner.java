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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.BridgeDecision;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.BridgePolicyDiagnostic;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.BridgePolicyGroup;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.BridgeProperty;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.PlanningStatus;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.RequirementStatus;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.SemanticRequirement;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.BridgeFeasibility;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus as SignaturePlanningStatus;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PositionKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureAtomicityGroup;

/**
 * Converts Java-level signature feasibility into an explicit semantic bridge-policy
 * matrix.
 *
 * <p>A parameter overload is never labelled compatible merely because it compiles.
 * The bridge must still decide or prove order, uniqueness, mutability, null behavior,
 * aliasing, concurrency and whether it passes a view, wrapper or independent copy.</p>
 */
public final class ContainerBridgePolicyPlanner {

	private static final List<ContractProperty> BRIDGE_PROPERTIES= List.of(
			ContractProperty.ORDER,
			ContractProperty.UNIQUENESS,
			ContractProperty.MUTABILITY,
			ContractProperty.NULLS,
			ContractProperty.ALIASING,
			ContractProperty.CONCURRENCY);

	/** Builds one immutable report-only bridge policy plan. */
	public ContainerBridgePolicyPlan plan(
			ContainerSignatureMigrationPlan signaturePlan,
			ContainerRecommendation recommendation) {
		Objects.requireNonNull(signaturePlan, "signaturePlan"); //$NON-NLS-1$
		Objects.requireNonNull(recommendation, "recommendation"); //$NON-NLS-1$

		List<BridgePolicyDiagnostic> diagnostics= new ArrayList<>();
		if (!signaturePlan.targetContract().equals(recommendation.targetContract())) {
			diagnostics.add(new BridgePolicyDiagnostic(
					DiagnosticKind.TARGET_CONTRACT_MISMATCH,
					"signature-plan", //$NON-NLS-1$
					"The signature plan and recommendation describe different target contracts.")); //$NON-NLS-1$
			return rejected(signaturePlan, diagnostics);
		}
		if (signaturePlan.status()
				== ContainerSignatureMigrationPlan.PlanningStatus.REJECTED) {
			diagnostics.add(new BridgePolicyDiagnostic(
					DiagnosticKind.SIGNATURE_PLAN_REJECTED,
					"signature-plan", //$NON-NLS-1$
					"Semantic bridge planning requires a valid atomic signature plan.")); //$NON-NLS-1$
			return rejected(signaturePlan, diagnostics);
		}
		if (signaturePlan.status()
				== ContainerSignatureMigrationPlan.PlanningStatus.NO_SIGNATURE_CHANGE
				|| signaturePlan.groups().isEmpty()) {
			return new ContainerBridgePolicyPlan(
					signaturePlan.targetContract(),
					List.of(),
					PlanningStatus.NO_BRIDGE_NEEDED,
					List.of());
		}

		AssessmentIndex assessments= AssessmentIndex.create(
				recommendation.assessments(), diagnostics);
		List<BridgePolicyGroup> groups= new ArrayList<>();
		for (SignatureAtomicityGroup signatureGroup : signaturePlan.groups()) {
			BridgePolicyGroup policyGroup= policyGroup(
					signatureGroup, assessments, diagnostics);
			if (policyGroup != null) {
				groups.add(policyGroup);
			}
		}
		return new ContainerBridgePolicyPlan(
				signaturePlan.targetContract(),
				groups,
				diagnostics.isEmpty() ? PlanningStatus.REPORT_ONLY : PlanningStatus.REJECTED,
				diagnostics);
	}

	private static BridgePolicyGroup policyGroup(
			SignatureAtomicityGroup signatureGroup,
			AssessmentIndex assessments,
			List<BridgePolicyDiagnostic> diagnostics) {
		if (signatureGroup.positionKind() == PositionKind.RETURN
				&& signatureGroup.bridgeFeasibility()
						== BridgeFeasibility.SAME_NAME_RETURN_BRIDGE_IMPOSSIBLE) {
			return new BridgePolicyGroup(
					signatureGroup.groupId(),
					PositionKind.RETURN,
					BridgeDecision.IMPOSSIBLE,
					List.of(),
					"The old and new return contracts cannot coexist under the same method name; a compatibility path requires a different method name or an explicitly breaking migration."); //$NON-NLS-1$
		}
		if (signatureGroup.positionKind() == PositionKind.PARAMETER
				&& signatureGroup.bridgeFeasibility()
						== BridgeFeasibility.OVERLOAD_POSSIBLE_POLICY_REQUIRED) {
			List<SemanticRequirement> requirements= new ArrayList<>();
			for (ContractProperty property : BRIDGE_PROPERTIES) {
				requirements.add(requirement(property, assessments.assessment(property)));
			}
			requirements.add(new SemanticRequirement(
					BridgeProperty.ADAPTER_FORM,
					RequirementStatus.EXPLICIT_POLICY_REQUIRED,
					Preservation.UNKNOWN,
					"Choose whether the deprecated overload passes a backed view, a wrapper, or an independent copy. These choices change structural mutation, element replacement and alias visibility.")); //$NON-NLS-1$
			return new BridgePolicyGroup(
					signatureGroup.groupId(),
					PositionKind.PARAMETER,
					BridgeDecision.POLICY_REQUIRED,
					requirements,
					"The old and new parameter signatures can coexist, but a deprecated overload is not semantically safe until every listed property has an explicit proof or policy." ); //$NON-NLS-1$
		}

		diagnostics.add(new BridgePolicyDiagnostic(
				DiagnosticKind.UNSUPPORTED_BRIDGE_CLASSIFICATION,
				signatureGroup.groupId(),
				"The signature group's position and Java-level bridge classification are inconsistent.")); //$NON-NLS-1$
		return null;
	}

	private static SemanticRequirement requirement(
			ContractProperty property,
			ContractAssessment assessment) {
		BridgeProperty bridgeProperty= bridgeProperty(property);
		if (assessment == null) {
			return new SemanticRequirement(
					bridgeProperty,
					RequirementStatus.PROOF_REQUIRED,
					Preservation.UNKNOWN,
					"No contract assessment is available for this property; the bridge must remain disabled until the behavior is proven." ); //$NON-NLS-1$
		}
		RequirementStatus status= switch (assessment.preservation()) {
			case PRESERVED -> RequirementStatus.CONFIRMED;
			case REQUIRES_PROOF, UNKNOWN -> RequirementStatus.PROOF_REQUIRED;
			case CHANGED -> RequirementStatus.EXPLICIT_POLICY_REQUIRED;
		};
		return new SemanticRequirement(
				bridgeProperty,
				status,
				assessment.preservation(),
				assessment.explanation());
	}

	private static BridgeProperty bridgeProperty(ContractProperty property) {
		return switch (property) {
			case ORDER -> BridgeProperty.ORDER;
			case UNIQUENESS -> BridgeProperty.UNIQUENESS;
			case MUTABILITY -> BridgeProperty.MUTABILITY;
			case NULLS -> BridgeProperty.NULLS;
			case ALIASING -> BridgeProperty.ALIASING;
			case CONCURRENCY -> BridgeProperty.CONCURRENCY;
			case SIGNATURES -> throw new IllegalArgumentException(
					"Signature compatibility is represented by the atomic signature plan"); //$NON-NLS-1$
		};
	}

	private static ContainerBridgePolicyPlan rejected(
			ContainerSignatureMigrationPlan signaturePlan,
			List<BridgePolicyDiagnostic> diagnostics) {
		return new ContainerBridgePolicyPlan(
				signaturePlan.targetContract(),
				List.of(),
				PlanningStatus.REJECTED,
				diagnostics);
	}

	private static final class AssessmentIndex {

		private final Map<ContractProperty, ContractAssessment> assessments;

		private AssessmentIndex(Map<ContractProperty, ContractAssessment> assessments) {
			this.assessments= assessments;
		}

		static AssessmentIndex create(
				List<ContractAssessment> input,
				List<BridgePolicyDiagnostic> diagnostics) {
			Map<ContractProperty, ContractAssessment> result=
					new EnumMap<>(ContractProperty.class);
			for (ContractAssessment assessment : input) {
				ContractAssessment existing= result.putIfAbsent(
						assessment.property(), assessment);
				if (existing != null && !existing.equals(assessment)) {
					diagnostics.add(new BridgePolicyDiagnostic(
							DiagnosticKind.AMBIGUOUS_CONTRACT_ASSESSMENT,
							"recommendation", //$NON-NLS-1$
							"Conflicting assessments exist for "
									+ assessment.property() + '.')); //$NON-NLS-1$
				}
			}
			return new AssessmentIndex(Map.copyOf(result));
		}

		ContractAssessment assessment(ContractProperty property) {
			return assessments.get(property);
		}
	}
}
