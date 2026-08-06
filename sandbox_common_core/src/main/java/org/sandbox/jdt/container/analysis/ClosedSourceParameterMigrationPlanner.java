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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan;
import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan.DiagnosticKind;
import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan.PlanningDiagnostic;
import org.sandbox.jdt.container.api.ClosedSourceParameterMigrationPlan.PlanningResult;
import org.sandbox.jdt.container.api.ContainerFlowComponent;
import org.sandbox.jdt.container.api.ContainerFlowComponent.LocatedFlowEdge;
import org.sandbox.jdt.container.api.ContainerFlowGraph.ClosureStatus;
import org.sandbox.jdt.container.api.ContainerFlowGraph.EdgeKind;
import org.sandbox.jdt.container.api.ContainerFlowGraph.FlowNode;
import org.sandbox.jdt.container.api.ContainerFlowGraph.NodeKind;
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan;
import org.sandbox.jdt.container.api.ContainerMigrationReadiness;
import org.sandbox.jdt.container.api.ContainerParameterRewritePlan;
import org.sandbox.jdt.container.api.ContainerRecommendation;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PlanningStatus;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.PositionKind;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureAtomicityGroup;
import org.sandbox.jdt.container.api.ContainerSignatureMigrationPlan.SignatureMember;
import org.sandbox.jdt.container.api.ContainerUsageProfile;

/**
 * Combines the existing local-array and parameter rewrite planners into the first
 * atomic closed-source caller-to-callee migration plan.
 */
public final class ClosedSourceParameterMigrationPlanner {

	private final ContainerLocalRewritePlanner localPlanner=
			new ContainerLocalRewritePlanner();
	private final ContainerParameterRewritePlanner parameterPlanner=
			new ContainerParameterRewritePlanner();

	/** Builds one two-unit plan or complete rejection diagnostics. */
	public PlanningResult plan(
			ContainerFlowComponent component,
			ContainerSignatureMigrationPlan signaturePlan,
			ContainerRecommendation recommendation,
			ContainerMigrationReadiness readiness,
			List<ContainerUsageProfile> profiles) {
		Objects.requireNonNull(component, "component"); //$NON-NLS-1$
		Objects.requireNonNull(signaturePlan, "signaturePlan"); //$NON-NLS-1$
		Objects.requireNonNull(recommendation, "recommendation"); //$NON-NLS-1$
		Objects.requireNonNull(readiness, "readiness"); //$NON-NLS-1$
		Objects.requireNonNull(profiles, "profiles"); //$NON-NLS-1$

		List<PlanningDiagnostic> diagnostics= new ArrayList<>();
		Topology topology= topology(component, diagnostics);
		Map<String, ContainerUsageProfile> profilesByBinding=
				profilesByBinding(profiles, diagnostics);
		SignatureSelection signature=
				signature(signaturePlan, topology, diagnostics);
		if (topology == null || signature == null) {
			return PlanningResult.rejected(diagnostics);
		}

		ContainerUsageProfile callerProfile=
				profilesByBinding.get(topology.caller().bindingKey());
		ContainerUsageProfile parameterProfile=
				profilesByBinding.get(topology.parameter().bindingKey());
		if (callerProfile == null || parameterProfile == null) {
			diagnostics.add(diagnostic(
					DiagnosticKind.PROFILE_NOT_FOUND,
					"Both exact caller and parameter profiles are required.")); //$NON-NLS-1$
			return PlanningResult.rejected(diagnostics);
		}
		if (!recommendation.sourceProfile().equals(callerProfile)) {
			diagnostics.add(diagnostic(
					DiagnosticKind.RECOMMENDATION_MISMATCH,
					"The recommendation must originate from the refined caller profile.")); //$NON-NLS-1$
		}
		if (!diagnostics.isEmpty()) {
			return PlanningResult.rejected(diagnostics);
		}

		ContainerLocalRewritePlan.PlanningResult local= localPlanner.plan(
				localComponent(topology.caller()), recommendation, readiness);
		ContainerParameterRewritePlan.PlanningResult parameter= parameterPlanner.plan(
				component,
				signaturePlan,
				signature.group(),
				signature.member(),
				parameterProfile,
				readiness);
		if (!local.ready()) {
			for (ContainerLocalRewritePlan.PlanningDiagnostic item : local.diagnostics()) {
				diagnostics.add(diagnostic(
						DiagnosticKind.LOCAL_REWRITE_REJECTED,
						item.kind() + ": " + item.message())); //$NON-NLS-1$
			}
		}
		if (!parameter.ready()) {
			for (ContainerParameterRewritePlan.PlanningDiagnostic item
					: parameter.diagnostics()) {
				diagnostics.add(diagnostic(
						DiagnosticKind.PARAMETER_REWRITE_REJECTED,
						item.kind() + ": " + item.message())); //$NON-NLS-1$
			}
		}
		if (!diagnostics.isEmpty()) {
			return PlanningResult.rejected(diagnostics);
		}

		ContainerLocalRewritePlan callerPlan= local.plan().orElseThrow();
		ContainerParameterRewritePlan parameterPlan= parameter.plan().orElseThrow();
		if (callerPlan.compilationUnitHandle()
				.equals(parameterPlan.compilationUnitHandle())) {
			return PlanningResult.rejected(List.of(diagnostic(
					DiagnosticKind.SAME_COMPILATION_UNIT,
					"The first aggregate executor requires distinct source units."))); //$NON-NLS-1$
		}
		return PlanningResult.accepted(new ClosedSourceParameterMigrationPlan(
				recommendation.targetContract(), callerPlan, parameterPlan));
	}

	private static Topology topology(
			ContainerFlowComponent component,
			List<PlanningDiagnostic> diagnostics) {
		if (component.closureStatus() != ClosureStatus.LOCAL_CLOSED
				|| !component.diagnostics().isEmpty()
				|| component.nodes().size() != 2
				|| component.edges().size() != 1) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_FLOW_TOPOLOGY,
					"The first aggregate slice requires one closed local-to-parameter edge.")); //$NON-NLS-1$
			return null;
		}
		LocatedFlowEdge edge= component.edges().get(0);
		FlowNode caller= component.node(edge.sourceNodeId()).orElse(null);
		FlowNode parameter= component.node(edge.targetNodeId()).orElse(null);
		if (edge.kind() != EdgeKind.ARGUMENT_TO_PARAMETER
				|| caller == null || caller.kind() != NodeKind.LOCAL_VARIABLE
				|| parameter == null || parameter.kind() != NodeKind.PARAMETER
				|| !caller.sourceResolved() || !parameter.sourceResolved()) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_FLOW_TOPOLOGY,
					"The canonical edge must connect one resolved local variable to one resolved parameter.")); //$NON-NLS-1$
			return null;
		}
		return new Topology(caller, parameter);
	}

	private static Map<String, ContainerUsageProfile> profilesByBinding(
			List<ContainerUsageProfile> profiles,
			List<PlanningDiagnostic> diagnostics) {
		Map<String, ContainerUsageProfile> result= new LinkedHashMap<>();
		for (ContainerUsageProfile profile : profiles) {
			String key= profile.identity().bindingKey();
			if (key.isBlank() || result.putIfAbsent(key, profile) != null) {
				diagnostics.add(diagnostic(
						DiagnosticKind.PROFILE_NOT_FOUND,
						"Profiles must have unique resolved binding keys.")); //$NON-NLS-1$
				return Map.of();
			}
		}
		return Map.copyOf(result);
	}

	private static SignatureSelection signature(
			ContainerSignatureMigrationPlan plan,
			Topology topology,
			List<PlanningDiagnostic> diagnostics) {
		if (topology == null
				|| plan.status() != PlanningStatus.CLOSED_SOURCE_AUTOMATIC
				|| plan.groups().size() != 1) {
			diagnostics.add(diagnostic(
					DiagnosticKind.SIGNATURE_PLAN_MISMATCH,
					"Exactly one automatic closed-source signature group is required.")); //$NON-NLS-1$
			return null;
		}
		SignatureAtomicityGroup group= plan.groups().get(0);
		if (group.positionKind() != PositionKind.PARAMETER
				|| group.signatureIndex() != topology.parameter().signatureIndex()
				|| group.members().size() != 1) {
			diagnostics.add(diagnostic(
					DiagnosticKind.SIGNATURE_PLAN_MISMATCH,
					"The signature group must describe the exact single parameter node.")); //$NON-NLS-1$
			return null;
		}
		SignatureMember member= group.members().get(0);
		if (!member.flowNodeId().equals(topology.parameter().stableId())
				|| !member.javaElementHandle()
						.equals(topology.parameter().javaElementHandle())) {
			diagnostics.add(diagnostic(
					DiagnosticKind.SIGNATURE_PLAN_MISMATCH,
					"The signature member does not match the canonical parameter node.")); //$NON-NLS-1$
			return null;
		}
		return new SignatureSelection(group, member);
	}

	private static ContainerFlowComponent localComponent(FlowNode local) {
		return new ContainerFlowComponent(
				local.stableId(),
				List.of(local),
				List.of(),
				ClosureStatus.LOCAL_CLOSED,
				List.of());
	}

	private static PlanningDiagnostic diagnostic(
			DiagnosticKind kind,
			String message) {
		return new PlanningDiagnostic(kind, message);
	}

	private record Topology(FlowNode caller, FlowNode parameter) {
	}

	private record SignatureSelection(
			SignatureAtomicityGroup group,
			SignatureMember member) {
	}
}
