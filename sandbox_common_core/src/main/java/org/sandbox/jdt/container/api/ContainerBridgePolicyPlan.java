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
package org.sandbox.jdt.container.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sandbox.jdt.container.api.ContainerRecommendation.Preservation;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PositionKind;

/**
 * Report-only semantic compatibility assessment for deprecated bridge methods.
 *
 * <p>Java-level overloadability is not treated as semantic compatibility. A parameter
 * bridge remains policy-dependent until every observable container property and the
 * adapter form have an explicit decision. Return-type changes are represented as
 * impossible under the same method name.</p>
 */
public record ContainerBridgePolicyPlan(
		TargetContainerContract targetContract,
		List<BridgePolicyGroup> groups,
		PlanningStatus status,
		List<BridgePolicyDiagnostic> diagnostics) {

	public ContainerBridgePolicyPlan {
		Objects.requireNonNull(targetContract, "targetContract"); //$NON-NLS-1$
		groups= List.copyOf(Objects.requireNonNull(groups, "groups")); //$NON-NLS-1$
		Objects.requireNonNull(status, "status"); //$NON-NLS-1$
		diagnostics= List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics")); //$NON-NLS-1$
		validateUniqueGroups(groups);
		if (status == PlanningStatus.REJECTED && diagnostics.isEmpty()) {
			throw new IllegalArgumentException("A rejected bridge policy requires diagnostics"); //$NON-NLS-1$
		}
	}

	/** One atomic signature group's bridge decision and semantic requirements. */
	public record BridgePolicyGroup(
			String signatureGroupId,
			PositionKind positionKind,
			BridgeDecision decision,
			List<SemanticRequirement> requirements,
			String explanation) {

		public BridgePolicyGroup {
			signatureGroupId= requiredText(signatureGroupId, "signatureGroupId"); //$NON-NLS-1$
			Objects.requireNonNull(positionKind, "positionKind"); //$NON-NLS-1$
			Objects.requireNonNull(decision, "decision"); //$NON-NLS-1$
			requirements= List.copyOf(Objects.requireNonNull(requirements, "requirements")); //$NON-NLS-1$
			validateUniqueProperties(requirements);
			explanation= requiredText(explanation, "explanation"); //$NON-NLS-1$
			if (decision == BridgeDecision.POLICY_REQUIRED && requirements.isEmpty()) {
				throw new IllegalArgumentException(
						"A policy-dependent bridge requires semantic requirements"); //$NON-NLS-1$
			}
			if (decision == BridgeDecision.IMPOSSIBLE && !requirements.isEmpty()) {
				throw new IllegalArgumentException(
						"An impossible bridge cannot have adapter requirements"); //$NON-NLS-1$
			}
		}

		/** Returns whether every listed property is already confirmed. */
		public boolean allRequirementsConfirmed() {
			return requirements.stream()
					.allMatch(requirement -> requirement.status() == RequirementStatus.CONFIRMED);
		}
	}

	/** One observable semantic property that a bridge must preserve or decide. */
	public record SemanticRequirement(
			BridgeProperty property,
			RequirementStatus status,
			Preservation sourceAssessment,
			String explanation) {

		public SemanticRequirement {
			Objects.requireNonNull(property, "property"); //$NON-NLS-1$
			Objects.requireNonNull(status, "status"); //$NON-NLS-1$
			Objects.requireNonNull(sourceAssessment, "sourceAssessment"); //$NON-NLS-1$
			explanation= requiredText(explanation, "explanation"); //$NON-NLS-1$
		}
	}

	/** One reason why semantic bridge assessment could not be produced. */
	public record BridgePolicyDiagnostic(
			DiagnosticKind kind,
			String signatureGroupId,
			String message) {

		public BridgePolicyDiagnostic {
			Objects.requireNonNull(kind, "kind"); //$NON-NLS-1$
			signatureGroupId= requiredText(signatureGroupId, "signatureGroupId"); //$NON-NLS-1$
			message= requiredText(message, "message"); //$NON-NLS-1$
		}
	}

	public enum BridgeDecision {
		/** A Java overload is possible, but the adapter semantics need explicit policy. */
		POLICY_REQUIRED,
		/** The old and new signatures cannot coexist under the same method name. */
		IMPOSSIBLE
	}

	public enum BridgeProperty {
		ORDER,
		UNIQUENESS,
		MUTABILITY,
		NULLS,
		ALIASING,
		CONCURRENCY,
		/** Whether the bridge passes a view, wrapper or independent copy. */
		ADAPTER_FORM
	}

	public enum RequirementStatus {
		CONFIRMED,
		PROOF_REQUIRED,
		EXPLICIT_POLICY_REQUIRED
	}

	public enum PlanningStatus {
		NO_BRIDGE_NEEDED,
		REPORT_ONLY,
		REJECTED
	}

	public enum DiagnosticKind {
		SIGNATURE_PLAN_REJECTED,
		UNSUPPORTED_BRIDGE_CLASSIFICATION
	}

	private static void validateUniqueGroups(List<BridgePolicyGroup> groups) {
		Set<String> ids= new HashSet<>();
		for (BridgePolicyGroup group : groups) {
			if (!ids.add(group.signatureGroupId())) {
				throw new IllegalArgumentException(
						"Duplicate bridge policy group: " + group.signatureGroupId()); //$NON-NLS-1$
			}
		}

	private static void validateUniqueProperties(List<SemanticRequirement> requirements) {
		Set<BridgeProperty> properties= new HashSet<>();
		for (SemanticRequirement requirement : requirements) {
			if (!properties.add(requirement.property())) {
				throw new IllegalArgumentException(
						"Duplicate bridge property: " + requirement.property()); //$NON-NLS-1$
			}
		}
	}

	private static String requiredText(String value, String fieldName) {
		String text= Objects.requireNonNull(value, fieldName).strip();
		if (text.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be empty"); //$NON-NLS-1$
		}
		return text;
	}
}
