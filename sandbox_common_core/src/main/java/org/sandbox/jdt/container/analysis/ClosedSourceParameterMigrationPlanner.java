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
import java.util.List;
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
 * Builds the first aggregate two-compilation-unit caller/parameter rewrite plan.
 *
 * <p>The accepted topology is deliberately narrow: one local array source, one direct
 * {@code ARGUMENT_TO_PARAMETER} edge and one source-resolved parameter declaration in
 * a different compilation unit. The unchanged argument is tied to the exact target
 * method handle and parameter index before either member plan is emitted.</p>
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
					"The first aggregate rewrite requires one local caller and one direct source parameter edge.")); //$NON-NLS-1$
			return PlanningResult.rejected(diagnostics);
		}
		Signature signature= signature(signaturePlan, topology.parameter());
		if (signature == null) {
			diagnostics.add(diagnostic(
					DiagnosticKind.SIGNATURE_PLAN_MISMATCH,
					"The automatic signature plan does not contain the exact parameter member.")); //$NON-NLS-1$
			return PlanningResult.rejected(diagnostics);
		}
		ContainerUsageProfile callerProfile= profile(
				memberProfiles, topology.caller().bindingKey());
		ContainerUsageProfile parameterProfile= profile(
				memberProfiles, topology.parameter().bindingKey());
		if (callerProfile == null || parameterProfile == null) {
			diagnostics.add(diagnostic(
					DiagnosticKind.PROFILE_NOT_FOUND,
					"Caller and parameter profiles must both be present exactly once.")); //$NON-NLS-1$
			return PlanningResult.rejected(diagnostics);
		}
		if (!recommendation.sourceProfile().equals(callerProfile)
				|| !recommendation.targetContract().equals(signaturePlan.targetContract())) {
			diagnostics.add(diagnostic(
					DiagnosticKind.RECOMMENDATION_MISMATCH,
					"The recommendation does not describe the exact aggregate caller and signature target.")); //$NON-NLS-1$
			return PlanningResult.rejected(diagnostics);
		}

		ArgumentTransfer transfer= new ArgumentTransfer(
				signature.member().javaElementHandle(),
				signature.group().signatureIndex(),
				topology.edge().sourceStart(),
				topology.edge().sourceLength());
		ContainerLocalRewritePlan.PlanningResult callerResult= localPlanner.plan(
				localComponent(topology.caller()),
				recommendation,
				readiness,
				List.of(transfer));
		if (!callerResult.ready()) {
			callerResult.diagnostics().forEach(item -> diagnostics.add(diagnostic(
					DiagnosticKind.LOCAL_REWRITE_REJECTED,
					item.kind() + ": " + item.message()))); //$NON-NLS-1$
		}
		ContainerParameterRewritePlan.PlanningResult parameterResult=
				parameterPlanner.plan(
						component,
						signaturePlan,
						signature.group(),
						signature.member(),
						parameterProfile,
						readiness);
		if (!parameterResult.ready()) {
			parameterResult.diagnostics().forEach(item -> diagnostics.add(diagnostic(
					DiagnosticKind.PARAMETER_REWRITE_REJECTED,
					item.kind() + ": " + item.message()))); //$NON-NLS-1$
		}
		if (!diagnostics.isEmpty()) {
			return PlanningResult.rejected(diagnostics);
		}

		ContainerLocalRewritePlan callerPlan= callerResult.plan().orElseThrow();
		ContainerParameterRewritePlan parameterPlan=
				parameterResult.plan().orElseThrow();
		if (callerPlan.compilationUnitHandle()
				.equals(parameterPlan.compilationUnitHandle())) {
			return PlanningResult.rejected(List.of(diagnostic(
					DiagnosticKind.SAME_COMPILATION_UNIT,
					"The first aggregate slice requires caller and parameter in distinct units."))); //$NON-NLS-1$
		}
		return PlanningResult.accepted(new ClosedSourceParameterMigrationPlan(
				recommendation.targetContract(), callerPlan, parameterPlan));
	}

	private static Topology topology(ContainerFlowComponent component) {
		if (component.closureStatus() != ClosureStatus.LOCAL_CLOSED
				|| !component.diagnostics().isEmpty()
				|| component.nodes().size() != 2
				|| component.edges().size() != 1) {
			return null;
		}
		LocatedFlowEdge edge= component.edges().get(0);
		if (edge.kind() != EdgeKind.ARGUMENT_TO_PARAMETER) {
			return null;
		}
		FlowNode source= component.node(edge.sourceNodeId()).orElse(null);
		FlowNode target= component.node(edge.targetNodeId()).orElse(null);
		if (source == null || target == null
				|| source.kind() != NodeKind.LOCAL_VARIABLE
				|| target.kind() != NodeKind.PARAMETER
				|| !source.sourceResolved()
				|| !target.sourceResolved()
				|| source.compilationUnitHandle().equals(target.compilationUnitHandle())
				|| !edge.compilationUnitHandle().equals(source.compilationUnitHandle())) {
			return null;
		}
		return new Topology(source, target, edge);
	}

	private static Signature signature(
			ContainerSignatureMigrationPlan plan,
			FlowNode parameter) {
		if (plan.status() != PlanningStatus.CLOSED_SOURCE_AUTOMATIC
				|| plan.groups().size() != 1) {
			return null;
		}
		SignatureAtomicityGroup group= plan.groups().get(0);
		if (group.positionKind() != PositionKind.PARAMETER
				|| group.signatureIndex() != parameter.signatureIndex()
				|| group.members().size() != 1) {
			return null;
		}
		SignatureMember member= group.members().get(0);
		return member.flowNodeId().equals(parameter.stableId())
				&& member.compilationUnitHandle()
						.equals(parameter.compilationUnitHandle())
				&& member.javaElementHandle().equals(parameter.javaElementHandle())
						? new Signature(group, member) : null;
	}

	private static ContainerUsageProfile profile(
			List<ContainerUsageProfile> profiles,
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
			FlowNode parameter,
			LocatedFlowEdge edge) {
	}

	private record Signature(
			SignatureAtomicityGroup group,
			SignatureMember member) {
	}
}
