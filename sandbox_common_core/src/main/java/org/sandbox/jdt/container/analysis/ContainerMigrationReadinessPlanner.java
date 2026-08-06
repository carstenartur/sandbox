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
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.BridgePolicyGroup;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.BridgeProperty;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.RequirementStatus;
import org.sandbox.jdt.container.api.ContainerBridgePolicyPlan.SemanticRequirement;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.BlockerProperty;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.BlockerSeverity;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionBlocker;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness.ExecutionStatus;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractAssessment;
import org.sandbox.jdt.container.api.ContainerRecommendation.ContractProperty;
import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;

/** Combines all semantic planning layers into one monotonic execution decision. */
public final class ContainerMigrationReadinessPlanner {

	private static final List<ContractProperty> EXECUTION_PROPERTIES= List.of(
			ContractProperty.ORDER,
			ContractProperty.UNIQUENESS,
			ContractProperty.MUTABILITY,
			ContractProperty.NULLS,
			ContractProperty.ALIASING,
			ContractProperty.CONCURRENCY);

	/** Builds the final execution gate for one semantic container recommendation. */
	public ContainerMigrationReadiness plan(
			ContainerFlowComponent component,
			ContainerRecommendation recommendation,
			ContainerSignatureMigrationPlan signaturePlan,
			ContainerBridgePolicyPlan bridgePlan) {
		Objects.requireNonNull(component, "component"); //$NON-NLS-1$
		Objects.requireNonNull(recommendation, "recommendation"); //$NON-NLS-1$
		Objects.requireNonNull(signaturePlan, "signaturePlan"); //$NON-NLS-1$
		Objects.requireNonNull(bridgePlan, "bridgePlan"); //$NON-NLS-1$

		List<ExecutionBlocker> blockers= new ArrayList<>();
		validateTargetContracts(recommendation, signaturePlan, bridgePlan, blockers);
		addFlowBlocker(component, blockers);
		addRejectedPlanBlockers(signaturePlan, bridgePlan, blockers);
		addContractAssessmentBlockers(recommendation, blockers);
		addSignatureAndBridgeBlockers(signaturePlan, bridgePlan, blockers);

		ExecutionStatus status= status(blockers);
		return new ContainerMigrationReadiness(
				recommendation.targetContract(), status, blockers);
	}

	private static void validateTargetContracts(
			ContainerRecommendation recommendation,
			ContainerSignatureMigrationPlan signaturePlan,
			ContainerBridgePolicyPlan bridgePlan,
			List<ExecutionBlocker> blockers) {
		if (!recommendation.targetContract().equals(signaturePlan.targetContract())
				|| !recommendation.targetContract().equals(bridgePlan.targetContract())) {
			blockers.add(blocker(
					BlockerProperty.TARGET_CONTRACT,
					BlockerSeverity.FATAL,
					"target-contract", //$NON-NLS-1$
					"Recommendation, signature plan and bridge plan do not describe the same target contract.")); //$NON-NLS-1$
		}
	}

	private static void addFlowBlocker(
			ContainerFlowComponent component,
			List<ExecutionBlocker> blockers) {
		switch (component.closureStatus()) {
			case LOCAL_CLOSED -> {
				// Flow is closed.
			}
			case REQUIRES_SCOPE_EXPANSION -> blockers.add(blocker(
					BlockerProperty.FLOW,
					BlockerSeverity.PROOF_REQUIRED,
					component.rootNodeId(),
					"The source flow still requires additional compilation units or continuation analysis.")); //$NON-NLS-1$
			case EXTERNAL_BOUNDARY -> blockers.add(blocker(
					BlockerProperty.FLOW,
					BlockerSeverity.FATAL,
					component.rootNodeId(),
					"The container value reaches an external or binary boundary that cannot be migrated atomically.")); //$NON-NLS-1$
			case REJECTED -> blockers.add(blocker(
					BlockerProperty.FLOW,
					BlockerSeverity.FATAL,
					component.rootNodeId(),
					"The semantic flow component contains a rejected or contradictory path.")); //$NON-NLS-1$
		}
	}

	private static void addRejectedPlanBlockers(
			ContainerSignatureMigrationPlan signaturePlan,
			ContainerBridgePolicyPlan bridgePlan,
			List<ExecutionBlocker> blockers) {
		if (signaturePlan.status()
				== ContainerSignatureMigrationPlan.PlanningStatus.REJECTED) {
			blockers.add(blocker(
					BlockerProperty.SIGNATURES,
					BlockerSeverity.FATAL,
					"signature-plan", //$NON-NLS-1$
					"Atomic signature planning was rejected.")); //$NON-NLS-1$
		}
		if (bridgePlan.status() == ContainerBridgePolicyPlan.PlanningStatus.REJECTED) {
			blockers.add(blocker(
					BlockerProperty.SIGNATURES,
					BlockerSeverity.FATAL,
					"bridge-plan", //$NON-NLS-1$
					"Semantic bridge-policy planning was rejected.")); //$NON-NLS-1$
		}
	}

	private static void addContractAssessmentBlockers(
			ContainerRecommendation recommendation,
			List<ExecutionBlocker> blockers) {
		Map<ContractProperty, ContractAssessment> assessments=
				new EnumMap<>(ContractProperty.class);
		for (ContractAssessment assessment : recommendation.assessments()) {
			ContractAssessment previous= assessments.putIfAbsent(
					assessment.property(), assessment);
			if (previous != null && !previous.equals(assessment)) {
				blockers.add(blocker(
						blockerProperty(assessment.property()),
						BlockerSeverity.FATAL,
						"recommendation", //$NON-NLS-1$
						"Conflicting contract assessments exist for "
								+ assessment.property() + '.')); //$NON-NLS-1$
			}
		}
		for (ContractProperty property : EXECUTION_PROPERTIES) {
			ContractAssessment assessment= assessments.get(property);
			if (assessment == null) {
				blockers.add(blocker(
						blockerProperty(property),
						BlockerSeverity.PROOF_REQUIRED,
						"recommendation", //$NON-NLS-1$
						"No semantic assessment is available for " + property + '.')); //$NON-NLS-1$
				continue;
			}
			if (assessment.preservation() == Preservation.REQUIRES_PROOF
					|| assessment.preservation() == Preservation.UNKNOWN) {
				blockers.add(blocker(
						blockerProperty(property),
						BlockerSeverity.PROOF_REQUIRED,
						"recommendation", //$NON-NLS-1$
						assessment.explanation()));
			} else if (assessment.preservation() == Preservation.CHANGED) {
				blockers.add(blocker(
						blockerProperty(property),
						BlockerSeverity.BREAKING_CHANGE,
						"recommendation", //$NON-NLS-1$
						assessment.explanation()));
			}
		}
	}

	private static void addSignatureAndBridgeBlockers(
			ContainerSignatureMigrationPlan signaturePlan,
			ContainerBridgePolicyPlan bridgePlan,
			List<ExecutionBlocker> blockers) {
		if (signaturePlan.status()
				== ContainerSignatureMigrationPlan.PlanningStatus.NO_SIGNATURE_CHANGE
				|| signaturePlan.status()
						== ContainerSignatureMigrationPlan.PlanningStatus.CLOSED_SOURCE_AUTOMATIC) {
			if (bridgePlan.status()
					!= ContainerBridgePolicyPlan.PlanningStatus.NO_BRIDGE_NEEDED) {
				blockers.add(blocker(
						BlockerProperty.SIGNATURES,
						BlockerSeverity.FATAL,
						"signature-plan", //$NON-NLS-1$
						"The bridge plan requests compatibility work although the signature plan retains no old API.")); //$NON-NLS-1$
			}
			return;
		}

		for (BridgePolicyGroup group : bridgePlan.groups()) {
			if (group.decision() == BridgeDecision.IMPOSSIBLE) {
				blockers.add(blocker(
						BlockerProperty.SIGNATURES,
						BlockerSeverity.BREAKING_CHANGE,
						group.signatureGroupId(),
						group.explanation()));
				continue;
			}
			for (SemanticRequirement requirement : group.requirements()) {
				if (requirement.status() == RequirementStatus.CONFIRMED) {
					continue;
				}
				BlockerSeverity severity= requirement.status()
						== RequirementStatus.PROOF_REQUIRED
								? BlockerSeverity.PROOF_REQUIRED
								: BlockerSeverity.POLICY_REQUIRED;
				blockers.add(blocker(
						blockerProperty(requirement.property()),
						severity,
						group.signatureGroupId(),
						requirement.explanation()));
			}
		}
	}

	private static ExecutionStatus status(List<ExecutionBlocker> blockers) {
		if (contains(blockers, BlockerSeverity.FATAL)) {
			return ExecutionStatus.REJECTED;
		}
		if (contains(blockers, BlockerSeverity.PROOF_REQUIRED)) {
			return ExecutionStatus.REPORT_ONLY;
		}
		if (contains(blockers, BlockerSeverity.BREAKING_CHANGE)) {
			return ExecutionStatus.INTERACTIVE_BREAKING;
		}
		if (contains(blockers, BlockerSeverity.POLICY_REQUIRED)) {
			return ExecutionStatus.INTERACTIVE_POLICY;
		}
		return ExecutionStatus.AUTOMATIC;
	}

	private static boolean contains(
			List<ExecutionBlocker> blockers,
			BlockerSeverity severity) {
		return blockers.stream().anyMatch(blocker -> blocker.severity() == severity);
	}

	private static BlockerProperty blockerProperty(ContractProperty property) {
		return switch (property) {
			case ORDER -> BlockerProperty.ORDER;
			case UNIQUENESS -> BlockerProperty.UNIQUENESS;
			case MUTABILITY -> BlockerProperty.MUTABILITY;
			case NULLS -> BlockerProperty.NULLS;
			case ALIASING -> BlockerProperty.ALIASING;
			case CONCURRENCY -> BlockerProperty.CONCURRENCY;
			case SIGNATURES -> BlockerProperty.SIGNATURES;
		};
	}

	private static BlockerProperty blockerProperty(BridgeProperty property) {
		return switch (property) {
			case ORDER -> BlockerProperty.ORDER;
			case UNIQUENESS -> BlockerProperty.UNIQUENESS;
			case MUTABILITY -> BlockerProperty.MUTABILITY;
			case NULLS -> BlockerProperty.NULLS;
			case ALIASING -> BlockerProperty.ALIASING;
			case CONCURRENCY -> BlockerProperty.CONCURRENCY;
			case ADAPTER_FORM -> BlockerProperty.ADAPTER_FORM;
		};
	}

	private static ExecutionBlocker blocker(
			BlockerProperty property,
			BlockerSeverity severity,
			String sourceId,
			String explanation) {
		return new ExecutionBlocker(property, severity, sourceId, explanation);
	}
}
