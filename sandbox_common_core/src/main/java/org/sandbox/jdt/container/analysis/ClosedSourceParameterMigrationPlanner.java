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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import org.sandbox.jdt.container.api.ContainerLocalRewritePlan.ArgumentTransfer;
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
 * Builds the first aggregate closed-source caller/parameter rewrite plan.
 *
 * <p>The accepted topology remains deliberately narrow on the producer side: one
 * local append-array source and one or more direct {@code ARGUMENT_TO_PARAMETER}
 * edges. The parameter side may now be one complete source-resolved atomicity group,
 * including interface declarations and all editable implementations/overrides. Every
 * group member requires its own complete parameter usage profile before any member
 * plan is emitted.</p>
 */
public final class ClosedSourceParameterMigrationPlanner {

	private final ContainerLocalRewritePlanner localPlanner=
			new ContainerLocalRewritePlanner();
	private final ContainerParameterRewritePlanner parameterPlanner=
			new ContainerParameterRewritePlanner();

	/** Builds one immutable aggregate plan or complete rejection diagnostics. */
	public PlanningResult plan(
			ContainerFlowComponent component,
			ContainerSignatureMigrationPlan signaturePlan,
			ContainerRecommendation recommendation,
			ContainerMigrationReadiness readiness,
			List<ContainerUsageProfile> memberProfiles) {
		Objects.requireNonNull(component, "component"); //$NON-NLS-1$
		Objects.requireNonNull(signaturePlan, "signaturePlan"); //$NON-NLS-1$
		Objects.requireNonNull(recommendation, "recommendation"); //$NON-NLS-1$
		Objects.requireNonNull(readiness, "readiness"); //$NON-NLS-1$
		memberProfiles= List.copyOf(
				Objects.requireNonNull(memberProfiles, "memberProfiles")); //$NON-NLS-1$

		List<PlanningDiagnostic> diagnostics= new ArrayList<>();
		Topology topology= topology(component);
		if (topology == null) {
			diagnostics.add(diagnostic(
					DiagnosticKind.UNSUPPORTED_FLOW_TOPOLOGY,
					"The closed-source parameter rewrite requires one local caller, one or more source parameters, and only direct argument-to-parameter edges from that caller.")); //$NON-NLS-1$
			return PlanningResult.rejected(diagnostics);
		}
		Signature signature= signature(signaturePlan, topology.parameters());
		if (signature == null) {
			diagnostics.add(diagnostic(
					DiagnosticKind.SIGNATURE_PLAN_MISMATCH,
					"The automatic signature plan does not cover the complete source parameter atomicity group.")); //$NON-NLS-1$
			return PlanningResult.rejected(diagnostics);
		}

		ContainerUsageProfile callerProfile= profile(
				memberProfiles, topology.caller().bindingKey());
		if (callerProfile == null) {
			diagnostics.add(diagnostic(
					DiagnosticKind.PROFILE_NOT_FOUND,
					"The local caller profile must be present exactly once.")); //$NON-NLS-1$
			return PlanningResult.rejected(diagnostics);
		}
		if (!recommendation.sourceProfile().equals(callerProfile)
				|| !recommendation.targetContract().equals(signaturePlan.targetContract())) {
			diagnostics.add(diagnostic(
					DiagnosticKind.RECOMMENDATION_MISMATCH,
					"The recommendation does not describe the exact aggregate caller and signature target.")); //$NON-NLS-1$
			return PlanningResult.rejected(diagnostics);
		}

		Map<String, SignatureMember> membersByNodeId= signature.group().members().stream()
				.collect(Collectors.toMap(
						SignatureMember::flowNodeId,
						Function.identity(),
						(left, right) -> left,
						LinkedHashMap::new));
		List<ArgumentTransfer> transfers=
				new ArrayList<>(topology.argumentEdges().size());
		for (LocatedFlowEdge edge : topology.argumentEdges()) {
			SignatureMember member= membersByNodeId.get(edge.targetNodeId());
			if (member == null) {
				diagnostics.add(diagnostic(
						DiagnosticKind.SIGNATURE_PLAN_MISMATCH,
						"An argument edge targets a parameter outside the automatic signature group.")); //$NON-NLS-1$
				continue;
			}
			transfers.add(new ArgumentTransfer(
					member.javaElementHandle(),
					signature.group().signatureIndex(),
					edge.sourceStart(),
					edge.sourceLength()));
		}

		ContainerLocalRewritePlan.PlanningResult callerResult= localPlanner.plan(
				localComponent(topology.caller()),
				recommendation,
				readiness,
				transfers);
		if (!callerResult.ready()) {
			callerResult.diagnostics().forEach(item -> diagnostics.add(diagnostic(
					DiagnosticKind.LOCAL_REWRITE_REJECTED,
					item.kind() + ": " + item.message()))); //$NON-NLS-1$
		}

		List<ContainerParameterRewritePlan> parameterPlans=
				new ArrayList<>(signature.group().members().size());
		for (SignatureMember member : signature.group().members()) {
			FlowNode parameter= component.node(member.flowNodeId()).orElse(null);
			ContainerUsageProfile parameterProfile= parameter == null
					? null : profile(memberProfiles, parameter.bindingKey());
			if (parameter == null || parameterProfile == null) {
				diagnostics.add(diagnostic(
						DiagnosticKind.PROFILE_NOT_FOUND,
						"Every automatic signature member requires one exact parameter profile: "
								+ member.javaElementHandle())); //$NON-NLS-1$
				continue;
			}
			ContainerParameterRewritePlan.PlanningResult parameterResult=
					parameterPlanner.plan(
							component,
							signaturePlan,
							signature.group(),
							member,
							parameterProfile,
							readiness);
			if (!parameterResult.ready()) {
				parameterResult.diagnostics().forEach(item -> diagnostics.add(diagnostic(
						DiagnosticKind.PARAMETER_REWRITE_REJECTED,
						member.javaElementHandle() + ": " //$NON-NLS-1$
								+ item.kind() + ": " + item.message()))); //$NON-NLS-1$
				continue;
			}
			parameterPlans.add(parameterResult.plan().orElseThrow());
		}
		if (!diagnostics.isEmpty()) {
			return PlanningResult.rejected(diagnostics);
		}

		return PlanningResult.accepted(new ClosedSourceParameterMigrationPlan(
				recommendation.targetContract(),
				callerResult.plan().orElseThrow(),
				parameterPlans));
	}

	private static Topology topology(ContainerFlowComponent component) {
		if (component.closureStatus() != ClosureStatus.LOCAL_CLOSED
				|| !component.diagnostics().isEmpty()) {
			return null;
		}
		List<FlowNode> callers= component.nodes().stream()
				.filter(node -> node.kind() == NodeKind.LOCAL_VARIABLE)
				.toList();
		List<FlowNode> parameters= component.nodes().stream()
				.filter(node -> node.kind() == NodeKind.PARAMETER)
				.toList();
		if (callers.size() != 1 || parameters.isEmpty()
				|| component.nodes().size() != callers.size() + parameters.size()) {
			return null;
		}
		FlowNode caller= callers.get(0);
		if (!caller.sourceResolved() || caller.bindingKey().isBlank()
				|| caller.compilationUnitHandle().isBlank()) {
			return null;
		}
		if (parameters.stream().anyMatch(parameter ->
				!parameter.sourceResolved()
						|| parameter.bindingKey().isBlank()
						|| parameter.javaElementHandle().isBlank()
						|| parameter.compilationUnitHandle().isBlank()
						|| parameter.signatureIndex() < 0)) {
			return null;
		}
		Set<String> parameterIds= parameters.stream()
				.map(FlowNode::stableId)
				.collect(Collectors.toSet());
		List<LocatedFlowEdge> argumentEdges= component.edges();
		if (argumentEdges.isEmpty()) {
			return null;
		}
		for (LocatedFlowEdge edge : argumentEdges) {
			if (edge.kind() != EdgeKind.ARGUMENT_TO_PARAMETER
					|| !edge.sourceNodeId().equals(caller.stableId())
					|| !parameterIds.contains(edge.targetNodeId())
					|| !edge.compilationUnitHandle().equals(
							caller.compilationUnitHandle())) {
				return null;
			}
		}
		return new Topology(caller, parameters, argumentEdges);
	}

	private static Signature signature(
			ContainerSignatureMigrationPlan plan,
			List<FlowNode> parameters) {
		if (plan.status() != PlanningStatus.CLOSED_SOURCE_AUTOMATIC
				|| plan.groups().size() != 1) {
			return null;
		}
		SignatureAtomicityGroup group= plan.groups().get(0);
		if (group.positionKind() != PositionKind.PARAMETER
				|| group.members().size() != parameters.size()) {
			return null;
		}
		Map<String, FlowNode> parametersById= parameters.stream()
				.collect(Collectors.toMap(
						FlowNode::stableId,
						Function.identity(),
						(left, right) -> left,
						LinkedHashMap::new));
		for (SignatureMember member : group.members()) {
			FlowNode parameter= parametersById.get(member.flowNodeId());
			if (parameter == null
					|| parameter.signatureIndex() != group.signatureIndex()
					|| !member.compilationUnitHandle().equals(
							parameter.compilationUnitHandle())
					|| !member.javaElementHandle().equals(
							parameter.javaElementHandle())) {
				return null;
			}
		}
		return new Signature(group);
	}

	private static ContainerUsageProfile profile(
			Collection<ContainerUsageProfile> profiles,
			String bindingKey) {
		List<ContainerUsageProfile> matches= profiles.stream()
				.filter(profile -> profile.identity().bindingKey().equals(bindingKey))
				.toList();
		return matches.size() == 1 ? matches.get(0) : null;
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

	private record Topology(
			FlowNode caller,
			List<FlowNode> parameters,
			List<LocatedFlowEdge> argumentEdges) {

		private Topology {
			parameters= List.copyOf(parameters);
			argumentEdges= List.copyOf(argumentEdges);
		}
	}

	private record Signature(SignatureAtomicityGroup group) {
	}
}
